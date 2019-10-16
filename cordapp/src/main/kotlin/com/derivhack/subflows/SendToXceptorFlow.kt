package com.derivhack.subflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.external.OutputClient
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
class SendToXceptorFlow(ourIdentity: Party, val stx: SignedTransaction): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val tx = stx.toLedgerTransaction(serviceHub, true)
        val client = OutputClient(ourIdentity)

        tx.outputStates.filter{ it is ExecutionState }?.map { (it as ExecutionState).executionJson }.forEach { client.sendJsonToXceptor(it) }
        tx.outputStates.filter{ it is AffirmationState }?.map { (it as AffirmationState).affirmationJson }.forEach { client.sendJsonToXceptor(it) }
        tx.outputStates.filter{ it is ConfirmationState }?.map { (it as ConfirmationState).confirmationJson }.forEach { client.sendJsonToXceptor(it) }
    }

}