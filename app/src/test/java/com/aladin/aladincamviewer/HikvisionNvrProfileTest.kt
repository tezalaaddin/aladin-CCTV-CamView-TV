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

    @Test fun `builds distinct Tiandy channel paths`() {
        assertEquals("/1/1", NvrStreamProfile.livePath("Tiandy", 1, false))
        assertEquals("/1/2", NvrStreamProfile.livePath("Tiandy", 1, true))
        assertEquals("/20/1", NvrStreamProfile.livePath("Tiandy", 20, false))
        assertEquals(20, NvrStreamProfile.knownCapacity("Tiandy", "TC-R3120 Spec:I/B/V3.0"))
    }

    @Test fun `builds vendor specific NVR channel paths`() {
        assertEquals("/cam/realmonitor?channel=3&subtype=0", NvrStreamProfile.livePath("Dahua", 3, false))
        assertEquals("/2/profile2/media.smp", NvrStreamProfile.livePath("Hanwha Wisenet", 3, true))
        assertEquals("/unicast/c3/s0/live", NvrStreamProfile.livePath("Uniview (UNV)", 3, false))
        assertEquals(
            "/user=user%20name&password=p%40ss&channel=3&stream=1.sdp?real_stream",
            NvrStreamProfile.livePath("XMeye", 3, true, "user name", "p@ss")
        )
    }
}
