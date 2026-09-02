package ai.deepcode.android.ui.automations

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import ai.deepcode.android.service.schedule.SimpleAutomationRunner
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject

class AutomationRunner(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val automationRepository by lazy { AutomationRepository(applicationContext) }
    private val simpleRunner by lazy { SimpleAutomationRunner(applicationContext) }

    override suspend fun doWork(): Result {
        val automationId = inputData.getString("automation_id") ?: return Result.failure()
        val inputCron = inputData.getString("cron") ?: return Result.failure()

        AppLogger.i("AutomationRunner", "Running automation: $automationId")

        // Start foreground service for long-running operations (AI calls, web search)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundInfo = createForegroundInfo(automationId)
            setForegroundAsync(foregroundInfo)
        }

        try {
            val rule = automationRepository.getAutomationById(automationId) ?: return Result.failure()
            if (!rule.isEnabled) {
                AppLogger.w("AutomationRunner", "Rule disabled, skipping: $automationId")
                return Result.success()
            }

            val configObj = try {
                Gson().fromJson(rule.configJson, JsonObject::class.java)
            } catch (e: Exception) {
                JsonObject()
            }
            val telegramChatId = configObj.get("telegram_chat_id")?.asString?.takeIf { it.isNotBlank() }
            val prompt = configObj.get("action_prompt")?.asString ?: "Execute automation tasks"

            AppLogger.i("AutomationRunner", "Running with prompt: $prompt")
            simpleRunner.run(prompt, telegramChatId)

            // Re-fetch automation in case it was updated during execution
            val currentRule = automationRepository.getAutomationById(automationId) ?: rule
            if (!currentRule.isEnabled) {
                AppLogger.i("AutomationRunner", "Automation disabled after execution, not rescheduling: $automationId")
                return Result.success()
            }

            // Use the UPDATED cron from database, not stale input data
            val nextDelay = AutomationScheduler.computeDelayMs(currentRule.cronExpression)
            val updated = currentRule.copy(
                lastRunAt = System.currentTimeMillis(),
                nextRunAt = System.currentTimeMillis() + nextDelay
            )
            automationRepository.insertAutomation(updated)
            AutomationScheduler(context = applicationContext).schedule(updated)

            AppLogger.i("AutomationRunner", "Completed: ${rule.name}, next run in ${nextDelay}ms (cron=${currentRule.cronExpression})")
            return Result.success()
        } catch (e: Exception) {
            AppLogger.e("AutomationRunner", "Error executing automation: $automationId", e)
            // On failure, try to reschedule using current cron from DB
            val currentRule = automationRepository.getAutomationById(automationId)
            if (currentRule != null && currentRule.isEnabled) {
                val nextDelay = AutomationScheduler.computeDelayMs(currentRule.cronExpression)
                val updated = currentRule.copy(
                    lastRunAt = System.currentTimeMillis(),
                    nextRunAt = System.currentTimeMillis() + nextDelay
                )
                automationRepository.insertAutomation(updated)
                AutomationScheduler.scheduleNext(applicationContext, automationId, currentRule.cronExpression)
            }
            return Result.failure()
        }
    }

    private suspend fun createForegroundInfo(automationId: String): ForegroundInfo {
        val rule = automationRepository.getAutomationById(automationId)
        val name = rule?.name ?: automationId
        val notification = AutomationForegroundService.createNotification(applicationContext, name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                AutomationForegroundService.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(AutomationForegroundService.NOTIFICATION_ID, notification)
        }
    }
}
