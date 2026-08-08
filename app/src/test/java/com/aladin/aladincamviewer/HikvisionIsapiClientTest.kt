package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class HikvisionIsapiClientTest {
    @Test
    fun authenticatedPlaybackUrlAcceptsUppercaseRtspScheme() {
        val recorder = RecorderEntity(
            name = "Tiandy",
            ipAddress = "192.168.1.3",
            httpPort = 80,
            rtspPort = 554,
            username = "admin",
            password = "secret",
            manufacturer = "Tiandy"
        )

        val result = HikvisionIsapiClient().authenticatedPlaybackUrl(
            recorder,
            "RTSP://192.168.1.3:554/replay/1/1?replaymode=onvifreplay"
        )

        assertEquals(
            "rtsp://admin:secret@192.168.1.3:554/replay/1/1?replaymode=onvifreplay",
            result
        )
    }
}
