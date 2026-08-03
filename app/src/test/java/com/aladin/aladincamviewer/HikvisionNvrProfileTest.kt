package com.aladin.aladincamviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HikvisionNvrProfileTest {
    @Test fun `builds Hikvision main and sub stream ids`() {
        assertEquals(101, HikvisionNvrProfile.streamId(1, false))
        assertEquals(102, HikvisionNvrProfile.streamId(1, true))
        assertEquals(1601, HikvisionNvrProfile.streamId(16, false))
    }

    @Test fun `recognizes target recorder capacities`() {
        assertEquals(16, HikvisionNvrProfile.knownCapacity("DS-7616NI-Q1"))
        assertEquals(4, HikvisionNvrProfile.knownCapacity("DS-7104NI-Q1/4P/M"))
        assertNull(HikvisionNvrProfile.knownCapacity("UNKNOWN"))
    }
}
