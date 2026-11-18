package org.opensleep.app.data.local.db

import androidx.room.TypeConverter

enum class AgeGroup(val defaultSleepNeedMinutes: Int) {
    TEEN(540),          // 14-17 tuổi: 9.0h
    YOUNG_ADULT(480),   // 18-25 tuổi: 8.0h
    ADULT(480),         // 26-64 tuổi: 8.0h
    OLDER_ADULT(450)    // 65+ tuổi: 7.5h
}

enum class DebtStatusLevel {
    OPTIMAL,    // < 120 phút (2h)
    MILD,       // 120 - 300 phút (2h - 5h)
    HIGH,       // 300 - 480 phút (5h - 8h)
    CRITICAL    // > 480 phút (> 8h)
}

enum class SessionType {
    NIGHT,
    NAP
}

enum class TrackingMode {
    SONAR,      // Sóng siêu âm 18-20kHz đo nhịp thở + cử động
    PASSIVE,    // Gia tốc kế + năng lượng âm thanh RMS
    MANUAL      // Người dùng tự nhập / chỉnh sửa tay
}

enum class SleepStage {
    AWAKE,
    LIGHT,
    DEEP,
    REM
}

enum class WakeMode {
    NATURAL_NO_ALARM, // Không báo thức: tự thức dậy -> Dữ liệu vàng hiệu chuẩn Nhu cầu ngủ
    SMART_ALARM,      // Dậy trong cửa sổ Smart Wake
    HARD_ALARM        // Bị ép dậy đúng giờ hẹn
}

enum class NoteImpactType {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

enum class SoundCategory {
    SNORING,
    VOICE,
    MOVEMENT,
    BODY,       // Ho, hắt hơi
    NOISE,
    ANIMAL,
    VEHICLE,
    WEATHER,
    UNKNOWN
}

class SleepConverters {
    @TypeConverter fun fromAgeGroup(v: AgeGroup): String = v.name
    @TypeConverter fun toAgeGroup(v: String): AgeGroup = AgeGroup.valueOf(v)

    @TypeConverter fun fromDebtLevel(v: DebtStatusLevel): String = v.name
    @TypeConverter fun toDebtLevel(v: String): DebtStatusLevel = DebtStatusLevel.valueOf(v)

    @TypeConverter fun fromSessionType(v: SessionType): String = v.name
    @TypeConverter fun toSessionType(v: String): SessionType = SessionType.valueOf(v)

    @TypeConverter fun fromTrackingMode(v: TrackingMode): String = v.name
    @TypeConverter fun toTrackingMode(v: String): TrackingMode = TrackingMode.valueOf(v)

    @TypeConverter fun fromSleepStage(v: SleepStage): String = v.name
    @TypeConverter fun toSleepStage(v: String): SleepStage = SleepStage.valueOf(v)

    @TypeConverter fun fromWakeMode(v: WakeMode): String = v.name
    @TypeConverter fun toWakeMode(v: String): WakeMode = WakeMode.valueOf(v)

    @TypeConverter fun fromNoteImpact(v: NoteImpactType): String = v.name
    @TypeConverter fun toNoteImpact(v: String): NoteImpactType = NoteImpactType.valueOf(v)

    @TypeConverter fun fromSoundCategory(v: SoundCategory): String = v.name
    @TypeConverter fun toSoundCategory(v: String): SoundCategory = SoundCategory.valueOf(v)
}
