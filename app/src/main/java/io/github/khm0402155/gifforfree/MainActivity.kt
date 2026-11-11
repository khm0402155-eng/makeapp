package io.github.khm0402155.gifforfree

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offline: View
    private lateinit var downloader: GifDownloadBridge

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGif: GifDownloadBridge.PendingGif? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var pageFailed = false

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        callback?.onReceiveValue(uris)
    }

    private val writePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingGif
        pendingGif = null
        if (granted && pending != null) {
            downloader.save(pending.fileName, pending.bytes, pending.mime)
        } else {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        webView = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)
        findViewById<View>(R.id.retry).setOnClickListener {
            pageFailed = false
            offline.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }

        downloader = GifDownloadBridge(this) { pending ->
            pendingGif = pending
            writePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        configureWebView()
        webView.addJavascriptInterface(downloader, JS_BRIDGE)
        webView.webChromeClient = chromeClient
        webView.webViewClient = viewClient

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> chromeClient.onHideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = settings.userAgentString + " GifForFreeApp/1.0"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)
    }

    private fun installDownloadHook() {
        webView.evaluateJavascript(DOWNLOAD_HOOK, null)
    }

    private val viewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            pageFailed = false
            offline.visibility = View.GONE
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progress.visibility = View.GONE
            if (!pageFailed) installDownloadHook()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) return
            pageFailed = true
            progress.visibility = View.GONE
            offline.visibility = View.VISIBLE
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            if (uri.host == SITE_HOST) return false
            if (uri.scheme == "https" || uri.scheme == "http") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                return true
            }
            return true
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            val intent = try {
                fileChooserParams?.createIntent()
            } catch (_: Exception) {
                null
            } ?: Intent(Intent.ACTION_GET_CONTENT)
            if (intent.type.isNullOrBlank()) intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            return try {
                fileChooser.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback?.onReceiveValue(null)
                false
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || customView != null) {
                callback?.onCustomViewHidden()
                return
            }
            customView = view
            customCallback = callback
            findViewById<FrameLayout>(R.id.root).addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            webView.visibility = View.GONE
        }

        override fun onHideCustomView() {
            val view = customView ?: return
            findViewById<FrameLayout>(R.id.root).removeView(view)
            customView = null
            webView.visibility = View.VISIBLE
            customCallback?.onCustomViewHidden()
            customCallback = null
        }
    }

    companion object {
        private const val SITE_HOST = "khm0402155-eng.github.io"
        private const val START_URL = "https://khm0402155-eng.github.io/gifforfree/"
        private const val JS_BRIDGE = "AndroidGif"

        private const val DOWNLOAD_HOOK = """
            (function () {
              if (window.__gifSaveHook) return;
              window.__gifSaveHook = true;
              var original = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function () {
                var href = this.href || "";
                if (href.indexOf("blob:") !== 0) return original.apply(this, arguments);
                var name = this.getAttribute("download") || "animation.gif";
                fetch(href).then(function (response) { return response.blob(); }).then(function (blob) {
                  var reader = new FileReader();
                  reader.onloadend = function () {
                    var result = String(reader.result || "");
                    var comma = result.indexOf(",");
                    var base64 = comma >= 0 ? result.slice(comma + 1) : "";
                    if (!base64) return;
                    var id = Date.now().toString(36) + Math.random().toString(36).slice(2);
                    var size = 192 * 1024;
                    var total = Math.max(1, Math.ceil(base64.length / size));
                    for (var i = 0; i < total; i++) {
                      AndroidGif.savePart(id, name, blob.type || "image/gif", i, total, base64.slice(i * size, (i + 1) * size));
                    }
                  };
                  reader.readAsDataURL(blob);
                }).catch(function () {});
              };
            })();
        """
    }
}
