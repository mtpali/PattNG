package com.v2ray.ang.service

/** One wake request per sleep; the monitor waits for a usable underlying network. */
internal class AwgWakeRecovery {
    private var offAt: Long? = null
    @Synchronized fun screenOff(now: Long) { if (offAt == null) offAt = now }
    @Synchronized fun wake(now: Long, amnezia: Boolean): Boolean {
        val start = offAt ?: return false
        offAt = null
        // Android Doze can leave an AWG UDP socket on a stale NAT binding even
        // without a network change. Remove once native AWG can verify/rebind it.
        return amnezia && now - start >= 3 * 60 * 1_000L
    }
}
