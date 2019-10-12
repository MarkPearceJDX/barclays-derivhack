package com.derivhack

import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import java.io.File

class ExecutionFlowTest : BaseFlowTest() {

    @Test
    fun execution() {
        val executionJson = File("C:/Users/maros.struk/source/repos/TradeData/UC1_Block_Trade_BT1.json").readText()
        val executionFlow = ExecutionFlow(executionJson)

        val future = node2.services.startFlow(executionFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)

        checkTxAssertions(tx)
    }

}