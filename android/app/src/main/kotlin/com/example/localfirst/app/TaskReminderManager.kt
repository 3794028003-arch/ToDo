package com.example.localfirst.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.ReminderScheduleCalculator
import com.example.localfirst.data.TaskReminderScheduler
import com.example.localfirst.sync.TaskStatus

class AlarmTaskReminderScheduler(context: Context) : TaskReminderScheduler {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)
    init { ensureReminderChannel(app) }
    override fun schedule(taskId:String,title:String,reminderAtMillis:Long,reminderRepeat:ReminderRepeat) {
        cancel(taskId)
        val trigger=ReminderScheduleCalculator.nextFutureTrigger(reminderAtMillis,reminderRepeat,System.currentTimeMillis())?:return
        val operation=PendingIntent.getBroadcast(app,taskId.stableRequestCode(),TaskReminderReceiver.intent(app,taskId,title,trigger,reminderRepeat),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.S||alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation)
    }
    override fun cancel(taskId:String){
        val operation=PendingIntent.getBroadcast(app,taskId.stableRequestCode(),TaskReminderReceiver.intent(app,taskId,"",0L,ReminderRepeat.NONE),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?:return
        alarms.cancel(operation);operation.cancel()
    }
}

class TaskReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val taskId=intent.getStringExtra(EXTRA_TASK_ID)?:return
        val title=intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank{"待办事项提醒"}
        val trigger=intent.getLongExtra(EXTRA_TRIGGER,0L)
        val repeat=intent.getStringExtra(EXTRA_REPEAT)?.let{runCatching{ReminderRepeat.valueOf(it)}.getOrNull()}?:ReminderRepeat.NONE
        val graph=(context.applicationContext as?LocalFirstApplication)?.graph
        if(graph?.isForeground==true)graph.showReminder(taskId,title)
        val manager=context.getSystemService(NotificationManager::class.java);ensureReminderChannel(context)
        val open=PendingIntent.getActivity(context,taskId.stableRequestCode(),Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle("任务提醒").setContentText(title).setStyle(Notification.BigTextStyle().bigText(title)).setContentIntent(open)
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_HIGH).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null,"进行中",actionIntent(context,taskId,TaskStatus.DOING)).build())
            .addAction(Notification.Action.Builder(null,"已完成",actionIntent(context,taskId,TaskStatus.DONE)).build())
            .setAutoCancel(true).build()
        manager.notify(taskId.stableRequestCode(),notification)
        if(repeat!=ReminderRepeat.NONE&&trigger>0L)ReminderScheduleCalculator.nextTriggerAfter(trigger,repeat)?.let{AlarmTaskReminderScheduler(context).schedule(taskId,title,it,repeat)}
    }
    companion object{
        private const val EXTRA_TASK_ID="task_id";private const val EXTRA_TASK_TITLE="task_title";private const val EXTRA_TRIGGER="trigger_at";private const val EXTRA_REPEAT="repeat";const val CHANNEL_ID="task_reminders"
        fun intent(context:Context,taskId:String,title:String,trigger:Long,repeat:ReminderRepeat)=Intent(context,TaskReminderReceiver::class.java).apply{action="com.example.localfirst.TASK_REMINDER.$taskId";putExtra(EXTRA_TASK_ID,taskId);putExtra(EXTRA_TASK_TITLE,title);putExtra(EXTRA_TRIGGER,trigger);putExtra(EXTRA_REPEAT,repeat.name)}
    }
}

fun ensureReminderChannel(context:Context){
    val sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?:RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    val channel=NotificationChannel(TaskReminderReceiver.CHANNEL_ID,"任务提醒",NotificationManager.IMPORTANCE_HIGH).apply{description="在任务设置的时间发出提醒";setSound(sound,audio);enableVibration(true)}
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

class TaskReminderActionReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val taskId=intent.getStringExtra(EXTRA_TASK_ID)?:return
        val status=intent.getStringExtra(EXTRA_STATUS)?.let{runCatching{TaskStatus.valueOf(it)}.getOrNull()}?:return
        (context.applicationContext as?LocalFirstApplication)?.graph?.handleReminderAction(taskId,status)
        context.getSystemService(NotificationManager::class.java).cancel(taskId.stableRequestCode())
    }
    companion object{private const val EXTRA_TASK_ID="task_id";private const val EXTRA_STATUS="status"
        fun intent(context:Context,taskId:String,status:TaskStatus)=Intent(context,TaskReminderActionReceiver::class.java).apply{action="com.example.localfirst.REMINDER_ACTION.$taskId.${status.name}";putExtra(EXTRA_TASK_ID,taskId);putExtra(EXTRA_STATUS,status.name)}}
}
private fun actionIntent(context:Context,taskId:String,status:TaskStatus)=PendingIntent.getBroadcast(context,"$taskId:${status.name}".stableRequestCode(),TaskReminderActionReceiver.intent(context,taskId,status),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
class ReminderBootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==Intent.ACTION_BOOT_COMPLETED)(context.applicationContext as?LocalFirstApplication)?.graph?.rescheduleReminders()}}
private fun String.stableRequestCode():Int=hashCode() and Int.MAX_VALUE
