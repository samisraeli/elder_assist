package il.kolkarov.assist

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class NoticeScheduler(private val context: Context) {
    companion object { private val lock = Mutex(); const val CHANNEL = "personal-reminders" }
    private val app get() = context.applicationContext as CompanionApp
    private val alarm get() = context.getSystemService(AlarmManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    private fun pending(key: String, title: String = ""): PendingIntent = PendingIntent.getBroadcast(context,0,
        Intent(context,ReminderReceiver::class.java).setData(Uri.parse("kolkarov://notice/${Uri.encode(key)}"))
            .putExtra("key",key).putExtra("title",title), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun refresh() = lock.withLock {
        withContext(Dispatchers.IO) {
            val dao = app.database.dao(); val profile = app.profiles.flow.first()
            val zone = ZoneId.of(profile.zone); val today = LocalDate.now(zone); val now = Instant.now().toEpochMilli()
            val upcoming = mutableListOf<ScheduledNotice>()
            dao.remindersNow().filter { !it.delivered && it.at > now }.forEach { upcoming += ScheduledNotice("reminder:${it.id}",it.title,it.at) }
            MedicationSchedule.agenda(dao.medicationsNow(),today,zone,3).filter { it.time.toInstant().toEpochMilli() > now }
                .groupBy { it.time.toInstant().toEpochMilli() }.forEach { (at,items) ->
                upcoming += ScheduledNotice("medication:$at","תזכורת לתרופות:\n"+items.joinToString("\n") { it.title },at)
            }
            val birthdays = birthdayAgenda(dao.peopleNow(),today,zone)
            val events = DeviceCalendar(context).events(profile,today)
            (birthdays + events).forEach { item ->
                listOf(-1L,0L).forEach { offset ->
                    val date = item.time.toLocalDate().plusDays(offset)
                    val configured = date.atTime(profile.noticeHour,profile.noticeMinute).atZone(zone).toInstant().toEpochMilli()
                    val at = if(offset == 0L && !item.allDay) minOf(configured,item.time.toInstant().toEpochMilli()) else configured
                    val label = if (offset == -1L) "מחר: " else "היום: "
                    if (at > now)
                        upcoming += ScheduledNotice("${item.key}:$offset",label + item.title,at)
                }
            }
            if (profile.morningNotice) (0L..6L).forEach { day ->
                val date = today.plusDays(day); val at = date.atTime(profile.noticeHour,profile.noticeMinute).atZone(zone).toInstant().toEpochMilli()
                if (at > now) upcoming += ScheduledNotice("morning:$date","הבוקר שלי מוכן — לחצו לפתיחה",at)
            }
            val wanted = upcoming.associateBy { it.key }
            val old = dao.notices().associateBy { it.key }
            old.values.filter { wanted[it.key] != it }.forEach { alarm.cancel(pending(it.key)); dao.deleteNotice(it.key) }
            upcoming.forEach { n ->
                val operation = pending(n.key,n.title)
                try {
                    if (exactAllowed()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,n.at,operation)
                    else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,n.at,operation)
                } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,n.at,operation) }
                dao.saveNotice(n)
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CompanionApp; val dao = app.database.dao()
                val key = intent.getStringExtra("key") ?: return@launch
                val notice = dao.notice(key) ?: return@launch
                // Ignore a cancelled alarm racing a database update, or an obsolete delivery.
                if (notice.at > System.currentTimeMillis() + 60000) return@launch
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(NoticeScheduler.CHANNEL,"תזכורות אישיות",NotificationManager.IMPORTANCE_HIGH))
                if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    val open = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    manager.notify(key,0,NotificationCompat.Builder(context,NoticeScheduler.CHANNEL)
                        .setSmallIcon(il.kolkarov.assist.R.drawable.ic_notification).setContentTitle("עזרה - העוזר האישי שלך")
                        .setContentText(notice.title).setStyle(NotificationCompat.BigTextStyle().bigText(notice.title))
                        .setContentIntent(open).setAutoCancel(true).build())
                    if (key.startsWith("reminder:")) dao.delivered(key.removePrefix("reminder:"))
                }
                dao.deleteNotice(key)
            } finally { result.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        WorkManager.getInstance(context).enqueueUniqueWork("reschedule",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<RefreshWorker>().build())
    }
}
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try { NoticeScheduler(applicationContext).refresh(); Result.success() } catch (_: Exception) { Result.retry() }
}
