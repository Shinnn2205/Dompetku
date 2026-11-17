package com.dompetku.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { arrayOf(it) }
        } else {
            null
        }
        callback.onReceiveValue(results)
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderReceiver.scheduleDailyReminders(this)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderReceiver.ensureChannel(this)
        val prefs = getSharedPreferences(ReminderReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ReminderReceiver.KEY_ENABLED, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    ReminderReceiver.scheduleDailyReminders(this)
                } else {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                ReminderReceiver.scheduleDailyReminders(this)
            }
        }

        webView = WebView(this)
        setContentView(webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                fileChooserLauncher.launch(Intent.createChooser(intent, "Pilih File Cadangan JSON"))
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){var m=document.querySelector('.modal-backdrop.open');if(m){m.classList.remove('open');return 'closed';}return 'none';})()"
                ) { res ->
                    if (res != "\"closed\"") {
                        finish()
                    }
                }
            }
        })

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun setRemindersEnabled(enabled: Boolean) {
            runOnUiThread {
                if (enabled) {
                    ReminderReceiver.scheduleDailyReminders(this@MainActivity)
                } else {
                    ReminderReceiver.cancelDailyReminders(this@MainActivity)
                }
            }
        }

        @JavascriptInterface
        fun areRemindersEnabled(): Boolean {
            val prefs = getSharedPreferences(ReminderReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(ReminderReceiver.KEY_ENABLED, true)
        }

        @JavascriptInterface
        fun testNotification() {
            runOnUiThread {
                ReminderReceiver.showNotification(
                    this@MainActivity,
                    999,
                    "🍳 Pengingat DompetKu Aktif!",
                    "Kamu akan diingatkan rutin untuk masak sendiri, tahan jajan, dan setor tabungan."
                )
            }
        }

        @JavascriptInterface
        fun exportBackup(jsonContent: String, filename: String) {
            runOnUiThread {
                try {
                    val dir = File(cacheDir, "backups").apply { mkdirs() }
                    val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val file = File(dir, safeName)
                    file.writeText(jsonContent, Charsets.UTF_8)

                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Simpan atau Bagikan Cadangan"))
                } catch (_: Exception) {
                }
            }
        }
    }
}
