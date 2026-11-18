package org.opensleep.app.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun buildRequestIgnoreBatteryIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun getManufacturerGuidance(): String {
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" ->
                "Trên MIUI/HyperOS: Vào Thông tin ứng dụng -> Tiết kiệm pin -> Chọn 'Không hạn chế' và bật 'Tự khởi chạy'."
            "samsung" ->
                "Trên OneUI: Vào Pin -> Giới hạn sử dụng nền -> Thêm OpenSleep vào 'Ứng dụng không bao giờ ngủ'."
            "oppo", "realme", "oneplus" ->
                "Trên ColorOS: Vào Quản lý pin -> Cho phép hoạt động nền & Khởi chạy tự động."
            else ->
                "Hãy chọn 'Không giới hạn' (Unrestricted) trong phần cài đặt Pin để ứng dụng không bị tắt lúc nửa đêm."
        }
    }
}
