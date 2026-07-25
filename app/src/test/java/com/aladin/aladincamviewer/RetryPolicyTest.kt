package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test
    fun `uses bounded exponential delays`() {
        assertEquals(1_000L, policy.delayForAttempt(0))
        assertEquals(2_000L, policy.delayForAttempt(1))
        assertEquals(15_000L, policy.delayForAttempt(4))
        assertNull(policy.delayForAttempt(5))
    }
}
