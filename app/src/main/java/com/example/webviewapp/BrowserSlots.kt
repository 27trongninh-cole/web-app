package com.example.webviewapp

// Mỗi lớp dưới đây hoàn toàn giống nhau về code — chỉ khác tên class.
// Lý do cần 6 lớp riêng: mỗi lớp được khai báo trong AndroidManifest.xml với
// android:process khác nhau (":slot0".. ":slot5"), giúp mỗi "tab lớn" (tài khoản)
// chạy ở 1 tiến trình Android riêng => cookie/đăng nhập tách biệt hoàn toàn.
// Muốn tăng số lượng tab lớn tối đa, thêm 1 class ở đây + 1 dòng <activity> trong Manifest.

class BrowserActivitySlot0 : BrowserActivity()
class BrowserActivitySlot1 : BrowserActivity()
class BrowserActivitySlot2 : BrowserActivity()
class BrowserActivitySlot3 : BrowserActivity()
class BrowserActivitySlot4 : BrowserActivity()
class BrowserActivitySlot5 : BrowserActivity()
