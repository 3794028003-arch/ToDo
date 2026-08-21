package com.example.localfirst.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus

@Composable
fun BoardRoute(
    viewModel: BoardViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val serverDeletionNotice = state.serverDeletionNotice
    LaunchedEffect(serverDeletionNotice?.taskId) {
        serverDeletionNotice?.let { notice ->
            snackbarHostState.showSnackbar(
                message = "任务“${notice.title}”已在其他设备删除",
            )
            viewModel.onAction(BoardAction.DismissServerDeletionNotice(notice.taskId))
        }
    }
    BoardScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    onAction: (BoardAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var newTaskTitle by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("极简任务看板", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "本地优先 · 自动后台同步",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("新任务") },
                    placeholder = { Text("例如：整理今日计划") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = newTaskTitle.isNotBlank(),
                    onClick = {
                        val title = newTaskTitle.trim()
                        if (title.isNotEmpty()) {
                            onAction(BoardAction.CreateTask(title))
                            newTaskTitle = ""
                        }
                    },
                ) {
                    Text("添加")
                }
            }

            Text(
                text = "左右滑动查看三种状态",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TaskColumn(
                        title = "待办 TODO",
                        tasks = state.todo,
                        accent = Color(0xFF5B6472),
                        onAction = onAction,
                        onEdit = { editingTask = it },
                    )
                }
                item {
                    TaskColumn(
                        title = "进行中 DOING",
                        tasks = state.doing,
                        accent = Color(0xFFB06C20),
                        onAction = onAction,
                        onEdit = { editingTask = it },
                    )
                }
                item {
                    TaskColumn(
                        title = "已完成 DONE",
                        tasks = state.done,
                        accent = Color(0xFF2F6B55),
                        onAction = onAction,
                        onEdit = { editingTask = it },
                    )
                }
            }
        }
    }

    editingTask?.let { task ->
        RenameTaskDialog(
            task = task,
            onDismiss = { editingTask = null },
            onConfirm = { title ->
                onAction(BoardAction.RenameTask(task.id, title))
                editingTask = null
            },
        )
    }
}

@Composable
private fun TaskColumn(
    title: String,
    tasks: List<Task>,
    accent: Color,
    onAction: (BoardAction) -> Unit,
    onEdit: (Task) -> Unit,
) {
    Card(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = tasks.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Spacer(Modifier.height(10.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "暂无任务",
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = Task::id) { task ->
                        TaskCard(
                            task = task,
                            onAction = onAction,
                            onEdit = { onEdit(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onAction: (BoardAction) -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                task.status.previous()?.let { previous ->
                    OutlinedButton(
                        onClick = { onAction(BoardAction.MoveTask(task.id, previous)) },
                    ) {
                        Text("上一步")
                    }
                } ?: Spacer(Modifier.width(1.dp))

                task.status.next()?.let { next ->
                    Button(
                        onClick = { onAction(BoardAction.MoveTask(task.id, next)) },
                    ) {
                        Text("下一步")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text("修改")
                }
                TextButton(
                    onClick = { onAction(BoardAction.DeleteTask(task.id)) },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RenameTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改任务") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun TaskStatus.previous(): TaskStatus? = when (this) {
    TaskStatus.TODO -> null
    TaskStatus.DOING -> TaskStatus.TODO
    TaskStatus.DONE -> TaskStatus.DOING
}

private fun TaskStatus.next(): TaskStatus? = when (this) {
    TaskStatus.TODO -> TaskStatus.DOING
    TaskStatus.DOING -> TaskStatus.DONE
    TaskStatus.DONE -> null
}
