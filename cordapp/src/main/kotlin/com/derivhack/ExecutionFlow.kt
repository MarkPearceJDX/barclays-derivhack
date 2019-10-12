package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.Amount
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.TwoPartyTradeFlow
import java.util.*

@InitiatingFlow
@StartableByRPC
class ExecutionFlow(val executionJson: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to convert trades from CDM representation to work towards Corda by loading
     *  the JSON file for the execution event provided for the Use Case 1 (UC1_block_execute_BT1.json),
     *  and using the parseEventFromJson function from the cdm-support package to
     *  create an Execution CDM Object and Execution State working with the CDMTransactionBuilder as well
     *  as also validate the trade against CDM data rules by using the CDMValidators.
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        val newTradeEvent = parseEventFromJson(executionJson)
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

@InitiatedBy(ExecutionFlow::class)
class ExecutionFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

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
