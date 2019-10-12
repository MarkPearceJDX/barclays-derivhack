package com.derivhack

import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionFlowTest() : FlowTestBase() {

    @Test
    fun execution() {

        val executionJson = File("C:/Users/maros.struk/source/repos/TradeData/UC1_Block_Trade_BT1.json").readText()
        val executionFlow = ExecutionFlow(executionJson)

        val future = node2.services.startFlow(executionFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)

        tx.commands
    }

}