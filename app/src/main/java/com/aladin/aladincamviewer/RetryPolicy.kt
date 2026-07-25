package com.aladin.aladincamviewer

/** Pure retry policy so reconnect behaviour can be unit tested without Android. */
class RetryPolicy(
    private val delaysMs: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
) {
    fun delayForAttempt(attempt: Int): Long? = delaysMs.getOrNull(attempt)
}
