package com.v2ray.ang.core

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.ProfileTraffic
import com.v2ray.ang.dto.entities.TrafficSnapshot
import com.v2ray.ang.extension.toCompactTrafficString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileTrafficTest {
    @Test fun combinesTrafficIntoOneCompactValueWithoutChangingTheStoredDirections() {
        val traffic = ProfileTraffic(256 * 1024, 768 * 1024)
        assertEquals(1048576L, traffic.totalBytes())
        assertEquals("1 MB", traffic.totalBytes().toCompactTrafficString())
        assertEquals("1.5 MB", (1572864L).toCompactTrafficString())
        assertEquals("0 B", 0L.toCompactTrafficString())
        assertEquals(Long.MAX_VALUE, ProfileTraffic(Long.MAX_VALUE, Long.MAX_VALUE).totalBytes())
        assertEquals(7L, ProfileTraffic(-2, 7).totalBytes())
        assertEquals(traffic, Gson().fromJson(Gson().toJson(traffic), ProfileTraffic::class.java))
    }
    @Test fun unusedAndResetProfilesHaveNoVisibleUsage() {
        assertFalse(ProfileTraffic().hasUsage())
        assertFalse(ProfileTraffic(0, 0).hasUsage())
        assertTrue(ProfileTraffic(1, 0).hasUsage())
        assertTrue(ProfileTraffic(0, 1).hasUsage())
        assertFalse(TrafficSnapshot("reset").profiles.containsKey("used-guid"))
    }

    @Test fun accumulatesBothDirectionsAndIgnoresNegativeDeltas() {
        assertEquals(ProfileTraffic(12, 24), ProfileTraffic(5, 9).add(7, 15))
        assertEquals(ProfileTraffic(5, 9), ProfileTraffic(5, 9).add(-1, -2))
        assertEquals(Long.MAX_VALUE, ProfileTraffic(Long.MAX_VALUE - 1, 0).add(9, 0).upload)
    }
    @Test fun retainsServerIdentityAcrossStorageAndReset() {
        val original = TrafficSnapshot(profiles = mutableMapOf("guid-b" to ProfileTraffic(2, 8), "guid-a" to ProfileTraffic(4, 6)))
        val restored = Gson().fromJson(Gson().toJson(original), TrafficSnapshot::class.java)
        assertEquals(ProfileTraffic(2, 8), restored.profiles["guid-b"])
        assertEquals(ProfileTraffic(4, 6), restored.profiles["guid-a"])
        val reset = TrafficSnapshot("new-generation")
        assertNotEquals(restored.generation, reset.generation)
        assertTrue(reset.profiles.isEmpty())
    }
}
