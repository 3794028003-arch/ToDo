package com.example.localfirst.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

enum class AccountPage { CLOSED, LOGIN, REGISTER, RESET_PASSWORD, PROFILE }
enum class SharePage { CLOSED, UPLOAD, DOWNLOAD }
enum class AccountSyncState { LOCAL_ONLY, SYNCING, SYNCED, ERROR }
data class AccountUiState(
    val page:AccountPage=AccountPage.CLOSED,val sharePage:SharePage=SharePage.CLOSED,
    val session:AccountSession?=null,val contact:String="",val password:String="",val confirmPassword:String="",
    val code:String="",val developmentCode:String?=null,val busy:Boolean=false,val message:String?=null,
    val shareCode:String?=null,val downloadCode:String="",val syncState:AccountSyncState=AccountSyncState.LOCAL_ONLY,
    val codeCooldownSeconds:Int=0,
)

class AccountViewModel(
    private val repository:AccountRepository,
    private val onAuthenticated:suspend()->Unit,
    private val onDownloaded:suspend(AccountSession,List<com.example.localfirst.data.RemoteTask>)->Unit,
):ViewModel(){
    private var operationRunning=false
    private var cooldownJob:Job?=null
    private val mutable=MutableStateFlow(AccountUiState(session=repository.session.value,syncState=if(repository.session.value==null)AccountSyncState.LOCAL_ONLY else AccountSyncState.SYNCED))
    val state:StateFlow<AccountUiState> = mutable.asStateFlow()
    init{viewModelScope.launch{repository.session.collectLatest{session->mutable.value=mutable.value.copy(session=session,syncState=if(session==null)AccountSyncState.LOCAL_ONLY else mutable.value.syncState)}}}
    fun openAccount(){update{copy(page=if(session==null)AccountPage.LOGIN else AccountPage.PROFILE,message=null)}}
    fun openLogin(){update{copy(page=AccountPage.LOGIN,message=null)}}
    fun closeAccount(){update{copy(page=AccountPage.CLOSED,password="",confirmPassword="",code="",message=null,developmentCode=null)}}
    fun showLogin(){update{copy(page=AccountPage.LOGIN,message=null)}}
    fun showRegister(){update{copy(page=AccountPage.REGISTER,message=null,code="",developmentCode=null)}}
    fun showReset(){update{copy(page=AccountPage.RESET_PASSWORD,message=null,code="",developmentCode=null)}}
    fun contact(value:String){update{copy(contact=value,message=null)}}
    fun password(value:String){update{copy(password=value,message=null)}}
    fun confirm(value:String){update{copy(confirmPassword=value,message=null)}}
    fun code(value:String){update{copy(code=value.filter(Char::isDigit).take(4),message=null)}}
    fun downloadCode(value:String){update{copy(downloadCode=value.uppercase().take(8),message=null)}}
    fun requestCode(){if(state.value.codeCooldownSeconds>0)return;runBusy{val purpose=if(state.value.page==AccountPage.RESET_PASSWORD)"RESET_PASSWORD" else "REGISTER";val generated=repository.requestCode(state.value.contact,purpose);update{copy(developmentCode=generated,message="验证码已由服务器生成")};startCooldown()}}
    fun submit(){when(state.value.page){AccountPage.LOGIN->login();AccountPage.REGISTER->register();AccountPage.RESET_PASSWORD->reset();else->Unit}}
    private fun login()=runBusy{repository.login(state.value.contact,state.value.password);afterAuth()}
    private fun register()=runBusy{if(state.value.password!=state.value.confirmPassword)error("两次输入的密码不一致");repository.register(state.value.contact,state.value.password,state.value.code);afterAuth()}
    private fun reset()=runBusy{repository.resetPassword(state.value.contact,state.value.password,state.value.code);update{copy(page=AccountPage.LOGIN,password="",code="",developmentCode=null,message="密码已重置，请重新登录")}}
    private suspend fun afterAuth(){update{copy(page=AccountPage.CLOSED,syncState=AccountSyncState.SYNCING,password="",confirmPassword="",code="",message=null)};runCatching{onAuthenticated()}.onSuccess{update{copy(syncState=AccountSyncState.SYNCED)}}.onFailure{update{copy(syncState=AccountSyncState.ERROR,message=it.message)}}}
    fun logout(){repository.logout();update{copy(syncState=AccountSyncState.LOCAL_ONLY,sharePage=SharePage.CLOSED,page=AccountPage.CLOSED)}}
    fun openUpload(){if(state.value.session==null){openLogin();return};update{copy(sharePage=SharePage.UPLOAD,shareCode=null,message=null)};runBusy{onAuthenticated();val code=repository.uploadShare();update{copy(shareCode=code)}}}
    fun openDownload(){if(state.value.session==null){openLogin();return};update{copy(sharePage=SharePage.DOWNLOAD,downloadCode="",message=null)}}
    fun performDownload(){runBusy{val owner=state.value.session?:error("请先登录");val tasks=repository.downloadShare(state.value.downloadCode);onDownloaded(owner,tasks);update{copy(sharePage=SharePage.CLOSED,message="已导入 ${tasks.size} 项任务")}}}
    fun closeShare(){update{copy(sharePage=SharePage.CLOSED,shareCode=null,downloadCode="",message=null)}}
    private fun startCooldown(){cooldownJob?.cancel();cooldownJob=viewModelScope.launch{for(second in 60 downTo 1){update{copy(codeCooldownSeconds=second)};delay(1_000)};update{copy(codeCooldownSeconds=0)}}}
    private fun runBusy(block:suspend()->Unit){
        if(operationRunning)return
        operationRunning=true
        update{copy(busy=true,message=null)}
        viewModelScope.launch{
            try{runCatching{block()}.onFailure{error->update{copy(message=error.message?:"操作失败")}}}
            finally{operationRunning=false;update{copy(busy=false)}}
        }
    }
    private fun update(block:AccountUiState.()->AccountUiState){mutable.value=mutable.value.block()}
}
