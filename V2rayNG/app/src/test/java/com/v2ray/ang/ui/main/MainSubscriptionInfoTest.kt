package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainSubscriptionInfoTest {
    private val now = 1_000_000_000_000L // ms

    @Test
    fun limitedTrafficComputesFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(upload = 100, download = 300, total = 1000), now)
        assertEquals(400, model.usedBytes)
        assertEquals(0.4f, model.trafficFraction!!, 0.0001f)
        assertFalse(model.trafficLow)
    }

    @Test
    fun unlimitedTrafficHasNoFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 5), now)
        assertEquals(0L, model.totalBytes)
        assertNull(model.trafficFraction)
        assertFalse(model.trafficLow)
    }

    @Test
    fun overUsageIsClampedAndFlaggedLow() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 2000, total = 1000), now)
        assertEquals(1f, model.trafficFraction!!, 0.0001f)
        assertTrue(model.trafficLow)
    }

    @Test
    fun expiryIsConvertedToMillisAndFlaggedWhenPast() {
        val future = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 + 60), now)
        assertEquals(now + 60_000, future.expireMillis)
        assertEquals(1L, future.remainingDays)
        assertFalse(future.expired)
        assertTrue(buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 - 1), now).expired)
    }

    @Test
    fun zeroExpireMeansNeverExpires() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = 0), now)
        assertNull(model.expireMillis)
        assertNull(model.remainingDays)
        assertFalse(model.expired)
    }

    @Test
    fun remainingDaysRoundUpPartialDaysAndClampExpiredToZero() {
        val day = 86_400L
        assertEquals(2L, buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 + 2 * day), now).remainingDays)
        assertEquals(3L, buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 + 2 * day + 1), now).remainingDays)
        assertEquals(0L, buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000), now).remainingDays)
        assertEquals(0L, buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 - day), now).remainingDays)
        assertNull(buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = -1), now).remainingDays)
    }

    @Test
    fun veryLargeExpiryDoesNotOverflowIntoAnExpiredDate() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = Long.MAX_VALUE), now)
        assertTrue(model.remainingDays!! > 0L)
        assertFalse(model.expired)
    }
}
