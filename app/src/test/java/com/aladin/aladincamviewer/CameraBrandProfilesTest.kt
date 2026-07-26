package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBrandProfilesTest {
    @Test fun `preferred brand profile is first`() {
        val first = CameraBrandProfiles.candidates("Hikvision", "192.168.1.10", "admin", "secret").first()
        assertTrue(first.first.endsWith("/Streaming/Channels/101"))
        assertTrue(first.second.endsWith("/Streaming/Channels/102"))
    }

    @Test fun `credentials are percent encoded`() {
        val first = CameraBrandProfiles.candidates("Tiandy", "192.168.1.5", "user name", "p@ss word").first()
        val encodedAuthority = "user%20name" + ":" + "p%40ss%20word" + "@"
        assertTrue(first.first.startsWith("rtsp://" + encodedAuthority))
    }

    @Test fun `known brands do not contain duplicates`() {
        val brands = CameraBrandProfiles.knownBrands()
        assertEquals(brands.distinct(), brands)
    }
}
