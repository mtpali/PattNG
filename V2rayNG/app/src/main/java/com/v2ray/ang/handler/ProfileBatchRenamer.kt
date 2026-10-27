package com.v2ray.ang.handler

/** Plans names by stable GUID before the storage transaction writes any record. */
internal object ProfileBatchRenamer {
    fun plan(prefix: String, guids: List<String>): Map<String, String> {
        val normalized = prefix.trim()
        return guids.distinct().mapIndexed { index, guid ->
            guid to if (normalized.isEmpty()) "${index + 1}" else "$normalized ${index + 1}"
        }.toMap(LinkedHashMap())
    }
}
