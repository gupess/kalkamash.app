package com.kalkamash.portal

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.provider.MediaStore
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private val home = "https://gupess.github.io/Site/"

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCb = null
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        web.evaluateJavascript("window.__permChanged&&window.__permChanged()", null)
    }

    private val poly = """
(function(){
  if(window.__kp||!window.AndroidApp)return; window.__kp=1; var A=window.AndroidApp;
  function N(t,o){A.notify(String(t),(o&&o.body)||'')}
  Object.defineProperty(N,'permission',{get:function(){return A.perm()}});
  N.requestPermission=function(cb){return new Promise(function(res){
    window.__permChanged=function(){window.__permChanged=null;var p=A.perm();res(p);if(cb)cb(p)};A.ask()})};
  window.Notification=N;
  if(window.ServiceWorkerRegistration){ServiceWorkerRegistration.prototype.showNotification=function(t,o){N(t,o);return Promise.resolve()}}
})();
"""

    inner class Bridge {
        @JavascriptInterface
        fun perm(): String =
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) "granted" else "default"

        @JavascriptInterface
        fun ask() {
            runOnUiThread {
                if (perm() == "granted") web.evaluateJavascript("window.__permChanged&&window.__permChanged()", null)
                else notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        @JavascriptInterface
        fun notify(title: String, body: String) { Notifier.show(applicationContext, title, body) }

        @JavascriptInterface
        fun setLectures(json: String) { Notifier.save(applicationContext, json) }

        @JavascriptInterface
        fun battery() {
            runOnUiThread {
                try {
                    val pm = getSystemService(PowerManager::class.java)
                    val uri = Uri.parse("package:" + packageName)
                    if (!pm.isIgnoringBatteryOptimizations(packageName))
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
                    else
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } catch (e: Exception) { toast("افتح إعدادات التطبيق ثم البطارية واختر بدون قيود") }
            }
        }

        @JavascriptInterface
        fun download(url: String, name: String) {
            runOnUiThread {
                try {
                    val req = DownloadManager.Request(Uri.parse(url))
                        .setTitle(name)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    toast("بدأ التنزيل: " + name)
                } catch (e: Exception) { toast("تعذّر التنزيل") }
            }
        }

        @JavascriptInterface
        fun save(b64: String, name: String, mime: String) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                toast("تم الحفظ في مجلد التنزيلات: " + name)
            } catch (e: Exception) { toast("تعذّر حفظ الملف") }
        }
    }

    private fun toast(s: String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            userAgentString = userAgentString + " KalkamashApp/1.7"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.addJavascriptInterface(Bridge(), "AndroidApp")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, poly, setOf("https://gupess.github.io"))
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.evaluateJavascript(poly, null)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(poly, null)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val h = u.host ?: ""
                if (u.scheme == "file" || h.endsWith("github.io") || h.endsWith("supabase.co") ||
                    h.endsWith("jsdelivr.net") || h.endsWith("cloudflare.com")) return false
                startActivity(Intent(Intent.ACTION_VIEW, u))
                return true
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) view?.loadUrl("file:///android_asset/offline.html")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    chooser.launch(p!!.createIntent()); true
                } catch (e: Exception) { fileCb = null; false }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        Notifier.heartbeat(this)
        val kp = getSharedPreferences("kp", MODE_PRIVATE)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !kp.getBoolean("askedBat", false)) {
            kp.edit().putBoolean("askedBat", true).apply()
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)))
            } catch (e: Exception) { }
        }
        val req = PeriodicWorkRequestBuilder<PostsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("posts", ExistingPeriodicWorkPolicy.KEEP, req)
        if (savedInstanceState == null) web.loadUrl(home) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }
}
