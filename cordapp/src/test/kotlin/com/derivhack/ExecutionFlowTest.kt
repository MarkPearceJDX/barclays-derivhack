package com.derivhack

import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import java.io.File

class ExecutionFlowTest : BaseFlowTest() {

    @Test
    fun executeOne() {
        execute("/${samplesDir}/UC1_block_execute_BT1.json")
    }

    @Test
    fun executeMany() {
        File("/${samplesDir}/UC1/").walk().forEach {
            execute(it.path)
        }
    }

    private fun execute(jsonPath: String) {
        val executionJson = readTextFromFile(jsonPath)
        val executionFlow = ExecutionFlow(executionJson)

        val future = node2.services.startFlow(executionFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)

        checkTxAssertions(tx)
    }

}