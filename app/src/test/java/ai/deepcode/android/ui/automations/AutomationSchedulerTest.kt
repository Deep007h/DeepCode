package ai.deepcode.android.ui.automations

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AutomationSchedulerTest {

    @Test
    fun testHourlyCronDelay() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = AutomationScheduler.computeDelayMs("0 * * * *", cal.timeInMillis)
        // From 10:15 to 11:00 is 45 minutes = 2,700,000 ms
        assertEquals(45 * 60 * 1000L, delay)
    }

    @Test
    fun testDailyCronDelay() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = AutomationScheduler.computeDelayMs("0 7 * * *", cal.timeInMillis)
        // From 06:30 to 07:00 is 30 minutes = 1,800,000 ms
        assertEquals(30 * 60 * 1000L, delay)
    }

    @Test
    fun testStepCronDelay() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 12)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = AutomationScheduler.computeDelayMs("*/5 * * * *", cal.timeInMillis)
        // Next multiple of 5 is minute 15 -> 3 minutes = 180,000 ms
        assertEquals(3 * 60 * 1000L, delay)
    }

    @Test
    fun testSundayBoth0And7() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay0 = AutomationScheduler.computeDelayMs("0 9 * * 0", cal.timeInMillis)
        val delay7 = AutomationScheduler.computeDelayMs("0 9 * * 7", cal.timeInMillis)
        // Both should target 9:00 AM on Sunday (1 hour away)
        assertEquals(60 * 60 * 1000L, delay0)
        assertEquals(60 * 60 * 1000L, delay7)
    }

    @Test
    fun testShortcutCron() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 10)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delayHourly = AutomationScheduler.computeDelayMs("@hourly", cal.timeInMillis)
        // Next hour 15:00 is 50 minutes away
        assertEquals(50 * 60 * 1000L, delayHourly)
    }

    @Test
    fun testComputeNextRunAt() {
        val now = 1700000000000L
        val nextRun = AutomationScheduler.computeNextRunAt("0 * * * *", now)
        assertTrue(nextRun > now)
        val expectedDelay = AutomationScheduler.computeDelayMs("0 * * * *", now)
        assertEquals(now + expectedDelay, nextRun)
    }
}
