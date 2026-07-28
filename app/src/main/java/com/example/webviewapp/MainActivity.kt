package com.example.webviewapp

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Trình duyệt nhiều tab dựa trên WebView.
 * Danh sách tab được đọc từ assets/links.txt (mỗi dòng: "Tên tab|https://url").
 * File links.txt khác nhau theo từng flavor (xem app/src/<flavor>/assets/links.txt).
 */
class MainActivity : AppCompatActivity() {

    private data class TabInfo(val name: String, val url: String)

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navTabs: NavigationView
    private lateinit var webContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    private val tabs = mutableListOf<TabInfo>()
    private val webViews = mutableMapOf<Int, WebView>()
    private var currentTabIndex = 0
    private var desktopMode = false

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val results: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileChooserCallback?.onReceiveValue(results)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        navTabs = findViewById(R.id.navTabs)
        webContainer = findViewById(R.id.webviewContainer)
        progressBar = findViewById(R.id.progressBar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        toolbar.inflateMenu(R.menu.menu_toolbar)
        toolbar.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        toolbar.overflowIcon?.setTint(android.graphics.Color.WHITE)

        loadTabsFromAssets()
        buildNavMenu()

        if (tabs.isNotEmpty()) {
            switchToTab(0)
        }

        navTabs.setNavigationItemSelectedListener { item ->
            switchToTab(item.itemId)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun loadTabsFromAssets() {
        tabs.clear()
        assets.open("links.txt").bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    tabs.add(TabInfo(parts[0].trim(), parts[1].trim()))
                } else {
                    // Nếu dòng chỉ có URL, dùng chính URL làm tên tab
                    tabs.add(TabInfo(line, line))
                }
            }
        }
    }

    private fun buildNavMenu() {
        navTabs.menu.clear()
        tabs.forEachIndexed { index, tab ->
            navTabs.menu.add(0, index, index, tab.name)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewForTab(index: Int): WebView {
        val webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        applyUserAgent(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (desktopMode) {
                    injectDesktopViewport(view)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (webViews[currentTabIndex] === view) {
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (webViews[currentTabIndex] === view && !title.isNullOrBlank()) {
                    toolbar.title = title
                }
            }

            // Cho phép mở popup / target=_blank trong cùng WebView
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
            ): Boolean {
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }

            // Chọn file khi bấm "tải lên" trong trang web
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        // Tải file xuống dùng DownloadManager hệ thống (giống trình duyệt thật)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Đang tải xuống...")
            val fileName = URLUtilGuessFileName(url, contentDisposition, mimeType)
            request.setTitle(fileName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        webView.loadUrl(tabs[index].url)
        return webView
    }

    private fun URLUtilGuessFileName(url: String, contentDisposition: String?, mimeType: String?): String =
        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)

    private fun injectDesktopViewport(webView: WebView) {
        val js = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.setAttribute('name', 'viewport');
                    document.head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=1280, initial-scale=1');
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun applyUserAgent(webView: WebView) {
        webView.settings.userAgentString = if (desktopMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        } else {
            null // dùng User-Agent mặc định của thiết bị (di động)
        }
        // Trang desktop thường có chữ to hơn khi hiển thị trên màn hình rộng thật,
        // giảm zoom chữ lại một chút để giống Chrome "Request Desktop Site".
        webView.settings.textZoom = if (desktopMode) 70 else 100
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        currentTabIndex = index

        val webView = webViews.getOrPut(index) { createWebViewForTab(index) }
        webContainer.removeAllViews()
        (webView.parent as? FrameLayout)?.removeView(webView)
        webContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        toolbar.title = tabs[index].name
        navTabs.setCheckedItem(index)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_desktop -> {
                desktopMode = !desktopMode
                item.isChecked = desktopMode
                webViews[currentTabIndex]?.let {
                    applyUserAgent(it)
                    it.reload()
                }
                true
            }
            R.id.action_reload -> {
                webViews[currentTabIndex]?.reload()
                true
            }
            R.id.action_home -> {
                webViews[currentTabIndex]?.loadUrl(tabs[currentTabIndex].url)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        val current = webViews[currentTabIndex]
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
