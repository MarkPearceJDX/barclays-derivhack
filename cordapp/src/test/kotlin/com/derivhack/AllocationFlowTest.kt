package com.derivhack

import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AllocationFlowTest : BaseFlowTest() {

    @Test
    fun allocation() {
        // --------- new trade
        val executionJson = readTextFromFile("/${samplesDir}/UC1_block_execute_BT1.json")
        val executionFlow = ExecutionFlow(executionJson)

        val future1 = node2.services.startFlow(executionFlow).resultFuture
        checkTxAssertions(future1.getOrThrow().toLedgerTransaction(node2.services))

        //----------------allocation
        val allocationJson = readTextFromFile("/${samplesDir}/UC2_allocation_execution_AT1.json")
        val future2 = node2.services.startFlow(AllocationFlow(allocationJson)).resultFuture
        val tx = future2.getOrThrow().toLedgerTransaction(node2.services)
        checkTheBasicFabricOfTheTransaction(tx, 1, 3, 0, 3)

        //look closer at the states
        val executionInputState = tx.inputStates.find { it is ExecutionState } as ExecutionState
        val cdmExecutionInputState = executionInputState.execution()
        assertNotNull(cdmExecutionInputState)
        checkIdentiferIsOnTrade(cdmExecutionInputState, "W3S0XZGEM4S82", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")

        val executionStateOutputStateOne = tx.outputStates.find { it is ExecutionState } as ExecutionState
        val executionStateOutputStateTwo = tx.outputStates.findLast { it is ExecutionState } as ExecutionState

        assertNotNull(executionStateOutputStateOne)
        assertNotNull(executionStateOutputStateTwo)

        val cdmExecutionStateOutputStateOne = executionStateOutputStateOne.execution()
        val cdmExecutionStateOutputStateTwo = executionStateOutputStateTwo.execution()

        assertNotNull(cdmExecutionStateOutputStateOne)
        assertNotNull(cdmExecutionStateOutputStateTwo)
        checkIdentiferIsOnTrade(cdmExecutionStateOutputStateOne, "W3S0XZGEM4S82", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")
        checkIdentiferIsOnTrade(cdmExecutionStateOutputStateTwo, "ST2K6U8RHX7MZ", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")

        //look closer at the commands
        assertEquals(listOf(party2.owningKey, party3.owningKey), tx.commands.get(0).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(1).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(2).signers)
    }

}