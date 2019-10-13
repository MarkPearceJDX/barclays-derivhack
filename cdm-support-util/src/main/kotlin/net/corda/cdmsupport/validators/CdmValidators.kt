package net.corda.cdmsupport.validators

import com.rosetta.model.lib.RosettaModelObject
import com.rosetta.model.lib.meta.RosettaMetaData
import com.rosetta.model.lib.path.RosettaPath
import com.rosetta.model.lib.validation.ValidationResult
import com.rosetta.model.lib.validation.Validator
import org.isda.cdm.*
import org.isda.cdm.meta.*

class CdmValidators() {

    fun validateEvent(event: Event): List<ValidationResult<in Event>> {
        val eventMeta = EventMeta()
        val validators = ArrayList<Validator<in Event>>()
        validators.addAll(eventMeta.choiceRuleValidators())
        validators.addAll(eventMeta.dataRules())
        validators.add(eventMeta.validator())

        return validators.map { it.validate(RosettaPath.valueOf("Event"), event) }.toList()
    }

    fun validateExecution(execution: Execution): List<ValidationResult<in Execution>> {
        val validators = getCdmObjectValidators<Execution>(ExecutionMeta())
        return validators.map { it.validate(RosettaPath.valueOf("Execution"), execution) }.toList()
    }

    fun validateExecutionPrimitive(executionPrimitive: ExecutionPrimitive): List<ValidationResult<in ExecutionPrimitive>> {
        val validators = getCdmObjectValidators<ExecutionPrimitive>(ExecutionPrimitiveMeta())
        return validators.map { it.validate(RosettaPath.valueOf("ExecutionPrimitive"), executionPrimitive) }.toList()
    }

    fun validateAllocationPrimitive(allocationPrimitive: AllocationPrimitive): List<ValidationResult<in AllocationPrimitive>> {
        val validators = getCdmObjectValidators<AllocationPrimitive>(AllocationPrimitiveMeta())
        return validators.map { it.validate(RosettaPath.valueOf("AllocationPrimitive"), allocationPrimitive) }.toList()
    }

    fun validateAffirmation(affirmation: Affirmation): List<ValidationResult<in Affirmation>> {
        val validators = getCdmObjectValidators<Affirmation>(AffirmationMeta())
        return validators.map { it.validate(RosettaPath.valueOf("Affirmation"), affirmation) }.toList()
    }

    fun <T: RosettaModelObject>getCdmObjectValidators(metaData: RosettaMetaData<T>) : ArrayList<Validator<in T>> {
        val validators = ArrayList<Validator<in T>>()
        validators.addAll(metaData.choiceRuleValidators())
        validators.addAll(metaData.dataRules())
        validators.add(metaData.validator())
        return validators
    }

}
