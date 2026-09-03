package com.example.localfirst.app

import com.example.localfirst.data.RemoteTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `repeated navigation keeps one deterministic settings page`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeAppearancePreferences(), FakeSettingsAccountRepository())

        repeat(8) { viewModel.openSettings() }
        assertEquals(SettingsPage.SETTINGS, viewModel.state.value.page)
        repeat(8) { viewModel.openAccountDetails() }
        assertEquals(SettingsPage.ACCOUNT_DETAILS, viewModel.state.value.page)
        viewModel.back()
        assertEquals(SettingsPage.SETTINGS, viewModel.state.value.page)
        repeat(8) { viewModel.close() }
        assertEquals(SettingsPage.CLOSED, viewModel.state.value.page)
    }

    @Test fun `theme and five font stops are stored by preferences`() = runTest(dispatcher) {
        val preferences = FakeAppearancePreferences()
        val viewModel = SettingsViewModel(preferences, FakeSettingsAccountRepository())

        viewModel.setTheme(AppThemeMode.DARK)
        viewModel.setFontSize(AppFontSize.EXTRA_LARGE)
        advanceUntilIdle()

        assertEquals(AppThemeMode.DARK, preferences.appearance.value.theme)
        assertEquals(AppFontSize.EXTRA_LARGE, preferences.appearance.value.fontSize)
        assertEquals(5, AppFontSize.entries.size)
    }

    @Test fun `rapid switch account logs out once and requests login once`() = runTest(dispatcher) {
        val repository = FakeSettingsAccountRepository()
        val viewModel = SettingsViewModel(FakeAppearancePreferences(), repository)

        repeat(10) { viewModel.switchAccount() }
        advanceUntilIdle()

        assertEquals(1, repository.logoutCalls)
        assertEquals(SettingsEvent.OPEN_LOGIN, viewModel.events.value)
    }
}

private class FakeAppearancePreferences : AppearancePreferences {
    override val appearance = MutableStateFlow(AppAppearance())
    override fun setTheme(value: AppThemeMode) { appearance.value = appearance.value.copy(theme = value) }
    override fun setFontSize(value: AppFontSize) { appearance.value = appearance.value.copy(fontSize = value) }
}

private class FakeSettingsAccountRepository : AccountRepository {
    private val mutable = MutableStateFlow<AccountSession?>(AccountSession("token", "id", "user@example.com", 1_700_000_000_000L))
    override val session: StateFlow<AccountSession?> = mutable
    var logoutCalls = 0
    override suspend fun requestCode(contact: String, purpose: String) = "1234"
    override suspend fun register(contact: String, password: String, code: String) = error("unused")
    override suspend fun login(contact: String, password: String) = error("unused")
    override suspend fun resetPassword(contact: String, password: String, code: String) = Unit
    override suspend fun snapshot(session: AccountSession) = emptyList<RemoteTask>()
    override suspend fun uploadShare() = "ABCDEFGH"
    override suspend fun downloadShare(code: String) = emptyList<RemoteTask>()
    override fun logout() { logoutCalls++; mutable.value = null }
}
