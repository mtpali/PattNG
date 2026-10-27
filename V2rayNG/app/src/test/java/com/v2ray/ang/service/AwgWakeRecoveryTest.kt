package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AwgWakeRecoveryTest {
    @Test fun longSleepRequestsOneRecoveryEvenWithTwoWakeBroadcasts() {
        val gate = AwgWakeRecovery()
        gate.screenOff(1_000)
        gate.screenOff(2_000)
        assertTrue(gate.wake(182_000, true))
        assertFalse(gate.wake(183_000, true))
        gate.screenOff(200_000)
        assertTrue(gate.wake(400_000, true))
    }
    @Test fun shortSleepsAndOtherProtocolsDoNotRestart() {
        val gate = AwgWakeRecovery()
        assertFalse(gate.wake(10, true))
        gate.screenOff(1)
        assertFalse(gate.wake(50_000, true))
        gate.screenOff(60_000)
        assertFalse(gate.wake(400_000, false))
    }
}
