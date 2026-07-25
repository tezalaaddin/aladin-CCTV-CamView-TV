package com.aladin.aladincamviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStallDetectorTest {
    @Test
    fun movingClockDoesNotStall() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, progress = 0)

        assertFalse(detector.isStalled(nowMs = 900, progress = 25))
        assertFalse(detector.isStalled(nowMs = 1_800, progress = 50))
    }

    @Test
    fun unchangedClockStallsAfterThreshold() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, progress = 500)

        assertFalse(detector.isStalled(nowMs = 999, progress = 500))
        assertTrue(detector.isStalled(nowMs = 1_000, progress = 500))
    }

    @Test
    fun unavailableClockAlsoStallsInsteadOfHangingForever() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, progress = -1)

        assertTrue(detector.isStalled(nowMs = 1_000, progress = -1))
    }

    @Test
    fun clockResetIsTreatedAsFreshProgress() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, progress = 5_000)

        assertFalse(detector.isStalled(nowMs = 900, progress = 100))
        assertFalse(detector.isStalled(nowMs = 1_800, progress = 1_000))
    }
}
