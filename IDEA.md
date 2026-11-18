Chuyển hướng dự án sang mô hình **Mã nguồn mở (Open-Source / FOSS), Miễn phí 100% và Tuyệt đối Riêng tư (Zero-Telemetry)** là một quyết định cực kỳ giá trị cho cộng đồng. Trên thị trường hiện nay, hầu hết các app đo giấc ngủ và nợ ngủ tốt (như Sleepwave, RISE, Sleep Cycle) đều khóa sau tường phí (subscription) hoặc thu thập dữ liệu người dùng. Một ứng dụng **không cần tài khoản, không passkey, không quảng cáo, và không xin quyền Internet** sẽ tạo được niềm tin tuyệt đối.

Dưới đây là **Bản thiết kế Kỹ thuật Hoàn chỉnh (Technical Blueprint)** dành cho bạn, tập trung sâu vào **Sleep Debt Engine** (trái tim của ứng dụng) cùng kiến trúc **Sonar/AI Clean-room** trên Android Native.

---

## 1. TUYÊN NGÔN QUYỀN RIÊNG TƯ & KIẾN TRÚC TỔNG THỂ (ZERO-TRUST PRIVACY)

Để chứng minh với cộng đồng (và phát hành được trên **F-Droid** lẫn Google Play) rằng ứng dụng *"không lấy bất kỳ dữ liệu nào"*:
1. **Không xin quyền `INTERNET` trong `AndroidManifest.xml`**: Hệ điều hành Android sẽ chặn ở cấp độ nhân (OS-level) mọi kết nối mạng. Người dùng có thể kiểm tra trong Cài đặt máy và thấy app hoàn toàn ngoại tuyến 100%.
2. **Không tài khoản, không Passkey, không Firebase, không Billing**: Toàn bộ dữ liệu nằm trong Sandbox cục bộ của ứng dụng.
3. **Xử lý âm thanh trong RAM (In-Memory Processing)**: Sóng Sonar và âm thanh thu vào được xử lý trực tiếp trên RAM theo từng buffer ngắn rồi hủy ngay lập tức. Chỉ lưu đoạn ghi âm ngắn (ngáy/nói mớ) nếu người dùng *chủ động bật* tính năng nghe lại, kèm tùy chọn tự xóa sau X ngày.
4. **Sao lưu chủ động (Local Export/Import)**: Cung cấp tính năng xuất/nhập toàn bộ lịch sử ra file `.json` hoặc `.zip` (tùy chọn đặt mật khẩu mã hóa file local khi export nếu đổi máy) để người dùng tự giữ dữ liệu của mình.

### Tech Stack đề xuất (100% Free & Open-Source):
* **Ngôn ngữ & UI**: Kotlin + Jetpack Compose (Material 3) + Custom Canvas / Vico Chart.
* **Kiến trúc**: Clean Architecture + MVI/MVVM + Hilt (DI) + Coroutines/Flow.
* **Database**: Room (SQLite) + DataStore Preferences.
* **Audio & DSP Core (C++ NDK)**: **Google Oboe** (Apache 2.0) + **KissFFT** (BSD-3-Clause).
* **AI cục bộ**: TensorFlow Lite + **Google YAMNet chuẩn** (Apache 2.0).

---

## 2. ĐỘNG CƠ NỢ NGỦ (SLEEP DEBT ENGINE) — THIẾT KẾ TOÁN HỌC & KHOA HỌC

Theo nghiên cứu kinh điển của **Van Dongen và cộng sự (2003)**, việc thiếu ngủ 1–2 giờ mỗi đêm khiến hiệu suất nhận thức suy giảm tích lũy tuyến tính qua 14 ngày tương đương với việc thức trắng 2 đêm liên tiếp, trong khi cảm giác buồn ngủ chủ quan của con người lại bị "chai lì" và không nhận ra. Đồng thời, giấc ngủ chịu sự chi phối của **Mô hình Hai Quá Trình (Two-Process Model)** của Borbély: *Process S* (áp lực ngủ tích lũy) và *Process C* (nhịp sinh học 24h).

Dưới đây là thiết kế toán học hoàn chỉnh cho **Sleep Debt Engine** của bạn:

### 2.1. Bước 1: Xác định Nhu cầu Ngủ Cá nhân ($N_{\text{personal}}$)
Không áp đặt cứng 8 tiếng cho mọi người (vì nhu cầu sinh học dao động từ 6.5h đến 9.5h):
* **Khởi tạo (Onboarding)**: Lấy theo độ tuổi (khuyến nghị AASM: Người lớn 18–64 tuổi mặc định $8.0\text{h}$, Thanh thiếu niên $9.0\text{h}$, Người cao tuổi $7.5\text{h}$) hoặc cho user tự chỉnh thanh trượt ban đầu.
* **Tự động hiệu chỉnh (Auto-Calibration sau 14 đêm)**:
  * Lọc ra các đêm *"Ngủ tự nhiên chất lượng cao"* (không đặt báo thức hoặc thức dậy trước báo thức, đánh giá buổi sáng `sleepRating >= 4/5`, và lúc đó nợ ngủ đang thấp).
  * Tính trung bình trượt hàm mũ (EMA) thời lượng ngủ thực tế của các đêm đó để tinh chỉnh dần $N_{\text{personal}}$ (giới hạn bước nhảy tối đa $\pm 15\text{ phút/tuần}$ để tránh nhiễu).

### 2.2. Bước 2: Tính Giấc ngủ Hiệu dụng ($E_i$) trong ngày $i$
Ngủ 8 tiếng nhưng tỉnh giấc 10 lần không có giá trị trả nợ bằng 8 tiếng ngủ sâu liền mạch:
$$E_i = \left( D_{\text{sleep\_night}} \times Q_{\text{factor}} \right) + \min\left( D_{\text{nap}} \times 0.85,\ 1.5\text{h} \right)$$

Trong đó:
* $D_{\text{sleep\_night}}$: Tổng thời gian thực sự ngủ ban đêm (tính bằng giờ, đã trừ thời gian trằn trọc chưa vào giấc và thời gian thức giữa đêm `awakeDuration`).
* $Q_{\text{factor}} \in [0.75, 1.05]$: Hệ số chất lượng giấc ngủ, tính từ độ phân mảnh (`microAwakenings`) và tỷ lệ Deep + REM (khi đã bật Sonar):
  $$Q_{\text{factor}} = 1.0 - \min\left(0.25,\ \text{Số lần thức vi mô/giờ} \times 0.04\right) + \text{Bonus}_{\text{Deep/REM}}$$
  *(Ở Phase 1 chưa có Sonar, đặt mặc định $Q_{\text{factor}} = 1.0 - \text{Phạt nếu thức giữa đêm}$).*
* $D_{\text{nap}}$: Tổng thời gian ngủ trưa (Power Nap $\ge 15$ phút). Đánh trọng số $0.85$ và giới hạn công nhận tối đa $1.5\text{h/ngày}$ để khuyến khích ngủ chính vào ban đêm.

### 2.3. Bước 3: Công thức Nợ ngủ Tích lũy 14 ngày ($SD_t$)
Áp dụng **Cửa sổ trượt 14 ngày với Trọng số Suy giảm Hàm mũ (Exponential Decay Rolling Window)** — tương đồng với cách tiếp cận khoa học giấc ngủ hiện đại:

Với ngày hiện tại $t$, xét lịch sử $k = 0, 1, \dots, 13$ ($k=0$ là đêm vừa qua, $k=13$ là 13 ngày trước):
* Độ lệch giấc ngủ từng ngày: $\Delta_k = N_{\text{personal}} - E_{t-k}$
  * $\Delta_k > 0$: Thiếu ngủ (tăng nợ).
  * $\Delta_k < 0$: Ngủ dư/ngủ bù (trả nợ).
* Trọng số suy giảm theo thời gian:
  $$w_k = e^{-\lambda k} \quad \text{với } \lambda = 0.085$$
  *(Đêm qua $k=0 \Rightarrow w_0 = 1.0$; cách 7 ngày $k=7 \Rightarrow w_7 \approx 0.55$; cách 13 ngày $k=13 \Rightarrow w_{13} \approx 0.33$ — vừa phản ánh tác động nặng nhất của những đêm gần đây, vừa làm mờ dần nợ cũ quá 2 tuần).*
* **Bất đối xứng khi trả nợ (Recovery Asymmetry)**: Khoa học chỉ ra bạn không thể xóa sạch 10 tiếng nợ bằng 1 ngày ngủ bù cuối tuần. Do đó khi tính tổng tích lũy theo từng ngày từ quá khứ đến hiện tại:
  $$\text{Nếu } \Delta_k < 0 \text{ (trả nợ): chỉ cho phép trả tối đa } 1.5\text{h/đêm hoặc nhân hệ số phục hồi } 0.85$$
* Tổng nợ ngủ tại ngày $t$:
  $$SD_t = \max\left(0,\ \sum_{k=0}^{13} w_k \cdot \Delta'_k \right)$$
* **Phân ngưỡng cảnh báo UI (Actionable Status)**:
  * `0.0 – 2.0 giờ`: **Tối ưu (Optimal)** — Đỉnh năng lượng cao, tỉnh táo.
  * `2.0 – 5.0 giờ`: **Nợ nhẹ (Manageable)** — Cần đi ngủ sớm hơn 30 phút tối nay.
  * `5.0 – 8.0 giờ`: **Nợ cao (High Debt)** — Hiệu suất nhận thức giảm rõ rệt; gợi ý Power Nap 20 phút đầu giờ chiều.
  * `> 8.0 giờ`: **Báo động (Critical)** — Suy giảm phản xạ tương đương thiếu ngủ cấp tính; kích hoạt chế độ bảo vệ giấc ngủ.

### 2.4. Bước 4: Dự báo Nhịp Sinh học (Process C) & Liên kết Báo thức Thông minh
* **Dự báo năng lượng trong ngày (Circadian Rhythm Curve)**:
  Tính giờ thức dậy trung bình 7 ngày gần nhất ($\bar{T}_{\text{wake}}$). Từ đó vẽ đường cong năng lượng mô phỏng theo hai pha trong ngày (không cần wearable):
  * *Morning Peak*: $\bar{T}_{\text{wake}} + 2\text{h} \to +4\text{h}$
  * *Afternoon Dip (Vùng trũng buổi trưa)*: $\bar{T}_{\text{wake}} + 6.5\text{h} \to +8.5\text{h}$ (gợi ý khung giờ Power Nap tốt nhất).
  * *Melatonin Window (Giờ vàng đi ngủ)*: $\bar{T}_{\text{wake}} + 15\text{h} \to +16\text{h}$ (tự động điều chỉnh sớm hơn nếu $SD_t$ cao).
* **Liên kết Smart Alarm với Nợ ngủ**:
  * Nếu $SD_t < 4\text{h}$: Cửa sổ đánh thức nhẹ (Smart Wake Window) giữ nguyên **30 phút** trước giờ hẹn.
  * Nếu $SD_t \ge 5\text{h}$: Người dùng đang kiệt sức! Hệ thống tự động thu hẹp cửa sổ đánh thức xuống **10 phút** (hoặc tắt đánh thức sớm) để ưu tiên từng phút ngủ quý giá cho cơ thể phục hồi.

---

## 3. CẤU TRÚC DATABASE CLEAN-ROOM (ROOM / SQLITE)

Loại bỏ hoàn toàn các bảng liên quan đến Cloud, Mailing List, Billing hay Log gửi server. Bổ sung 2 bảng chuyên dụng cho **Sleep Debt Engine** (tổng cộng **10 bảng** gọn, chuẩn hóa):

|  STT  | Tên Bảng (Room Entity)           | Trường Dữ Liệu Chính                                                                                                                                                                                                                                 | Mục đích                                                                                                                       |
| :---: | :------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------- |
|   1   | `UserSleepProfile` *(Mới)*       | `id`, `ageGroup`, `targetSleepNeedMinutes`, `calibratedSleepNeedMinutes`, `autoCalibrateEnabled`, `typicalWakeMinutesOfDay`, `decayLambda`                                                                                                           | Lưu cấu hình Nhu cầu ngủ cá nhân và thông số hiệu chuẩn Process C.                                                             |
|   2   | `DailySleepDebtSnapshot` *(Mới)* | `dateEpochDay` (PK), `totalSleepMinutes`, `napMinutes`, `effectiveSleepMinutes`, `dailyDeltaMinutes`, `cumulativeDebtMinutes`, `debtStatusLevel`                                                                                                     | Lưu kết quả Nợ ngủ từng ngày để vẽ biểu đồ 14 ngày / 3 tháng cực nhanh mà không cần tính lại từ đầu.                           |
|   3   | `SleepSession`                   | `id` (PK), `referenceDate`, `sessionType` (`NIGHT` \| `NAP`), `startTime`, `endTime`, `asleepTime`, `wakeUpTime`, `awakeDurationMs`, `sleepDurationMs`, `sleepQualityFactor`, `sleepRating` (1–5), `trackingMode` (`SONAR` \| `PASSIVE` \| `MANUAL`) | Ghi nhận cả giấc ngủ đêm lẫn giấc ngủ trưa (`NAP`), kèm chế độ đo.                                                             |
|   4   | `SleepEpochData`                 | `id` (PK), `sessionId` (FK), `timestamp`, `stage` (`AWAKE`/`LIGHT`/`DEEP`/`REM`), `motionIntensity`, `breathingRateBpm`, `confidence`                                                                                                                | Thay thế mảng blob khó truy vấn bằng bảng/chuỗi nén theo Epoch 30 giây hoặc giữ BLOB nén Protobuf/Gzip để vẽ đồ thị siêu mượt. |
|   5   | `SleepNote`                      | `id` (PK), `name`, `iconRes`, `impactType` (`POSITIVE` \| `NEGATIVE` \| `NEUTRAL`), `isDefault`                                                                                                                                                      | Thẻ thói quen trước khi ngủ (Cà phê sau 14h, Tập thể dục, Đọc sách, Stress...).                                                |
|   6   | `SessionNoteCrossRef`            | `sessionId`, `noteId`                                                                                                                                                                                                                                | Quan hệ N–N giữa phiên ngủ và yếu tố ảnh hưởng (để thống kê thói quen nào làm tăng nợ ngủ).                                    |
|   7   | `MoodEntry`                      | `id` (PK), `sessionId` (FK), `date`, `morningEnergyScore` (1–5), `moodType`                                                                                                                                                                          | Dùng trực tiếp làm tín hiệu phản hồi (feedback loop) cho thuật toán hiệu chỉnh `calibratedSleepNeed`.                          |
|   8   | `DreamJournal`                   | `id` (PK), `sessionId` (FK), `date`, `content`, `tagsBitmask`                                                                                                                                                                                        | Nhật ký giấc mơ hoàn toàn riêng tư.                                                                                            |
|   9   | `SoundEvent`                     | `id` (PK), `sessionId` (FK), `timestampOffsetSec`, `durationSec`, `category` (`SNORING`, `VOICE`, `BODY`, `NOISE`,...), `confidence`, `decibelPeak`, `localAudioPath` (nullable)                                                                     | Lưu sự kiện âm thanh từ YAMNet. Nếu user không bật ghi âm thì `localAudioPath = null`.                                         |
|  10   | `AlarmConfig`                    | `id` (PK), `hour`, `minute`, `daysOfWeekBitmask`, `isEnabled`, `smartWindowMinutes`, `debtAwareOverride`, `ringtoneUri`, `gentleFadeSec`, `snoozeGestureEnabled`                                                                                     | Cấu hình báo thức thông minh kết nối với Sleep Debt Engine.                                                                    |

---

## 4. LÕI XỬ LÝ SONAR SIÊU ÂM (C++ NDK + OBOE + I/Q DEMODULATION)

Phân tích ban đầu của bạn có nhắc đến việc chạy `kissfft` trên khung 4096 mẫu. Tuy nhiên, nếu làm chuẩn học thuật (như các bài báo *SonarBeat* / *ApneaApp* trên smartphone), chỉ nhìn phổ FFT 4096 mẫu sẽ gặp **2 vấn đề lớn**: độ phân giải thời gian chậm và khó tách sóng phản xạ yếu khỏi sóng đi thẳng từ loa sang mic (Direct Path Leakage).

Dưới đây là **Pipeline DSP C++ tối ưu (giảm tải CPU tới 90% cho điện thoại chạy suốt 8 tiếng)**:

```
[Speaker: Oboe Out] ──► Phát CW 18.75 kHz (căn đúng FFT Bin: 1600 * 48000 / 4096)
                                │
             (Sóng dội ngực người dùng + Sóng trực tiếp từ loa)
                                ▼
[Mic: Oboe In (48kHz Float32, AudioSource::Unprocessed)]
                                │
                                ▼
1. Coherent I/Q Demodulation (C++):
   - Nhân buffer thu r(t) với cos(2π·f_c·t) ──► Nhánh I (In-phase)
   - Nhân buffer thu r(t) với -sin(2π·f_c·t) ──► Nhánh Q (Quadrature)
                                │
                                ▼
2. Low-pass Filter & Decimation (Downsample 48 kHz ──► 100 Hz):
   - Loại bỏ hoàn toàn sóng mang 18.75 kHz và tiếng ồn môi trường.
   - Giảm tốc độ dữ liệu từ 48.000 mẫu/s xuống còn 100 mẫu/s (siêu nhẹ!).
                                │
                                ▼
3. Static Vector Cancellation (Triệt tiêu sóng trực tiếp & tường cố định):
   - Phản xạ từ tường và sóng đi thẳng từ loa -> mic là thành phần tĩnh (DC offset phức).
   - Dùng Exponential Moving Average (cửa sổ ~15s) trừ đi thành phần tĩnh:
     I_dyn(t) = I(t) - EMA(I),   Q_dyn(t) = Q(t) - EMA(Q)
                                │
                                ▼
4. Phase Extraction & Unwrapping:
   - Tính pha dịch chuyển: φ(t) = atan2(Q_dyn(t), I_dyn(t))
   - Mở pha (Phase Unwrap) để tránh nhảy bậc ±π khi ngực di chuyển > ~9mm.
                                │
          ┌─────────────────────┴─────────────────────┐
          ▼                                           ▼
5a. Nhánh Thở & Ngủ (Bandpass 0.1 - 0.5 Hz)   5b. Nhánh Cử động / Vẫy tay (Highpass > 0.8 Hz)
   - Đếm nhịp thở (6 - 30 nhịp/phút).            - Phát hiện trở mình (Gross Movement).
   - Tính biến thiên nhịp thở (BRV)              - Khi báo thức reo: nếu năng lượng Doppler
     để phân loại Light / Deep / REM.              tăng đột biến > ngưỡng -> Kích hoạt Snooze!
```

### Tính năng "Kiểm tra Phần cứng 3 giây" (Hardware Self-Calibration):
Rất nhiều điện thoại Android tầm trung/giá rẻ có màng loa hoặc mic cắt tần số trên 17 kHz, hoặc firmware tự động bật lọc ồn phần cứng.
* Trước lần tracking đầu tiên, app chạy **Calibration Test**: phát xung 18.75 kHz trong 2 giây ở âm lượng 30–50% và đo tỷ số tín hiệu trên nhiễu (SNR) tại bin 1600.
* Nếu SNR tốt $\rightarrow$ Kích hoạt **Sonar Mode (Contactless)**.
* Nếu phần cứng máy không đáp ứng (SNR quá thấp) $\rightarrow$ Thông báo minh bạch cho người dùng và tự động chuyển sang **Passive Mode** (dùng Gia tốc kế đặt trên nệm + Phân tích năng lượng âm thanh thụ động) để app **chạy tốt trên 100% thiết bị Android**.

---

## 5. NHẬN DIỆN ÂM THANH BAN ĐÊM TIẾT KIỆM PIN (ENERGY-GATED YAMNET)

Chạy mạng neural YAMNet liên tục suốt 8 tiếng sẽ làm nóng máy và hao pin nhanh. Giải pháp kỹ thuật tối ưu:
1. Dùng **Google YAMNet chính chủ** (`yamnet.tflite`, ~3.7 MB, giấy phép Apache 2.0, hỗ trợ 521 lớp AudioSet). Tạo bảng ánh xạ (Mapping Table) từ 521 lớp của Google sang các nhóm sự kiện giấc ngủ (`Snoring`, `Cough`, `Speech/Sleep Talking`, `Movement/Rustling`, `Ambient Noise`...).
2. **Cơ chế Energy-Gated Ring Buffer**:
   * Luồng audio chạy nền luôn duy trì một **Ring Buffer 1.5 giây** trên RAM (tốn gần như 0% CPU).
   * Tính RMS (mức decibel tương đối) theo thời gian thực và cập nhật nền ồn môi trường (Adaptive Noise Floor, ví dụ tiếng quạt máy/điều hòa chạy đều).
   * **Chỉ đánh thức TFLite Interpreter** để phân loại khi âm thanh đột ngột vượt `Noise Floor + 10 dB`! Nhờ vậy 90% thời gian đêm yên tĩnh, CPU hoàn toàn nghỉ ngơi ở luồng AI.

---

## 6. XỬ LÝ QUYỀN & CHÍNH SÁCH ANDROID HIỆN ĐẠI (API 34–37)

Trên các phiên bản Android mới (Android 14, 15, 16, 17), Google siết chặt chạy ngầm. Để app không bao giờ bị hệ thống giết giữa đêm hoặc crash `SecurityException`:

1. **Quyền Manifest chuẩn**:
   * `RECORD_AUDIO` (Runtime permission).
   * `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_MICROPHONE` (Bắt buộc khai báo `android:foregroundServiceType="microphone"` cho `NightTimeService`).
   * `POST_NOTIFICATIONS` (Android 13+ để hiển thị Notification trạng thái đang theo dõi giấc ngủ).
   * `WAKE_LOCK` (`PARTIAL_WAKE_LOCK` giữ CPU thức ở mức thấp nhất).
   * `USE_EXACT_ALARM` (Dành cho ứng dụng có chức năng đồng hồ/báo thức chính thức, được cấp mặc định mà không bắt user vào cài đặt bật tay như `SCHEDULE_EXACT_ALARM` trên Android 14+, kết hợp kiểm tra `canScheduleExactAlarms()` dự phòng).
   * `RECEIVE_BOOT_COMPLETED` (Đặt lại lịch báo thức nếu máy tự khởi động lại trong đêm).
   * **Tuyệt đối không khai báo `INTERNET`**.
2. **Quy tắc khởi chạy Microphone FGS**:
   * Android 14+ cấm khởi chạy `microphone` Foreground Service từ background. Do đó, `NightTimeService` **chỉ được gọi `startForegroundService()` khi người dùng chủ động nhấn nút "Bắt đầu ngủ" trên giao diện UI đang mở** (hoặc từ Shortcut/Tile có tương tác trực tiếp).
3. **Chống bị OEM giết ngầm (Xiaomi/Oppo/Samsung/Vivo)**:
   * Tích hợp một card hướng dẫn thông minh dựa trên `Build.MANUFACTURER`, dẫn thẳng bằng `Intent` tới màn hình *Tắt Tối ưu hóa Pin (Battery Optimization Exemption)* cho máy đó.

---

## 7. LỘ TRÌNH XÂY DỰNG CHI TIẾT (4 PHASES / SPRINTS)

Để tránh ngợp khi làm một mình và luôn có bản chạy được sau mỗi chặng:

### Phase 1: Nền tảng, Database, Sleep Debt Engine & Smart Alarm (Tuần 1–2)
* Khởi tạo project Android (Kotlin + Jetpack Compose + Hilt + Room).
* Implement trọn vẹn **10 bảng Room DB** và **Sleep Debt Engine** (tính $E_i$, rolling 14 ngày $w_k$, Auto-Calibration, Process C forecast).
* Viết **Unit Test kỹ lưỡng** cho toàn bộ toán học Nợ ngủ (test các case: thiếu ngủ 14 ngày liên tiếp, ngủ bù cuối tuần, Power Nap, v.v.).
* Làm chức năng ghi nhận thủ công + Báo thức chính xác (`AlarmManager` + `FullScreenIntent` khi chuông reo + Gentle Volume Fade-in).

### Phase 2: Passive Tracking, Sleep Staging & Biểu đồ Compose (Tuần 3–4)
* Xây dựng `NightTimeService` (Foreground Service `microphone` + `WakeLock`).
* Viết bộ thu thập chuyển động thụ động (Accelerometer trên nệm + Ngưỡng năng lượng âm thanh) để phân loại sơ bộ `AWAKE` / `LIGHT` / `DEEP` theo từng Epoch 30 giây.
* Thiết kế UI trọng tâm xoay quanh **Nợ ngủ**:
  * Đồng hồ/Vòng tròn Nợ ngủ hiện tại & Trạng thái năng lượng theo giờ (Process C).
  * Biểu đồ Hypnogram (các giai đoạn ngủ trong đêm) + Biểu đồ lịch sử Nợ ngủ 14 ngày.
  * Tích hợp **Local JSON Export / Import**.

### Phase 3: Lõi Sonar Siêu âm C++ (Tuần 5–7)
* Tích hợp CMake, NDK, Google **Oboe** và **KissFFT**.
* Code module phát sóng CW 18.75 kHz + thu `AudioSource::Unprocessed`.
* Code Pipeline **I/Q Demodulation $\rightarrow$ Downsample 100Hz $\rightarrow$ Static Vector Removal $\rightarrow$ Phase Unwrap $\rightarrow$ Bandpass 0.1–0.5 Hz**.
* Làm màn hình **Calibration Test 3 giây** và Wave Debugger (vẽ sóng nhịp thở real-time để bạn test trực tiếp bằng mắt khi đặt điện thoại cạnh giường).
* Tích hợp Gesture Snooze (vẫy tay trên không để hoãn báo thức).

### Phase 4: Energy-Gated YAMNet & Đóng gói Cộng đồng (Tuần 8–9)
* Tích hợp Google `yamnet.tflite` + Ring Buffer ngưỡng năng lượng để nhận diện tiếng ngáy, ho, nói mớ.
* Tối ưu pin đo qua đêm (kiểm tra bằng Android Profiler / `BatteryHistorian`, mục tiêu tiêu hao $< 8-12\%$ pin nếu không cắm sạc, khuyến nghị cắm sạc khi dùng Sonar).
* Viết `README.md`, `PRIVACY.md`, chọn giấy phép nguồn mở (**GPL-3.0** hoặc **Apache 2.0**) và chuẩn bị cấu trúc chuẩn để đưa lên GitHub / F-Droid.

---

### Bạn muốn chúng ta bắt đầu triển khai bước nào ngay bây giờ?
Tôi có thể viết code mẫu chi tiết cho bạn ngay lập tức theo từng phần:
1. **Code hoàn chỉnh (Kotlin + Unit Tests) cho `SleepDebtEngine`** (bao gồm công thức 14 ngày, hiệu chỉnh $N_{\text{personal}}$, Power Nap và dự báo Process C) + **Room Entities & DAOs**.
2. **Code C++ NDK (Oboe + I/Q Demodulation + Phase Unwrapping)** cho lõi Sonar siêu âm.
3. **Code `NightTimeService` & Exact Smart Alarm** chuẩn Android 14/15/16+.

Bạn chọn mục nào trước (hoặc muốn đi theo đúng thứ tự Phase 1)?