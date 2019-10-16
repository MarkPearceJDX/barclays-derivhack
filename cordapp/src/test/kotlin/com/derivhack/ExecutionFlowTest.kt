package com.derivhack

import org.junit.Test
import java.io.File

class ExecutionFlowTest : BaseFlowTest() {

    /*@Test
    fun executeOne() {
        execute("/${samplesDir}/UC1_block_execute_BT1.json")
    }*/

    @Test
    fun executeMany() {
        File("/${samplesDir}/UC1/").walk().forEach {
            execute(it.path)
        }
    }

}