package com.example.localfirst.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.localfirst.board.BoardRoute
import com.example.localfirst.board.BoardViewModel
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus

class MainActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as LocalFirstApplication).graph
    private val boardViewModel: BoardViewModel by viewModels {
        BoardViewModelFactory(repository = graph.repository)
    }
    private val reminderViewModel: ReminderViewModel by viewModels {
        ReminderViewModelFactory(
            store = graph.reminderQueueStore,
            onMove = graph::handleReminderAction,
        )
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val boardState by boardViewModel.state.collectAsState()
            val reminderState by reminderViewModel.state.collectAsState()
            val reminderAlert = reminderState.current
            var showNotificationSettings by remember { mutableStateOf(false) }
            val exactAlarmLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                graph.rescheduleReminders()
            }
            val notificationSettingsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                if (notificationsEnabled()) {
                    requestExactAlarmAccess(exactAlarmLauncher::launch)
                } else {
                    showNotificationSettings = true
                }
            }
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted && notificationsEnabled()) {
                    requestExactAlarmAccess(exactAlarmLauncher::launch)
                } else {
                    showNotificationSettings = true
                }
            }

            MaterialTheme(
                colorScheme = if (boardState.isDarkMode) DARK_COLORS else LIGHT_COLORS,
            ) {
                BoardRoute(
                    viewModel = boardViewModel,
                    reminderTitle = reminderAlert?.title,
                    reminderCount = reminderState.pendingCount,
                    onReminderDoing = { reminderAlert?.let { reminderViewModel.move(it.taskId, TaskStatus.DOING) } },
                    onReminderDone = { reminderAlert?.let { reminderViewModel.move(it.taskId, TaskStatus.DONE) } },
                    onDismissReminder = { reminderAlert?.let { reminderViewModel.dismiss(it.taskId) } },
                    onReminderPermissionsRequired = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            if (notificationsEnabled()) {
                                requestExactAlarmAccess(exactAlarmLauncher::launch)
                            } else {
                                showNotificationSettings = true
                            }
                        }
                    },
                )
                if (showNotificationSettings) {
                    AlertDialog(
                        onDismissRequest = { showNotificationSettings = false },
                        title = { Text("开启任务通知") },
                        text = { Text("通知权限当前已关闭。不开启时，返回桌面或使用其他应用将无法收到任务提醒。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNotificationSettings = false
                                    notificationSettingsLauncher.launch(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        },
                                    )
                                },
                            ) { Text("前往设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationSettings = false }) { Text("暂不开启") }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.setForeground(true)
    }

    override fun onStop() {
        graph.setForeground(false)
        super.onStop()
    }

    private fun requestExactAlarmAccess(launch: (Intent) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                launch(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return
            }
        }
        graph.rescheduleReminders()
    }

    private fun notificationsEnabled(): Boolean =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()
}

private val LIGHT_COLORS = lightColorScheme(
    primary = Color(0xFF2F80ED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F1FF),
    onPrimaryContainer = Color(0xFF153A66),
    secondary = Color(0xFF667085),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF1D232D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D232D),
    surfaceDim = Color(0xFFE4E8EE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF3F5F8),
    surfaceContainerHigh = Color(0xFFEEF1F5),
    surfaceContainerHighest = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF626B78),
    outline = Color(0xFFB8C0CC),
    outlineVariant = Color(0xFFE3E7ED),
)

private val DARK_COLORS = darkColorScheme(
    primary = Color(0xFF9CBBFF),
    onPrimary = Color(0xFF082E73),
    primaryContainer = Color(0xFF1F4FAD),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFC5CAC7),
    onSecondary = Color(0xFF2C312F),
    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF0F0F10),
    onSurface = Color(0xFFE6E1E5),
    surfaceDim = Color(0xFF0F0F10),
    surfaceBright = Color(0xFF343437),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF171719),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF242427),
    surfaceContainerHighest = Color(0xFF2D2D30),
    surfaceVariant = Color(0xFF242426),
    onSurfaceVariant = Color(0xFFC9C5C8),
    outline = Color(0xFF777276),
    outlineVariant = Color(0xFF454246),
)

private class BoardViewModelFactory(
    private val repository: TaskRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BoardViewModel::class.java))
        return BoardViewModel(repository) as T
    }
}

private class ReminderViewModelFactory(
    private val store: ReminderQueueStore,
    private val onMove: (String, TaskStatus) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReminderViewModel::class.java))
        return ReminderViewModel(store, onMove) as T
    }
}
