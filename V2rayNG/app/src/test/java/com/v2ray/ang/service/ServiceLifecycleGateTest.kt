package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServiceLifecycleGateTest {
    @Test fun onlyOneStartIsAccepted() {
        val gate = ServiceLifecycleGate()
        assertTrue(gate.start())
        assertFalse(gate.start())
        assertTrue(gate.mayStart())
    }
    @Test fun stopDuringSetupPreventsLateStartAndRepeatedCleanup() {
        val gate = ServiceLifecycleGate()
        assertTrue(gate.start())
        assertTrue(gate.stop())
        assertFalse(gate.mayStart())
        assertFalse(gate.stop())
        assertFalse(gate.start())
    }
    @Test fun stopBeforeStartIsTerminal() {
        val gate = ServiceLifecycleGate()
        assertTrue(gate.stop())
        assertFalse(gate.start())
    }
}
