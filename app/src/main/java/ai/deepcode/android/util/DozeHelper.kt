package ai.deepcode.android.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import ai.deepcode.android.util.AppLogger

object DozeHelper {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: android.app.Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (!isIgnoringBatteryOptimizations(activity)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${activity.packageName}")
            }
            launcher.launch(intent)
            AppLogger.i("DozeHelper", "Requested ignore battery optimizations")
        }
    }

    fun requestIgnoreBatteryOptimizations(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (!isIgnoringBatteryOptimizations(activity)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
            AppLogger.i("DozeHelper", "Requested ignore battery optimizations")
        }
    }

    fun isDeviceIdleMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isDeviceIdleMode
    }

    fun isPowerSaveMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }
}