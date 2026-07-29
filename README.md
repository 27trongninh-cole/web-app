# Multi Account WebView (1 APK duy nhất)

Ứng dụng WebView có **nhiều "tab lớn"**, mỗi tab lớn = **1 tài khoản Google riêng biệt, session/cookie
tách biệt hoàn toàn** — dù chỉ nằm trong **1 APK**. Trong mỗi tab lớn có các **"tab nhỏ"** (mặc định:
`Dashboard` trỏ tới `dashboard.render.com`, `Web` trỏ tới trang bạn muốn keep-alive), có thể tự
thêm/xoá tab nhỏ ngay trong app.

## Vì sao tách được session trong cùng 1 APK?

Android cho phép 1 app chạy nhiều **tiến trình (process)** song song, mỗi tiến trình có vùng nhớ và
(quan trọng nhất) **thư mục dữ liệu WebView riêng** nếu ta gọi `WebView.setDataDirectorySuffix()`.
App này khai báo sẵn **6 "slot"** (`BrowserActivitySlot0`..`Slot5`), mỗi slot chạy ở 1 tiến trình
riêng (`android:process=":slot0"`, `":slot1"`, ...). Khi bạn tạo 1 tài khoản mới, nó được gán vào
1 slot còn trống — đăng nhập Google ở slot này không hề ảnh hưởng / không nhìn thấy được ở slot khác.

**Giới hạn:** tối đa **6 tài khoản cùng lúc** vì số slot được khai báo cứng trong code. Muốn tăng lên,
xem mục "Tăng số lượng tài khoản tối đa" bên dưới.

## Cách dùng trong app

1. Mở app → màn hình đầu tiên là **danh sách tài khoản** (trống lúc đầu)
2. Bấm **"+ Thêm tài khoản"** → nhập tên (tuỳ ý) + link web cần keep-alive → bấm Thêm
   → app tự mở tài khoản đó với 2 tab nhỏ mặc định: `Dashboard` (Render) và `Web` (link bạn nhập)
3. Trong màn hình 1 tài khoản: bấm menu 3 gạch góc trên trái để xem/chuyển/thêm/xoá tab nhỏ
4. Đăng nhập Google ngay trong tab Dashboard — lần sau mở lại tài khoản này sẽ **không cần đăng nhập
   lại** (cookie được lưu riêng theo slot, tự ghi xuống đĩa sau mỗi lần tải trang)
5. Muốn đăng xuất tài khoản đó (vd để tái sử dụng slot cho tài khoản Google khác): vào menu ⋮ trên
   toolbar → **"Đăng xuất tài khoản này"**
6. Nút ⋮ trên toolbar cũng có: Chế độ máy tính (đổi User-Agent + ép layout desktop), Tải lại trang,
   Về trang chủ tab, Về danh sách tài khoản

## Lưu ý quan trọng: Google có thể vẫn chặn đăng nhập trong WebView

Google có chính sách chặn đăng nhập trong WebView tự chế (kể cả không lỗi kỹ thuật gì), hiện thông báo
kiểu "This browser or app may not be secure". Đây là giới hạn từ phía Google, không phải lỗi app. Nếu
gặp phải, báo lại để đổi hướng xử lý (thường cần Chrome Custom Tabs riêng cho bước đăng nhập).

## Thiết lập build (giống trước, không đổi)

1. Chạy workflow **"0. Tạo keystore (chạy 1 lần)"** trong tab Actions để tạo keystore
2. Thêm 4 secret: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. Push code lên nhánh `main` → Actions tự build và tạo Release đính kèm file
   `MultiAccountWebView.apk` — vào tab **Releases** để tải, không cần vào Actions

## Tăng số lượng tài khoản tối đa (hiện đang là 6)

1. Mở `app/src/main/java/com/example/webviewapp/BrowserSlots.kt`, thêm dòng:
   ```kotlin
   class BrowserActivitySlot6 : BrowserActivity()
   ```
2. Mở `app/src/main/AndroidManifest.xml`, thêm block:
   ```xml
   <activity android:name=".BrowserActivitySlot6" android:process=":slot6"
       android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden" />
   ```
3. Mở `app/src/main/java/com/example/webviewapp/AccountListActivity.kt`, thêm
   `BrowserActivitySlot6::class.java` vào cuối danh sách `slotActivities`.
4. Commit, push, build lại.

## Cài đặt lên máy

Chỉ có **1 file APK duy nhất** (`MultiAccountWebView.apk`) — cài 1 lần, dùng cho mọi tài khoản (khác
hoàn toàn cách cũ là mỗi tài khoản 1 APK riêng). Cập nhật app sau này chỉ cần cài đè, dữ liệu/tài khoản
đã lưu vẫn giữ nguyên.
