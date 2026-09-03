package com.example.localfirst.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppThemeMode { LIGHT, SYSTEM, DARK }

enum class AppFontSize(val scale: Float, val title: String) {
    EXTRA_SMALL(.85f, "特小"),
    SMALL(.925f, "小"),
    STANDARD(1f, "标准"),
    LARGE(1.1f, "大"),
    EXTRA_LARGE(1.2f, "特大"),
}

data class AppAppearance(
    val theme: AppThemeMode = AppThemeMode.SYSTEM,
    val fontSize: AppFontSize = AppFontSize.STANDARD,
)

interface AppearancePreferences {
    val appearance: StateFlow<AppAppearance>
    fun setTheme(value: AppThemeMode)
    fun setFontSize(value: AppFontSize)
}

class SharedAppearancePreferences(context: Context) : AppearancePreferences {
    private val preferences = context.getSharedPreferences("doti-appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    override val appearance: StateFlow<AppAppearance> = mutable.asStateFlow()

    override fun setTheme(value: AppThemeMode) {
        if (mutable.value.theme == value) return
        preferences.edit().putString(KEY_THEME, value.name).apply()
        mutable.value = mutable.value.copy(theme = value)
    }

    override fun setFontSize(value: AppFontSize) {
        if (mutable.value.fontSize == value) return
        preferences.edit().putString(KEY_FONT_SIZE, value.name).apply()
        mutable.value = mutable.value.copy(fontSize = value)
    }

    private fun load() = AppAppearance(
        theme = preferences.getString(KEY_THEME, null).enumOrDefault(AppThemeMode.SYSTEM),
        fontSize = preferences.getString(KEY_FONT_SIZE, null).enumOrDefault(AppFontSize.STANDARD),
    )

    private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE = "font-size"
    }
}

enum class SettingsPage { CLOSED, SETTINGS, ACCOUNT_DETAILS }
enum class SettingsEvent { NONE, OPEN_LOGIN }

data class SettingsUiState(
    val page: SettingsPage = SettingsPage.CLOSED,
    val appearance: AppAppearance = AppAppearance(),
    val session: AccountSession? = null,
)

class SettingsViewModel(
    private val preferences: AppearancePreferences,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        SettingsUiState(appearance = preferences.appearance.value, session = accounts.session.value),
    )
    val state: StateFlow<SettingsUiState> = mutable.asStateFlow()
    private val mutableEvents = MutableStateFlow(SettingsEvent.NONE)
    val events: StateFlow<SettingsEvent> = mutableEvents.asStateFlow()
    private var switchingAccount = false

    init {
        viewModelScope.launch {
            preferences.appearance.collectLatest { value -> update { copy(appearance = value) } }
        }
        viewModelScope.launch {
            accounts.session.collectLatest { value -> update { copy(session = value) } }
        }
    }

    fun openSettings() = update { if (page == SettingsPage.CLOSED) copy(page = SettingsPage.SETTINGS) else this }
    fun openAccountDetails() = update {
        if (page == SettingsPage.SETTINGS && session != null) copy(page = SettingsPage.ACCOUNT_DETAILS) else this
    }
    fun back() = update {
        when (page) {
            SettingsPage.ACCOUNT_DETAILS -> copy(page = SettingsPage.SETTINGS)
            SettingsPage.SETTINGS -> copy(page = SettingsPage.CLOSED)
            SettingsPage.CLOSED -> this
        }
    }
    fun close() = update { if (page == SettingsPage.CLOSED) this else copy(page = SettingsPage.CLOSED) }
    fun setTheme(value: AppThemeMode) = preferences.setTheme(value)
    fun setFontSize(value: AppFontSize) = preferences.setFontSize(value)

    fun switchAccount() {
        if (switchingAccount) return
        switchingAccount = true
        accounts.logout()
        mutableEvents.value = SettingsEvent.OPEN_LOGIN
        close()
    }

    fun consumeEvent() {
        mutableEvents.value = SettingsEvent.NONE
        switchingAccount = false
    }

    private fun update(block: SettingsUiState.() -> SettingsUiState) {
        mutable.value = mutable.value.block()
    }
}
