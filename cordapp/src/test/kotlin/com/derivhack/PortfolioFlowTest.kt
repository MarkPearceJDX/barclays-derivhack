package com.derivhack

import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test

class PortfolioFlowTest: BaseFlowTest() {

    @Test
    fun getPortfolio() {
        val executionJson = readTextFromFile("/${samplesDir}/UC1_Block_Trade_BT1_old.json")
        val executionFlow = ExecutionFlow(executionJson)

        val future1 = node2.services.startFlow(executionFlow).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)

        //allocate("/${samplesDir}/UC2_allocation_execution_AT1.json")
        //allocate("/${samplesDir}/UC2_Allocation_Trade_AT1_old.json")

        val allocationJson = readTextFromFile("/${samplesDir}/UC2_Allocation_Trade_AT1_old.json")
        val allocationFlow = AllocationFlow(allocationJson)

        val future2 = node2.services.startFlow(allocationFlow).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node2.services)

        val transferJson = readTextFromFile("/${samplesDir}/UC2_Allocation_Trade_AT1_old.json")
        val transferFlow = TransferFlow(transferJson)

        val future = node2.services.startFlow(transferFlow).resultFuture
        val tx = future.getOrThrow().toLedgerTransaction(node2.services)


        val executionRefs = tx.outputStates.filter { it is ExecutionState }.map { (it as ExecutionState).execution().meta.globalKey }
        val transferRefs = tx.outputStates.filter { it is TransferState }.map { (it as TransferState).transfer().meta.globalKey}

        val portfolioFlow = PortfolioFlow(transferRefs, executionRefs, "/jsons/UC6_Portfolio_Instructions_20191016.json")
    }

}