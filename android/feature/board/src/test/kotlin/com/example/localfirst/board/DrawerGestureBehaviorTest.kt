package com.example.localfirst.board

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerGestureBehaviorTest {
    @Test
    fun slowDrag_usesTwentyPercentPositionalThreshold() {
        assertEquals(200f, drawerPositionalThreshold(1_000f))
    }

    @Test
    fun invalidDistance_hasZeroThreshold() {
        assertEquals(0f, drawerPositionalThreshold(-1f))
    }

    @Test
    fun statusIndicator_selectsThePageItIsNearestTo() {
        assertEquals(0, statusPageForIndicator(.49f, 3))
        assertEquals(1, statusPageForIndicator(.51f, 3))
        assertEquals(2, statusPageForIndicator(1.51f, 3))
    }

    @Test
    fun statusIndicator_clampsToAvailablePages() {
        assertEquals(0, statusPageForIndicator(-1f, 3))
        assertEquals(2, statusPageForIndicator(4f, 3))
    }
}
