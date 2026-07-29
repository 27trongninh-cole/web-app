package com.example.webviewapp

import android.app.Application
import android.os.Build
import android.webkit.WebView

/**
 * Mỗi "tab lớn" (tài khoản) chạy trong 1 tiến trình Android riêng (khai báo qua
 * android:process trong AndroidManifest, xem BrowserActivitySlot0..5).
 * Ở đây ta gọi WebView.setDataDirectorySuffix() DUY NHẤT 1 LẦN, SỚM NHẤT có thể
 * (trước khi bất kỳ WebView/CookieManager nào được tạo ra trong tiến trình đó),
 * dựa theo tên tiến trình hiện tại (":slot0", ":slot1", ...).
 * => Mỗi slot có thư mục cookie/localStorage/IndexedDB HOÀN TOÀN riêng biệt,
 * tức là đăng nhập Google ở slot này không ảnh hưởng / không nhìn thấy slot khác.
 */
class WebViewApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val suffix = currentProcessSuffix()
        if (suffix != null) {
            try {
                WebView.setDataDirectorySuffix(suffix)
            } catch (e: Exception) {
                // Nếu gọi trễ (hiếm khi xảy ra) thì bỏ qua, app vẫn chạy được
                // nhưng có thể mất tách biệt dữ liệu cho tiến trình này.
            }
        }
    }

    /** Trả về vd "slot0" nếu tiến trình hiện tại tên là "com.example.webviewapp:slot0", null nếu là tiến trình chính. */
    private fun currentProcessSuffix(): String? {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            null
        } ?: return null

        val idx = processName.indexOf(':')
        if (idx == -1) return null // tiến trình chính (AccountListActivity), không cần suffix riêng
        return processName.substring(idx + 1)
    }
}
