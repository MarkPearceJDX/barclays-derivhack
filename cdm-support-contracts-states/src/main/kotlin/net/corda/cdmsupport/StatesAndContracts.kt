package net.corda.cdmsupport

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import org.isda.cdm.ExecutionState

class CDMEvent : Contract {


    companion object {
        val ID = "net.corda.cdmsupport.CDMEvent"
    }

    interface Commands : CommandData {
        class Affirmation() : Commands
        class Execution(val outputIndex: Int) : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        /*tx.commands.forEach {
            when (it.value) {
                is Commands.Execution -> verifyExecution(tx)
                is Commands.Affirmation -> {
                }
            }
        }*/
    }

    private fun verifyExecution(tx: LedgerTransaction) {
        requireThat {
            "Execution is and issuance command, thus it cannot have input states." using (tx.inputStates.count() == 0)
        }
    }

    private fun verifyAllocation(tx: LedgerTransaction) {
        requireThat {
            "Allocation command requires exactly one input state." using (tx.inputStates.count() == 1)
            "Allocation command must result in exactly 2 output states (subject to change in the future)." using (tx.outputStates.count() == 2)
            "Trade quantities of the newly allocated states does not match the original execution quantity." using (verifyAllocationQuantity(tx))
        }
    }

    private fun verifyAllocationQuantity(tx: LedgerTransaction): Boolean {
        val inputState = tx.inputStates[0] as ExecutionState
        val outputState1 = tx.outputStates[0] as ExecutionState
        val outputState2 = tx.outputStates[1] as ExecutionState

        return outputState1.execution.quantity.amount + outputState2.execution.quantity.amount == inputState.execution.quantity.amount
    }


}











