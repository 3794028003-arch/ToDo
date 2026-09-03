package com.example.localfirst.board
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DRAWER_WIDTH_FRACTION = .72f
private const val DRAWER_SETTLE_THRESHOLD = .20f
private const val DRAWER_ANIMATION_DURATION_MS = 220

internal fun drawerPositionalThreshold(distance: Float): Float =
    (distance * DRAWER_SETTLE_THRESHOLD).coerceAtLeast(0f)

internal fun statusPageForIndicator(position: Float, pageCount: Int): Int =
    if (pageCount <= 0) 0 else position.roundToInt().coerceIn(0, pageCount - 1)

private enum class DrawerValue { Closed, Open }

@Composable
fun BoardRoute(
    viewModel: BoardViewModel,
    onReminderPermissionsRequired: () -> Unit,
    reminderTitle: String? = null,
    reminderCount: Int = 0,
    onReminderDoing: () -> Unit = {},
    onReminderDone: () -> Unit = {},
    onDismissReminder: () -> Unit = {},
    accountContact: String? = null,
    accountSyncState: String = "LOCAL_ONLY",
    appVersion: String = "2.0.0",
    onAccountClick: () -> Unit = {},
    onUpload: () -> Unit = {},
    onDownload: () -> Unit = {},
    onSettings: () -> Unit = {},
    darkModeOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BoardEffect.RequestReminderPermissions -> onReminderPermissionsRequired()
                is BoardEffect.ShowStatusMessage -> coroutineScope {
                    snackbar.currentSnackbarData?.dismiss()
                    val showing = launch {
                        snackbar.showSnackbar(effect.message, duration = androidx.compose.material3.SnackbarDuration.Indefinite)
                    }
                    delay(750)
                    snackbar.currentSnackbarData?.dismiss()
                    showing.cancel()
                }
                is BoardEffect.ShowRefreshMessage -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(effect.message)
                }
            }
        }
    }
    BoardScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbar,
        reminderTitle = reminderTitle,
        reminderCount = reminderCount,
        onReminderDoing = onReminderDoing,
        onReminderDone = onReminderDone,
        onDismissReminder = onDismissReminder,
        accountContact = accountContact,
        accountSyncState = accountSyncState,
        appVersion = appVersion,
        onAccountClick = onAccountClick,
        onUpload = onUpload,
        onDownload = onDownload,
        onSettings = onSettings,
        darkModeOverride = darkModeOverride,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    onAction: (BoardAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    reminderTitle: String? = null,
    reminderCount: Int = 0,
    onReminderDoing: () -> Unit = {},
    onReminderDone: () -> Unit = {},
    onDismissReminder: () -> Unit = {},
    accountContact: String? = null,
    accountSyncState: String = "LOCAL_ONLY",
    appVersion: String = "2.0.0",
    onAccountClick: () -> Unit = {},
    onUpload: () -> Unit = {},
    onDownload: () -> Unit = {},
    onSettings: () -> Unit = {},
    darkModeOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pager = rememberPagerState { PAGES.size }
    val lists = listOf(rememberLazyListState(), rememberLazyListState(), rememberLazyListState())
    val drawerScope = rememberCoroutineScope()
    var drawerWidthPx by remember { mutableFloatStateOf(1f) }
    val maximumDrawerWidthPx = with(density) { 360.dp.toPx() }
    val drawerState = remember { AnchoredDraggableState(DrawerValue.Closed) }
    val drawerAnimation = tween<Float>(durationMillis = DRAWER_ANIMATION_DURATION_MS)
    val drawerFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = drawerState,
        positionalThreshold = ::drawerPositionalThreshold,
        animationSpec = drawerAnimation,
    )
    val visibleDrawerOffset = drawerState.offset
        .takeUnless(Float::isNaN)
        ?.coerceIn(0f, drawerWidthPx)
        ?: 0f
    val drawerVisible = visibleDrawerOffset > 0f
    val drawerProgress = (visibleDrawerOffset / drawerWidthPx).coerceIn(0f, 1f)
    fun animateDrawer(
        open: Boolean,
        onFinished: (() -> Unit)? = null,
    ) {
        drawerScope.launch {
            drawerState.animateTo(if (open) DrawerValue.Open else DrawerValue.Closed, drawerAnimation)
            onFinished?.invoke()
        }
    }
    val drawerGesturesEnabled =
        state.editor == null && !state.isSearching && !state.isRecycleBinOpen && !state.isBatchEditing &&
            (drawerVisible || (
                pager.currentPage == 0 && pager.settledPage == 0 &&
                    state.selectedStatus == TaskStatus.TODO
            ))
    val drawerPagerConnection = remember(
        drawerState,
        drawerGesturesEnabled,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val offset = drawerState.offset.takeUnless(Float::isNaN) ?: 0f
                if (!drawerGesturesEnabled || source != NestedScrollSource.UserInput ||
                    (offset <= 0f && available.x <= 0f)
                ) return Offset.Zero
                return Offset(drawerState.dispatchRawDelta(available.x), 0f)
            }

            // The parent AnchoredDraggable owns the only fling/settle operation.
            // Starting another settle here can cancel it and leave the drawer half-open.
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
        }
    }
    var statusTabWidthPx by remember { mutableFloatStateOf(1f) }
    var statusTabRequestedPage by remember { mutableIntStateOf(0) }
    var statusTabDragPosition by remember { mutableFloatStateOf(Float.NaN) }
    val pagerIndicatorPosition = (pager.currentPage + pager.currentPageOffsetFraction)
        .coerceIn(0f, PAGES.lastIndex.toFloat())
    val statusIndicatorPosition = statusTabDragPosition.takeUnless(Float::isNaN)
        ?: pagerIndicatorPosition

    BackHandler(drawerVisible || state.isSearching || state.editor != null || state.isBatchEditing || state.isRecycleBinOpen) {
        when {
            drawerVisible -> animateDrawer(false)
            state.editor != null -> onAction(BoardAction.RequestCloseEditor)
            state.isSearching -> { keyboard?.hide(); focusManager.clearFocus(true); onAction(BoardAction.ExitSearch) }
            state.isBatchEditing -> onAction(BoardAction.CancelBatchEdit)
            state.isRecycleBinOpen -> onAction(
                if (state.isRecycleBinBatchEditing) BoardAction.CancelRecycleBinBatchEdit
                else BoardAction.CloseRecycleBin,
            )
        }
    }
    LaunchedEffect(state.selectedStatus) {
        val page = state.selectedStatus.pageIndex()
        if (pager.currentPage != page) pager.animateScrollToPage(page)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collectLatest { onAction(BoardAction.SelectStatus(PAGES[it].status)) }
    }
    LaunchedEffect(state.highlightRequest) {
        val request = state.highlightRequest ?: return@LaunchedEffect
        pager.scrollToPage(request.status.pageIndex())
        val index = state.tasksFor(request.status).indexOfFirst { it.id == request.taskId }
        if (index >= 0) lists[request.status.pageIndex()].animateScrollToItem(index)
    }
    LaunchedEffect(state.todoScrollToEndGeneration) {
        if (state.todoScrollToEndGeneration > 0 && state.todo.isNotEmpty()) {
            pager.scrollToPage(0); lists[0].animateScrollToItem(state.todo.lastIndex)
        }
    }

    val isDarkMode = darkModeOverride ?: state.isDarkMode
    val boardBackground = if (isDarkMode) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF07345F), Color(0xFF010B14), Color.Black),
            center = Offset(90f, 0f),
            radius = 920f,
        )
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(boardBackground)
            .nestedScroll(drawerPagerConnection)
            .onSizeChanged {
                val newWidth = (it.width * DRAWER_WIDTH_FRACTION)
                    .coerceAtMost(maximumDrawerWidthPx)
                    .coerceAtLeast(1f)
                if (newWidth != drawerWidthPx) {
                    drawerWidthPx = newWidth
                    drawerState.updateAnchors(
                        DraggableAnchors {
                            DrawerValue.Closed at 0f
                            DrawerValue.Open at newWidth
                        },
                    )
                }
            }
            .anchoredDraggable(
                state = drawerState,
                orientation = Orientation.Horizontal,
                enabled = drawerGesturesEnabled,
                flingBehavior = drawerFlingBehavior,
            ),
    ) {
    SideMenu(
        modifier = Modifier.align(Alignment.CenterStart)
            .fillMaxHeight().width(with(density) { drawerWidthPx.toDp() })
            .graphicsLayer { translationX = visibleDrawerOffset - drawerWidthPx },
        accountContact = accountContact,
        accountSyncState = accountSyncState,
        appVersion = appVersion,
        onUpload = { animateDrawer(false, onFinished = onUpload) },
        onDownload = { animateDrawer(false, onFinished = onDownload) },
        onRecycleBin = {
            animateDrawer(false, onFinished = { onAction(BoardAction.OpenRecycleBin) })
        },
        onSettings = {
            drawerScope.launch { drawerState.snapTo(DrawerValue.Closed) }
            onSettings()
        },
    )
    Box(
        modifier = Modifier.fillMaxSize()
            .graphicsLayer { translationX = visibleDrawerOffset }
            .background(boardBackground)
            .zIndex(1f),
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isRecycleBinOpen) RecycleBinTopBar(state, onAction)
            else if (state.isSearching) SearchTopBar(state.searchQuery, onAction)
            else TopAppBar(
                navigationIcon = {
                    if (!state.isBatchEditing) {
                        IconButtonSurface("打开菜单", { animateDrawer(true) }) { MenuGlyph() }
                    }
                },
                title = {
                    Text(
                        if (state.isBatchEditing) "已选择 ${state.selectedTaskIds.size} 项" else "记录你的每一天",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = .5.sp,
                    )
                },
                actions = {
                    if (state.isBatchEditing) TextButton(onClick = { onAction(BoardAction.CancelBatchEdit) }, enabled = !state.isBatchOperationRunning) { Text("取消") }
                    else {
                        IconButtonSurface("搜索", { onAction(BoardAction.StartSearch) }) { SearchGlyph() }
                        AccountActionButton(accountContact, accountSyncState, onAccountClick)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color.Transparent else MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (!state.isSearching && !state.isBatchEditing && state.editor == null && !state.isRecycleBinOpen) {
                Box(
                    Modifier.navigationBarsPadding().padding(bottom = 72.dp, end = 4.dp).size(60.dp)
                        .shadow(14.dp, CircleShape, clip = false)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF8A2BE2))),
                            CircleShape,
                        )
                        .semantics { contentDescription = "添加任务" }
                        .clickable { onAction(BoardAction.OpenCreate) },
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light) }
            }
        },
        bottomBar = { if (state.isBatchEditing) BatchBar(state, onAction) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        if (state.isRecycleBinOpen) RecycleBinScreen(state, onAction, Modifier.fillMaxSize())
        else if (state.isSearching) SearchResults(state, onAction, Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = pager.currentPage,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .onSizeChanged { statusTabWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .semantics { contentDescription = "任务状态切换栏" }
                    .pointerInput(
                        drawerVisible,
                        state.isBatchEditing,
                        state.isBatchOperationRunning,
                        statusTabWidthPx,
                    ) {
                        if (drawerVisible || state.isBatchEditing || state.isBatchOperationRunning) {
                            return@pointerInput
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                statusTabRequestedPage = pager.currentPage
                                statusTabDragPosition = pager.currentPage.toFloat()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val tabWidthPx = statusTabWidthPx / PAGES.size
                                if (tabWidthPx > 0f && !statusTabDragPosition.isNaN()) {
                                    val nextPosition = (statusTabDragPosition + dragAmount.x / tabWidthPx)
                                        .coerceIn(0f, PAGES.lastIndex.toFloat())
                                    statusTabDragPosition = nextPosition
                                    val targetPage = statusPageForIndicator(nextPosition, PAGES.size)
                                    if (targetPage != statusTabRequestedPage) {
                                        statusTabRequestedPage = targetPage
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        drawerScope.launch { pager.scrollToPage(targetPage) }
                                    }
                                }
                            },
                            onDragEnd = { statusTabDragPosition = Float.NaN },
                            onDragCancel = { statusTabDragPosition = Float.NaN },
                        )
                    },
                containerColor = if (isDarkMode) Color.Transparent else MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val indicatorColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxSize()) {
                        val boundedPosition = statusIndicatorPosition
                            .coerceIn(0f, tabPositions.lastIndex.toFloat())
                        val startIndex = boundedPosition.toInt().coerceAtMost(tabPositions.lastIndex)
                        val endIndex = (startIndex + 1).coerceAtMost(tabPositions.lastIndex)
                        val fraction = boundedPosition - startIndex
                        val startCenter = tabPositions[startIndex].left.toPx() +
                            tabPositions[startIndex].width.toPx() / 2f
                        val endCenter = tabPositions[endIndex].left.toPx() +
                            tabPositions[endIndex].width.toPx() / 2f
                        val center = startCenter + (endCenter - startCenter) * fraction
                        val indicatorWidth = 42.dp.toPx()
                        val indicatorHeight = 4.dp.toPx()
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(center - indicatorWidth / 2f, size.height - indicatorHeight),
                            size = Size(indicatorWidth, indicatorHeight),
                            cornerRadius = CornerRadius(indicatorHeight / 2f),
                        )
                    }
                },
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDarkMode) .1f else .08f),
                    )
                },
            ) {
                PAGES.forEachIndexed { index, page ->
                    Tab(
                        selected = pager.currentPage == index,
                        enabled = !state.isBatchEditing,
                        onClick = { onAction(BoardAction.SelectStatus(page.status)) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                page.shortTitle,
                                modifier = Modifier
                                    .background(
                                        if (!statusTabDragPosition.isNaN() && statusTabRequestedPage == index) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                                        } else {
                                            Color.Transparent
                                        },
                                        RoundedCornerShape(9.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 14.sp,
                                fontWeight = if (pager.currentPage == index) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }
            HorizontalPager(
                pager,
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "任务状态内容区" },
                userScrollEnabled = !drawerVisible && !state.isBatchOperationRunning && !state.isBatchEditing,
            ) { index ->
                TaskPage(PAGES[index], state.tasksFor(PAGES[index].status), lists[index], state, onAction)
            }
        }
        reminderTitle?.let {
            ReminderBanner(it, reminderCount, onReminderDoing, onReminderDone, onDismissReminder, Modifier.align(Alignment.TopCenter))
        }
        }
    }
    if (drawerVisible) {
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(2f)
                .graphicsLayer { alpha = .28f * drawerProgress }
                .background(Color.Black)
                .systemGestureExclusion()
                .semantics { contentDescription = "点击关闭菜单" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { animateDrawer(false) },
                ),
        )
    }
    }
    }

    state.editor?.let { TaskEditorSheet(it, onAction) }
    if (state.showTimePicker && state.editor != null) TimePickerSheet(state.editor, onAction)
    if (state.showRepeatPicker && state.editor != null) RepeatPickerSheet(state.editor.reminderRepeat, onAction)
    if (state.showStartDatePicker && state.editor != null) DatePickerModal(
        title = "选择起始日期",
        initialDateMillis = state.editor.startDateMillis,
        onConfirm = { onAction(BoardAction.SetStartDate(it)); onAction(BoardAction.CloseDatePicker) },
        onDismiss = { onAction(BoardAction.CloseDatePicker) },
    )
    if (state.showDueDatePicker && state.editor != null) DatePickerModal(
        title = "选择截止日期",
        initialDateMillis = state.editor.dueDateMillis,
        onConfirm = { onAction(BoardAction.SetDueDate(it)); onAction(BoardAction.CloseDatePicker) },
        onDismiss = { onAction(BoardAction.CloseDatePicker) },
    )
    if (state.showDiscardConfirmation) DiscardDialog(onAction)
    state.operationConfirmation?.let { OperationDialog(it, onAction) }
    if (state.serverDeletionNotices.isNotEmpty()) ServerDeletionDialog(state.serverDeletionNotices, onAction)
}

@Composable private fun AccountActionButton(contact:String?,syncState:String,onClick:()->Unit){
    if(contact==null){
        Button(
            onClick=onClick,
            modifier=Modifier.padding(end=8.dp).height(38.dp),
            shape=RoundedCornerShape(20.dp),
            contentPadding=PaddingValues(horizontal=16.dp),
            colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0A84FF),contentColor=Color.White),
            elevation=ButtonDefaults.buttonElevation(defaultElevation=4.dp,pressedElevation=2.dp),
        ){Text("登入/注册",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Color.White)}
    }else{
        Box(Modifier.padding(end=10.dp).size(40.dp).clickable(onClick=onClick),contentAlignment=Alignment.Center){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(36.dp)){Box(contentAlignment=Alignment.Center){Text(contact.firstOrNull()?.uppercase()?:"我",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold)}}
            Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(when(syncState){"SYNCED"->Color(0xFF30D158);"ERROR"->Color(0xFFFF453A);else->Color(0xFFFFCC00)},CircleShape).border(2.dp,MaterialTheme.colorScheme.background,CircleShape))
        }
    }
}

private enum class SideMenuGlyph { Upload, Download, RecycleBin, Settings }

@Composable
private fun SideMenu(
    modifier: Modifier,
    accountContact: String?,
    accountSyncState: String,
    appVersion: String,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onRecycleBin: () -> Unit,
    onSettings: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
    val drawerColor = if (isDark) Color(0xFF121212) else Color(0xFFFEFEFE)
    val dividerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEFF1F4)
    val primaryText = if (isDark) Color(0xFFF3F4F6) else Color(0xFF171A21)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val accountLabel = accountContact?.takeIf { it.isNotBlank() } ?: "未登录 · 数据仅保存在本机"
    val avatarText = accountContact?.trim()?.firstOrNull()?.uppercase() ?: "D"
    val syncColor = when (accountSyncState) {
        "SYNCED" -> Color(0xFF30D158)
        "ERROR" -> Color(0xFFFF453A)
        else -> Color(0xFFFFCC00)
    }
    val syncLabel = when {
        accountContact == null -> "本机模式"
        accountSyncState == "SYNCED" -> "云端数据已同步"
        accountSyncState == "ERROR" -> "同步失败，请稍后重试"
        else -> "等待同步"
    }

    Surface(
        modifier = modifier,
        color = drawerColor,
        shape = RoundedCornerShape(0.dp),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(start = 28.dp, top = 28.dp, end = 24.dp, bottom = 18.dp)) {
                Box(Modifier.size(58.dp)) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0xFF4C7FF7),
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(avatarText, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(13.dp)
                            .background(syncColor, CircleShape)
                            .border(2.dp, drawerColor, CircleShape),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("DoTi", color = primaryText, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                Text(
                    accountLabel,
                    color = secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(8.dp).background(syncColor, CircleShape))
                    Text(syncLabel, color = secondaryText, fontSize = 12.sp)
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = dividerColor, thickness = 1.dp)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                SideMenuItem("上传到云端", SideMenuGlyph.Upload, primaryText, secondaryText, onClick = onUpload)
                SideMenuItem("从云端同步", SideMenuGlyph.Download, primaryText, secondaryText, onClick = onDownload)
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = dividerColor)
                SideMenuItem(
                    "回收站",
                    SideMenuGlyph.RecycleBin,
                    primaryText,
                    secondaryText,
                    onClick = onRecycleBin,
                )
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = dividerColor)
                SideMenuItem("设置", SideMenuGlyph.Settings, primaryText, secondaryText, onClick = onSettings)
            }

            Spacer(Modifier.weight(1f))
            Text(
                "DoTi V$appVersion",
                color = if (isDark) Color(0xFF5F6570) else Color(0xFF9AA4B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 22.dp),
            )
        }
    }
}

@Composable
private fun SideMenuItem(
    text: String,
    glyph: SideMenuGlyph,
    primaryText: Color,
    secondaryText: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlighted = selected || isPressed
    val itemColor = if (highlighted) Color(0xFFDDEAFE) else Color.Transparent
    val contentColor = if (highlighted) Color(0xFF2864E8) else primaryText
    val glyphColor = if (highlighted) Color(0xFF356FF6) else secondaryText
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        color = itemColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(26.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideMenuIcon(glyph, glyphColor)
            Spacer(Modifier.width(16.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = contentColor)
        }
    }
}

@Composable
private fun SideMenuIcon(glyph: SideMenuGlyph, color: Color) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = glyph.name }) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (glyph) {
            SideMenuGlyph.Upload, SideMenuGlyph.Download -> {
                val cloud = Path().apply {
                    moveTo(w * .22f, h * .68f)
                    cubicTo(w * .05f, h * .68f, w * .05f, h * .42f, w * .25f, h * .40f)
                    cubicTo(w * .30f, h * .14f, w * .70f, h * .14f, w * .76f, h * .43f)
                    cubicTo(w * .96f, h * .43f, w * .97f, h * .69f, w * .78f, h * .69f)
                }
                drawPath(cloud, color, style = stroke)
                val upload = glyph == SideMenuGlyph.Upload
                val shaftStart = if (upload) h * .75f else h * .35f
                val shaftEnd = if (upload) h * .38f else h * .76f
                drawLine(color, Offset(w * .5f, shaftStart), Offset(w * .5f, shaftEnd), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val arrowY = if (upload) h * .38f else h * .76f
                val wingY = if (upload) h * .50f else h * .64f
                drawLine(color, Offset(w * .5f, arrowY), Offset(w * .38f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .5f, arrowY), Offset(w * .62f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            SideMenuGlyph.RecycleBin -> {
                drawRoundRect(color, Offset(w * .30f, h * .30f), androidx.compose.ui.geometry.Size(w * .40f, h * .55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .04f), style = stroke)
                drawLine(color, Offset(w * .22f, h * .24f), Offset(w * .78f, h * .24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .41f, h * .16f), Offset(w * .59f, h * .16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .43f, h * .42f), Offset(w * .43f, h * .72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .57f, h * .42f), Offset(w * .57f, h * .72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            SideMenuGlyph.Settings -> {
                drawCircle(color, radius = w * .18f, center = center, style = stroke)
                repeat(8) { index ->
                    val angle = Math.PI * index / 4.0
                    val inner = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * w * .31f,
                        center.y + kotlin.math.sin(angle).toFloat() * h * .31f,
                    )
                    val outer = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * w * .42f,
                        center.y + kotlin.math.sin(angle).toFloat() * h * .42f,
                    )
                    drawLine(color, inner, outer, strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                drawCircle(color, radius = w * .35f, center = center, style = stroke)
            }
        }
    }
}

@Composable
private fun ReminderBanner(title:String,pendingCount:Int,onDoing:()->Unit,onDone:()->Unit,onDismiss:()->Unit,modifier:Modifier=Modifier){
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var bannerWidth by remember { mutableFloatStateOf(1f) }
    Card(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .onSizeChanged { bannerWidth = it.width.toFloat() }
            .graphicsLayer {
                translationX = dragOffset
                alpha = 1f - (abs(dragOffset) / bannerWidth).coerceIn(0f, .65f)
            }
            .pointerInput(onDismiss, bannerWidth) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceIn(-bannerWidth, bannerWidth)
                    },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = {
                        if (abs(dragOffset) >= bannerWidth * .25f) onDismiss()
                        else dragOffset = 0f
                    },
                )
            },
        shape=RoundedCornerShape(16.dp),
        elevation=CardDefaults.cardElevation(10.dp),
    ){
        Column(Modifier.fillMaxWidth().padding(14.dp)){
            Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text(if(pendingCount>1)"$pendingCount 个任务到期" else "任务提醒",fontWeight=FontWeight.Bold);Text("×",Modifier.clickable(onClick=onDismiss).padding(horizontal=8.dp))}
            Text(title,Modifier.padding(vertical=8.dp),maxLines=4,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),Arrangement.End){TextButton(onClick=onDoing){Text("进行中")};TextButton(onClick=onDone){Text("已完成")}}
        }
    }
}

@Composable
private fun IconButtonSurface(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).semantics { contentDescription = description }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(44.dp)) { content() } }
}

@Composable private fun MenuGlyph() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.padding(11.dp).fillMaxSize()) {
        val stroke = 2.dp.toPx()
        listOf(.28f, .5f, .72f).forEach { y ->
            drawLine(
                color = color,
                start = Offset(size.width * .18f, size.height * y),
                end = Offset(size.width * .82f, size.height * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable private fun SearchGlyph() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.padding(11.dp).fillMaxSize()) {
        val stroke = size.minDimension * .1f
        drawCircle(color, size.minDimension * .28f, Offset(size.width * .43f, size.height * .43f), style = Stroke(stroke))
        drawLine(color, Offset(size.width * .63f, size.height * .63f), Offset(size.width * .88f, size.height * .88f), stroke)
    }
}
@Composable private fun MoreGlyph() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.padding(13.dp).fillMaxSize()) {
        listOf(.2f,.5f,.8f).forEach { drawCircle(color, size.minDimension * .09f, Offset(size.width/2, size.height*it)) }
    }
}
@Composable private fun ThemeGlyph(dark: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(22.dp)) {
        if (dark) { drawCircle(color, size.minDimension*.4f); drawCircle(surface, size.minDimension*.36f, Offset(size.width*.68f,size.height*.33f)) }
        else { drawCircle(color, size.minDimension*.25f, style=Stroke(2.dp.toPx())); repeat(8) { val a=Math.PI*it/4; drawLine(color, Offset(size.width/2,size.height/2), Offset((size.width/2+Math.cos(a)*size.width*.48).toFloat(),(size.height/2+Math.sin(a)*size.height*.48).toFloat()),2.dp.toPx()) } }
    }
}

@Composable private fun ChecklistGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(color = color, style = Stroke(stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawLine(color, Offset(size.width * .23f, size.height * .5f), Offset(size.width * .42f, size.height * .68f), stroke)
        drawLine(color, Offset(size.width * .42f, size.height * .68f), Offset(size.width * .78f, size.height * .3f), stroke)
    }
}

@Composable private fun TrashGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * .22f, size.height * .25f), Offset(size.width * .78f, size.height * .25f), stroke)
        drawLine(color, Offset(size.width * .38f, size.height * .12f), Offset(size.width * .62f, size.height * .12f), stroke)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * .28f, size.height * .32f),
            size = androidx.compose.ui.geometry.Size(size.width * .44f, size.height * .55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecycleBinTopBar(state: BoardUiState, onAction: (BoardAction) -> Unit) {
    var backDispatched by remember(state.isRecycleBinBatchEditing) { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            TextButton(
                enabled = !backDispatched,
                onClick = {
                    if (!backDispatched) {
                        backDispatched = true
                        onAction(
                            if (state.isRecycleBinBatchEditing) BoardAction.CancelRecycleBinBatchEdit
                            else BoardAction.CloseRecycleBin,
                        )
                    }
                },
            ) { Text("‹ 返回") }
        },
        title = { Text("回收站", fontWeight = FontWeight.Bold) },
        actions = {
            if (state.deletedTasks.isNotEmpty()) {
                TextButton(
                    enabled = !state.isRecycleBinOperationRunning,
                    onClick = {
                        onAction(
                            if (state.isRecycleBinBatchEditing) BoardAction.CancelRecycleBinBatchEdit
                            else BoardAction.StartRecycleBinBatchEdit,
                        )
                    },
                ) { Text(if (state.isRecycleBinBatchEditing) "取消" else "批量编辑") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable private fun RecycleBinScreen(
    state: BoardUiState,
    onAction: (BoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks = state.deletedTasks
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(
            "已删除任务将在回收站保留 30 天",
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(tasks, key = Task::id) { task ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.isRecycleBinBatchEditing) {
                                Checkbox(
                                    checked = task.id in state.selectedDeletedTaskIds,
                                    onCheckedChange = { onAction(BoardAction.ToggleDeletedTaskSelection(task.id)) },
                                    enabled = !state.isRecycleBinOperationRunning,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(task.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    "${task.status.chineseLabel()} · 删除于 ${task.deletedAtMillis?.formatDateTime().orEmpty()}",
                                    Modifier.padding(top = 5.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (!state.isRecycleBinBatchEditing) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(
                                            enabled = task.id !in state.busyTaskIds,
                                            onClick = { onAction(BoardAction.RequestPermanentDelete(task)) },
                                        ) {
                                            Text("永久删除", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.isRecycleBinBatchEditing) {
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val allSelected = tasks.isNotEmpty() && state.selectedDeletedTaskIds.containsAll(tasks.map(Task::id))
                        TextButton(
                            enabled = !state.isRecycleBinOperationRunning,
                            onClick = { onAction(BoardAction.ToggleSelectAllDeletedTasks) },
                        ) { Text(if (allSelected) "取消全选" else "全选") }
                        TextButton(
                            enabled = state.selectedDeletedTaskIds.isNotEmpty() && !state.isRecycleBinOperationRunning,
                            onClick = { onAction(BoardAction.RequestPermanentDeleteSelected) },
                        ) { Text("删除（${state.selectedDeletedTaskIds.size}）", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable private fun SearchResultGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(28.dp)) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * .08f, size.height * .04f),
            size = androidx.compose.ui.geometry.Size(size.width * .62f, size.height * .78f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(color, size.width * .18f, Offset(size.width * .69f, size.height * .7f), style = Stroke(stroke))
        drawLine(color, Offset(size.width * .82f, size.height * .83f), Offset(size.width * .96f, size.height * .97f), stroke)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SearchTopBar(query: String, onAction: (BoardAction) -> Unit) {
    val focus = remember { FocusRequester() }; val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(100); focus.requestFocus(); keyboard?.show() }
    TopAppBar(
        navigationIcon = {
            TextButton(onClick = { keyboard?.hide(); onAction(BoardAction.ExitSearch) }) { Text("‹ 返回") }
        },
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = { onAction(BoardAction.UpdateSearch(it)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("搜索任务") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Box(
                            Modifier.size(44.dp).semantics { contentDescription = "清除搜索" }
                                .clickable { onAction(BoardAction.UpdateSearch("")) },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable private fun SearchResults(state: BoardUiState, onAction: (BoardAction)->Unit, modifier: Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    when {
        state.searchQuery.isBlank() -> Box(modifier.fillMaxSize(), Alignment.TopCenter) {
            Text("输入第一个字后显示搜索结果", Modifier.padding(top = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.searchResults.isEmpty() -> Box(modifier.fillMaxSize(), Alignment.TopCenter) {
            Text("没有找到相关任务", Modifier.padding(top = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.searchResults, key = Task::id) { task ->
                Surface(
                    onClick = { keyboard?.hide(); onAction(BoardAction.SelectSearchResult(task)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchResultGlyph()
                        Text(task.title, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        StatusBadge(task.status)
                    }
                }
            }
            item {
                Text(
                    "点击结果可跳转并定位任务",
                    Modifier.fillMaxWidth().padding(top = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable private fun StatusBadge(status: TaskStatus) {
    val (background, foreground) = when (status) {
        TaskStatus.TODO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        TaskStatus.DOING -> Color(0xFFFFF1D6) to Color(0xFF9A6200)
        TaskStatus.DONE -> Color(0xFFE4F3EA) to Color(0xFF2F6B55)
    }
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(status.name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = foreground, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun TaskPage(page: BoardPage, tasks: List<Task>, list: LazyListState, state: BoardUiState, onAction:(BoardAction)->Unit) {
    val taskOrderKey = tasks.map(Task::id)
    val orderedTasks = remember(page.status, taskOrderKey) { tasks.toMutableStateList() }
    var draggedTaskId by remember(page.status) { mutableStateOf<String?>(null) }
    var draggedOffsetY by remember(page.status) { mutableFloatStateOf(0f) }
    val reorderEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxAutoScrollPx = with(LocalDensity.current) { 18.dp.toPx() }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(BoardAction.RefreshBoard) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .clickable(enabled = state.expandedTaskId != null) { onAction(BoardAction.CloseTaskActions) },
            state = list,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 112.dp),
        ) {
            if (orderedTasks.isNotEmpty()) {
                item(key = "${page.status}-header") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onAction(BoardAction.CloseTaskActions) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(page.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(orderedTasks.size.toString(), color = page.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (orderedTasks.isEmpty()) item(key = "${page.status}-empty") {
                EmptyTaskState(page, Modifier.fillParentMaxHeight(.82f).fillMaxWidth())
            } else {
                items(orderedTasks, key=Task::id) { task ->
                    val isDragged = draggedTaskId == task.id
                    val reorderEnabled = !state.isBatchOperationRunning &&
                        task.id !in state.busyTaskIds &&
                        task.id !in state.transientCompletedTaskIds
                    TaskCard(
                        task,
                        page.accent,
                        state,
                        onAction,
                        Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .shadow(if (isDragged) 12.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = draggedOffsetY
                                    scaleX = 1.015f
                                    scaleY = 1.015f
                                }
                            }
                            .pointerInput(reorderEnabled, task.id, taskOrderKey) {
                                if (reorderEnabled) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (!state.isBatchEditing) {
                                                onAction(BoardAction.StartBatchEdit(task.id))
                                            } else if (task.id !in state.selectedTaskIds) {
                                                onAction(BoardAction.ToggleBatchSelection(task.id))
                                            }
                                            draggedTaskId = task.id
                                            draggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedOffsetY += dragAmount.y
                                            var visible = list.layoutInfo.visibleItemsInfo
                                            val currentInfo = visible.firstOrNull { it.key == task.id } ?: return@detectDragGesturesAfterLongPress
                                            var draggedCenter = currentInfo.offset + currentInfo.size / 2f + draggedOffsetY
                                            val layoutInfo = list.layoutInfo
                                            val edgeScroll = when {
                                                draggedCenter < layoutInfo.viewportStartOffset + reorderEdgePx -> {
                                                    val proximity = ((layoutInfo.viewportStartOffset + reorderEdgePx - draggedCenter) / reorderEdgePx)
                                                        .coerceIn(0f, 1f)
                                                    -maxAutoScrollPx * proximity
                                                }
                                                draggedCenter > layoutInfo.viewportEndOffset - reorderEdgePx -> {
                                                    val proximity = ((draggedCenter - (layoutInfo.viewportEndOffset - reorderEdgePx)) / reorderEdgePx)
                                                        .coerceIn(0f, 1f)
                                                    maxAutoScrollPx * proximity
                                                }
                                                else -> 0f
                                            }
                                            if (edgeScroll != 0f) {
                                                val consumedScroll = list.dispatchRawDelta(edgeScroll)
                                                draggedOffsetY += consumedScroll
                                                visible = list.layoutInfo.visibleItemsInfo
                                                val updatedInfo = visible.firstOrNull { it.key == task.id }
                                                if (updatedInfo != null) {
                                                    draggedCenter = updatedInfo.offset + updatedInfo.size / 2f + draggedOffsetY
                                                }
                                            }
                                            val targetInfo = visible
                                                .filter { info -> orderedTasks.any { it.id == info.key } }
                                                .minByOrNull { info -> abs((info.offset + info.size / 2f) - draggedCenter) }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val currentIndex = orderedTasks.indexOfFirst { it.id == task.id }
                                            val targetIndex = orderedTasks.indexOfFirst { it.id == targetInfo.key }
                                            if (currentIndex >= 0 && targetIndex >= 0 && currentIndex != targetIndex) {
                                                val previousOffset = currentInfo.offset
                                                val targetOffset = targetInfo.offset
                                                val moved = orderedTasks.removeAt(currentIndex)
                                                orderedTasks.add(targetIndex, moved)
                                                draggedOffsetY += previousOffset - targetOffset
                                            }
                                        },
                                        onDragEnd = {
                                            val finalOrder = orderedTasks.map(Task::id)
                                            draggedTaskId = null
                                            draggedOffsetY = 0f
                                            onAction(BoardAction.ReorderTasks(finalOrder))
                                        },
                                        onDragCancel = {
                                            orderedTasks.clear()
                                            orderedTasks.addAll(tasks)
                                            draggedTaskId = null
                                            draggedOffsetY = 0f
                                        },
                                    )
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTaskState(page: BoardPage, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { contentDescription = "任务列表为空" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f),
        ) {
            Box(contentAlignment = Alignment.Center) { EmptyListGlyph(Modifier.size(42.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "${page.title} 列表为空",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "点击右下角按钮添加新任务",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun EmptyListGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val stroke = 2.4.dp.toPx()
        val bulletRadius = 2.2.dp.toPx()
        listOf(.27f, .5f, .73f).forEach { y ->
            drawCircle(color, bulletRadius, Offset(size.width * .18f, size.height * y))
            drawLine(
                color,
                Offset(size.width * .34f, size.height * y),
                Offset(size.width * .86f, size.height * y),
                stroke,
            )
        }
    }
}

@Composable private fun TaskCard(task: Task, accent: Color, state: BoardUiState, onAction:(BoardAction)->Unit, modifier: Modifier = Modifier) {
    val flash=remember(task.id){Animatable(0f)}
    val generation=state.highlightRequest?.takeIf{it.taskId==task.id}?.generation
    LaunchedEffect(generation) { if(generation!=null){ repeat(3){flash.snapTo(1f);delay(500);flash.snapTo(0f);delay(500)};onAction(BoardAction.HighlightConsumed)} }
    Column {
        val isTransientCompleted = task.status != TaskStatus.DONE && task.id in state.transientCompletedTaskIds
        val shape=RoundedCornerShape(16.dp)
        Card(
            modifier.fillMaxWidth().border(2.dp,Color(0xFFFFE082).copy(alpha=flash.value),shape)
                .semantics {
                    contentDescription = "任务卡片：${task.title}"
                    if (!isTransientCompleted && task.id !in state.busyTaskIds) {
                        onLongClick {
                            if (!state.isBatchEditing) onAction(BoardAction.StartBatchEdit(task.id))
                            true
                        }
                    }
                }
                .combinedClickable(
                    enabled = !isTransientCompleted,
                    onClick={ if(state.isBatchEditing) onAction(BoardAction.ToggleBatchSelection(task.id)) else onAction(BoardAction.OpenEdit(task)) },
                    onLongClick = null,
                ),
            shape=shape,
            colors=CardDefaults.cardColors(if(isTransientCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
            elevation=CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment=Alignment.Top) {
                if(state.isBatchEditing) Checkbox(task.id in state.selectedTaskIds,{onAction(BoardAction.ToggleBatchSelection(task.id))},enabled=!state.isBatchOperationRunning)
                else if (task.status != TaskStatus.DONE) {
                    if (isTransientCompleted) CompletedMarker(Modifier.padding(top = 1.dp, end = 10.dp))
                    else StatusMarker(
                        accent,
                        Modifier.padding(top = 1.dp, end = 10.dp)
                            .semantics { contentDescription = "完成任务：${task.title}" }
                            .clickable(
                            enabled = task.id !in state.busyTaskIds,
                            onClick = { onAction(BoardAction.QuickComplete(task)) },
                        ),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight=FontWeight.SemiBold, maxLines=3, overflow=TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    task.compactMetadata()?.let { metadata ->
                        Text(
                            metadata,
                            Modifier.padding(top = 5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (isTransientCompleted) {
                HorizontalDivider(
                    Modifier.align(Alignment.CenterStart).padding(start = 48.dp, end = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                )
            }
            }
        }
    }
}

@Composable private fun CompletedMarker(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
    Canvas(modifier.size(24.dp).semantics { contentDescription = "已勾选完成" }) {
        drawCircle(color)
        val stroke = 2.dp.toPx()
        drawLine(Color.White, Offset(size.width * .25f, size.height * .52f), Offset(size.width * .43f, size.height * .7f), stroke)
        drawLine(Color.White, Offset(size.width * .43f, size.height * .7f), Offset(size.width * .76f, size.height * .34f), stroke)
    }
}

@Composable private fun StatusMarker(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) { drawCircle(color = color, style = Stroke(2.dp.toPx())) }
}

@Composable private fun ClockGlyph(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color, size.minDimension * .42f, center, style = Stroke(stroke))
        drawLine(color, center, Offset(center.x, center.y - size.height * .22f), stroke)
        drawLine(color, center, Offset(center.x + size.width * .18f, center.y + size.height * .1f), stroke)
    }
}

@Composable private fun BatchBar(state:BoardUiState,onAction:(BoardAction)->Unit){
    val hasSelection = state.selectedTaskIds.isNotEmpty() && !state.isBatchOperationRunning
    Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=8.dp){
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=10.dp),
            horizontalArrangement=Arrangement.spacedBy(10.dp),
        ){
            BatchButton("全选",!state.isBatchOperationRunning,Color(0xFF0A84FF)){onAction(BoardAction.ToggleSelectAll)}
            if(state.selectedStatus!=TaskStatus.TODO){
                val previous=if(state.selectedStatus==TaskStatus.DOING)TaskStatus.TODO else TaskStatus.DOING
                BatchButton("撤回",hasSelection,Color(0xFFFFC107)){onAction(BoardAction.BatchMove(previous))}
            }
            BatchButton("删除",hasSelection,Color(0xFFFF453A)){onAction(BoardAction.RequestBatchDelete)}
        }
    }
}
@Composable private fun RowScope.BatchButton(text:String,enabled:Boolean,activeColor:Color,onClick:()->Unit){
    val color=if(enabled)activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.48f)
    Box(
        Modifier.weight(1f).height(46.dp).border(1.dp,color,RoundedCornerShape(12.dp))
            .clickable(enabled=enabled,onClick=onClick),
        contentAlignment=Alignment.Center,
    ){Text(text,color=color,fontWeight=FontWeight.SemiBold)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskEditorSheet(editor:TaskEditorState,onAction:(BoardAction)->Unit){
    val focus=remember{FocusRequester()}; val keyboard=LocalSoftwareKeyboardController.current; val focusManager=LocalFocusManager.current
    var contentFieldReady by remember(editor.taskId) { mutableStateOf(false) }
    var initialFocusRequested by remember(editor.taskId) { mutableStateOf(false) }
    var sheetHeightPx by remember(editor.taskId) { mutableFloatStateOf(1f) }
    var dragOffsetPx by remember(editor.taskId) { mutableFloatStateOf(0f) }
    var contentFieldFocused by remember(editor.taskId) { mutableStateOf(false) }
    var keyboardDismissedForGesture by remember(editor.taskId) { mutableStateOf(false) }
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        Unit
    }
    fun dismissKeyboardForVerticalGesture() {
        if (contentFieldFocused && !keyboardDismissedForGesture) {
            keyboardDismissedForGesture = true
            dismissKeyboard()
        }
    }
    fun consumeEditorDrag(delta: Float): Float {
        if (delta != 0f) dismissKeyboardForVerticalGesture()
        if (delta <= 0f && dragOffsetPx <= 0f) return 0f
        val previous = dragOffsetPx
        dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, sheetHeightPx)
        return dragOffsetPx - previous
    }
    val editorDragState = rememberDraggableState(::consumeEditorDrag)
    suspend fun settleEditorDrag(velocity: Float) {
        val shouldDismiss = shouldDismissEditorDrag(dragOffsetPx, sheetHeightPx, velocity)
        when {
            !shouldDismiss -> animate(
                initialValue = dragOffsetPx,
                targetValue = 0f,
                animationSpec = tween(180),
            ) { value, _ -> dragOffsetPx = value }
            editor.isDirty() -> {
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = 0f,
                    animationSpec = tween(160),
                ) { value, _ -> dragOffsetPx = value }
                onAction(BoardAction.RequestCloseEditor)
            }
            else -> {
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = sheetHeightPx,
                    animationSpec = tween(210),
                ) { value, _ -> dragOffsetPx = value }
                onAction(BoardAction.RequestCloseEditor)
            }
        }
        keyboardDismissedForGesture = false
    }
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    val editorDragConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y != 0f) dismissKeyboardForVerticalGesture()
            if (available.y < 0f && dragOffsetPx > 0f) {
                return Offset(0f, consumeEditorDrag(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            return Offset(0f, consumeEditorDrag(available.y))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (dragOffsetPx <= 0f) return Velocity.Zero
            settleEditorDrag(available.y)
            return Velocity(0f, available.y)
        }
    }
    LaunchedEffect(editor.taskId, contentFieldReady) {
        if (!contentFieldReady || initialFocusRequested) return@LaunchedEffect
        initialFocusRequested = true
        focus.requestFocus()
        withFrameNanos { }
        keyboard?.show()
    }
    Box(Modifier.fillMaxSize().zIndex(4f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .32f))
                .semantics { contentDescription = "点击关闭任务编辑" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onAction(BoardAction.RequestCloseEditor) },
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = dragOffsetPx },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(contentFieldFocused) {
                    if (!contentFieldFocused) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        var previousY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.position.y != previousY) {
                                dismissKeyboardForVerticalGesture()
                                break
                            }
                            if (!change.pressed) break
                            previousY = change.position.y
                        }
                    }
                }
                .nestedScroll(editorDragConnection)
                .semantics { contentDescription = "下拉关闭任务编辑" }
                .draggable(
                    state = editorDragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        keyboardDismissedForGesture = false
                    },
                    onDragStopped = { velocity -> settleEditorDrag(velocity) },
                )
                .padding(horizontal=20.dp,vertical=8.dp)
                .clickable(
                    interactionSource = backgroundInteractionSource,
                    indication = null,
                    onClick = dismissKeyboard,
                ),
        ){
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier.padding(top = 4.dp).width(32.dp).height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editor.isEditing) "修改任务" else "添加待办",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { onAction(BoardAction.RequestCloseEditor) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            dismissKeyboard()
                            onAction(BoardAction.SaveEditor)
                        },
                        enabled = editor.content.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                    ) { Text(if (editor.isEditing) "保存" else "添加") }
                }
            }
            LazyColumn(
                modifier=Modifier.fillMaxWidth().weight(1f),
                contentPadding=PaddingValues(top=18.dp,bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp),
            ){
                item {
                    OutlinedTextField(
                        value = editor.content,
                        onValueChange = { onAction(BoardAction.UpdateEditorContent(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .onSizeChanged {
                                if (it.width > 0 && it.height > 0) contentFieldReady = true
                            }
                            .focusRequester(focus)
                            .onFocusChanged {
                                contentFieldFocused = it.isFocused
                                if (it.isFocused) keyboardDismissedForGesture = false
                            }
                            .semantics { contentDescription = "任务内容输入框" },
                        label = { Text("任务内容") },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 7,
                        maxLines = 14,
                        keyboardOptions = KeyboardOptions(imeAction=ImeAction.Default),
                    )
                }
                item {
                    DateRangeSection(editor, dismissKeyboard, onAction)
                }
                item {
                    SettingRow(
                        label = "提醒时间",
                        value = if(editor.hasReminder) "%02d:%02d".format(editor.reminderHour,editor.reminderMinute) else "未设置",
                        icon = SettingIcon.TIME,
                    ){dismissKeyboard();onAction(BoardAction.OpenTimePicker)}
                }
                if(editor.hasReminder)item { TextButton(onClick={dismissKeyboard();onAction(BoardAction.ClearReminderTime)}){Text("清除提醒")} }
                item {
                    SettingRow(
                        label = "重复类型",
                        value = if(editor.hasReminder) editor.reminderRepeat.label() else "请先设置提醒时间",
                        icon = SettingIcon.REPEAT,
                    ){dismissKeyboard();onAction(BoardAction.OpenRepeatPicker)}
                }
                editor.validationMessage?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error)}}
            }
        }
    }
    }
}

private enum class SettingIcon { DATE, TIME, REPEAT }

@Composable private fun DateRangeSection(
    editor: TaskEditorState,
    dismissKeyboard: () -> Unit,
    onAction: (BoardAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                    dismissKeyboard()
                    onAction(BoardAction.ToggleDateSection)
                }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CalendarGlyph(Modifier.size(24.dp))
                Text("日期（选填）", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(if (editor.isDateSectionExpanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editor.isDateSectionExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DateRow("起始日期", editor.startDateMillis) {
                    dismissKeyboard(); onAction(BoardAction.OpenStartDatePicker)
                }
                DateRow("截止日期", editor.dueDateMillis) {
                    dismissKeyboard(); onAction(BoardAction.OpenDueDatePicker)
                }
            }
        }
    }
}

@Composable private fun DateRow(label: String, value: Long?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.formatDate() ?: "未设置", fontWeight = FontWeight.Medium)
        Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SettingRow(label:String,value:String,icon:SettingIcon,onClick:()->Unit){
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick=onClick),
        shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ){
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ){
            when(icon){
                SettingIcon.DATE -> CalendarGlyph(Modifier.size(24.dp))
                SettingIcon.TIME -> ClockGlyph(Modifier.size(24.dp))
                SettingIcon.REPEAT -> RepeatGlyph()
            }
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable private fun CalendarGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        drawRoundRect(color = color, style = Stroke(stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawLine(color, Offset(0f, size.height * .34f), Offset(size.width, size.height * .34f), stroke)
        drawLine(color, Offset(size.width * .28f, 0f), Offset(size.width * .28f, size.height * .18f), stroke)
        drawLine(color, Offset(size.width * .72f, 0f), Offset(size.width * .72f, size.height * .18f), stroke)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DatePickerModal(
    title: String,
    initialDateMillis: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(picker.selectedDateMillis) }) { Text("确定") } },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) { Text("清除") }
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = picker, title = { Text(title, Modifier.padding(start = 24.dp, top = 20.dp)) })
    }
}

@Composable private fun RepeatGlyph(){
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)){
        val stroke=1.8.dp.toPx()
        drawArc(color,-55f,220f,false,style=Stroke(stroke))
        drawArc(color,125f,220f,false,style=Stroke(stroke))
        drawLine(color,Offset(size.width*.84f,size.height*.12f),Offset(size.width*.93f,size.height*.35f),stroke)
        drawLine(color,Offset(size.width*.84f,size.height*.12f),Offset(size.width*.62f,size.height*.17f),stroke)
        drawLine(color,Offset(size.width*.16f,size.height*.88f),Offset(size.width*.07f,size.height*.65f),stroke)
        drawLine(color,Offset(size.width*.16f,size.height*.88f),Offset(size.width*.38f,size.height*.83f),stroke)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimePickerSheet(editor:TaskEditorState,onAction:(BoardAction)->Unit){
    val now=java.time.LocalTime.now(); var hour by remember{mutableIntStateOf(editor.reminderHour?:now.hour)};var minute by remember{mutableIntStateOf(editor.reminderMinute?:now.minute)}
    var sheetHeightPx by remember { mutableFloatStateOf(1f) }
    var dismissDragPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val dismissDragState = rememberDraggableState { delta ->
        dismissDragPx = (dismissDragPx + delta).coerceIn(-sheetHeightPx, sheetHeightPx)
    }
    suspend fun settleDismissDrag(velocity: Float) {
        val shouldDismiss = abs(dismissDragPx) >= dismissThresholdPx || abs(velocity) >= 1_200f
        val target = if (shouldDismiss) {
            val direction = when {
                abs(velocity) >= 1_200f -> if (velocity < 0f) -1f else 1f
                dismissDragPx < 0f -> -1f
                else -> 1f
            }
            direction * sheetHeightPx
        } else {
            0f
        }
        animate(
            initialValue = dismissDragPx,
            targetValue = target,
            animationSpec = tween(if (shouldDismiss) 180 else 160),
        ) { value, _ -> dismissDragPx = value }
        if (shouldDismiss) onAction(BoardAction.CloseTimePicker)
    }
    ModalBottomSheet(
        onDismissRequest = {onAction(BoardAction.CloseTimePicker)},
        modifier = Modifier
            .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = dismissDragPx },
        sheetState=rememberModalBottomSheetState(true),
        sheetGesturesEnabled=false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ){
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 560.dp)
                .semantics { contentDescription = "上下滑动关闭提醒时间" }
                .draggable(
                    state = dismissDragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dismissDragPx = 0f },
                    onDragStopped = { velocity -> settleDismissDrag(velocity) },
                )
                .padding(bottom=24.dp),
        ){
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){
                Text("提醒时间",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                TextButton(onClick={onAction(BoardAction.CloseTimePicker)}, modifier = Modifier.heightIn(min = 48.dp)){Text("关闭")}
            }
            Text("%02d:%02d".format(hour,minute),Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),style=MaterialTheme.typography.displaySmall,fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().height(288.dp).padding(horizontal=22.dp, vertical = 6.dp)){
                Wheel(24,hour,{hour=it},Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Wheel(60,minute,{minute=it},Modifier.weight(1f))
            }
            TextButton(
                onClick={onAction(BoardAction.SetReminderTime(hour,minute));onAction(BoardAction.CloseTimePicker)},
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).padding(horizontal = 28.dp),
            ){Text("确定", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)}
        }
    }
}
@Composable private fun Wheel(count:Int,selected:Int,onSelected:(Int)->Unit,modifier:Modifier){
    val state=rememberLazyListState(initialFirstVisibleItemIndex=circularTimeInitialIndex(count,selected))
    val snap=rememberSnapFlingBehavior(lazyListState=state)
    LaunchedEffect(state){snapshotFlow{state.isScrollInProgress to state.firstVisibleItemIndex}.collectLatest{(moving,index)->if(!moving)onSelected(circularTimeValue(index,count))}}
    LaunchedEffect(selected){
        if(!state.isScrollInProgress&&circularTimeValue(state.firstVisibleItemIndex,count)!=selected){
            state.animateScrollToItem(nearestCircularTimeIndex(state.firstVisibleItemIndex,count,selected))
        }
    }
    Box(modifier.fillMaxHeight()){
        Surface(Modifier.fillMaxWidth().height(52.dp).align(Alignment.Center),RoundedCornerShape(16.dp),MaterialTheme.colorScheme.primaryContainer.copy(alpha=.7f)){}
        LazyColumn(Modifier.fillMaxSize(),state=state,flingBehavior=snap,contentPadding=PaddingValues(vertical=118.dp)){
            items(CircularTimeWheelItemCount){index->
                val value=circularTimeValue(index,count);val isCentered=index==state.firstVisibleItemIndex
                Box(Modifier.fillMaxWidth().height(52.dp).clickable{onSelected(value)},Alignment.Center){Text("%02d".format(value),style=if(isCentered)MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,fontWeight=if(isCentered)FontWeight.SemiBold else FontWeight.Normal,color=if(isCentered)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RepeatPickerSheet(selected:ReminderRepeat,onAction:(BoardAction)->Unit){
    ModalBottomSheet(
        onDismissRequest = {onAction(BoardAction.CloseRepeatPicker)},
        sheetState=rememberModalBottomSheetState(true),
        sheetGesturesEnabled=false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ){
        Column(Modifier.fillMaxWidth().padding(bottom=28.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){Text("重复类型",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);TextButton(onClick={onAction(BoardAction.CloseRepeatPicker)}, modifier = Modifier.heightIn(min = 48.dp)){Text("关闭")}}
            ReminderRepeat.entries.forEach{repeat->Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable{onAction(BoardAction.SetReminderRepeat(repeat))}.padding(horizontal = 20.dp, vertical = 16.dp),Arrangement.SpaceBetween){Text(repeat.label(),color=if(repeat==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,fontWeight = if(repeat==selected)FontWeight.SemiBold else FontWeight.Normal);if(repeat==selected)Text("✓",color=MaterialTheme.colorScheme.primary,fontWeight = FontWeight.Bold)};HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)}
        }
    }
}

@Composable private fun DiscardDialog(onAction:(BoardAction)->Unit)=AlertDialog({onAction(BoardAction.KeepEditing)},title={Text("放弃本次修改？")},text={Text("任务内容、日期、提醒时间或重复类型已经发生变化。")},confirmButton={TextButton(onClick={onAction(BoardAction.ConfirmDiscardEditor)}){Text("确认放弃",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={onAction(BoardAction.KeepEditing)}){Text("继续编辑")}})

@Composable
private fun ServerDeletionDialog(
    notices: List<ServerDeletionNotice>,
    onAction: (BoardAction) -> Unit,
) {
    val ids = notices.mapTo(mutableSetOf(), ServerDeletionNotice::taskId)
    val acknowledge: (Boolean) -> Unit = { openRecycleBin ->
        onAction(BoardAction.AcknowledgeServerDeletionNotices(ids, openRecycleBin))
    }
    AlertDialog(
        onDismissRequest = { acknowledge(false) },
        title = {
            Text(
                if (notices.size == 1) "任务已在其他设备删除"
                else "${notices.size} 个任务已在其他设备删除",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notices.take(3).forEach { notice ->
                    Column {
                        Text("“${notice.title}”")
                        Text(
                            "删除时间：${notice.deletedAtMillis.formatDeletionTime()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (notices.size > 3) {
                    Text(
                        "另有 ${notices.size - 3} 个任务，点击“查看回收站”查看全部。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "本设备已同步更新。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { acknowledge(false) }) { Text("知道了") }
        },
        dismissButton = {
            TextButton(onClick = { acknowledge(true) }) { Text("查看回收站") }
        },
    )
}
@Composable private fun OperationDialog(value:OperationConfirmation,onAction:(BoardAction)->Unit){
    val title=when(value){is OperationConfirmation.BatchDelete->"删除所选任务？";is OperationConfirmation.Delete->"删除任务？";is OperationConfirmation.PermanentDelete->"永久删除任务？";is OperationConfirmation.PermanentDeleteMany->"永久删除所选任务？";is OperationConfirmation.Move->"确认移动任务？"}
    val text=when(value){is OperationConfirmation.BatchDelete->"将删除 ${value.taskIds.size} 条任务，删除后可在回收站查看。";is OperationConfirmation.Delete->"任务将移入回收站并保留 30 天。";is OperationConfirmation.PermanentDelete->"永久删除后无法恢复，并将在同步完成后清除本机副本。";is OperationConfirmation.PermanentDeleteMany->"将永久删除 ${value.taskIds.size} 条任务。此操作无法恢复。";is OperationConfirmation.Move->"任务将移至${value.destination.chineseLabel()}。"}
    val destructive=value is OperationConfirmation.PermanentDelete || value is OperationConfirmation.PermanentDeleteMany
    AlertDialog({onAction(BoardAction.CancelOperation)},title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick={onAction(BoardAction.ConfirmOperation)}){Text(if(destructive)"永久删除" else "确认",color=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}},dismissButton={TextButton(onClick={onAction(BoardAction.CancelOperation)}){Text("取消")}})
}

private data class BoardPage(val shortTitle:String,val title:String,val status:TaskStatus,val accent:Color)
private val PAGES=listOf(BoardPage("TODO","待办 TODO",TaskStatus.TODO,Color(0xFF8E8E93)),BoardPage("DOING","进行中 DOING",TaskStatus.DOING,Color(0xFF0A84FF)),BoardPage("DONE","已完成 DONE",TaskStatus.DONE,Color(0xFF30D158)))
private fun BoardUiState.tasksFor(status:TaskStatus)=when(status){TaskStatus.TODO->todo;TaskStatus.DOING->doing;TaskStatus.DONE->done}
private fun TaskStatus.pageIndex()=when(this){TaskStatus.TODO->0;TaskStatus.DOING->1;TaskStatus.DONE->2}
private fun TaskStatus.accent()=PAGES[pageIndex()].accent
private fun TaskStatus.chineseLabel()=when(this){TaskStatus.TODO->"待办";TaskStatus.DOING->"进行中";TaskStatus.DONE->"已完成"}
private fun ReminderRepeat.label()=when(this){ReminderRepeat.NONE->"不重复";ReminderRepeat.DAILY->"每天重复";ReminderRepeat.WEEKLY->"每周重复";ReminderRepeat.WEEKDAYS->"每周工作日重复"}
private fun Long.formatReminder()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun Long.formatDate()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
private fun Long.formatDateTime()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun Long.formatDeletionTime()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
private fun Task.compactMetadata(): String? = buildList {
    if (isPinned) add("⌖ 已置顶")
    val start = startDateMillis
    val due = dueDateMillis
    when {
        start != null && due != null -> add("${start.formatDate()}—${due.formatDate()}")
        start != null -> add("开始 ${start.formatDate()}")
        due != null -> add("截止 ${due.formatDate()}")
    }
    reminderAtMillis?.let { add("提醒 ${it.formatReminder()}") }
    if (reminderRepeat != ReminderRepeat.NONE) add(reminderRepeat.label())
}.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
