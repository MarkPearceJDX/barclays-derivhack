package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class AllocationFlow(val allocationJson: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to work towards the JSON file for the allocation event provided for the
     *  Use Case 2 (UC2_allocation_execution_AT1.json), by using the parseEventFromJson function
     *  from the cdm-support package and ingest/consume the allocation trades on Corda,
     *  demonstrate lineage to the block trade from Use Case 1 and validate the trade
     *  against CDM data rules by creating validations similar to those for Use Case 1.
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        val newTradeEvent = parseEventFromJson(allocationJson)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, newTradeEvent, DefaultCdmVaultQuery(serviceHub))

        cdmTransactionBuilder.verify(serviceHub)

        val signedTxByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)

        val counterpartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTxByMe, counterpartySessions, CollectSignaturesFlow.tracker()))
        val finalityTx = subFlow(FinalityFlow(fullySignedTx, counterpartySessions))
        subFlow(ObserveryFlow(regulator, finalityTx))

        return finalityTx
    }
}

@InitiatedBy(AllocationFlow::class)
class AllocationFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }

        val signedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId =  signedId.id))
    }
}