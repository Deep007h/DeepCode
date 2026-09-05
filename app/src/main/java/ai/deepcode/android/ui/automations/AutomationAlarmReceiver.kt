package ai.deepcode.android.ui.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val automationId = intent.getStringExtra("automation_id") ?: return
        val forceRun = intent.getBooleanExtra("force_run", false)
        AppLogger.i("AutomationAlarmReceiver", "Alarm fired for automation: $automationId (forceRun=$forceRun)")

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "deepcode:automation_alarm_$automationId"
        )?.apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes max timeout
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val rule = try {
                    AutomationRepository(context).getAutomationById(automationId)
                } catch (_: Exception) { null }
                val taskName = rule?.name ?: "Automation Task"
                AutomationForegroundService.start(context, taskName)
                AutomationRunner.executeAutomation(context.applicationContext, automationId, forceRun)
            } catch (e: Exception) {
                AppLogger.e("AutomationAlarmReceiver", "Failed to execute automation $automationId", e)
            } finally {
                try {
                    AutomationForegroundService.stop(context)
                } catch (_: Exception) {}
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
