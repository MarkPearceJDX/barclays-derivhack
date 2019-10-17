package com.derivhack

import CDMBuilders
import co.paralleluniverse.fibers.Suspendable
import com.derivhack.subflows.SendToXceptorFlow
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.ValidationUnsuccessfull
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.external.OutputClient
import net.corda.cdmsupport.functions.confirmationBuilderFromExecution
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.ConfirmationStatusEnum

@InitiatingFlow
@StartableByRPC
class ConfirmationFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    /**
     *  You're expected to generate relevant CDM objects and link them to associated allocated
     *  trades created with UC3 as well as validate them against CDM data rules by
     *  creating validations similar to those for the previous use cases
     *
     *  For building your confirmation CDM Object see net.corda.cdmsupport.builders
     *  package in the project
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        var uniqueRef: String = ""

        try {
            val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
            val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

            val state = stateAndRef.state.data
            uniqueRef = state.execution().meta.globalKey

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val participants = state.participants.map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) }

            val builder = TransactionBuilder(notary)

            val confirmation = confirmationBuilderFromExecution(state)
            val confirmationState = ConfirmationState(serializeCdmObjectIntoJson(confirmation), participants)
            val executionState = state.copy(workflowStatus = ConfirmationStatusEnum.CONFIRMED.name)

            builder.addInputState(stateAndRef)
            builder.addCommand(CDMEvent.Commands.Confirmation(), participants.map { it.owningKey })
            builder.addOutputState(confirmationState)
            builder.addOutputState(executionState)

            builder.verify(serviceHub)

            val signedTransaction = serviceHub.signInitialTransaction(builder)

            val session = participants.minus(ourIdentity).map { initiateFlow(it) }

            val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

            val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

            val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

            subFlow(ObserverFlow(regulator, finalityTx))
            subFlow(SendToXceptorFlow(ourIdentity, finalityTx))

            return finalityTx
        } catch (e: Exception) {
            OutputClient(ourIdentity).sendTextToFile("${uniqueRef}: ${e.message}")
            throw e
        }
    }
}

@InitiatedBy(ConfirmationFlow::class)
class ConfirmationFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        try {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    stx.toLedgerTransaction(serviceHub, false).verify()
                }
            }

            val signedId = subFlow(signedTransactionFlow)

            subFlow(SendToXceptorFlow(ourIdentity, signedId))

            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedId.id))
        } catch (e: Exception) {
            OutputClient(ourIdentity).sendTextToFile(": ${e.message}")
            throw e
        }
    }
}