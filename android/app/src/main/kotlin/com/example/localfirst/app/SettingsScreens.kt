package com.example.localfirst.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val PAGE_ANIMATION_MILLIS = 220
private val PageEaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)

internal fun discreteSelectionIndex(positionX: Float, width: Float, itemCount: Int): Int {
    if (itemCount <= 1 || width <= 0f) return 0
    return ((positionX / width) * itemCount).toInt().coerceIn(0, itemCount - 1)
}

@Composable
fun SettingsNavigationHost(state: SettingsUiState, viewModel: SettingsViewModel) {
    AnimatedVisibility(
        visible = state.page != SettingsPage.CLOSED,
        enter = slideInHorizontally(tween(PAGE_ANIMATION_MILLIS, easing = PageEaseOut)) { it },
        exit = slideOutHorizontally(tween(PAGE_ANIMATION_MILLIS, easing = PageEaseOut)) { it },
    ) {
        Box(Modifier.fillMaxSize()) {
            SettingsScreen(state, viewModel)
            AnimatedVisibility(
                visible = state.page == SettingsPage.ACCOUNT_DETAILS,
                enter = slideInHorizontally(tween(PAGE_ANIMATION_MILLIS, easing = PageEaseOut)) { it },
                exit = slideOutHorizontally(tween(PAGE_ANIMATION_MILLIS, easing = PageEaseOut)) { it },
            ) {
                AccountDetailsScreen(state.session, viewModel::back)
            }
        }
    }
    if (state.page != SettingsPage.CLOSED) BackHandler(onBack = viewModel::back)
}

@Composable
private fun SettingsScreen(state: SettingsUiState, viewModel: SettingsViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SettingsTopBar("设置", viewModel::back)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SectionTitle("账号")
                AccountCard(state.session, viewModel::openAccountDetails, viewModel::switchAccount)
                Spacer(Modifier.height(22.dp))
                SectionTitle("显示与外观")
                AppearanceCard(state.appearance, viewModel::setTheme, viewModel::setFontSize)
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(top = 2.dp),
            fontSize = 38.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 9.dp, start = 8.dp),
    )
}

@Composable
private fun AccountCard(session: AccountSession?, onDetails: () -> Unit, onSwitch: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 70.dp)
                    .clip(RoundedCornerShape(14.dp)).clickable(enabled = session != null, onClick = onDetails)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_launcher), "DoTi", Modifier.size(52.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("DoTi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        session?.contact ?: "尚未登录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (session != null) Text("›", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onSwitch),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (session == null) "登录账号" else "切换账号", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun AppearanceCard(
    appearance: AppAppearance,
    onTheme: (AppThemeMode) -> Unit,
    onFont: (AppFontSize) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var themeTrackWidthPx by remember { mutableFloatStateOf(1f) }
    var fontTrackWidthPx by remember { mutableFloatStateOf(1f) }
    var fontPreview by remember { mutableStateOf<AppFontSize?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("◉  主题模式", fontWeight = FontWeight.SemiBold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .onSizeChanged { themeTrackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        var lastAppliedIndex = -1
                        fun applyAt(positionX: Float) {
                            val index = discreteSelectionIndex(positionX, themeTrackWidthPx, AppThemeMode.entries.size)
                            if (index != lastAppliedIndex) {
                                lastAppliedIndex = index
                                onTheme(AppThemeMode.entries[index])
                            }
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { applyAt(it.x) },
                            onDrag = { change, _ ->
                                change.consume()
                                applyAt(change.position.x)
                            },
                            onDragEnd = { lastAppliedIndex = -1 },
                            onDragCancel = { lastAppliedIndex = -1 },
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ThemeChoice("☀ 浅色", AppThemeMode.LIGHT, appearance.theme, onTheme, Modifier.weight(1f))
                ThemeChoice("▣ 系统", AppThemeMode.SYSTEM, appearance.theme, onTheme, Modifier.weight(1f))
                ThemeChoice("☾ 深色", AppThemeMode.DARK, appearance.theme, onTheme, Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Tᵀ  字体大小", fontWeight = FontWeight.SemiBold)
            val displayedFont = fontPreview ?: appearance.fontSize
            val selectedIndex = AppFontSize.entries.indexOf(displayedFont)
            val previewScaleRatio = displayedFont.scale / appearance.fontSize.scale
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .onSizeChanged { fontTrackWidthPx = it.width.toFloat().coerceAtLeast(1f) },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${displayedFont.title} · 预览：记录你的每一天",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = (16f * previewScaleRatio).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Slider(
                        value = selectedIndex.toFloat(),
                        onValueChange = { value ->
                            val next = AppFontSize.entries[
                                value.roundToInt().coerceIn(0, AppFontSize.entries.lastIndex)
                            ]
                            if (next != displayedFont) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            fontPreview = next
                        },
                        onValueChangeFinished = {
                            val committedFont = fontPreview
                            fontPreview = null
                            if (committedFont != null) onFont(committedFont)
                        },
                        valueRange = 0f..AppFontSize.entries.lastIndex.toFloat(),
                        steps = AppFontSize.entries.size - 2,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AppFontSize.entries.forEachIndexed { index, item ->
                            Text(
                                item.title,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "字体大小滑动区域" }
                        .pointerInput(appearance.fontSize) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                val gestureWidthPx = fontTrackWidthPx
                                var pendingFont: AppFontSize? = null
                                var lastPreviewedFont = appearance.fontSize
                                fun previewAt(positionX: Float) {
                                    val index = discreteSelectionIndex(
                                        positionX,
                                        gestureWidthPx,
                                        AppFontSize.entries.size,
                                    )
                                    val next = AppFontSize.entries[index]
                                    if (next != pendingFont) {
                                        pendingFont = next
                                        fontPreview = next
                                        if (next != lastPreviewedFont) {
                                            lastPreviewedFont = next
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                                previewAt(down.position.x)
                                down.consume()
                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    previewAt(change.position.x)
                                    change.consume()
                                }
                                val committedFont = pendingFont
                                fontPreview = null
                                if (released && committedFont != null) onFont(committedFont)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: AppThemeMode,
    selected: AppThemeMode,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier,
) {
    val active = value == selected
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(9.dp))
            .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp)) else Modifier)
            .clickable { onClick(value) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountDetailsScreen(session: AccountSession?, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SettingsTopBar("账号详情", onBack)
            Column(Modifier.padding(24.dp)) {
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        DetailRow("注册手机号/邮箱", session?.contact ?: "未登录")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow("注册时间", session?.createdAtMillis.registrationTime())
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Long?.registrationTime(): String {
    if (this == null || this <= 0L) return "服务器暂未返回"
    return DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))
}
