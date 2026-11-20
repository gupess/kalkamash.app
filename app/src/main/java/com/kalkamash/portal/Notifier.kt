package com.kalkamash.portal

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray

object Notifier {
    private const val CH = "lectures"
    private const val SLOTS = 150

    fun show(c: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "إشعارات المحاضرات", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(c))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(c).notify((title + "|" + body).hashCode(), n)
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun canExact(am: AlarmManager) = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun save(c: Context, json: String) {
        c.getSharedPreferences("kp", Context.MODE_PRIVATE).edit().putString("lec", json).putBoolean("student", true).apply()
        schedule(c)
    }

    fun schedule(c: Context) {
        val arr = JSONArray(c.getSharedPreferences("kp", Context.MODE_PRIVATE).getString("lec", "[]"))
        val am = c.getSystemService(AlarmManager::class.java)
        for (i in 0 until SLOTS) {
            PendingIntent.getBroadcast(
                c, 1000 + i, Intent(c, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )?.let { am.cancel(it) }
        }
        val now = System.currentTimeMillis()
        var k = 0
        for (i in 0 until arr.length()) {
            if (k >= SLOTS) break
            val o = arr.getJSONObject(i)
            val t = o.getLong("t")
            if (t <= now) continue
            val intent = Intent(c, AlarmReceiver::class.java)
                .putExtra("title", o.getString("n")).putExtra("body", o.getString("b")).putExtra("t", t)
            val p = PendingIntent.getBroadcast(c, 1000 + k, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            else am.setAlarmClock(AlarmManager.AlarmClockInfo(t, openApp(c)), p)
            k++
        }
    }

    // نبضة كل 10 دقائق لفحص التبليغات الجديدة حتى والتطبيق مغلق
    fun heartbeat(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = PendingIntent.getBroadcast(
            c, 999, Intent(c, HeartbeatReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val t = System.currentTimeMillis() + 10 * 60 * 1000
        if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val t = i.getLongExtra("t", 0L)
        if (t > 0L && System.currentTimeMillis() - t > 20 * 60 * 1000L) return
        Notifier.show(c, i.getStringExtra("title") ?: "بدأت المحاضرة الآن", i.getStringExtra("body") ?: "")
    }
}

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<PostsWorker>().build())
        Notifier.heartbeat(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notifier.schedule(c)
        Notifier.heartbeat(c)
    }
}
