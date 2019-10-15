package net.corda.cdmsupport.functions

import CDMBuilders
import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.Confirmation

fun confirmationBuilderFromExecution(state : ExecutionState) : Confirmation {
    val execution = state.execution()
    val lineage = CDMBuilders.buildLineage(state.eventReference, state.execution().meta.globalKey)

    return CDMBuilders.buildConfirmation(
            execution.meta.globalKey,
            execution.party.map { it.value }.toMutableList(),
            execution.partyRole,
            lineage).build()
}
