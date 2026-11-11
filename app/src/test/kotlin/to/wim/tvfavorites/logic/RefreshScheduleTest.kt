package to.wim.tvfavorites.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshScheduleTest {
    @Test
    fun `no job yet is scheduled`() {
        assertTrue(RefreshSchedule.needsSchedule(null))
    }

    @Test
    fun `a job lost on restart, from an older version, is replaced`() {
        assertTrue(RefreshSchedule.needsSchedule(RefreshSchedule.Pending(persisted = false, intervalMillis = RefreshSchedule.INTERVAL_MILLIS)))
    }

    @Test
    fun `a job with another interval is replaced`() {
        assertTrue(RefreshSchedule.needsSchedule(RefreshSchedule.Pending(persisted = true, intervalMillis = 15 * 60 * 1000L)))
    }

    @Test
    fun `an up-to-date job is left alone, so its timer keeps running`() {
        assertFalse(RefreshSchedule.needsSchedule(RefreshSchedule.Pending(persisted = true, intervalMillis = RefreshSchedule.INTERVAL_MILLIS)))
    }
}
