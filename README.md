# WebView Multi-Account App

Trình duyệt WebView nhiều tab, build ra nhiều APK (mỗi APK ứng với 1 tài khoản Google/dùng riêng) từ **cùng 1 codebase** thông qua Gradle product flavors.

## Vì sao không cần "luồng đăng nhập riêng"?

Mỗi flavor có `applicationId` khác nhau (`com.example.webviewapp.accounta`, `.accountb`, ...).
Android tự cô lập dữ liệu (cookie, localStorage, cache) theo từng package name — nên bạn chỉ cần
cài các APK, đăng nhập Google 1 lần trong từng app, phiên đăng nhập sẽ không lẫn giữa các app.
=> Có thể cài **tất cả APK song song trên cùng 1 máy** vì package name khác nhau.

## 1. Thiết lập keystore (chỉ làm 1 lần)

Cần dùng **chung 1 keystore cho mọi flavor** (không bắt buộc phải khác nhau — vì phân biệt app đã
dựa vào applicationId rồi). Việc dùng chung giúp bạn update app sau này mà không lỗi "chữ ký không khớp".

```bash
keytool -genkey -v -keystore release.keystore -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000
```

Sau đó thêm các secret sau vào **GitHub repo → Settings → Secrets and variables → Actions**:

| Secret | Giá trị |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` (nội dung base64 của file keystore) |
| `KEYSTORE_PASSWORD` | mật khẩu keystore |
| `KEY_ALIAS` | `mykey` (hoặc alias bạn đặt) |
| `KEY_PASSWORD` | mật khẩu key |

## 2. Thêm 1 "apk/tài khoản" mới

1. Mở `app/build.gradle`, trong khối `productFlavors`, copy 1 block flavor có sẵn, đổi tên
   (vd `accountC`) và `applicationId` (vd `com.example.webviewapp.accountc`).
2. Tạo file `app/src/accountC/assets/links.txt`, mỗi dòng 1 tab theo định dạng:
   ```
   Tên tab|https://duong-dan-website.com
   ```
3. Commit & push. GitHub Actions sẽ tự phát hiện flavor mới (do `app/build.gradle` thay đổi)
   và build APK cho **tất cả** flavor (an toàn, tránh sót app khi đổi cấu hình chung).
4. Nếu chỉ sửa link của 1 app đã có sẵn (chỉ đổi file trong `app/src/accountA/assets/links.txt`
   chẳng hạn), Actions sẽ **chỉ build lại app đó**.

## 3. Lấy APK

Vào tab **Actions** trên GitHub → chọn lần chạy mới nhất → mục **Artifacts** phía dưới,
tải file `apk-accountA`, `apk-accountB`, ... về máy rồi cài trực tiếp (bật "Cài từ nguồn không xác định"
nếu Android yêu cầu).

## 4. Chạy build thủ công / ép build lại toàn bộ

Vào tab Actions → chọn workflow "Build APKs" → **Run workflow** → tick `force_all` nếu muốn build lại
tất cả APK bất kể có thay đổi hay không.

## Tính năng WebView đã có

- Nhiều tab, danh sách tab nằm trong menu 3 gạch (góc trên bên trái)
- Chuyển đổi "Chế độ máy tính" (đổi User-Agent desktop, có trong menu overflow ⋮ trên toolbar)
- Tải file xuống qua DownloadManager hệ thống (giống trình duyệt thật)
- Tải file lên (input file trên web) qua bộ chọn file của Android
- Hỗ trợ mở tab/popup mới (`target=_blank`, `window.open`)
- Nút Reload, nút "Về trang chủ tab" trong menu overflow
- Nút back vật lý sẽ quay lại lịch sử trong WebView trước khi thoát app
