package com.aladin.aladincamviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryDeviceTest {
    @Test fun `classifies target Hikvision recorders`() {
        assertTrue(DiscoveryDevice("192.0.2.1", brand = "Hikvision", model = "DS-7616NI-Q1").isRecorderCandidate())
        assertTrue(DiscoveryDevice("192.0.2.2", brand = "Hikvision", model = "DS-7104NI-Q1/4P/M").isRecorderCandidate())
    }

    @Test fun `does not classify ordinary Hikvision camera as recorder`() {
        assertFalse(DiscoveryDevice("192.0.2.3", brand = "Hikvision", model = "DS-2CD2143G2-I").isRecorderCandidate())
    }
}
