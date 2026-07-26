package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {
    private fun camera(ip: String, order: Int = 1, url: String = "rtsp://192.0.2.1/live") = CameraEntity(
        name = "Test camera",
        ipAddress = ip,
        username = "",
        password = "",
        mainStreamUrl = url,
        subStreamUrl = "",
        displayOrder = order
    )

    @Test fun `valid configuration passes`() {
        assertTrue(ConfigValidator.validate(ConfigModel(listOf(camera("192.0.2.1")))).isEmpty())
    }

    @Test fun `duplicate IP is rejected before replacement`() {
        val errors = ConfigValidator.validate(ConfigModel(listOf(camera("192.0.2.1"), camera("192.0.2.1", 2))))
        assertTrue(errors.any { it.contains("duplicate IP") })
    }

    @Test fun `invalid stream and display order are rejected`() {
        val errors = ConfigValidator.validate(ConfigModel(listOf(camera("192.0.2.2", 0, "http://192.0.2.2"))))
        assertEquals(2, errors.size)
    }
}
