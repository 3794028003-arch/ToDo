package com.example.localfirst.board

import org.junit.Assert.assertEquals
import org.junit.Test

class CircularTimeWheelTest {
    @Test
    fun hoursWrapContinuouslyAcrossMidnight() {
        val midnight = circularTimeInitialIndex(valueCount = 24, selectedValue = 0)

        assertEquals(22, circularTimeValue(midnight - 2, 24))
        assertEquals(23, circularTimeValue(midnight - 1, 24))
        assertEquals(0, circularTimeValue(midnight, 24))
        assertEquals(1, circularTimeValue(midnight + 1, 24))
        assertEquals(2, circularTimeValue(midnight + 2, 24))
    }

    @Test
    fun minutesWrapContinuouslyAcrossHourBoundary() {
        val zero = circularTimeInitialIndex(valueCount = 60, selectedValue = 0)

        assertEquals(58, circularTimeValue(zero - 2, 60))
        assertEquals(59, circularTimeValue(zero - 1, 60))
        assertEquals(0, circularTimeValue(zero, 60))
        assertEquals(1, circularTimeValue(zero + 1, 60))
    }

    @Test
    fun selectedValueMovesByShortestCircularDistance() {
        val hour23 = circularTimeInitialIndex(valueCount = 24, selectedValue = 23)

        assertEquals(hour23 + 1, nearestCircularTimeIndex(hour23, 24, 0))
        assertEquals(hour23 - 1, nearestCircularTimeIndex(hour23, 24, 22))
    }
}
