package com.aladin.aladincamviewer

/** Detects a player that still reports Playing while its media clock no longer advances. */
class PlaybackStallDetector(
    private val stallThresholdMs: Long = 25_000L,
    private val minimumProgressMs: Long = 250L
) {
    private var lastPositionMs: Long? = null
    private var lastProgressAtMs: Long = 0L

    fun reset(nowMs: Long, positionMs: Long) {
        lastPositionMs = positionMs.takeIf { it >= 0L }
        lastProgressAtMs = nowMs
    }

    fun isStalled(nowMs: Long, positionMs: Long): Boolean {
        val previous = lastPositionMs
        if (positionMs >= 0L && (previous == null || positionMs < previous || positionMs - previous >= minimumProgressMs)) {
            lastPositionMs = positionMs
            lastProgressAtMs = nowMs
            return false
        }
        return nowMs - lastProgressAtMs >= stallThresholdMs
    }
}
