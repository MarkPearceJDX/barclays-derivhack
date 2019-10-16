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

   /* @Test
    fun affirmOne() {
        // --------- new trade
        execute("/${samplesDir}/UC1_block_execute_BT1.json")

        //----------------allocation
        val allocatedExecutions = allocate("/${samplesDir}/UC2_allocation_execution_AT1.json")

        //-----------------affirmation
        allocatedExecutions.forEach() {
            affirm( it.execution().meta.globalKey )
        }
    }*/

    @Test
    fun affirmMany() {
        File("/${samplesDir}/UC1/").walk().forEach {
            execute(it.path)
        }
        File("/${samplesDir}/UC2/").walk().forEach {
            allocate(it.path).forEach() {
                affirm( it.execution().meta.globalKey )
            }
        }
    }
}