package com.example.localfirst.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduleCalculator {
    fun firstTriggerAt(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        repeat: ReminderRepeat,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(hour in 0..23)
        require(minute in 0..59)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        var candidate = now.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zoneId)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (repeat == ReminderRepeat.WEEKDAYS) {
            candidate = candidate.nextWeekday()
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun nextTriggerAfter(
        triggerAtMillis: Long,
        repeat: ReminderRepeat,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val trigger = Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId)
        val next = when (repeat) {
            ReminderRepeat.NONE -> return null
            ReminderRepeat.DAILY -> trigger.plusDays(1)
            ReminderRepeat.WEEKLY -> trigger.plusWeeks(1)
            ReminderRepeat.WEEKDAYS -> trigger.plusDays(1).nextWeekday()
        }
        return next.toInstant().toEpochMilli()
    }

    fun nextFutureTrigger(
        scheduledAtMillis: Long,
        repeat: ReminderRepeat,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        var candidate: Long? = scheduledAtMillis
        while (candidate != null && candidate <= nowMillis) {
            candidate = nextTriggerAfter(candidate, repeat, zoneId)
        }
        return candidate
    }
}

private fun ZonedDateTime.nextWeekday(): ZonedDateTime {
    var value = this
    while (value.dayOfWeek == DayOfWeek.SATURDAY || value.dayOfWeek == DayOfWeek.SUNDAY) {
        value = value.plusDays(1)
    }
    return value
}
