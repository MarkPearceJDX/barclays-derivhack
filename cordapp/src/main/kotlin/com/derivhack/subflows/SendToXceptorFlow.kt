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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@StartableByRPC
class SendToXceptorFlow(val identity: Party, val stx: SignedTransaction): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val tx = stx.toLedgerTransaction(serviceHub, false)
        //val client = OutputClient(identity)

        tx.outputStates.filter{ it is ExecutionState }?.map { (it as ExecutionState).executionJson }.forEach { sendJson(it) }
        tx.outputStates.filter{ it is AffirmationState }?.map { (it as AffirmationState).affirmationJson }.forEach { sendJson(it) }
        tx.outputStates.filter{ it is ConfirmationState }?.map { (it as ConfirmationState).confirmationJson }.forEach { sendJson(it) }
    }

    private fun sendJson(json: String) {
        val sdf = SimpleDateFormat("ddMMyyyyhhmmss")
        val currentDate = sdf.format(Date())
        File("C:/Temp/${identity.name.organisation}/${currentDate}.json").writeText(json)
    }

}