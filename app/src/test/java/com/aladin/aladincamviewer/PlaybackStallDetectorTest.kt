package com.aladin.aladincamviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStallDetectorTest {
    @Test
    fun movingClockDoesNotStall() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000, minimumProgressMs = 100)
        detector.reset(nowMs = 0, positionMs = 0)

        assertFalse(detector.isStalled(nowMs = 900, positionMs = 900))
        assertFalse(detector.isStalled(nowMs = 1_800, positionMs = 1_800))
    }

    @Test
    fun unchangedClockStallsAfterThreshold() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000, minimumProgressMs = 100)
        detector.reset(nowMs = 0, positionMs = 500)

        assertFalse(detector.isStalled(nowMs = 999, positionMs = 500))
        assertTrue(detector.isStalled(nowMs = 1_000, positionMs = 500))
    }

    @Test
    fun unavailableClockAlsoStallsInsteadOfHangingForever() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, positionMs = -1)

        assertTrue(detector.isStalled(nowMs = 1_000, positionMs = -1))
    }

    @Test
    fun clockResetIsTreatedAsFreshProgress() {
        val detector = PlaybackStallDetector(stallThresholdMs = 1_000)
        detector.reset(nowMs = 0, positionMs = 5_000)

        assertFalse(detector.isStalled(nowMs = 900, positionMs = 100))
        assertFalse(detector.isStalled(nowMs = 1_800, positionMs = 1_000))
    }
}
