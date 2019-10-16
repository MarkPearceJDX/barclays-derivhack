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
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@InitiatingFlow
@StartableByRPC
class AllocationFlow(val allocationJson: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to work towards the JSON files for the allocation event provided for the
     *  Use Case 2 (UC2_Allocation_Trade_AT1.json ...), by using the parseEventFromJson function
     *  from the cdm-support package and ingest/consume the allocation trades on Corda,
     *  demonstrate lineage to the block trade from UC1 and validate the trade
     *  against CDM data rules by creating validations similar to those for UC1.
     *
     *  Bonus: Instead of just loading the ready to use Json fil, you can build your own
     *  Allocation Event, using the Allocate functions of the CDM and the Corda Implementation
     *  and builders that you can find in net.corda.cdmsupport.builders package in the project
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        var uniqueRef: String = ""

        try {
            val newTradeEvent = parseEventFromJson(allocationJson)
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val cdmTransactionBuilder = CdmTransactionBuilder(notary, newTradeEvent, DefaultCdmVaultQuery(serviceHub))
            uniqueRef = newTradeEvent.meta.globalKey

            cdmTransactionBuilder.verify(serviceHub)

            val signedTxByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
            val tx = signedTxByMe.toLedgerTransaction(serviceHub, false)
            if (!tx.outputStates.map { it as ExecutionState }.all { CdmValidators().validateExecution((it).execution()).all { it.isSuccess } })
                throw FlowException("One or more allocated execution states are invalid.")

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

@InitiatedBy(AllocationFlow::class)
class AllocationFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val tx = stx.toLedgerTransaction(serviceHub, false)
                tx.verify()
                if (!tx.outputStates.map { it as ExecutionState }.all { CdmValidators().validateExecution((it).execution()).all { it.isSuccess } })
                    throw FlowException("One or more allocated execution states are invalid.")
            }
        }

        val signedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId =  signedId.id))
    }
}