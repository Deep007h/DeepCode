package ai.deepcode.android.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object PermissionHelper {
    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    fun requestExactAlarmPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onResult(true)
            return
        }
        val alarmManager = activity.getSystemService(android.app.AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) {
            onResult(true)
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        activity.startActivity(intent)
        onResult(false)
    }
}