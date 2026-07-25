package com.aladin.aladincamviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraIdentityMatcherTest {
    private fun camera(uuid: String = "", mac: String? = null, brand: String = "Custom") = CameraEntity(
        name = "Test", ipAddress = "192.168.1.37", username = "u", password = "p",
        mainStreamUrl = "rtsp://host/main", subStreamUrl = "rtsp://host/sub",
        brand = brand, uuid = uuid, macAddress = mac
    )

    @Test fun `normalizes uuid prefixes`() {
        assertTrue(CameraIdentityMatcher.strongMatch(camera(uuid = "uuid:ABC"), DiscoveryDevice("x", uuid = "urn:uuid:abc")))
    }

    @Test fun `normalizes mac separators`() {
        assertTrue(CameraIdentityMatcher.strongMatch(camera(mac = "AA:BB:CC:DD:EE:FF"), DiscoveryDevice("x", mac = "aa-bb-cc-dd-ee-ff")))
    }

    @Test fun `does not match missing identities`() {
        assertFalse(CameraIdentityMatcher.strongMatch(camera(), DiscoveryDevice("x")))
    }

    @Test fun `generic brand is compatible but different known brands are not`() {
        assertTrue(CameraIdentityMatcher.isBrandCompatible("Custom", "Tiandy"))
        assertFalse(CameraIdentityMatcher.isBrandCompatible("Hikvision", "Dahua"))
    }
}
