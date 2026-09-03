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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.localfirst.board.BoardRoute
import com.example.localfirst.board.BoardRefresher
import com.example.localfirst.board.BoardViewModel
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus

class MainActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as LocalFirstApplication).graph
    private val boardViewModel: BoardViewModel by viewModels {
        BoardViewModelFactory(
            repository = graph.repository,
            refresher = BoardRefresher(graph::synchronizeAccount),
        )
    }
    private val reminderViewModel: ReminderViewModel by viewModels {
        ReminderViewModelFactory(
            store = graph.reminderQueueStore,
            onMove = graph::handleReminderAction,
        )
    }
    private val accountViewModel: AccountViewModel by viewModels {
        AccountViewModelFactory(graph.accountRepository, graph::synchronizeAccount, graph::mergeDownloadedTasks)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(graph.appearancePreferences, graph.accountRepository)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val reminderState by reminderViewModel.state.collectAsState()
            val accountState by accountViewModel.state.collectAsState()
            val settingsState by settingsViewModel.state.collectAsState()
            val settingsEvent by settingsViewModel.events.collectAsState()
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

            val useDarkMode = when (settingsState.appearance.theme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.DARK -> true
            }
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                density = systemDensity.density,
                fontScale = systemDensity.fontScale * settingsState.appearance.fontSize.scale,
            )
            LaunchedEffect(settingsEvent) {
                if (settingsEvent == SettingsEvent.OPEN_LOGIN) {
                    accountViewModel.openLogin()
                    settingsViewModel.consumeEvent()
                }
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MaterialTheme(colorScheme = if (useDarkMode) DARK_COLORS else LIGHT_COLORS) {
                    Box(Modifier.fillMaxSize()) {
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
                                this@MainActivity,
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
                    accountContact = accountState.session?.contact,
                    accountSyncState = accountState.syncState.name,
                    appVersion = BuildConfig.VERSION_NAME,
                    onAccountClick = accountViewModel::openAccount,
                    onUpload = accountViewModel::openUpload,
                    onDownload = accountViewModel::openDownload,
                    onSettings = settingsViewModel::openSettings,
                    darkModeOverride = useDarkMode,
                )
                SettingsNavigationHost(settingsState, settingsViewModel)
                ShareDialogs(accountState, accountViewModel)
                AccountScreen(accountState, accountViewModel)
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
        }
    }

    override fun onStart() {
        super.onStart()
        graph.setForeground(true)
        graph.scheduleSync()
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
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF123A63),
    onPrimaryContainer = Color(0xFFDCEEFF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF151517),
    onSurface = Color.White,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF101012),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFF242426),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF2C2C2E),
)

private class BoardViewModelFactory(
    private val repository: TaskRepository,
    private val refresher: BoardRefresher,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BoardViewModel::class.java))
        return BoardViewModel(repository, refresher) as T
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

private class AccountViewModelFactory(
    private val repository: AccountRepository,
    private val onAuthenticated: suspend () -> Unit,
    private val onDownloaded: suspend (AccountSession, List<com.example.localfirst.data.RemoteTask>) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AccountViewModel::class.java))
        return AccountViewModel(repository, onAuthenticated, onDownloaded) as T
    }
}

private class SettingsViewModelFactory(
    private val preferences: AppearancePreferences,
    private val repository: AccountRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(preferences, repository) as T
    }
}
