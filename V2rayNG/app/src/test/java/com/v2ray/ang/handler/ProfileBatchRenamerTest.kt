package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileBatchRenamerTest {
    @Test fun emptyAndWhitespacePrefixesNumberTheStoredOrder() {
        listOf("", " \t\n ").forEach { prefix ->
            assertEquals(linkedMapOf("guid-b" to "1", "guid-a" to "2", "guid-c" to "3"),
                ProfileBatchRenamer.plan(prefix, listOf("guid-b", "guid-a", "guid-c")))
        }
    }

    @Test fun aPrefixIsTrimmedAndAnEmptyGroupHasNoWrites() {
        assertEquals(mapOf("guid" to "Server 1"), ProfileBatchRenamer.plan(" Server ", listOf("guid")))
        assertTrue(ProfileBatchRenamer.plan("Server", emptyList()).isEmpty())
    }

    @Test fun duplicateReferencesCannotRenameOneProfileTwice() {
        assertEquals(linkedMapOf("a" to "1", "b" to "2"), ProfileBatchRenamer.plan("", listOf("a", "a", "b")))
    }
}
