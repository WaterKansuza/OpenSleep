# OpenSleep R8 / ProGuard Rules for Release Builds

# 1. Keep JNI native bridge methods intact
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.opensleep.app.service.tracking.SonarNativeBridge {
    *;
}

# 2. Kotlinx Serialization (Backup JSON DTOs)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupPayload** { *; }
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupSessionDto** { *; }
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupEpochDto** { *; }
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupSoundEventDto** { *; }
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupMoodDto** { *; }
-keep,includedescriptorclasses class org.opensleep.app.core.util.BackupDreamDto** { *; }
-keep,includedescriptorclasses class org.opensleep.app.data.local.db.** { *; }

# 3. Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# 4. LiteRT / TensorFlow Lite (YAMNet offline inference)
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# 5. Kotlin Coroutines & Hilt
-dontwarn kotlinx.coroutines.**
-dontwarn javax.annotation.**