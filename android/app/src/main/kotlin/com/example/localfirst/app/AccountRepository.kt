package com.example.localfirst.app

import android.content.Context
import com.example.localfirst.data.RemoteTask
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.sync.TaskStatus
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.localfirst.data.LOCAL_TASK_SPACE
import com.example.localfirst.data.accountTaskSpace
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class AccountSession(val token:String,val userId:String,val contact:String,val createdAtMillis:Long=0L)

class AccountSessionStore(context:Context){
    private val preferences=context.getSharedPreferences("doti-account",Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(load())
    private val mutableTaskSpace=MutableStateFlow(mutable.value.taskSpace())
    val session:StateFlow<AccountSession?> = mutable.asStateFlow()
    val taskSpace:StateFlow<String> = mutableTaskSpace.asStateFlow()
    fun current():AccountSession?=mutable.value
    fun save(value:AccountSession){preferences.edit().putString("token",value.token).putString("userId",value.userId).putString("contact",value.contact).putLong("createdAtMillis",value.createdAtMillis).apply();mutable.value=value;mutableTaskSpace.value=value.taskSpace()}
    fun clear(){preferences.edit().clear().apply();mutable.value=null;mutableTaskSpace.value=LOCAL_TASK_SPACE}
    private fun load():AccountSession?{
        val token=preferences.getString("token",null)?:return null
        return AccountSession(token,preferences.getString("userId",null)?:return null,preferences.getString("contact",null)?:return null,preferences.getLong("createdAtMillis",0L))
    }
}

private fun AccountSession?.taskSpace():String = this?.let { accountTaskSpace(it.userId) } ?: LOCAL_TASK_SPACE

interface AccountRepository{
    val session:StateFlow<AccountSession?>
    suspend fun requestCode(contact:String,purpose:String):String
    suspend fun register(contact:String,password:String,code:String):AccountSession
    suspend fun login(contact:String,password:String):AccountSession
    suspend fun resetPassword(contact:String,password:String,code:String)
    suspend fun snapshot(session:AccountSession):List<RemoteTask>
    suspend fun uploadShare():String
    suspend fun downloadShare(code:String):List<RemoteTask>
    fun logout()
}

class RetrofitAccountRepository(baseUrl:String,private val sessions:AccountSessionStore):AccountRepository{
    private val api=Retrofit.Builder().baseUrl(baseUrl.ensureSlash()).addConverterFactory(GsonConverterFactory.create()).build().create(AccountApi::class.java)
    override val session:StateFlow<AccountSession?> = sessions.session
    override suspend fun requestCode(contact:String,purpose:String)=call { api.code(CodeRequest(contact,purpose)).developmentCode }
    override suspend fun register(contact:String,password:String,code:String)=call{api.register(RegisterRequest(contact,password,code)).session().also(sessions::save)}
    override suspend fun login(contact:String,password:String)=call{api.login(LoginRequest(contact,password)).session().also(sessions::save)}
    override suspend fun resetPassword(contact:String,password:String,code:String)=call{api.reset(ResetRequest(contact,password,code))}
    override suspend fun snapshot(session:AccountSession)=call{api.tasks("Bearer ${session.token}").map(TaskResponse::remote)}
    override suspend fun uploadShare()=call{api.upload(auth()).shareCode}
    override suspend fun downloadShare(code:String)=call{api.download(auth(),code.trim().uppercase()).tasks.map(TaskResponse::remote)}
    override fun logout()=sessions.clear()
    private fun auth()="Bearer ${sessions.current()?.token ?: error("请先登录")}"
    private suspend fun <T> call(block:suspend()->T):T=try{block()}catch(error:HttpException){
        val message=runCatching{Gson().fromJson(error.response()?.errorBody()?.string(),ErrorResponse::class.java).message}.getOrNull()
        throw IllegalStateException(message?:"服务器请求失败（${error.code()}）")
    }
}

private interface AccountApi{
    @POST("api/v1/auth/codes") suspend fun code(@Body request:CodeRequest):CodeResponse
    @POST("api/v1/auth/register") suspend fun register(@Body request:RegisterRequest):SessionResponse
    @POST("api/v1/auth/login") suspend fun login(@Body request:LoginRequest):SessionResponse
    @POST("api/v1/auth/reset-password") suspend fun reset(@Body request:ResetRequest)
    @GET("api/v1/tasks") suspend fun tasks(@Header("Authorization") auth:String):List<TaskResponse>
    @POST("api/v1/shares") suspend fun upload(@Header("Authorization") auth:String):ShareCodeResponse
    @POST("api/v1/shares/{code}/download") suspend fun download(@Header("Authorization") auth:String,@Path("code") code:String):ShareDownloadResponse
}
private data class CodeRequest(val contact:String,val purpose:String)
private data class CodeResponse(val developmentCode:String,val expiresAtMillis:Long)
private data class RegisterRequest(val contact:String,val password:String,val code:String)
private data class LoginRequest(val contact:String,val password:String)
private data class ResetRequest(val contact:String,val newPassword:String,val code:String)
private data class SessionResponse(val token:String,val userId:String,val contact:String,val createdAtMillis:Long=0L){fun session()=AccountSession(token,userId,contact,createdAtMillis)}
private data class ShareCodeResponse(val shareCode:String)
private data class ShareDownloadResponse(val tasks:List<TaskResponse>)
private data class ErrorResponse(val message:String?)
private data class TaskResponse(
    val id:String,val title:String,val status:String,val version:Long,val reminderAtMillis:Long?=null,
    val reminderRepeat:String="NONE",val isPinned:Boolean=false,val startDateMillis:Long?=null,
    val dueDateMillis:Long?=null,val deletedAtMillis:Long?=null,
){fun remote()=RemoteTask(id,title,TaskStatus.valueOf(status),version,reminderAtMillis,runCatching{ReminderRepeat.valueOf(reminderRepeat)}.getOrDefault(ReminderRepeat.NONE),isPinned,startDateMillis,dueDateMillis,deletedAtMillis)}
private fun String.ensureSlash()=if(endsWith('/'))this else "$this/"
