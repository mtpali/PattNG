package com.v2ray.ang.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PolicyGroupGeoIpTest {
    @Test
    fun aReleasedPolicyGroupUsesStandardGeoIpWithoutChangingItsServersOrBalancer() {
        val text = checkNotNull(javaClass.getResource("/policy-group-p69.json")).readText()
        val config = JsonParser.parseString(text).asJsonObject
        val expected = config.deepCopy()
        val oldRule = expected.getAsJsonObject("routing").getAsJsonArray("rules")
            .map { it.asJsonObject }
            .single { it.getAsJsonArray("ip")?.toString()?.contains("geoip-only-cn-private.dat") == true }
        oldRule.add("ip", JsonParser.parseString("""["geoip:private"]"""))

        CoreConfigManager.normalizeGeoIpReferences(config)

        assertEquals(expected, config)
        assertFalse(config.toString().contains("geoip-only-cn-private.dat"))
        CoreConfigManager.normalizeGeoIpReferences(config)
        assertEquals(expected, config)
    }

    @Test
    fun onlyTheTwoLegacyBuiltInAliasesAreRewritten() {
        val config = JsonParser.parseString(
            """{"routing":{"rules":[{"ip":["ext:geoip-only-cn-private.dat:cn",
                "ext:geoip-only-cn-private.dat:private","geoip:ir","10.0.0.0/8",
                "ext:custom.dat:private","ext:geoip-only-cn-private.dat:!cn",
                "ext:geoip-only-cn-private.dat:us"]}]},"outbounds":[],"unknown":{"enabled":true}}"""
        ).asJsonObject
        val expected = JsonParser.parseString(
            """{"routing":{"rules":[{"ip":["geoip:cn","geoip:private","geoip:ir","10.0.0.0/8",
                "ext:custom.dat:private","ext:geoip-only-cn-private.dat:!cn",
                "ext:geoip-only-cn-private.dat:us"]}]},"outbounds":[],"unknown":{"enabled":true}}"""
        )

        CoreConfigManager.normalizeGeoIpReferences(config)

        assertEquals(expected, config)
    }

    @Test
    fun unrelatedAndInvalidFieldsAreLeftForTheCoreToValidate() {
        for (text in listOf(
            "{}", """{"routing":null}""", """{"routing":{"rules":{}}}""",
            """{"routing":{"rules":[null,1,{}, {"ip":null}, {"ip":"geoip:private"}, {"ip":[null,7,false]}]}}"""
        )) {
            val config = JsonParser.parseString(text).asJsonObject
            val before = config.deepCopy()

            CoreConfigManager.normalizeGeoIpReferences(config)

            assertEquals(before, config)
        }
    }
}
