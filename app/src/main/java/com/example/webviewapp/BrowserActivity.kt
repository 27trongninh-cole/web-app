package com.example.webviewapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Màn hình trình duyệt cho 1 "tab lớn" (1 tài khoản). Được mở qua 1 trong các lớp
 * BrowserActivitySlot0..5 — mỗi lớp chạy ở 1 tiến trình Android riêng (xem AndroidManifest
 * + WebViewApp.kt) nên cookie/đăng nhập của tab lớn này KHÔNG lẫn với tab lớn khác.
 *
 * Bên trong 1 tab lớn có các "tab nhỏ" (Dashboard, Web, ...) y hệt cơ chế cũ:
 * lưu trong SharedPreferences riêng theo bigTabId, có thể thêm/xoá ngay trong app.
 */
open class BrowserActivity : AppCompatActivity() {

    private data class SubTab(val id: String, var name: String, var url: String)

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navTabs: NavigationView
    private lateinit var webContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var bigTabId: String
    private lateinit var bigTabName: String

    private val subTabs = mutableListOf<SubTab>()
    private val webViews = mutableMapOf<String, WebView>()
    private var currentSubTabId: String? = null
    private var currentPopup: WebView? = null
    private var desktopMode = false

    private val ADD_TAB_ITEM_ID = 999_999

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

        bigTabId = intent.getStringExtra(EXTRA_BIG_TAB_ID) ?: "default"
        bigTabName = intent.getStringExtra(EXTRA_BIG_TAB_NAME) ?: "Tài khoản"

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        navTabs = findViewById(R.id.navTabs)
        webContainer = findViewById(R.id.webviewContainer)
        progressBar = findViewById(R.id.progressBar)

        CookieManager.getInstance().setAcceptCookie(true)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        toolbar.inflateMenu(R.menu.menu_toolbar)
        toolbar.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        toolbar.overflowIcon?.setTint(android.graphics.Color.WHITE)

        loadSubTabs()
        buildNavMenu()

        if (subTabs.isNotEmpty()) {
            switchToSubTab(subTabs[0].id)
        }

        navTabs.setNavigationItemSelectedListener { item ->
            if (item.itemId == ADD_TAB_ITEM_ID) {
                showAddSubTabDialog()
            } else if (item.itemId in subTabs.indices) {
                switchToSubTab(subTabs[item.itemId].id)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            true
        }
    }

    // ==================== Lưu / đọc danh sách tab nhỏ (riêng theo từng tab lớn) ====================

    private fun prefs() = getSharedPreferences("subtabs_$bigTabId", Context.MODE_PRIVATE)

    private fun loadSubTabs() {
        subTabs.clear()
        val saved = prefs().getString("tabs_json", null)
        if (saved != null) {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                subTabs.add(SubTab(obj.getString("id"), obj.getString("name"), obj.getString("url")))
            }
            return
        }
        // Tab lớn mới tạo: nạp sẵn 2 tab nhỏ mặc định Dashboard + Web
        val webUrl = intent.getStringExtra(EXTRA_DEFAULT_WEB_URL)?.takeIf { it.isNotBlank() }
            ?: "https://render.com"
        subTabs.add(SubTab(UUID.randomUUID().toString(), "Dashboard", "https://dashboard.render.com"))
        subTabs.add(SubTab(UUID.randomUUID().toString(), "Web", webUrl))
        saveSubTabs()
    }

    private fun saveSubTabs() {
        val arr = JSONArray()
        subTabs.forEach { tab ->
            val obj = JSONObject()
            obj.put("id", tab.id)
            obj.put("name", tab.name)
            obj.put("url", tab.url)
            arr.put(obj)
        }
        prefs().edit().putString("tabs_json", arr.toString()).apply()
    }

    // ==================== Menu danh sách tab nhỏ (drawer bên trái) ====================

    private fun buildNavMenu() {
        navTabs.menu.clear()
        subTabs.forEachIndexed { index, tab ->
            val item = navTabs.menu.add(0, index, index, tab.name)
            val deleteBtn = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setPadding(28, 20, 28, 20)
                setOnClickListener { confirmDeleteSubTab(tab.id) }
            }
            item.actionView = deleteBtn
        }
        navTabs.menu.add(0, ADD_TAB_ITEM_ID, subTabs.size, "+ Thêm tab")
    }

    private fun showAddSubTabDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply { hint = "Tên tab" }
        val urlInput = EditText(this).apply { hint = "https://duong-dan-website.com" }
        container.addView(nameInput)
        container.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Thêm tab mới")
            .setView(container)
            .setPositiveButton("Thêm") { _, _ ->
                var url = urlInput.text.toString().trim()
                val name = nameInput.text.toString().trim().ifEmpty { url }
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    val newTab = SubTab(UUID.randomUUID().toString(), name, url)
                    subTabs.add(newTab)
                    saveSubTabs()
                    buildNavMenu()
                    switchToSubTab(newTab.id)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun confirmDeleteSubTab(tabId: String) {
        if (subTabs.size <= 1) {
            AlertDialog.Builder(this)
                .setMessage("Cần giữ lại ít nhất 1 tab.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val tab = subTabs.find { it.id == tabId } ?: return
        AlertDialog.Builder(this)
            .setTitle("Xoá tab")
            .setMessage("Xoá tab \"${tab.name}\"?")
            .setPositiveButton("Xoá") { _, _ -> deleteSubTab(tabId) }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun deleteSubTab(tabId: String) {
        val index = subTabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        webViews.remove(tabId)?.let { wv ->
            (wv.parent as? FrameLayout)?.removeView(wv)
            wv.destroy()
        }
        subTabs.removeAt(index)
        saveSubTabs()
        buildNavMenu()

        if (currentSubTabId == tabId) {
            switchToSubTab(subTabs[0].id)
        }
    }

    // ==================== WebView ====================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewForTab(tab: SubTab): WebView {
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (webViews[currentSubTabId] === view) {
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (webViews[currentSubTabId] === view && !title.isNullOrBlank()) {
                    toolbar.title = title
                }
            }

            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
            ): Boolean {
                val popupWebView = WebView(this@BrowserActivity)
                popupWebView.settings.javaScriptEnabled = true
                popupWebView.settings.domStorageEnabled = true
                popupWebView.settings.setSupportMultipleWindows(true)
                popupWebView.settings.userAgentString = webView.settings.userAgentString
                popupWebView.webViewClient = WebViewClient()
                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        webContainer.removeView(popupWebView)
                        popupWebView.destroy()
                        currentPopup = null
                    }
                }

                webContainer.addView(
                    popupWebView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                currentPopup = popupWebView

                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

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

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Đang tải xuống...")
            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            request.setTitle(fileName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        webView.loadUrl(tab.url)
        return webView
    }

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
            null
        }
        webView.settings.textZoom = if (desktopMode) 70 else 100
    }

    private fun switchToSubTab(tabId: String) {
        val tab = subTabs.find { it.id == tabId } ?: return
        currentSubTabId = tabId

        val webView = webViews.getOrPut(tabId) { createWebViewForTab(tab) }
        webContainer.removeAllViews()
        (webView.parent as? FrameLayout)?.removeView(webView)
        webContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        toolbar.title = "$bigTabName · ${tab.name}"
        val index = subTabs.indexOfFirst { it.id == tabId }
        if (index >= 0) navTabs.setCheckedItem(index)
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
                webViews[currentSubTabId]?.let {
                    applyUserAgent(it)
                    it.reload()
                }
                true
            }
            R.id.action_reload -> {
                webViews[currentSubTabId]?.reload()
                true
            }
            R.id.action_home -> {
                val tab = subTabs.find { it.id == currentSubTabId }
                if (tab != null) webViews[currentSubTabId]?.loadUrl(tab.url)
                true
            }
            R.id.action_logout -> {
                confirmLogout()
                true
            }
            R.id.action_back_to_accounts -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất tài khoản này")
            .setMessage("Xoá toàn bộ cookie/đăng nhập của tab lớn \"$bigTabName\"? Các tab nhỏ sẽ tải lại từ đầu.")
            .setPositiveButton("Đăng xuất") { _, _ -> doLogout() }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun doLogout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webViews.forEach { (id, wv) ->
            wv.clearCache(true)
            subTabs.find { it.id == id }?.let { wv.loadUrl(it.url) }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onBackPressed() {
        val popup = currentPopup
        if (popup != null) {
            webContainer.removeView(popup)
            popup.destroy()
            currentPopup = null
            return
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        val current = webViews[currentSubTabId]
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_BIG_TAB_ID = "big_tab_id"
        const val EXTRA_BIG_TAB_NAME = "big_tab_name"
        const val EXTRA_DEFAULT_WEB_URL = "default_web_url"
    }
}
