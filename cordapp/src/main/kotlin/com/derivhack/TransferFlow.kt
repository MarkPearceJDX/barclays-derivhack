package com.derivhack

import CDMBuilders
import co.paralleluniverse.fibers.Suspendable
import com.derivhack.subflows.SendToXceptorFlow
import com.rosetta.model.lib.meta.FieldWithMeta
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.external.OutputClient
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import org.isda.cdm.Money
import org.isda.cdm.Security
import org.isda.cdm.TransferPrimitive
import org.isda.cdm.metafields.FieldWithMetaString
import java.math.BigDecimal
import java.util.*

@InitiatingFlow
@StartableByRPC
class TransferFlow(val jsonEvent: String) : FlowLogic<SignedTransaction>() {


    //TODO
    /**
     *  You're expected to simulate transfer/settlement process, ensuring that the
     *  cash and securities transfers refer to relevant accounts through use of SSIs
     *  from the output allocated trades from UC2 as well as validate them against
     *  CDM data rules by creating validations similar to those for the previous use cases
     *
     *  For building your transfer/settlement see net.corda.cdmsupport.builders
     *  package in the project
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        val transferEvent = parseEventFromJson(jsonEvent)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, transferEvent, DefaultCdmVaultQuery(serviceHub))


        //settlementEvent.primitive.transfer

        //cdmTransactionBuilder.

        /*val transferBuilder = TransferPrimitive.builder()
        val helperBuilder = CDMBuilders()
        transferBuilder.addCashTransfer(CDMBuilders().buildCashTransfer(
                helperBuilder.buildMoney(BigDecimal(100000), "USD"),
                helperBuilder.buildPayerReceiver()))
        transferBuilder.addSecurityTransfer(CDMBuilders().buildSecurityTransfer())

        val newEvent = transferEvent.toBuilder().setPrimitive(null).build()*/

        TransferPrimitive.builder()

        cdmTransactionBuilder.verify(serviceHub)

        val signedTxByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)
        val tx = signedTxByMe.toLedgerTransaction(serviceHub, false)

        val counterpartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTxByMe, counterpartySessions, CollectSignaturesFlow.tracker()))
        val finalityTx = subFlow(FinalityFlow(fullySignedTx, counterpartySessions))

        subFlow(ObserverFlow(regulator, finalityTx))
        subFlow(SendToXceptorFlow(ourIdentity, finalityTx))

        return finalityTx
    }
}

@InitiatedBy(TransferFlow::class)
class TransferFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {

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
        } catch (e: FlowException) {
            OutputClient(ourIdentity).sendExceptionToXceptor("", e.message ?: "")
            throw e
        }
    }

}