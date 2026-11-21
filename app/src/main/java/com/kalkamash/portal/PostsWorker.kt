package com.kalkamash.portal

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class PostsWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val sp = applicationContext.getSharedPreferences("kp", Context.MODE_PRIVATE)
        if (!sp.getBoolean("student", false) || sp.getBoolean("fcm_ok", false)) return Result.success()
        return try {
            val url = URL("https://ukjrktjobxywlskizgqa.supabase.co/rest/v1/posts?select=id,kind,title,body,date,created_at&order=created_at.desc&limit=5")
            val cn = url.openConnection() as HttpURLConnection
            cn.setRequestProperty("apikey", "sb_publishable_McqEBzpnhnwSuB8acuKn0g_Rpa7FG6C")
            cn.connectTimeout = 15000
            cn.readTimeout = 15000
            val arr = JSONArray(cn.inputStream.bufferedReader().readText())
            if (arr.length() == 0) return Result.success()
            val newest = arr.getJSONObject(0).getString("created_at")
            val last = sp.getString("lastPost", null)
            if (last == null) {
                sp.edit().putString("lastPost", newest).apply()
                return Result.success()
            }
            val kinds = mapOf("exam" to "امتحان", "quiz" to "كويز", "report" to "تقرير", "info" to "تبليغ")
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                if (o.getString("created_at") > last) {
                    val d = o.optString("date", "")
                    val extra = if (d.isNotEmpty() && d != "null") " – " + d else ""
                    Notifier.show(applicationContext, "📢 " + (kinds[o.getString("kind")] ?: "تبليغ") + " جديد", o.getString("title") + extra)
                }
            }
            sp.edit().putString("lastPost", newest).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
