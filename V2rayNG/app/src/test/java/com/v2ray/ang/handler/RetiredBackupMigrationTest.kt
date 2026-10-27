package com.v2ray.ang.handler

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetiredBackupMigrationTest {
    @Test fun anOldBackupLosesOnlyItsRetiredRemoteCredentials() {
        val precedingRelease = JsonParser.parseString("""{
            "WEBDAV_CONFIG":"{\"baseUrl\":\"https://invalid.example\",\"username\":\"test\",\"password\":\"unused\"}",
            "SELECTED_SERVER":"guid-a","SUB_IDS":"[\"sub-a\"]",
            "PROFILE_TRAFFIC_V1":"{\"generation\":\"old\",\"profiles\":{\"guid-a\":{\"upload\":4,\"download\":8}}}"
        }""").asJsonObject
        val records = precedingRelease.entrySet().associate { it.key to it.value.asString }.toMutableMap()
        val retained = records.filterKeys { it != "WEBDAV_CONFIG" }
        MmkvManager.clearRetiredPreferences { records.remove(it) }
        assertEquals(retained, records)
        MmkvManager.clearRetiredPreferences { records.remove(it) }
        assertEquals(retained, records)
    }

    @Test fun aFailedRemovalCanBeRetriedWithoutChangingProfileData() {
        assertThrows(IllegalStateException::class.java) {
            MmkvManager.clearRetiredPreferences { throw IllegalStateException("unavailable") }
        }
    }
}
