package com.example.localfirst.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderScheduleCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `one-time reminder chooses today when future and tomorrow when time passed`() {
        val morning = millis(2026, 8, 25, 9, 0)
        assertEquals(
            millis(2026, 8, 25, 10, 15),
            ReminderScheduleCalculator.firstTriggerAt(morning, 10, 15, ReminderRepeat.NONE, zone),
        )
        assertEquals(
            millis(2026, 8, 26, 8, 30),
            ReminderScheduleCalculator.firstTriggerAt(morning, 8, 30, ReminderRepeat.NONE, zone),
        )
    }

    @Test
    fun `weekday repeat skips Saturday and Sunday`() {
        val friday = millis(2026, 8, 28, 18, 0)
        assertEquals(
            millis(2026, 8, 31, 9, 0),
            ReminderScheduleCalculator.firstTriggerAt(
                friday,
                9,
                0,
                ReminderRepeat.WEEKDAYS,
                zone,
            ),
        )
        assertEquals(
            millis(2026, 8, 31, 9, 0),
            ReminderScheduleCalculator.nextTriggerAfter(
                millis(2026, 8, 28, 9, 0),
                ReminderRepeat.WEEKDAYS,
                zone,
            ),
        )
    }

    @Test
    fun `daily and weekly repeats preserve selected local time`() {
        val trigger = millis(2026, 8, 25, 17, 35)
        assertEquals(
            millis(2026, 8, 26, 17, 35),
            ReminderScheduleCalculator.nextTriggerAfter(trigger, ReminderRepeat.DAILY, zone),
        )
        assertEquals(
            millis(2026, 9, 1, 17, 35),
            ReminderScheduleCalculator.nextTriggerAfter(trigger, ReminderRepeat.WEEKLY, zone),
        )
        assertNull(ReminderScheduleCalculator.nextTriggerAfter(trigger, ReminderRepeat.NONE, zone))
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
