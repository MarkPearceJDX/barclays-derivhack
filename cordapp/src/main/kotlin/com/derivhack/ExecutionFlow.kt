package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.derivhack.subflows.SendToXceptorFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.external.OutputClient
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import org.joda.time.DateTime
import java.io.File
import java.lang.Exception
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@InitiatingFlow
@StartableByRPC
class ExecutionFlow(val executionJson: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to convert trades from CDM representation to work towards Corda by loading
     *  the JSON files for the execution events provided for the UC1 (UC1_Block_Trade_BT1.json ...),
     *  and using the parseEventFromJson function from the cdm-support package to
     *  create an Execution CDM Object and Execution State working with the CDMTransactionBuilder as well
     *  as also validate the trade against CDM data rules by using the CDMValidators.
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        var uniqueRef: String = ""

        try {
            val newTradeEvent = parseEventFromJson(executionJson)
            CdmValidators().validateEvent(newTradeEvent)
            uniqueRef = newTradeEvent.meta.globalKey

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val cdmTransactionBuilder = CdmTransactionBuilder(notary, newTradeEvent, DefaultCdmVaultQuery(serviceHub))

            cdmTransactionBuilder.verify(serviceHub)

            val signedTxByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
            val tx = signedTxByMe.toLedgerTransaction(serviceHub, false)
            if (!CdmValidators().validateExecution((tx.outputStates.first() as ExecutionState).execution()).all { it.isSuccess })
                throw FlowException("Execution state is invalid.")

            val counterpartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
            val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

            val fullySignedTx = subFlow(CollectSignaturesFlow(signedTxByMe, counterpartySessions, CollectSignaturesFlow.tracker()))
            val finalityTx = subFlow(FinalityFlow(fullySignedTx, counterpartySessions))

            subFlow(ObserverFlow(regulator, finalityTx))
            subFlow(SendToXceptorFlow(ourIdentity, finalityTx))

            return finalityTx
        } catch (e: FlowException) {
            OutputClient(ourIdentity).sendExceptionToXceptor(uniqueRef, e.message ?: "")
            throw e
        }
    }
}

@InitiatedBy(ExecutionFlow::class)
class ExecutionFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        var uniqueRef: String = ""

        try {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val tx = stx.toLedgerTransaction(serviceHub, false)
                    tx.verify()
                    if (!CdmValidators().validateExecution((tx.outputStates.first() as ExecutionState).execution()).all { it.isSuccess })
                        throw FlowException("Execution state is invalid.")
                }
            }

            val signedId = subFlow(signedTransactionFlow)
            signedId.verify(serviceHub, true)

            subFlow(SendToXceptorFlow(ourIdentity, signedId))
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedId.id))
        } catch (e: FlowException) {
            OutputClient(ourIdentity).sendExceptionToXceptor(uniqueRef, e.message ?: "")
            throw e
        }
    }
}
