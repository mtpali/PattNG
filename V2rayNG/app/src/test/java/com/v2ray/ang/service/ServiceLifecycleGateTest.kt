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

    @Test fun explicitStopCancelsAPendingRestartAndLaterRestartCannotUndoIt() {
        val gate = ServiceLifecycleGate()
        gate.start()
        assertTrue(gate.stop(restart = true))
        assertFalse(gate.stop())
        assertFalse(gate.stop(restart = true))
        assertFalse(gate.consumeRestart())
    }

    @Test fun aCompletedRestartIsConsumedOnlyOnce() {
        val gate = ServiceLifecycleGate()
        gate.start()
        gate.stop(restart = true)
        assertTrue(gate.consumeRestart())
        assertFalse(gate.consumeRestart())
    }

    @Test fun cancelledAppStartsAreRejectedWhileAndroidOwnedStartsStayValid() {
        assertTrue(ServiceStartToken.valid("session-a", "session-a"))
        assertFalse(ServiceStartToken.valid("session-a", ""))
        assertFalse(ServiceStartToken.valid("session-a", "session-b"))
        assertFalse(ServiceStartToken.valid("session-a", null))
        assertTrue(ServiceStartToken.valid(null, ""))
    }
}
