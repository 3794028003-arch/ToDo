package com.example.localfirst.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.board.BoardAction
import com.example.localfirst.board.BoardScreen
import com.example.localfirst.board.BoardUiState
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardScreenInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressTask_showsOnlyThatTasksStatusActions() {
        val first = Task("task-1", "第一项任务", TaskStatus.TODO)
        val second = Task("task-2", "第二项任务", TaskStatus.TODO)
        var expandedTaskId: String? = null

        composeRule.setContent {
            var state by remember {
                mutableStateOf(BoardUiState(todo = listOf(first, second)))
            }
            MaterialTheme {
                BoardScreen(
                    state = state,
                    onAction = { action ->
                        if (action is BoardAction.ToggleTaskActions) {
                            expandedTaskId = action.taskId.takeUnless {
                                    it == state.expandedTaskId
                                }
                            state = state.copy(expandedTaskId = expandedTaskId)
                        } else if (action == BoardAction.CloseTaskActions) {
                            expandedTaskId = null
                            state = state.copy(expandedTaskId = null)
                        }
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("任务卡片：第一项任务")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("置顶").assertIsDisplayed()
        composeRule.onNodeWithText("下一步").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("任务卡片：第二项任务")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("置顶").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            click(Offset(center.x, height * 0.75f))
        }
        assertNull(expandedTaskId)
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
}
