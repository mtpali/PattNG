package com.v2ray.ang.dto

import java.io.Serializable

data class TestServiceMessage(
    val key: Int,
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList(),
    // Kept only to read pre-P69.3 IPC payloads; receivers always execute real-delay tests.
    val onlyTcp: Boolean = false
) : Serializable

