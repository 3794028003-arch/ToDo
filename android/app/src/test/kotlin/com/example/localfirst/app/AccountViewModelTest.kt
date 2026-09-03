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
class AccountViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setUp(){Dispatchers.setMain(dispatcher)}
    @After fun tearDown(){Dispatchers.resetMain()}

    @Test fun `rapid login taps send only one request`()=runTest(dispatcher){
        val repository=FakeAccountRepository()
        val viewModel=AccountViewModel(repository,{}, { _, _ -> })
        viewModel.openLogin();viewModel.contact("user@example.com");viewModel.password("password8")

        viewModel.submit();viewModel.submit();viewModel.submit();advanceUntilIdle()

        assertEquals(1,repository.loginCalls)
    }
}

private class FakeAccountRepository:AccountRepository{
    private val mutable=MutableStateFlow<AccountSession?>(null)
    override val session:StateFlow<AccountSession?> = mutable
    var loginCalls=0
    override suspend fun requestCode(contact:String,purpose:String)="1234"
    override suspend fun register(contact:String,password:String,code:String)=AccountSession("token","id",contact)
    override suspend fun login(contact:String,password:String):AccountSession{loginCalls++;return AccountSession("token","id",contact).also{mutable.value=it}}
    override suspend fun resetPassword(contact:String,password:String,code:String)=Unit
    override suspend fun snapshot(session:AccountSession)=emptyList<RemoteTask>()
    override suspend fun uploadShare()="ABCDEFGH"
    override suspend fun downloadShare(code:String)=emptyList<RemoteTask>()
    override fun logout(){mutable.value=null}
}
