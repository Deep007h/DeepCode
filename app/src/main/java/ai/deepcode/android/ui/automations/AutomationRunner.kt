package ai.deepcode.android.ui.automations

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import ai.deepcode.android.agent.AgentEngine
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.data.local.SessionEntity
import ai.deepcode.android.service.schedule.SimpleAutomationRunner
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class AutomationRunner(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val automationId = inputData.getString("automation_id") ?: return Result.failure()
        AppLogger.i("AutomationRunner", "WorkManager doWork running for: $automationId")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val foregroundInfo = createForegroundInfo(applicationContext, automationId)
                setForegroundAsync(foregroundInfo)
            } catch (e: Exception) {
                AppLogger.w("AutomationRunner", "Could not set foreground async: ${e.message}")
            }
        }

        val success = executeAutomation(applicationContext, automationId)
        return if (success) Result.success() else Result.failure()
    }

    companion object {
        suspend fun executeAutomation(context: Context, automationId: String): Boolean {
            AppLogger.i("AutomationRunner", "executeAutomation started for: $automationId")
            val automationRepository = AutomationRepository(context)
            val database = AppDatabase.getDatabase(context)
            val sessionDao = database.sessionDao()

            try {
                val rule = automationRepository.getAutomationById(automationId) ?: run {
                    AppLogger.w("AutomationRunner", "Automation not found: $automationId")
                    return false
                }

                if (!rule.isEnabled) {
                    AppLogger.i("AutomationRunner", "Rule '${rule.name}' is disabled, skipping execution")
                    return true
                }

                // 1. Resolve or create the single dedicated ChatSession for this automation task
                val existingSessionId = rule.getEffectiveChatSessionId()
                val existingSession = if (existingSessionId != null) {
                    sessionDao.getSessionById(existingSessionId)
                } else null

                val targetSessionId: String
                if (existingSession != null) {
                    targetSessionId = existingSession.id
                    AppLogger.i("AutomationRunner", "Reusing existing chat session $targetSessionId for task '${rule.name}'")
                } else {
                    val newSessionId = UUID.randomUUID().toString()
                    val newSession = SessionEntity(
                        id = newSessionId,
                        title = "🤖 ${rule.name}",
                        createdAt = System.currentTimeMillis()
                    )
                    sessionDao.insertSession(newSession)
                    targetSessionId = newSessionId
                    AppLogger.i("AutomationRunner", "Created new dedicated chat session $targetSessionId for task '${rule.name}'")

                    // Persist linked chatSessionId to the automation entity and configJson
                    val configObj = try {
                        Gson().fromJson(rule.configJson, JsonObject::class.java)
                    } catch (_: Exception) {
                        JsonObject()
                    }.apply {
                        addProperty("chat_session_id", targetSessionId)
                    }
                    val updatedRuleWithSession = rule.copy(
                        chatSessionId = targetSessionId,
                        configJson = Gson().toJson(configObj)
                    )
                    automationRepository.insertAutomation(updatedRuleWithSession)
                }

                // 2. Calculate dynamic date, time, and system data by itself
                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm a", Locale.getDefault())
                val formattedDateTime = dateFormat.format(Date(now))
                val timezone = TimeZone.getDefault().id

                val batteryStatus = getBatteryStatus(context)
                val networkStatus = getNetworkStatus(context)

                val configObj = try {
                    Gson().fromJson(rule.configJson, JsonObject::class.java)
                } catch (_: Exception) {
                    JsonObject()
                }
                val rawPrompt = configObj.get("action_prompt")?.asString?.takeIf { it.isNotBlank() }
                    ?: "Execute scheduled task: ${rule.name}"
                val telegramChatId = configObj.get("telegram_chat_id")?.asString?.takeIf { it.isNotBlank() }

                val shortDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
                val shortTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
                val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))

                val interpolatedPrompt = rawPrompt
                    .replace("{{datetime}}", formattedDateTime)
                    .replace("{{date}}", shortDate)
                    .replace("{{time}}", shortTime)
                    .replace("{{day}}", dayOfWeek)
                    .replace("{{battery}}", batteryStatus)
                    .replace("{{network}}", networkStatus)
                    .replace("{{timezone}}", timezone)
                    .replace("{{name}}", rule.name)

                val contextualPrompt = buildString {
                    appendLine("⏰ [Scheduled Task: ${rule.name}]")
                    appendLine("Time: $formattedDateTime ($timezone) | Battery: $batteryStatus | Network: $networkStatus")
                    appendLine()
                    append(interpolatedPrompt)
                }

                AppLogger.i("AutomationRunner", "Running agent in session $targetSessionId with prompt: $interpolatedPrompt")

                // 3. Execute through AgentEngine into the single separate chat session
                val engine = AgentEngine(context)
                val responseTokens = StringBuilder()
                try {
                    engine.run(targetSessionId, contextualPrompt).collect { token ->
                        responseTokens.append(token)
                    }
                } catch (e: Exception) {
                    AppLogger.e("AutomationRunner", "AgentEngine execution failed for ${rule.name}", e)
                }

                val finalOutput = responseTokens.toString().trim()
                AppLogger.i("AutomationRunner", "Execution completed in session $targetSessionId (output length: ${finalOutput.length})")

                // 4. Mirror to Telegram if chat ID is configured
                if (!telegramChatId.isNullOrBlank() && finalOutput.isNotEmpty()) {
                    try {
                        SimpleAutomationRunner(context).run(interpolatedPrompt, telegramChatId)
                    } catch (e: Exception) {
                        AppLogger.w("AutomationRunner", "Telegram mirror failed: ${e.message}")
                    }
                }

                // 5. Re-fetch rule, calculate next delay, update lastRunAt and nextRunAt, and schedule next alarm
                val currentRule = automationRepository.getAutomationById(automationId) ?: rule
                if (currentRule.isEnabled) {
                    val nextDelay = AutomationScheduler.computeDelayMs(currentRule.cronExpression)
                    val nextRun = System.currentTimeMillis() + nextDelay
                    val updated = currentRule.copy(
                        lastRunAt = System.currentTimeMillis(),
                        nextRunAt = nextRun,
                        chatSessionId = targetSessionId
                    )
                    automationRepository.insertAutomation(updated)
                    AutomationScheduler(context).schedule(updated, forceRecalculate = true)
                    AppLogger.i("AutomationRunner", "Rescheduled '${rule.name}' for next run in ${nextDelay}ms at $nextRun")
                }

                return true
            } catch (e: Exception) {
                AppLogger.e("AutomationRunner", "Fatal error executing automation $automationId", e)
                // Even on error, try to reschedule to prevent the task from stalling permanently
                try {
                    val currentRule = automationRepository.getAutomationById(automationId)
                    if (currentRule != null && currentRule.isEnabled) {
                        val nextDelay = AutomationScheduler.computeDelayMs(currentRule.cronExpression)
                        val nextRun = System.currentTimeMillis() + nextDelay
                        val updated = currentRule.copy(
                            lastRunAt = System.currentTimeMillis(),
                            nextRunAt = nextRun
                        )
                        automationRepository.insertAutomation(updated)
                        AutomationScheduler(context).schedule(updated, forceRecalculate = true)
                    }
                } catch (_: Exception) {}
                return false
            }
        }

        private fun getBatteryStatus(context: Context): String {
            return try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryIntent = context.registerReceiver(null, filter) ?: return "Unknown"
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                if (pct >= 0) "$pct% (${if (isCharging) "Charging" else "On battery"})" else "Unknown"
            } catch (_: Exception) {
                "Unknown"
            }
        }

        private fun getNetworkStatus(context: Context): String {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val active = cm?.activeNetwork ?: return "Disconnected"
                val caps = cm.getNetworkCapabilities(active) ?: return "Disconnected"
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Connected"
                }
            } catch (_: Exception) {
                "Unknown"
            }
        }

        private suspend fun createForegroundInfo(context: Context, automationId: String): ForegroundInfo {
            val rule = AutomationRepository(context).getAutomationById(automationId)
            val name = rule?.name ?: "Automation Task"
            val notification = AutomationForegroundService.createNotification(context, name)
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
}

