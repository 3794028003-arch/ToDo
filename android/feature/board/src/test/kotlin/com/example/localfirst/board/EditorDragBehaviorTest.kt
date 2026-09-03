package com.example.localfirst.board

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDragBehaviorTest {
    @Test
    fun shortSlowDrag_returnsToEditor() {
        assertFalse(
            shouldDismissEditorDrag(
                offsetPx = 120f,
                sheetHeightPx = 1_000f,
                velocityYPxPerSecond = 500f,
            ),
        )
    }

    @Test
    fun longDrag_dismissesEditor() {
        assertTrue(
            shouldDismissEditorDrag(
                offsetPx = 180f,
                sheetHeightPx = 1_000f,
                velocityYPxPerSecond = 0f,
            ),
        )
    }

    @Test
    fun quickDownwardFlick_dismissesEditorEvenBeforeDistanceThreshold() {
        assertTrue(
            shouldDismissEditorDrag(
                offsetPx = 40f,
                sheetHeightPx = 1_000f,
                velocityYPxPerSecond = 1_250f,
            ),
        )
    }

    @Test
    fun upwardFlick_neverDismissesEditor() {
        assertFalse(
            shouldDismissEditorDrag(
                offsetPx = 0f,
                sheetHeightPx = 1_000f,
                velocityYPxPerSecond = -2_000f,
            ),
        )
    }
}
