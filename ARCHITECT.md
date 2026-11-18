
Tuyệt vời. Chúng ta sẽ đi tuần tự từng phần một cách chắc chắn, code sạch chuẩn sản xuất (Production-ready Clean Architecture) để bạn có thể copy vào Android Studio là biên dịch và chạy được ngay.
Dưới đây là bản đồ toàn bộ 6 Phần chúng ta sẽ cùng xây dựng:
Phần 1 (Hiện tại): Khởi tạo Dự án, AndroidManifest.xml (Zero-Internet Privacy), và Tầng Room Database (10 Entities + DAOs).
Phần 2: Lõi Sleep Debt Engine (Toán học 14 ngày suy giảm hàm mũ, Auto-Calibration, Power Nap, Process C Forecast) + Bộ Unit Tests.
Phần 3: NightTimeService & Exact Smart Alarm (Foreground Service microphone, WakeLock, USE_EXACT_ALARM, Gentle Fade-in, liên kết cửa sổ đánh thức với Nợ ngủ).
Phần 4: Passive Tracking & Sleep Staging V1 (Gia tốc kế + Năng lượng âm thanh RMS + Thuật toán phân tầng Epoch 30s).
Phần 5: Lõi Sonar Siêu âm C++ (NDK + Google Oboe + I/Q Demodulation + Phase Unwrapping) + Hardware Calibration 3 giây + Gesture Snooze.
Phần 6: Energy-Gated Open-Source YAMNet + Local JSON Backup/Export + Giao diện Jetpack Compose (Dashboard Nợ ngủ, Hypnogram, Wave Debugger).

PHẦN 1: KHỞI TẠO DỰ ÁN, QUYỀN RIÊNG TƯ & CƠ SỞ DỮ LIỆU (ROOM DB)
Tên dự án gợi ý: OpenSleep (org.opensleep.app) — Giấy phép cộng đồng đề xuất: GPL-3.0 hoặc Apache-2.0.
app/src/main/
├── cpp/                          # (Dùng ở Phần 5 cho Sonar Oboe & KissFFT)
├── java/org/opensleep/app/
│   ├── OpenSleepApp.kt           # @HiltAndroidApp
│   ├── core/
│   │   ├── di/                   # Hilt Modules (DatabaseModule, EngineModule...)
│   │   └── util/                 # DateTime & Backup helpers
│   ├── data/
│   │   ├── local/
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── SleepConverters.kt
│   │   │   │   ├── dao/          # SleepSessionDao, SleepDebtDao, AlarmDao...
│   │   │   │   └── entity/       # 10 Room Entities
│   │   └── repository/           # Triển khai Repository
│   ├── domain/
│   │   ├── model/                # Domain models sạch (không phụ thuộc Android/Room)
│   │   └── engine/               # SleepDebtEngine, SleepStagingEngine
│   ├── service/                  # NightTimeService, AlarmReceiver, BootReceiver
│   └── ui/                       # Jetpack Compose Screens & ViewModels
└── AndroidManifest.xml