package com.derivhack

import CDMBuilders
import co.paralleluniverse.fibers.Suspendable
import com.derivhack.subflows.SendToXceptorFlow
import com.rosetta.model.lib.meta.MetaFieldsI
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.external.OutputClient
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import org.isda.cdm.*
import org.isda.cdm.metafields.FieldWithMetaString
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.rosettakey.SerialisingHashFunction
import java.io.File
import java.math.BigDecimal

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

        /*val helperBuilder = CDMBuilders()
        val transferPrimitive= buildTransferPrimitive()

        val newEvent = transferEvent.toBuilder().setPrimitive(helperBuilder.buildPrimitiveEvent(transferPrimitive)).build()
        File("C:/Temp/TransferEvent.json").writeText(serializeCdmObjectIntoJson(newEvent))*/

        cdmTransactionBuilder.verify(serviceHub)

        val effectedExecutionRefs = cdmTransactionBuilder.event.eventEffect.effectedExecution.map { it.globalReference }

        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val statesAndRefToSettle = statesAndRef.filter { effectedExecutionRefs.contains(it.state.data.execution().meta.globalKey) }
        val statesToSettle = statesAndRefToSettle.map { it.state.data }
        val settledStates = statesToSettle.map { it.copy(workflowStatus = TransferStatusEnum.SETTLED.name) }

        settledStates.forEach {
            cdmTransactionBuilder.addOutputState(it)
        }

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

    private fun buildTransferPrimitive(): TransferPrimitive {
        val transferBuilder = TransferPrimitive.builder()
        val helperBuilder = CDMBuilders()

        var account1 = helperBuilder.buildAccount("TEST_ACCT1", "PAYER123456")
        val account1Key = SerialisingHashFunction().hash(account1)
        account1 = account1.toBuilder().setMetaBuilder(MetaFields.MetaFieldsBuilder().setGlobalKey(account1Key)).build()

        var account2 = helperBuilder.buildAccount("TEST_ACCT2", "RECEIVER123")
        val account2Key = SerialisingHashFunction().hash(account2)
        account2 = account2.toBuilder().setMetaBuilder(MetaFields.MetaFieldsBuilder().setGlobalKey(account2Key)).build()

        var payer = helperBuilder.buildParty(account1, ourIdentity.name.organisation, "")
        val payerKey = SerialisingHashFunction().hash(payer)
        payer = payer.toBuilder().setMetaBuilder(MetaFields.MetaFieldsBuilder().setGlobalKey(payerKey)).build()

        val tmpParty = serviceHub.identityService.getAllIdentities().first()

        var receiver = helperBuilder.buildParty(account2, tmpParty.name.organisation, "")
        val receiverKey = SerialisingHashFunction().hash(receiver)
        receiver = receiver.toBuilder().setMetaBuilder(MetaFields.MetaFieldsBuilder().setGlobalKey(receiverKey)).build()

        transferBuilder.addCashTransfer(CDMBuilders().buildCashTransfer(
                helperBuilder.buildMoney(BigDecimal(100000), "USD"),
                helperBuilder.buildPayerReceiver(payer, receiver),
                FieldWithMetaString.FieldWithMetaStringBuilder().build()))
        transferBuilder.addSecurityTransfer(CDMBuilders().buildSecurityTransfer(
                BigDecimal(800000),
                helperBuilder.buildSecurity(helperBuilder.buildBond(helperBuilder.buildProductIdentifier("ProductIdentifierXX", ProductIdSourceEnum.CUSIP))),
                helperBuilder.buildTransferorTransferee(payer, receiver)))

        return transferBuilder.build()
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
            OutputClient(ourIdentity).sendTextToFile(e.message ?: "")
            throw e
        }
    }

}