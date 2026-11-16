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
import org.json.JSONArray

object Notifier {
    private const val CH = "lectures"

    fun show(c: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "إشعارات المحاضرات", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(c).notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    fun save(c: Context, json: String) {
        c.getSharedPreferences("kp", Context.MODE_PRIVATE).edit().putString("lec", json).apply()
        schedule(c)
    }

    fun schedule(c: Context) {
        val arr = JSONArray(c.getSharedPreferences("kp", Context.MODE_PRIVATE).getString("lec", "[]"))
        val am = c.getSystemService(AlarmManager::class.java)
        for (i in 0 until 60) {
            PendingIntent.getBroadcast(
                c, 1000 + i, Intent(c, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )?.let { am.cancel(it) }
        }
        val now = System.currentTimeMillis()
        var k = 0
        for (i in 0 until arr.length()) {
            if (k >= 60) break
            val o = arr.getJSONObject(i)
            val t = o.getLong("t")
            if (t <= now) continue
            val intent = Intent(c, AlarmReceiver::class.java)
                .putExtra("title", o.getString("n")).putExtra("body", o.getString("b"))
            val p = PendingIntent.getBroadcast(c, 1000 + k, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            else
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            k++
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notifier.show(c, i.getStringExtra("title") ?: "بدأت المحاضرة الآن", i.getStringExtra("body") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Notifier.schedule(c) }
}
