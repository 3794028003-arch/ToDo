package com.example.localfirst.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsGestureInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneContinuousFontGestureCommitsOnlyItsFinalValue() {
        val applied = mutableListOf<AppFontSize>()
        composeRule.setContent {
            var appearance by remember { mutableStateOf(AppAppearance()) }
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                density = systemDensity.density,
                fontScale = systemDensity.fontScale * appearance.fontSize.scale,
            )
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MaterialTheme {
                    AppearanceCard(
                        appearance = appearance,
                        onTheme = { appearance = appearance.copy(theme = it) },
                        onFont = {
                            applied += it
                            appearance = appearance.copy(fontSize = it)
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("字体大小滑动区域").performTouchInput {
            down(Offset(width * .5f, center.y))
            advanceEventTime(650)
            moveTo(Offset(width * .95f, center.y), delayMillis = 160)
            moveTo(Offset(width * .05f, center.y), delayMillis = 160)
            moveTo(Offset(width * .95f, center.y), delayMillis = 160)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(AppFontSize.EXTRA_LARGE), applied)
    }
}
