package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionImportNamesTest {
    @Test fun aSingleUnnamedSubscriptionUsesSub() {
        assertEquals(listOf("sub"), subscriptionImportNames(listOf(null), emptySet(), "sub"))
        assertEquals(listOf("sub"), subscriptionImportNames(listOf("  "), emptySet(), "sub"))
    }

    @Test fun simultaneousImportsAreNumberedAndExplicitNamesStayIntact() {
        assertEquals(listOf("sub 1", "Private", "sub 2"),
            subscriptionImportNames(listOf(null, "Private", ""), emptySet(), "sub"))
        assertEquals(listOf("Private"), subscriptionImportNames(listOf("Private"), emptySet(), "sub"))
        assertEquals(listOf("Private", "sub 1"), subscriptionImportNames(listOf("Private", null), emptySet(), "sub"))
        assertEquals(emptyList<String>(), subscriptionImportNames(emptyList(), emptySet(), "sub"))
    }

    @Test fun newDefaultsDoNotCollideWithExistingOrExplicitNames() {
        assertEquals(listOf("sub 2"), subscriptionImportNames(listOf(null), setOf("sub", "sub 1"), "sub"))
        assertEquals(listOf("sub 3", "sub 1", "sub 4"),
            subscriptionImportNames(listOf(null, "sub 1", null), setOf("sub 2"), "sub"))
    }
}
