package com.derivhack

import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
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

class AffirmationFlowTest : BaseFlowTest() {

    @Test
    fun affirm() {
        // --------- new trade
        val executionJson = readTextFromFile("/${samplesDir}/UC1_block_execute_BT1.json")
        val executionFlow = ExecutionFlow(executionJson)

        val future1 = node2.services.startFlow(executionFlow).resultFuture
        checkTxAssertions(future1.getOrThrow().toLedgerTransaction(node2.services))

        //----------------allocation
        val allocationJson = readTextFromFile("/${samplesDir}/UC2_allocation_execution_AT1.json")
        val future2 = node2.services.startFlow(AllocationFlow(allocationJson)).resultFuture
        checkTheBasicFabricOfTheTransaction(future2.getOrThrow().toLedgerTransaction(node2.services), 1, 3, 0, 3)

        //-----------------affirmation
        val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture

        val tx = future.getOrThrow().toLedgerTransaction(node1.services)

        checkTheBasicFabricOfTheTransaction(tx, 1, 2, 0, 1)

        val inputState = tx.inputStates.find { it is ExecutionState } as ExecutionState

        val affirmationState = tx.outputStates.find { it is AffirmationState } as AffirmationState
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState

        CdmValidators().validateAffirmation(affirmationState.affirmation())

        assertNotNull(inputState)
        assertNotNull(affirmationState)
        assertNotNull(executionState)

        //look closer at the commands
        assertTrue(tx.commands.get(0).value is CDMEvent.Commands.Affirmation)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(0).signers)
    }
}