package com.derivhack

import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AllocationFlowTest : BaseFlowTest() {

    /*@Test
    fun allocateOne() {
        execute("/${samplesDir}/UC1_block_execute_BT1.json")
        // has a few extra checks compared to allocate()
        allocateSingle()
    }*/
    
    @Test
    fun allocateMany() {
        File("/${samplesDir}/UC1/").walk().forEach {
            execute(it.path)
        }
        File("/${samplesDir}/UC2/").walk().forEach {
            allocate(it.path)
        }
    }
}