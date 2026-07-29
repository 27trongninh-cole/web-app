package com.example.webviewapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Màn hình đầu tiên khi mở app: danh sách các "tab lớn" (mỗi tab lớn = 1 tài khoản
 * Google riêng biệt, chạy ở 1 tiến trình/slot riêng). Bấm vào 1 tài khoản sẽ mở
 * BrowserActivitySlotN tương ứng.
 */
class AccountListActivity : AppCompatActivity() {

    private data class BigTab(val id: String, var name: String, var slot: Int, var defaultWebUrl: String)

    // Danh sách lớp Activity cho từng slot — số lượng slot = số tài khoản tối đa cùng lúc.
    private val slotActivities = listOf(
        BrowserActivitySlot0::class.java,
        BrowserActivitySlot1::class.java,
        BrowserActivitySlot2::class.java,
        BrowserActivitySlot3::class.java,
        BrowserActivitySlot4::class.java,
        BrowserActivitySlot5::class.java,
    )

    private val bigTabs = mutableListOf<BigTab>()
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        listContainer = findViewById(R.id.accountListContainer)
        val addBtn = findViewById<MaterialButton>(R.id.btnAddAccount)
        addBtn.setOnClickListener { showAddAccountDialog() }

        loadBigTabs()
        renderList()
    }

    override fun onResume() {
        super.onResume()
        renderList() // refresh tên/thứ tự khi quay lại từ màn browser
    }

    // ==================== Lưu / đọc danh sách tab lớn ====================

    private fun prefs() = getSharedPreferences("big_tabs_store", Context.MODE_PRIVATE)

    private fun loadBigTabs() {
        bigTabs.clear()
        val saved = prefs().getString("list_json", null) ?: return
        val arr = JSONArray(saved)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            bigTabs.add(
                BigTab(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.getInt("slot"),
                    obj.optString("defaultWebUrl", "")
                )
            )
        }
    }

    private fun saveBigTabs() {
        val arr = JSONArray()
        bigTabs.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("slot", it.slot)
            obj.put("defaultWebUrl", it.defaultWebUrl)
            arr.put(obj)
        }
        prefs().edit().putString("list_json", arr.toString()).apply()
    }

    private fun nextFreeSlot(): Int? {
        val used = bigTabs.map { it.slot }.toSet()
        for (i in slotActivities.indices) {
            if (i !in used) return i
        }
        return null
    }

    // ==================== Giao diện danh sách ====================

    private fun renderList() {
        listContainer.removeAllViews()
        if (bigTabs.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Chưa có tài khoản nào. Bấm \"+ Thêm tài khoản\" bên dưới để bắt đầu."
                setPadding(16, 16, 16, 16)
            }
            listContainer.addView(empty)
            return
        }
        bigTabs.forEach { bigTab ->
            listContainer.addView(buildRow(bigTab))
        }
    }

    private fun buildRow(bigTab: BigTab): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 24)
        }
        val label = TextView(this).apply {
            text = bigTab.name
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openBigTab(bigTab) }
        }
        val openBtn = TextView(this).apply {
            text = "Mở"
            setPadding(24, 12, 24, 12)
            setTextColor(Color.parseColor("#1565C0"))
            setOnClickListener { openBigTab(bigTab) }
        }
        val deleteBtn = TextView(this).apply {
            text = "Xoá"
            setPadding(24, 12, 24, 12)
            setTextColor(Color.parseColor("#C62828"))
            setOnClickListener { confirmDeleteBigTab(bigTab) }
        }
        row.addView(label)
        row.addView(openBtn)
        row.addView(deleteBtn)
        return row
    }

    private fun openBigTab(bigTab: BigTab) {
        val activityClass = slotActivities.getOrNull(bigTab.slot) ?: return
        val intent = Intent(this, activityClass).apply {
            putExtra(BrowserActivity.EXTRA_BIG_TAB_ID, bigTab.id)
            putExtra(BrowserActivity.EXTRA_BIG_TAB_NAME, bigTab.name)
            putExtra(BrowserActivity.EXTRA_DEFAULT_WEB_URL, bigTab.defaultWebUrl)
        }
        startActivity(intent)
    }

    private fun showAddAccountDialog() {
        val freeSlot = nextFreeSlot()
        if (freeSlot == null) {
            AlertDialog.Builder(this)
                .setTitle("Đã đạt tối đa")
                .setMessage("Đã dùng hết ${slotActivities.size} tài khoản tối đa. Muốn thêm nữa cần sửa code thêm slot (xem README).")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply { hint = "Tên tài khoản (vd: Tài khoản 1)" }
        val urlInput = EditText(this).apply { hint = "Link web cần keep-alive (tab \"Web\")" }
        container.addView(nameInput)
        container.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Thêm tài khoản mới")
            .setView(container)
            .setPositiveButton("Thêm") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { "Tài khoản ${bigTabs.size + 1}" }
                var url = urlInput.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                val newTab = BigTab(UUID.randomUUID().toString(), name, freeSlot, url)
                bigTabs.add(newTab)
                saveBigTabs()
                renderList()
                openBigTab(newTab)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun confirmDeleteBigTab(bigTab: BigTab) {
        AlertDialog.Builder(this)
            .setTitle("Xoá tài khoản")
            .setMessage(
                "Xoá \"${bigTab.name}\" khỏi danh sách?\n\n" +
                    "Lưu ý: thao tác này chỉ ẩn khỏi danh sách, KHÔNG tự xoá cookie/đăng nhập đã lưu. " +
                    "Nếu muốn xoá sạch để dùng slot này cho tài khoản khác, hãy mở tài khoản này và bấm " +
                    "\"Đăng xuất tài khoản này\" trong menu ⋮ trước khi xoá."
            )
            .setPositiveButton("Xoá") { _, _ ->
                bigTabs.remove(bigTab)
                saveBigTabs()
                renderList()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
