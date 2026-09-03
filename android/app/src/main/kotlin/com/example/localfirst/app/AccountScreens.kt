package com.example.localfirst.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountScreen(state:AccountUiState,viewModel:AccountViewModel){
    if(state.page==AccountPage.CLOSED)return
    BackHandler(onBack=viewModel::closeAccount)
    if(state.page==AccountPage.PROFILE){
        Surface(Modifier.fillMaxSize(),color=Color(0xFF0F0F10)){
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)){
                TextButton(onClick=viewModel::closeAccount){Text("‹ 返回")};Spacer(Modifier.height(30.dp));DoTiMark()
                Text("账号与同步",Modifier.fillMaxWidth(),color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text(state.session?.contact.orEmpty(),Modifier.fillMaxWidth().padding(top=10.dp),color=Color(0xFF9A9AA0),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text(when(state.syncState){AccountSyncState.SYNCED->"● 已同步";AccountSyncState.SYNCING->"● 同步中";AccountSyncState.ERROR->"● 同步失败";else->"● 仅保存在本机"},Modifier.fillMaxWidth().padding(top=18.dp),color=when(state.syncState){AccountSyncState.SYNCED->Color(0xFF30D158);AccountSyncState.ERROR->Color(0xFFFF453A);else->Color(0xFF0A84FF)},textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.weight(1f));OutlinedButton(onClick=viewModel::logout,Modifier.fillMaxWidth().height(54.dp)){Text("退出登录")}
            }
        };return
    }
    var showPassword by remember{mutableStateOf(false)}
    val isLogin=state.page==AccountPage.LOGIN
    val isRegister=state.page==AccountPage.REGISTER
    Surface(Modifier.fillMaxSize(),color=Color(0xFF0F0F10)){
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=24.dp)){
            TextButton(onClick=viewModel::closeAccount,modifier=Modifier.heightIn(min=48.dp)){Text("‹ 返回")}
            Spacer(Modifier.height(8.dp));DoTiMark();Spacer(Modifier.height(12.dp))
            Text(when(state.page){AccountPage.LOGIN->"DoTi";AccountPage.REGISTER->"创建账号";else->"重置密码"},Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text(when(state.page){AccountPage.LOGIN->"让每一天清晰流转";AccountPage.REGISTER->"开启你的高效之旅";else->"服务器将生成四位验证码"},Modifier.fillMaxWidth().padding(top=6.dp,bottom=26.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=Color(0xFF9A9AA0))
            AccountField(state.contact,viewModel::contact,if(isLogin)"电子邮箱 / 手机号" else if(isRegister)"电子邮箱 / 手机号" else "绑定的电子邮箱 / 手机号",KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            if(!isLogin){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    AccountField(state.code,viewModel::code,"四位验证码",KeyboardType.Number,Modifier.weight(1f))
                    Button(onClick=viewModel::requestCode,enabled=!state.busy&&state.codeCooldownSeconds==0,modifier=Modifier.height(56.dp)){Text(if(state.codeCooldownSeconds>0)"${state.codeCooldownSeconds}s" else "获取验证码")}
                }
                state.developmentCode?.let{Text("测试验证码  $it",color=Color(0xFF30D158),modifier=Modifier.fillMaxWidth().padding(top=8.dp),fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.height(14.dp))
            }
            AccountField(state.password,viewModel::password,if(isRegister)"设置密码（至少8位）" else if(isLogin)"密码" else "输入新密码",KeyboardType.Password,visual=if(showPassword)VisualTransformation.None else PasswordVisualTransformation(),trailing={Text(if(showPassword)"隐藏" else "显示",Modifier.clickable{showPassword=!showPassword}.padding(8.dp),color=Color(0xFF0A84FF))})
            if(isRegister){Spacer(Modifier.height(14.dp));AccountField(state.confirmPassword,viewModel::confirm,"确认密码",KeyboardType.Password,visual=if(showPassword)VisualTransformation.None else PasswordVisualTransformation())}
            if(isLogin) Text("忘记密码？",Modifier.fillMaxWidth().clickable(onClick=viewModel::showReset).padding(vertical=16.dp),color=Color(0xFF0A84FF),textAlign=androidx.compose.ui.text.style.TextAlign.End)
            state.message?.let{Text(it,color=Color(0xFFFF9F0A),modifier=Modifier.padding(vertical=10.dp))}
            if(isRegister) Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(54.dp).background(Brush.horizontalGradient(listOf(Color(0xFF667085),Color(0xFF0A84FF),Color(0xFF30D158))),RoundedCornerShape(14.dp)).clickable(enabled=!state.busy,onClick=viewModel::submit),contentAlignment=Alignment.Center){Text(if(state.busy)"请稍候…" else when(state.page){AccountPage.LOGIN->"登 录";AccountPage.REGISTER->"注 册";else->"确 认 重 置"},color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp)}
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp),horizontalArrangement=Arrangement.Center){
                Text(if(isLogin)"还没有账号？ " else "已有账号？ ",color=Color(0xFF9A9AA0))
                Text(if(isLogin)"立即注册" else "返回登录",color=if(isLogin)Color(0xFF30D158) else Color(0xFF0A84FF),fontWeight=FontWeight.SemiBold,modifier=Modifier.clickable(onClick=if(isLogin)viewModel::showRegister else viewModel::showLogin))
            }
        }
    }
}

@Composable private fun AccountField(value:String,onValue:(String)->Unit,label:String,keyboardType:KeyboardType,modifier:Modifier=Modifier,visual:VisualTransformation=VisualTransformation.None,trailing:(@Composable ()->Unit)?=null){
    OutlinedTextField(value,onValue,modifier.fillMaxWidth().heightIn(min=56.dp),label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=keyboardType),visualTransformation=visual,trailingIcon=trailing,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Color(0xFF0A84FF),unfocusedBorderColor=Color(0xFF3A3A3C),focusedLabelColor=Color(0xFF0A84FF),unfocusedLabelColor=Color(0xFF8E8E93),cursorColor=Color(0xFF0A84FF)))
}

@Composable private fun DoTiMark(){
    Box(Modifier.fillMaxWidth().height(72.dp),contentAlignment=Alignment.Center){
        Icon(
            painter=painterResource(R.drawable.ic_launcher),
            contentDescription="DoTi 应用图标",
            modifier=Modifier.size(72.dp),
            tint=Color.Unspecified,
        )
    }
}

@Composable fun ShareDialogs(state:AccountUiState,viewModel:AccountViewModel){
    when(state.sharePage){
        SharePage.UPLOAD->AlertDialog(onDismissRequest=viewModel::closeShare,title={Text("上传分享")},text={Column{Text("将当前账号的 TODO、DOING、DONE 生成一次性任务副本。分享码7天内有效。");Spacer(Modifier.height(14.dp));Text(state.shareCode?:if(state.busy)"正在生成…" else "生成失败",fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}},confirmButton={TextButton(onClick=viewModel::closeShare){Text("完成")}})
        SharePage.DOWNLOAD->AlertDialog(onDismissRequest=viewModel::closeShare,title={Text("下载任务")},text={Column{Text("输入另一位用户提供的8位分享码");OutlinedTextField(state.downloadCode,viewModel::downloadCode,Modifier.fillMaxWidth().padding(top=12.dp),singleLine=true,label={Text("分享码")});state.message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=8.dp))}}},confirmButton={Button(onClick=viewModel::performDownload,enabled=state.downloadCode.length==8&&!state.busy){Text(if(state.busy)"导入中…" else "下载")}},dismissButton={TextButton(onClick=viewModel::closeShare){Text("取消")}})
        else->Unit
    }
}
