package com.example.localfirst.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGestureTest {
    @Test
    fun horizontalPositionMapsToEachDiscreteSetting() {
        assertEquals(0, discreteSelectionIndex(-20f, 300f, 3))
        assertEquals(0, discreteSelectionIndex(0f, 300f, 3))
        assertEquals(0, discreteSelectionIndex(99f, 300f, 3))
        assertEquals(1, discreteSelectionIndex(100f, 300f, 3))
        assertEquals(1, discreteSelectionIndex(199f, 300f, 3))
        assertEquals(2, discreteSelectionIndex(200f, 300f, 3))
        assertEquals(2, discreteSelectionIndex(400f, 300f, 3))
    }

    @Test
    fun invalidTrackFallsBackToFirstSetting() {
        assertEquals(0, discreteSelectionIndex(10f, 0f, 5))
        assertEquals(0, discreteSelectionIndex(10f, 100f, 0))
    }
}
