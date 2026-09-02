package ai.deepcode.android.ui.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val automationId = intent.getStringExtra("automation_id") ?: return
        AppLogger.i("AutomationAlarmReceiver", "Alarm fired for automation: $automationId")

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repo = AutomationRepository(context)
                val automation = repo.getAutomationById(automationId) ?: return@launch
                if (!automation.isEnabled) return@launch

                val cron = automation.cronExpression

                val data = Data.Builder()
                    .putString("automation_id", automationId)
                    .putString("cron", cron)
                    .build()

                val request = OneTimeWorkRequestBuilder<AutomationRunner>()
                    .setInitialDelay(0, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(automationId)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    automationId,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
                AppLogger.i("AutomationAlarmReceiver", "Enqueued automation: $automationId")
            } catch (e: Exception) {
                AppLogger.e("AutomationAlarmReceiver", "Failed", e)
            }
        }
    }
}
