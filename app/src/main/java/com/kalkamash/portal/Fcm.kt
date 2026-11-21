package com.kalkamash.portal

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Fcm {
    private const val SB = "https://ukjrktjobxywlskizgqa.supabase.co"
    private const val KEY = "sb_publishable_McqEBzpnhnwSuB8acuKn0g_Rpa7FG6C"

    private fun sp(c: Context) = c.getSharedPreferences("kp", Context.MODE_PRIVATE)

    fun init(c: Context) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { register(c, it) }
        } catch (e: Throwable) {
            // Firebase غير مفعّل (لا يوجد google-services.json): يبقى النظام المحلي
        }
    }

    fun setShift(c: Context, shift: String) {
        sp(c).edit().putString("shift", shift).apply()
        init(c)
    }

    fun clear(c: Context) {
        val tk = sp(c).getString("fcm", null)
        sp(c).edit().remove("shift").putBoolean("fcm_ok", false).apply()
        if (tk != null) call("unregister_token", JSONObject().put("p_token", tk).toString(), null)
    }

    fun register(c: Context, token: String) {
        sp(c).edit().putString("fcm", token).apply()
        val shift = sp(c).getString("shift", null) ?: return
        call("register_token", JSONObject().put("p_token", token).put("p_shift", shift).toString()) { ok ->
            sp(c).edit().putBoolean("fcm_ok", ok).apply()
            if (ok) Notifier.cancelLocal(c)
        }
    }

    private fun call(fn: String, body: String, done: ((Boolean) -> Unit)?) {
        Thread {
            var ok = false
            try {
                val cn = URL("$SB/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
                cn.requestMethod = "POST"
                cn.doOutput = true
                cn.connectTimeout = 15000
                cn.readTimeout = 15000
                cn.setRequestProperty("apikey", KEY)
                cn.setRequestProperty("Content-Type", "application/json")
                cn.outputStream.use { it.write(body.toByteArray()) }
                ok = cn.responseCode in 200..299
            } catch (e: Exception) {
            }
            done?.invoke(ok)
        }.start()
    }
}

class MyFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(m: RemoteMessage) {
        val t = m.data["title"] ?: return
        Notifier.show(applicationContext, t, m.data["body"] ?: "")
    }

    override fun onNewToken(token: String) {
        Fcm.register(applicationContext, token)
    }
}
