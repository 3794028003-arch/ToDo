package com.example.localfirst.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun BoardRoute(
    viewModel: BoardViewModel,
    onReminderPermissionsRequired: () -> Unit,
    reminderTitle: String? = null,
    reminderCount: Int = 0,
    onReminderDoing: () -> Unit = {},
    onReminderDone: () -> Unit = {},
    onDismissReminder: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.serverDeletionNotice?.taskId) {
        state.serverDeletionNotice?.let {
            snackbar.showSnackbar("任务“${it.title}”已在其他设备删除")
            viewModel.onAction(BoardAction.DismissServerDeletionNotice(it.taskId))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { if (it == BoardEffect.RequestReminderPermissions) onReminderPermissionsRequired() }
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
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val pager = rememberPagerState { PAGES.size }
    val lists = listOf(rememberLazyListState(), rememberLazyListState(), rememberLazyListState())

    BackHandler(state.isSearching || state.editor != null || state.isBatchEditing || state.isRecycleBinOpen) {
        when {
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isRecycleBinOpen) RecycleBinTopBar(state, onAction)
            else if (state.isSearching) SearchTopBar(state.searchQuery, onAction)
            else TopAppBar(
                title = {
                    Text(
                        if (state.isBatchEditing) "已选择 ${state.selectedTaskIds.size} 项" else "记录你的每一天",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    if (state.isBatchEditing) TextButton(onClick = { onAction(BoardAction.CancelBatchEdit) }, enabled = !state.isBatchOperationRunning) { Text("取消") }
                    else {
                        IconButtonSurface("搜索", { onAction(BoardAction.StartSearch) }) { SearchGlyph() }
                        Box {
                            IconButtonSurface("更多", { onAction(BoardAction.ToggleMainMenu) }) { MoreGlyph() }
                            DropdownMenu(
                                expanded = state.isMainMenuOpen,
                                onDismissRequest = { onAction(BoardAction.CloseMainMenu) },
                                modifier = Modifier.width(208.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("回收站") },
                                    leadingIcon = { TrashGlyph() },
                                    onClick = { onAction(BoardAction.OpenRecycleBin) },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.isDarkMode) "切换浅色模式" else "切换深色模式") },
                                    leadingIcon = { ThemeGlyph(state.isDarkMode) },
                                    onClick = { onAction(BoardAction.ToggleTheme) },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text("批量编辑") },
                                    leadingIcon = { ChecklistGlyph() },
                                    onClick = { onAction(BoardAction.StartBatchEdit) },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (!state.isSearching && !state.isBatchEditing && state.editor == null && !state.isRecycleBinOpen) {
                FloatingActionButton(
                    onClick = { onAction(BoardAction.OpenCreate) },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 72.dp, end = 4.dp).size(60.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) { Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 34.sp, fontWeight = FontWeight.Light) }
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
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                PAGES.forEachIndexed { index, page ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { onAction(BoardAction.SelectStatus(page.status)) },
                        text = {
                            Text(
                                page.shortTitle,
                                fontWeight = if (pager.currentPage == index) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !state.isBatchOperationRunning) { index ->
                TaskPage(PAGES[index], state.tasksFor(PAGES[index].status), lists[index], state, onAction)
            }
        }
        reminderTitle?.let {
            ReminderBanner(it, reminderCount, onReminderDoing, onReminderDone, onDismissReminder, Modifier.align(Alignment.TopCenter))
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
    PullToRefreshBox(
        isRefreshing = false,
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
            item(key = "${page.status}-header") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onAction(BoardAction.CloseTaskActions) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(page.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(tasks.size.toString(), color = page.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            if (tasks.isEmpty()) item(key = "${page.status}-empty") {
                Box(Modifier.fillParentMaxHeight(.8f).fillMaxWidth()) {
                    Text("暂无任务", Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(tasks, key=Task::id) { task -> TaskCard(task,page.accent,state,onAction) }
        }
    }
}

@Composable private fun TaskCard(task: Task, accent: Color, state: BoardUiState, onAction:(BoardAction)->Unit) {
    val flash=remember(task.id){Animatable(0f)}
    val generation=state.highlightRequest?.takeIf{it.taskId==task.id}?.generation
    LaunchedEffect(generation) { if(generation!=null){ repeat(3){flash.snapTo(1f);delay(500);flash.snapTo(0f);delay(500)};onAction(BoardAction.HighlightConsumed)} }
    Column {
        val isTransientCompleted = task.status != TaskStatus.DONE && task.id in state.transientCompletedTaskIds
        val shape=RoundedCornerShape(16.dp)
        Card(
            Modifier.fillMaxWidth().border(2.dp,Color(0xFFFFE082).copy(alpha=flash.value),shape)
                .semantics { contentDescription = "任务卡片：${task.title}" }
                .combinedClickable(
                    enabled = !isTransientCompleted,
                    onClick={ if(state.isBatchEditing) onAction(BoardAction.ToggleBatchSelection(task.id)) else onAction(BoardAction.OpenEdit(task)) },
                    onLongClick={ if(!state.isBatchEditing && task.id !in state.busyTaskIds) onAction(BoardAction.ToggleTaskActions(task.id)) },
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
                        Modifier.padding(top = 1.dp, end = 10.dp).clickable(
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
        if(!state.isBatchEditing && state.expandedTaskId==task.id){Spacer(Modifier.height(6.dp));TaskActionBar(task,task.id in state.busyTaskIds,onAction)}
    }
}

@Composable private fun CompletedMarker(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
    Canvas(modifier.size(24.dp)) {
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

@Composable private fun TaskActionBar(task:Task,busy:Boolean,onAction:(BoardAction)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        task.actions().forEach { action -> Surface(Modifier.weight(1f).height(44.dp).clickable(enabled=!busy){onAction(action.toAction(task))},RoundedCornerShape(10.dp),action.color){Box(Modifier.fillMaxSize(),Alignment.Center){Text(action.label,color=Color.White,fontWeight=FontWeight.SemiBold)}} }
    }
}

@Composable private fun BatchBar(state:BoardUiState,onAction:(BoardAction)->Unit){
    Surface(shadowElevation=8.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp),Arrangement.SpaceEvenly){
        BatchButton("全选",!state.isBatchOperationRunning){onAction(BoardAction.ToggleSelectAll)}
        BatchButton("删除",state.selectedTaskIds.isNotEmpty()&&!state.isBatchOperationRunning){onAction(BoardAction.RequestBatchDelete)}
        BatchButton("进行中",state.selectedTaskIds.isNotEmpty()&&!state.isBatchOperationRunning){onAction(BoardAction.BatchMove(TaskStatus.DOING))}
        BatchButton("已完成",state.selectedTaskIds.isNotEmpty()&&!state.isBatchOperationRunning){onAction(BoardAction.BatchMove(TaskStatus.DONE))}
    }}
}
@Composable private fun BatchButton(text:String,enabled:Boolean,onClick:()->Unit)=TextButton(onClick=onClick,enabled=enabled){Text(text)}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskEditorSheet(editor:TaskEditorState,onAction:(BoardAction)->Unit){
    val sheet=rememberModalBottomSheetState(true,confirmValueChange={if(it==SheetValue.Hidden){onAction(BoardAction.RequestCloseEditor);false}else true})
    val focus=remember{FocusRequester()}; val keyboard=LocalSoftwareKeyboardController.current; val focusManager=LocalFocusManager.current
    LaunchedEffect(editor.taskId){delay(120);focus.requestFocus();keyboard?.show()}
    ModalBottomSheet(
        onDismissRequest={onAction(BoardAction.RequestCloseEditor)},
        sheetState=sheet,
        sheetGesturesEnabled=false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ){
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(horizontal=20.dp,vertical=8.dp)){
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
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                        onAction(BoardAction.SaveEditor)
                    },
                    enabled = editor.content.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) { Text(if (editor.isEditing) "保存" else "添加") }
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
                        modifier = Modifier.fillMaxWidth().height(220.dp).focusRequester(focus),
                        label = { Text("任务内容") },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 7,
                        maxLines = 14,
                        keyboardOptions = KeyboardOptions(imeAction=ImeAction.Default),
                    )
                }
                item {
                    DateRangeSection(editor, focusManager::clearFocus, keyboard, onAction)
                }
                item {
                    SettingRow(
                        label = "提醒时间",
                        value = if(editor.hasReminder) "%02d:%02d".format(editor.reminderHour,editor.reminderMinute) else "未设置",
                        icon = SettingIcon.TIME,
                    ){focusManager.clearFocus();keyboard?.hide();onAction(BoardAction.OpenTimePicker)}
                }
                if(editor.hasReminder)item { TextButton(onClick={onAction(BoardAction.ClearReminderTime)}){Text("清除提醒")} }
                item {
                    SettingRow(
                        label = "重复类型",
                        value = if(editor.hasReminder) editor.reminderRepeat.label() else "请先设置提醒时间",
                        icon = SettingIcon.REPEAT,
                    ){focusManager.clearFocus();keyboard?.hide();onAction(BoardAction.OpenRepeatPicker)}
                }
                editor.validationMessage?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error)}}
            }
        }
    }
}

private enum class SettingIcon { DATE, TIME, REPEAT }

@Composable private fun DateRangeSection(
    editor: TaskEditorState,
    clearFocus: (Boolean) -> Unit,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
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
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onAction(BoardAction.ToggleDateSection) }
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
                    clearFocus(true); keyboard?.hide(); onAction(BoardAction.OpenStartDatePicker)
                }
                DateRow("截止日期", editor.dueDateMillis) {
                    clearFocus(true); keyboard?.hide(); onAction(BoardAction.OpenDueDatePicker)
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
    ModalBottomSheet(
        onDismissRequest = {onAction(BoardAction.CloseTimePicker)},
        sheetState=rememberModalBottomSheetState(true),
        sheetGesturesEnabled=false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ){
        Column(Modifier.fillMaxWidth().heightIn(min = 560.dp).padding(bottom=24.dp)){
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
@Composable private fun OperationDialog(value:OperationConfirmation,onAction:(BoardAction)->Unit){
    val title=when(value){is OperationConfirmation.BatchDelete->"删除所选任务？";is OperationConfirmation.Delete->"删除任务？";is OperationConfirmation.PermanentDelete->"永久删除任务？";is OperationConfirmation.PermanentDeleteMany->"永久删除所选任务？";is OperationConfirmation.Move->"确认移动任务？"}
    val text=when(value){is OperationConfirmation.BatchDelete->"将删除 ${value.taskIds.size} 条任务，删除后可在回收站查看。";is OperationConfirmation.Delete->"任务将移入回收站并保留 30 天。";is OperationConfirmation.PermanentDelete->"永久删除后无法恢复，并将在同步完成后清除本机副本。";is OperationConfirmation.PermanentDeleteMany->"将永久删除 ${value.taskIds.size} 条任务。此操作无法恢复。";is OperationConfirmation.Move->"任务将移至${value.destination.chineseLabel()}。"}
    val destructive=value is OperationConfirmation.PermanentDelete || value is OperationConfirmation.PermanentDeleteMany
    AlertDialog({onAction(BoardAction.CancelOperation)},title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick={onAction(BoardAction.ConfirmOperation)}){Text(if(destructive)"永久删除" else "确认",color=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}},dismissButton={TextButton(onClick={onAction(BoardAction.CancelOperation)}){Text("取消")}})
}

private data class BoardPage(val shortTitle:String,val title:String,val status:TaskStatus,val accent:Color)
private val PAGES=listOf(BoardPage("TODO","待办 TODO",TaskStatus.TODO,Color(0xFF5B6472)),BoardPage("DOING","进行中 DOING",TaskStatus.DOING,Color(0xFFB06C20)),BoardPage("DONE","已完成 DONE",TaskStatus.DONE,Color(0xFF2F6B55)))
private enum class MenuAction(val label:String,val color:Color){PIN("置顶",Color(0xFF2563EB)),UNPIN("取消置顶",Color(0xFF2563EB)),PREVIOUS("上一步",Color(0xFF2563EB)),NEXT("下一步",Color(0xFFD69E00)),DELETE("删除",Color(0xFFD32F2F))}
private fun Task.actions()=buildList{if(status==TaskStatus.TODO)add(if(isPinned)MenuAction.UNPIN else MenuAction.PIN)else if(isPinned)add(MenuAction.UNPIN);if(status.previous()!=null)add(MenuAction.PREVIOUS);if(status.next()!=null)add(MenuAction.NEXT);add(MenuAction.DELETE)}
private fun MenuAction.toAction(task:Task):BoardAction=when(this){MenuAction.PIN->BoardAction.SetPinned(task,true);MenuAction.UNPIN->BoardAction.SetPinned(task,false);MenuAction.PREVIOUS->BoardAction.MoveImmediately(task,checkNotNull(task.status.previous()));MenuAction.NEXT->BoardAction.RequestMove(task,checkNotNull(task.status.next()));MenuAction.DELETE->BoardAction.RequestDelete(task)}
private fun BoardUiState.tasksFor(status:TaskStatus)=when(status){TaskStatus.TODO->todo;TaskStatus.DOING->doing;TaskStatus.DONE->done}
private fun TaskStatus.pageIndex()=when(this){TaskStatus.TODO->0;TaskStatus.DOING->1;TaskStatus.DONE->2}
private fun TaskStatus.accent()=PAGES[pageIndex()].accent
private fun TaskStatus.previous()=when(this){TaskStatus.TODO->null;TaskStatus.DOING->TaskStatus.TODO;TaskStatus.DONE->TaskStatus.DOING}
private fun TaskStatus.next()=when(this){TaskStatus.TODO->TaskStatus.DOING;TaskStatus.DOING->TaskStatus.DONE;TaskStatus.DONE->null}
private fun TaskStatus.chineseLabel()=when(this){TaskStatus.TODO->"待办";TaskStatus.DOING->"进行中";TaskStatus.DONE->"已完成"}
private fun ReminderRepeat.label()=when(this){ReminderRepeat.NONE->"不重复";ReminderRepeat.DAILY->"每天重复";ReminderRepeat.WEEKLY->"每周重复";ReminderRepeat.WEEKDAYS->"每周工作日重复"}
private fun Long.formatReminder()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun Long.formatDate()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
private fun Long.formatDateTime()=Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
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
