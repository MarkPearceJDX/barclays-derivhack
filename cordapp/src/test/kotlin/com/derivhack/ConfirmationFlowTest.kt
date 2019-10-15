package com.derivhack

import org.junit.Test
import java.io.File

class ConfirmationFlowTest: BaseFlowTest() {

   /* @Test
    fun conffirmOne() {
        // --------- new trade
        execute("/${samplesDir}/UC1_block_execute_BT1.json")

        //----------------allocation
        val allocatedExecutions = allocateSingle()

        //-----------------affirmation
        allocatedExecutions.forEach() {
            affirm( it.execution().meta.globalKey )
            confirm( it.execution().meta.globalKey )
        }
    }*/

    @Test
    fun conffirmMany() {
        File("/${samplesDir}/UC1/").walk().forEach {
            execute(it.path)
        }
        File("/${samplesDir}/UC2/").walk().forEach {
            allocate(it.path).forEach() {
                affirm( it.execution().meta.globalKey )
                confirm( it.execution().meta.globalKey )
            }
        }
    }
}