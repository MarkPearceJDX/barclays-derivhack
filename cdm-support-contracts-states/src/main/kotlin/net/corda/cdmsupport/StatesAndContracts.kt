package net.corda.cdmsupport

import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.Vault
import net.corda.core.transactions.LedgerTransaction
import org.isda.cdm.Event
import java.math.BigDecimal
import java.time.LocalDate

class CDMEvent : Contract {

    companion object {
        val ID = "net.corda.cdmsupport.CDMEvent"
    }

    interface Commands : CommandData {
        class Affirmation() : Commands
        class Confirmation() : Commands
        class Portfolio() : Commands
        class Execution(val outputIndex: Int) : Commands
        class Transfer(val outputIndex: Int) : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        if (tx.commands.count() > 1) {
            verifyAllocation(tx)
        } else {
            val command = tx.findCommand<Commands> { true }
            when (command.value) {
                is Commands.Execution -> verifyExecution(tx)
                is Commands.Affirmation -> verifyAffirmation(tx)
                is Commands.Confirmation -> verifyConfirmation(tx)
            }
        }
    }

    private fun verifyExecution(tx: LedgerTransaction) {
        requireThat {
            "Execution is and issuance command, thus it cannot have input states." using (tx.inputStates.count() == 0)
            "Execution is invalid" using (CdmValidators().validateExecution((tx.outputStates.first() as ExecutionState).execution()).all { it.isSuccess })
        }
    }

    private fun verifyAllocation(tx: LedgerTransaction) {
        requireThat {
            "Allocation command requires exactly one input state." using (tx.inputStates.count() == 1)
            "Allocation command must result in exactly 3 output states (subject to change in the future)." using (tx.outputStates.count() == 3)
            "Closed state identifier does not match the input state identifier." using (verifyAllocationClosedState(tx))
            "Trade quantities of the newly allocated states does not match the original execution quantity." using (verifyAllocationQuantity(tx))
            "One or more allocation states are invalid" using (tx.outputStates.all { CdmValidators().validateExecution((it as ExecutionState).execution()).all { it.isSuccess } })
        }
    }

    private fun verifyAffirmation(tx: LedgerTransaction) {
        requireThat {
            "Affirmation requires one execution input state." using (tx.inputStates.firstOrNull() { it is ExecutionState } != null)
            "Affirmation requires two output states, where one is execution state." using (tx.outputStates.firstOrNull() { it is ExecutionState } != null)
            "Affirmation requires two output states, where one is affirmation state." using (tx.outputStates.firstOrNull() { it is AffirmationState } != null)
            "Output affirmation state must reference the input execution state." using (verifyAffirmationStatesLineage(tx))
        }
    }

    private fun verifyConfirmation(tx: LedgerTransaction) {
        requireThat {
            "Confirmation requires one execution input state." using (tx.inputStates.firstOrNull() { it is ExecutionState } != null)
            "Confirmation requires two output states, where one is execution state." using (tx.outputStates.firstOrNull() { it is ExecutionState } != null)
            "Confirmation requires two output states, where one is confirmation state." using (tx.outputStates.firstOrNull() { it is ConfirmationState } != null)
            "Output confirmation state must reference the input execution state." using (verifyAffirmationStatesLineage(tx))
        }
    }

    private fun verifyAllocationQuantity(tx: LedgerTransaction): Boolean {
        val inputExecution = (tx.inputStates[0] as ExecutionState).execution()
        val outputExecutions = tx.outputStates.map { (it as ExecutionState).execution() }
        val allocatedQuantity = outputExecutions.filter { it.closedState == null }.sumByDouble { it.quantity.amount.toDouble() }

        return inputExecution.quantity.amount.toDouble() == allocatedQuantity
    }

    private fun verifyAllocationClosedState(tx: LedgerTransaction) : Boolean {
        val inputExecution = (tx.inputStates[0] as ExecutionState).execution()
        val closedExecution = tx.outputStates.map { (it as ExecutionState).execution() }.first { it.closedState != null }

        return inputExecution.identifier == closedExecution.identifier
    }

    private fun verifyAffirmationStatesLineage(tx: LedgerTransaction): Boolean {
        val inputExecution = (tx.inputStates.first() as ExecutionState).execution()
        val outputExecution = (tx.outputStates.first() { it is ExecutionState } as ExecutionState).execution()
        val outputAffirmation = (tx.outputStates.first() { it is AffirmationState } as AffirmationState).affirmation()

        return inputExecution.meta.globalKey == outputExecution.meta.globalKey &&
                inputExecution.meta.globalKey == outputAffirmation.lineage.executionReference.first().globalReference
    }

    private fun verifyConfirmationStatesLineage(tx: LedgerTransaction): Boolean {
        val inputExecution = (tx.inputStates.first() as ExecutionState).execution()
        val outputExecution = (tx.outputStates.first() { it is ExecutionState } as ExecutionState).execution()
        val outputConfirmation = (tx.outputStates.first() { it is ConfirmationState } as ConfirmationState).confirmation()

        return inputExecution.meta.globalKey == outputExecution.meta.globalKey &&
                inputExecution.meta.globalKey == outputConfirmation.lineage.executionReference.first().globalReference
    }
}