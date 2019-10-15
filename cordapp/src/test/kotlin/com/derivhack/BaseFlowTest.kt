package com.derivhack

import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.Execution
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class BaseFlowTest(val samplesDir: String = "jsons") {
    lateinit var mockNetwork: InternalMockNetwork
    lateinit var node1: TestStartedNode
    lateinit var node2: TestStartedNode
    lateinit var node3: TestStartedNode
    lateinit var node4: TestStartedNode
    lateinit var party1: Party
    lateinit var party2: Party
    lateinit var party3: Party
    lateinit var party4: Party

    @Before
    fun setup() {
        mockNetwork = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("com.derivhack", "net.corda.cdmsupport"),
                threadPerNode = true, initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4))
        node1 = mockNetwork.createPartyNode(CordaX500Name(organisation = "Client1", locality = "New York", country = "US"))
        node2 = mockNetwork.createPartyNode(CordaX500Name(organisation = "Broker1", locality = "New York", country = "US"))
        node3 = mockNetwork.createPartyNode(CordaX500Name(organisation = "Broker2", locality = "New York", country = "US"))
        node4 = mockNetwork.createPartyNode(CordaX500Name(organisation = "Observery", locality = "New York", country = "US"))
        party1 = node1.services.myInfo.legalIdentities.first()
        party2 = node2.services.myInfo.legalIdentities.first()
        party3 = node3.services.myInfo.legalIdentities.first()
        party4 = node4.services.myInfo.legalIdentities.first()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    protected fun execute(jsonPath: String) {
        val executionJson = readTextFromFile(jsonPath)
        val executionFlow = ExecutionFlow(executionJson)

        val future = node2.services.startFlow(executionFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)

        checkTxAssertions(tx)
    }

    protected fun allocate(jsonPath: String): List<ExecutionState> {
        val allocationJson = readTextFromFile(jsonPath)
        val allocationFlow = AllocationFlow(allocationJson)

        val future = node2.services.startFlow(allocationFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)

        checkTheBasicFabricOfTheTransaction(tx, 1, 3, 0, 3)

        val executionStateOutputStateOne = tx.outputStates[0] as ExecutionState
        val executionStateOutputStateTwo = tx.outputStates[1] as ExecutionState
        val executionStateOutputStateThree = tx.outputStates[2] as ExecutionState

        assertNotNull(executionStateOutputStateOne)
        assertNotNull(executionStateOutputStateTwo)
        assertNotNull(executionStateOutputStateThree)

        val cdmExecutionStateOutputStateOne = executionStateOutputStateOne.execution()
        val cdmExecutionStateOutputStateTwo = executionStateOutputStateTwo.execution()
        val cdmExecutionStateOutputStateThree = executionStateOutputStateThree.execution()

        assertNotNull(cdmExecutionStateOutputStateOne)
        assertNotNull(cdmExecutionStateOutputStateTwo)
        assertNotNull(cdmExecutionStateOutputStateThree)

        //look closer at the commands
        assertEquals(listOf(party2.owningKey, party3.owningKey), tx.commands.get(0).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(1).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(2).signers)

        // Return states to affirm (can't affirm closed states).
        return tx.outputStates.filter{ (it as ExecutionState).execution().closedState == null }.map { it as ExecutionState }
    }

    // has a few extra checks compared to allocate()
    protected fun allocateSingle(): List<ExecutionState> {
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

        // Return states to affirm (can't affirm closed states).
        return tx.outputStates.filter{ (it as ExecutionState).execution().closedState == null }.map { it as ExecutionState }
    }

    protected fun affirm(executionRef: String) {
        val future = node1.services.startFlow(AffirmationFlow(executionRef)).resultFuture
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

    protected fun confirm(executionRef: String) {
        val future = node1.services.startFlow(ConfirmationFlow(executionRef)).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node1.services)

        checkTheBasicFabricOfTheTransaction(tx, 1, 2, 0, 1)

        val inputState = tx.inputStates.find { it is ExecutionState } as ExecutionState
        val confirmationState = tx.outputStates.find { it is ConfirmationState } as ConfirmationState
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState

        CdmValidators().validateConfirmation(confirmationState.confirmation())

        assertNotNull(inputState)
        assertNotNull(confirmationState)
        assertNotNull(executionState)

        //look closer at the commands
        assertTrue(tx.commands.get(0).value is CDMEvent.Commands.Confirmation)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(0).signers)
    }

    protected fun checkTxAssertions(tx: LedgerTransaction) {
        checkTheBasicFabricOfTheTransaction(tx, 0, 1, 0, 1)

        //look closer at the states
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState
        assertNotNull(executionState)
        //assertEquals(newTradeEvent.primitive.execution[0].after.execution, executionState.execution())
        assertEquals(listOf(party2, party3), executionState.participants)

        //look closer at the commands
        assertTrue(tx.commands.get(0).value is CDMEvent.Commands.Execution)
        assertEquals(listOf(party2.owningKey, party3.owningKey), tx.commands.get(0).signers)
    }


    //confirming things
    protected fun checkTheBasicFabricOfTheTransaction(tx: LedgerTransaction, numInputStates: Int, numOutputStates: Int, numReferenceStates: Int, numCommands: Int) {
        assertEquals(numInputStates, tx.inputs.size)
        assertEquals(numReferenceStates, tx.referenceStates.size)
        assertEquals(numOutputStates, tx.outputStates.size)
        assertEquals(numCommands, tx.commands.size)
    }

    protected fun checkIdentiferIsOnTrade(cdmExecutionInputState: Execution, identifier: String, issuerReference: String) {
        assertTrue(cdmExecutionInputState.identifier.any { it.issuerReference.globalReference == issuerReference && it.assignedIdentifier[0].identifier.value == identifier })
    }
}