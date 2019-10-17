package com.derivhack
import co.paralleluniverse.fibers.Suspendable
import com.google.inject.Guice
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.*
import implementations.EvaluatePortfolioStateCordaImpl
import net.corda.cdmsupport.functions.portfolioBuilderFromExecutions
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.PortfolioState
import net.corda.cdmsupport.states.TransferState
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.CdmRuntimeModule

@InitiatingFlow
@StartableByRPC
class PortfolioFlow(val transferRefs: List<String>,
                    val executionRefs : List<String>,
                    val pathToInstructions : String) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val allExecutionRefs = mutableSetOf<String>()
        allExecutionRefs.addAll(executionRefs)

        val transferStatesAndRef = serviceHub.vaultService.queryBy<TransferState>().states
        val transferStates = transferStatesAndRef
                .filter { transferRefs.contains(it.state.data.transfer().meta.globalKey) }
                .map { it.state.data }

        val executionRefsFromTransfers = transferStates.map { it.executionReference }
        allExecutionRefs.addAll(executionRefsFromTransfers)
        val executionStatesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val executionStates = executionStatesAndRef
                .filter { allExecutionRefs.contains(it.state.data.execution().meta.globalKey) }
                .map { it.state.data }

        val executions = executionStates.map { it.execution() }
        executions.forEach { println(it.meta.globalKey) }

        val portfolioInstructionsJson = readTextFromFile(pathToInstructions)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)
        val portfolioFromExecutions = portfolioBuilderFromExecutions(executionStates, portfolioInstructionsJson)
        val injector = Guice.createInjector(CdmRuntimeModule())
        val func = EvaluatePortfolioStateCordaImpl(executions)

        injector.injectMembers(func)

        val eval = func.evaluate(portfolioFromExecutions)
        val outputPortfolioState = portfolioFromExecutions.toBuilder().setPortfolioState(eval).build()
        val portfolioState = PortfolioState(serializeCdmObjectIntoJson(outputPortfolioState), listOf(ourIdentity))

        builder.addCommand(CDMEvent.Commands.Portfolio(), ourIdentity.owningKey )
        builder.addOutputState(portfolioState)
        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(signedTransaction, emptyList()))
    }
}