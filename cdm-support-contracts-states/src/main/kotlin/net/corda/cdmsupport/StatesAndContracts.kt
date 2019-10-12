package net.corda.cdmsupport

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class CDMEvent : Contract {


    companion object {
        val ID = "net.corda.cdmsupport.CDMEvent"
    }

    interface Commands : CommandData {
        class Affirmation() : Commands
        class Execution(val outputIndex: Int) : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.findCommand<Commands> { true }

        when (command.value) {
            is Commands.Execution -> {
                verifyExecution(tx)
            }
            is Commands.Affirmation -> {

            }
        }
    }

    private fun verifyExecution(tx: LedgerTransaction) {
        requireThat {
            "Execution is and issuance command, thus it cannot have input states." using (tx.inputStates.count() == 0)

        }
    }


}











