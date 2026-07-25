package com.aladin.aladincamviewer

/** Detects a player that still reports Playing while its frame/progress counter no longer advances. */
class PlaybackStallDetector(
    private val stallThresholdMs: Long = 25_000L,
    private val minimumProgress: Long = 1L
) {
    private var lastProgress: Long? = null
    private var lastProgressAtMs: Long = 0L

    fun reset(nowMs: Long, progress: Long) {
        lastProgress = progress.takeIf { it >= 0L }
        lastProgressAtMs = nowMs
    }

    fun isStalled(nowMs: Long, progress: Long): Boolean {
        val previous = lastProgress
        if (progress >= 0L && (previous == null || progress < previous || progress - previous >= minimumProgress)) {
            lastProgress = progress
            lastProgressAtMs = nowMs
            return false
        }
        return nowMs - lastProgressAtMs >= stallThresholdMs
    }
}
