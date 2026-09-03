package com.example.localfirst.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.board.BoardAction
import com.example.localfirst.board.BoardScreen
import com.example.localfirst.board.BoardUiState
import com.example.localfirst.board.TaskEditorState
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardScreenInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressTask_entersBatchEditingWithoutLegacyActionBar() {
        val first = Task("task-1", "第一项任务", TaskStatus.TODO)
        val second = Task("task-2", "第二项任务", TaskStatus.TODO)
        composeRule.setContent {
            var state by remember {
                mutableStateOf(BoardUiState(todo = listOf(first, second)))
            }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        if (action is BoardAction.StartBatchEdit) {
                            state = state.copy(
                                isBatchEditing = true,
                                selectedTaskIds = setOf(action.taskId),
                            )
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：第一项任务")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("已选择 1 项").assertIsDisplayed()
        composeRule.onNodeWithText("全选").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onNodeWithText("置顶").assertDoesNotExist()
        composeRule.onNodeWithText("下一步").assertDoesNotExist()
    }

    @Test
    fun firstLongPressCanContinueDirectlyIntoReorderDrag() {
        val first = Task("task-1", "第一项任务", TaskStatus.TODO)
        val second = Task("task-2", "第二项任务", TaskStatus.TODO)
        var reorderedIds: List<String>? = null
        composeRule.setContent {
            var state by remember { mutableStateOf(BoardUiState(todo = listOf(first, second))) }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is BoardAction.StartBatchEdit -> state = state.copy(
                                isBatchEditing = true,
                                selectedTaskIds = setOf(action.taskId),
                            )
                            is BoardAction.ReorderTasks -> reorderedIds = action.orderedTaskIds
                            else -> Unit
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：第一项任务").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, height * 1.2f), delayMillis = 160)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已选择 1 项").assertIsDisplayed()
        assertEquals(listOf("task-2", "task-1"), reorderedIds)
    }

    @Test
    fun taskCardsCanBeReorderedTwiceWithoutStaleGestureState() {
        val first = Task("task-1", "第一项任务", TaskStatus.TODO)
        val second = Task("task-2", "第二项任务", TaskStatus.TODO)
        val reorderHistory = mutableListOf<List<String>>()
        composeRule.setContent {
            var state by remember { mutableStateOf(BoardUiState(todo = listOf(first, second))) }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is BoardAction.StartBatchEdit -> state = state.copy(
                                isBatchEditing = true,
                                selectedTaskIds = setOf(action.taskId),
                            )
                            is BoardAction.ReorderTasks -> {
                                reorderHistory += action.orderedTaskIds
                                val tasksById = state.todo.associateBy(Task::id)
                                state = state.copy(
                                    todo = action.orderedTaskIds.mapNotNull(tasksById::get),
                                )
                            }
                            else -> Unit
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：第一项任务").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, height * 1.2f), delayMillis = 160)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("任务卡片：第一项任务").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, -height * 1.2f), delayMillis = 160)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("task-2", "task-1"), reorderHistory.first())
        assertEquals(listOf("task-1", "task-2"), reorderHistory.last())
        assertEquals(2, reorderHistory.size)
    }

    @Test
    fun tappingTaskStatusUsesTheOriginalCheckedCompletionMarker() {
        val task = Task("task-1", "恢复对勾任务", TaskStatus.TODO)
        var completionRequested = false
        composeRule.setContent {
            var state by remember { mutableStateOf(BoardUiState(todo = listOf(task))) }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        if (action is BoardAction.QuickComplete) {
                            completionRequested = true
                            state = state.copy(transientCompletedTaskIds = setOf(task.id))
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("完成任务：恢复对勾任务").performClick()
        composeRule.waitForIdle()

        assertTrue(completionRequested)
        composeRule.onNodeWithContentDescription("已勾选完成").assertIsDisplayed()
    }

    @Test
    fun taskCardHorizontalSwipeStillOpensDrawer() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(
                        todo = listOf(Task("task-1", "侧栏回归任务", TaskStatus.TODO)),
                        isDarkMode = true,
                    ),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：侧栏回归任务").performTouchInput {
            swipe(
                start = Offset(width * .25f, center.y),
                end = Offset(width * .85f, center.y),
                durationMillis = 100,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("上传到云端").assertIsDisplayed()
    }

    @Test
    fun visibleMenuButtonOpensDrawerFromDoingPage() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(selectedStatus = TaskStatus.DOING),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("打开菜单").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("上传到云端").assertIsDisplayed()
        composeRule.onNodeWithText("从云端同步").assertIsDisplayed()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val closeBounds = composeRule.onNodeWithContentDescription("点击关闭菜单")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(closeBounds.top <= rootBounds.top + 1f)
        assertTrue(closeBounds.bottom >= rootBounds.bottom - 1f)

        composeRule.onRoot().performTouchInput {
            click(Offset(width * .9f, height * .5f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsNotDisplayed()
    }

    @Test
    fun drawerSlowDragSettlesOpenAndMenuDragSettlesClosed() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(
                        todo = listOf(Task("task-1", "真机抽屉吸附任务", TaskStatus.TODO)),
                    ),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：真机抽屉吸附任务").performTouchInput {
            swipe(
                start = Offset(width * .2f, center.y),
                end = Offset(width * .55f, center.y),
                durationMillis = 1_200,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsDisplayed()

        composeRule.onNodeWithText("上传到云端").performTouchInput {
            swipe(
                start = center,
                end = Offset(0f, center.y),
                durationMillis = 1_200,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsNotDisplayed()
    }

    @Test
    fun searchResults_showStatusBadgesAndClearAction() {
        val results = listOf(
            Task("todo", "整理今日计划", TaskStatus.TODO),
            Task("doing", "整理会议记录", TaskStatus.DOING),
            Task("done", "整理旧照片", TaskStatus.DONE),
        )
        var latestQuery = "整理"

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    BoardUiState(
                        isSearching = true,
                        searchQuery = latestQuery,
                        searchResults = results,
                    ),
                )
            }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        if (action is BoardAction.UpdateSearch) {
                            latestQuery = action.query
                            state = state.copy(searchQuery = action.query)
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithText("整理今日计划").assertIsDisplayed()
        composeRule.onNodeWithText("TODO").assertIsDisplayed()
        composeRule.onNodeWithText("DOING").assertIsDisplayed()
        composeRule.onNodeWithText("DONE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("清除搜索").performTouchInput { click() }

        assertEquals("", latestQuery)
    }

    @Test
    fun taskEditor_verticalDragInEitherDirectionClearsFocus() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(editor = TaskEditorState(content = "测试待办")),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        val contentField = composeRule.onNodeWithContentDescription("任务内容输入框")
        contentField.performClick()
        contentField.assertIsFocused()

        contentField.performTouchInput {
            down(center)
            moveBy(Offset(0f, 1f), delayMillis = 16)
            up()
        }

        contentField.assertIsNotFocused()

        contentField.performClick()
        contentField.assertIsFocused()
        contentField.performTouchInput {
            down(center)
            moveBy(Offset(0f, -1f), delayMillis = 16)
            up()
        }

        contentField.assertIsNotFocused()
    }

    @Test
    fun taskEditor_fullPageDownwardDragRequestsClose() {
        var closeRequested = false
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(editor = TaskEditorState(content = "测试待办")),
                    onAction = { if (it == BoardAction.RequestCloseEditor) closeRequested = true },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("下拉关闭任务编辑").performTouchInput {
            swipe(
                start = Offset(center.x, height * .25f),
                end = Offset(center.x, height * .8f),
                durationMillis = 420,
            )
        }
        composeRule.waitForIdle()

        assertTrue(closeRequested)
    }

    @Test
    fun taskEditor_formScrollsDownAndBackUpAfterFocusIsCleared() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(
                        editor = TaskEditorState(
                            content = "测试待办",
                            reminderHour = 9,
                            reminderMinute = 30,
                            isDateSectionExpanded = true,
                        ),
                    ),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithText("添加待办").performTouchInput { click() }
        composeRule.onNodeWithText("重复类型").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("任务内容输入框").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun timePicker_verticalSwipeOutsideWheelsRequestsClose() {
        var closeRequested = false
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(
                        editor = TaskEditorState(content = "测试待办"),
                        showTimePicker = true,
                    ),
                    onAction = { if (it == BoardAction.CloseTimePicker) closeRequested = true },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("上下滑动关闭提醒时间").performTouchInput {
            swipe(
                start = Offset(center.x, height * .08f),
                end = Offset(center.x, height * .38f),
                durationMillis = 80,
            )
        }
        composeRule.waitForIdle()

        assertTrue(closeRequested)
    }

    @Test
    fun statusTabBar_longPressDragSwitchesPageBeforeRelease() {
        var selectedStatus = TaskStatus.TODO
        val selectedHistory = mutableListOf<TaskStatus>()
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(selectedStatus = selectedStatus),
                    onAction = {
                        if (it is BoardAction.SelectStatus) {
                            selectedStatus = it.status
                            selectedHistory += it.status
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务状态切换栏").performTouchInput {
            down(Offset(width * .15f, center.y))
            advanceEventTime(600)
            moveTo(Offset(width * .82f, center.y), delayMillis = 160)
            moveTo(Offset(width * .48f, center.y), delayMillis = 160)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(TaskStatus.DONE in selectedHistory)
        assertEquals(TaskStatus.DOING, selectedStatus)
    }

    @Test
    fun statusContent_horizontalSwipeSwitchesAndTabClickStillWorks() {
        var selectedStatus = TaskStatus.TODO
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(selectedStatus = TaskStatus.TODO),
                    onAction = {
                        if (it is BoardAction.SelectStatus) selectedStatus = it.status
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务状态内容区").performTouchInput {
            swipe(
                start = Offset(width * .8f, center.y),
                end = Offset(width * .2f, center.y),
                durationMillis = 180,
            )
        }
        composeRule.waitForIdle()
        assertEquals(TaskStatus.DOING, selectedStatus)

        composeRule.onNodeWithText("DONE").performClick()
        composeRule.waitForIdle()
        assertEquals(TaskStatus.DONE, selectedStatus)
    }

    @Test
    fun emptyTodo_showsReferenceEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(isDarkMode = true),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    accountContact = "record@everyday.com",
                    accountSyncState = "SYNCED",
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务列表为空").assertIsDisplayed()
        composeRule.onNodeWithText("待办 TODO 列表为空").assertIsDisplayed()
        composeRule.onNodeWithText("点击右下角按钮添加新任务").assertIsDisplayed()
    }

    @Test
    fun todoDrawer_usesPositionForSlowDragVelocityForFlingAndClosesFromMenuArea() {
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(isDarkMode = true),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    accountContact = "record@everyday.com",
                    accountSyncState = "SYNCED",
                )
            }
        }

        val root = composeRule.onRoot()
        val bounds = root.fetchSemanticsNode().boundsInRoot
        fun point(x: Float, y: Float) = Offset(
            bounds.left + bounds.width * x,
            bounds.top + bounds.height * y,
        )

        root.performTouchInput {
            swipe(
                start = point(.55f, .55f),
                end = point(.68f, .55f),
                durationMillis = 1_800,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsNotDisplayed()

        root.performTouchInput {
            swipe(
                start = point(.55f, .55f),
                end = point(.68f, .55f),
                durationMillis = 80,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsDisplayed()
        composeRule.onNodeWithText("从云端同步").assertIsDisplayed()
        composeRule.onNodeWithText("record@everyday.com").assertIsDisplayed()
        composeRule.onNodeWithText("DoTi V2.0.0").assertIsDisplayed()

        root.performTouchInput {
            swipe(
                start = point(.20f, .55f),
                end = point(.05f, .55f),
                durationMillis = 80,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上传到云端").assertIsNotDisplayed()
    }

    @Test
    fun settingsClick_navigatesImmediatelyWithoutWaitingForDrawerAnimation() {
        var settingsOpened = false
        composeRule.setContent {
            MaterialTheme {
                BoardScreen(
                    state = BoardUiState(isDarkMode = true),
                    onAction = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    onSettings = { settingsOpened = true },
                )
            }
        }

        val root = composeRule.onRoot()
        val bounds = root.fetchSemanticsNode().boundsInRoot
        root.performTouchInput {
            swipe(
                start = Offset(bounds.center.x, bounds.center.y),
                end = Offset(bounds.right * .68f, bounds.center.y),
                durationMillis = 80,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("设置").performClick()
        assertTrue(settingsOpened)
        composeRule.mainClock.autoAdvance = true
    }
}
