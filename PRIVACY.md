# OpenSleep Privacy Policy

**Cam kết Zero-Internet Privacy (100% Cục bộ & Bảo mật Tuyệt đối)**

OpenSleep được xây dựng theo triết lý mã nguồn mở với sự tôn trọng tuyệt đối quyền riêng tư và dữ liệu sức khỏe của người dùng:

1. **Không có quyền truy cập Internet**: Ứng dụng hoàn toàn **KHÔNG** khai báo quyền `android.permission.INTERNET` hay `android.permission.ACCESS_NETWORK_STATE` trong `AndroidManifest.xml`. Về mặt kỹ thuật, ứng dụng không có bất kỳ khả năng nào để gửi dữ liệu ra bên ngoài thiết bị.
2. **Không có SDK Theo dõi, Phân tích hay Quảng cáo**: Ứng dụng không tích hợp bất kỳ SDK bên thứ ba nào (như Google Analytics, Firebase, Crashlytics, Facebook SDK, hay AdMob).
3. **Không cần tài khoản hay máy chủ**: Mọi tính năng hoạt động độc lập không yêu cầu đăng ký, đăng nhập hay dịch vụ đám mây.
4. **Xử lý âm thanh & AI trực tiếp trên RAM**:
   - Khi theo dõi thụ động hoặc phân tích âm thanh ban đêm (YAMNet), luồng âm thanh được xử lý hoàn toàn trong bộ nhớ RAM và bị xóa ngay sau chu kỳ phân tích; ứng dụng không ghi lại file âm thanh người dùng trừ khi có cài đặt chủ động.
   - Sóng siêu âm Sonar hoạt động hoàn toàn cục bộ trên phần cứng thiết bị.
5. **Quyền tự chủ dữ liệu (Local Backup & SAF)**: Dữ liệu lịch sử giấc ngủ và nợ ngủ được lưu trữ cục bộ trong Room Database (`opensleep_local.db`). Người dùng có thể chủ động xuất hoặc nhập file JSON sao lưu qua Storage Access Framework (SAF) mà không bị phụ thuộc vào bất kỳ nhà cung cấp dịch vụ nào.
