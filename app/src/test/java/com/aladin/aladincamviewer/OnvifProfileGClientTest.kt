package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OnvifProfileGClientTest {
    @Test
    fun tiandyReplayUriUsesSelectedChannel() {
        val recorder = RecorderEntity(
            name = "NVR", ipAddress = "192.168.1.3", username = "admin",
            password = "secret", manufacturer = NvrStreamProfile.TIANDY
        )

        val result = OnvifProfileGClient().channelReplayUri(
            recorder,
            "RTSP://192.168.1.3:554/replay/1/1?replaymode=onvifreplay",
            7
        )

        assertEquals("RTSP://192.168.1.3:554/replay/7/1?replaymode=onvifreplay", result)
    }

    @Test
    fun otherManufacturersKeepOnvifReplayUri() {
        val recorder = RecorderEntity(
            name = "NVR", ipAddress = "192.168.1.4", username = "admin",
            password = "secret", manufacturer = NvrStreamProfile.UNIVIEW
        )
        val uri = "rtsp://192.168.1.4/replay/1/1"

        assertEquals(uri, OnvifProfileGClient().channelReplayUri(recorder, uri, 4))
    }

    @Test
    fun tiandyReplayUriIncludesUtcRange() {
        val recorder = RecorderEntity(
            name = "NVR", ipAddress = "192.168.1.3", username = "admin",
            password = "secret", manufacturer = NvrStreamProfile.TIANDY
        )
        val result = OnvifProfileGClient().timedReplayUri(
            recorder,
            "rtsp://192.168.1.3/replay/2/1?replaymode=onvifreplay",
            Instant.parse("2026-08-08T17:00:00Z"),
            Instant.parse("2026-08-08T17:10:00Z")
        )

        assertEquals(
            "rtsp://192.168.1.3/replay/2/1?replaymode=onvifreplay&starttime=20260808T170000Z&endtime=20260808T171000Z",
            result
        )
    }
}
