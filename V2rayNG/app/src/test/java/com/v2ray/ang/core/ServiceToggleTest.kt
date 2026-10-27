package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServiceToggleTest {
    @Test fun absentAndStoppedDaemonAllowStart() {
        assertEquals(ToggleAction.Start, ServiceToggle.decide(0, false, false))
        assertEquals(ToggleAction.Start, ServiceToggle.decide(AppConfig.MSG_STATE_NOT_RUNNING, false, false))
    }
    @Test fun runningStartingStoppingAndUnknownRequireStop() {
        listOf(AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_STARTING, AppConfig.MSG_STATE_STOPPING, -1, 987, null).forEach {
            assertEquals(ToggleAction.Stop, ServiceToggle.decide(it, false, false))
        }
    }
    @Test fun pendingStartCanBeCancelledBeforeReceiverRegisters() {
        assertEquals(ToggleAction.Stop, ServiceToggle.decide(0, true, false))
    }
    @Test fun repeatedStopCannotStartAnotherSession() {
        assertEquals(ToggleAction.Ignore, ServiceToggle.decide(0, false, true))
    }
    @Test fun onlyOwnedDaemonMayBeKilled() {
        assertTrue(DaemonIdentity.owned(20, 1001, "com.v2ray.ang:daemon", 10, 1001, "com.v2ray.ang"))
        assertFalse(DaemonIdentity.owned(10, 1001, "com.v2ray.ang:daemon", 10, 1001, "com.v2ray.ang"))
        assertFalse(DaemonIdentity.owned(20, 1002, "com.v2ray.ang:daemon", 10, 1001, "com.v2ray.ang"))
        assertFalse(DaemonIdentity.owned(20, 1001, "other:daemon", 10, 1001, "com.v2ray.ang"))
    }
}
