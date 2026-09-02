package ai.deepcode.android.ui.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutomationScheduler(private val context: Context) {
    private val workManager: WorkManager? = try {
        WorkManager.getInstance(context)
    } catch (e: Exception) {
        AppLogger.e("AutomationScheduler", "WorkManager init failed", e)
        null
    }

    fun schedule(automation: AutomationEntity) {
        try {
            val delayMs = computeDelayMs(automation.cronExpression)
            val nextRun = System.currentTimeMillis() + delayMs

            // Use AlarmManager for <1min delays (more reliable on MIUI) or when WorkManager is unavailable
            // Check for exact alarm permission on Android 12+
            val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

            if (delayMs < 60000L || workManager == null) {
                if (canUseExactAlarm || workManager == null) {
                    scheduleWithAlarm(automation.id, delayMs)
                    AppLogger.i("AutomationScheduler", "Alarm scheduled '${automation.name}' in ${delayMs}ms")
                } else {
                    AppLogger.w("AutomationScheduler", "Exact alarm permission not granted, falling back to WorkManager for '${automation.name}'")
                    scheduleWithWorkManager(automation, delayMs)
                }
            } else {
                scheduleWithWorkManager(automation, delayMs)
            }

            // Sync nextRunAt in DB so UI shows accurate countdown
            val updated = automation.copy(nextRunAt = nextRun)
            val repo = AutomationRepository(context)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try { repo.insertAutomation(updated) } catch (_: Exception) {}
            }

            AppLogger.i("AutomationScheduler", "Scheduled '${automation.name}' trigger at ${delayMs}ms (cron=${automation.cronExpression})")
        } catch (e: Exception) {
            AppLogger.e("AutomationScheduler", "schedule failed for '${automation.name}'", e)
        }
    }

    private fun scheduleWithWorkManager(automation: AutomationEntity, delayMs: Long) {
        val data = Data.Builder()
            .putString("automation_id", automation.id)
            .putString("cron", automation.cronExpression)
            .build()

        val request = OneTimeWorkRequestBuilder<AutomationRunner>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(automation.id)
            .build()

        workManager?.enqueueUniqueWork(
            automation.id,
            ExistingWorkPolicy.REPLACE,
            request
        )
        AppLogger.i("AutomationScheduler", "WorkManager scheduled '${automation.name}' with delay ${delayMs / 60000}min")
    }

    private fun scheduleWithAlarm(automationId: String, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            putExtra("automation_id", automationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            AppLogger.i("AutomationScheduler", "Exact alarm set for $automationId in ${delayMs}ms")
        } catch (e: SecurityException) {
            AppLogger.w("AutomationScheduler", "setExact not allowed, using setWindow: ${e.message}")
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5000, pendingIntent)
        }
    }

    fun cancel(id: String) {
        try {
            workManager?.cancelUniqueWork(id)
        } catch (e: Exception) {
            AppLogger.e("AutomationScheduler", "cancel failed for $id", e)
        }
        // Cancel any AlarmManager alarms
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            putExtra("automation_id", id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()
        AppLogger.i("AutomationScheduler", "Cancelled automation $id")
    }

    companion object {
        fun computeDelayMs(cron: String): Long {
            val cronTrimmed = cron.trim()
            
            // Handle special cron shortcuts
            return when (cronTrimmed) {
                "@yearly", "@annually" -> computeDelayForCron("0 0 1 1 *")
                "@monthly" -> computeDelayForCron("0 0 1 * *")
                "@weekly" -> computeDelayForCron("0 0 * * 0")
                "@daily", "@midnight" -> computeDelayForCron("0 0 * * *")
                "@hourly" -> computeDelayForCron("0 * * * *")
                else -> computeDelayForCron(cronTrimmed)
            }
        }

        private fun computeDelayForCron(cron: String): Long {
            val parts = cron.trim().split("\\s+".toRegex())

            // Detect 6-field cron (with seconds) vs 5-field
            val hasSeconds = parts.size == 6
            val idxSec = if (hasSeconds) 0 else -1
            val idxMin = if (hasSeconds) 1 else 0
            val idxHour = if (hasSeconds) 2 else 1
            val idxDom = if (hasSeconds) 3 else 2
            val idxMonth = if (hasSeconds) 4 else 3
            val idxDow = if (hasSeconds) 5 else 4

            val cronSec = if (hasSeconds) parts[0] else "*"
            val cronMin = parts.getOrElse(idxMin) { "*" }
            val cronHour = parts.getOrElse(idxHour) { "*" }
            val cronDom = parts.getOrElse(idxDom) { "*" }
            val cronMonth = parts.getOrElse(idxMonth) { "*" }
            val cronDow = parts.getOrElse(idxDow) { "*" }

            val now = Calendar.getInstance()

            // Sub-minute: `*/N * * * * *` → schedule every N seconds
            if (hasSeconds && cronSec.startsWith("*/")) {
                val step = cronSec.removePrefix("*/").toIntOrNull()
                if (step != null && step in 1..59) {
                    val currentSec = now.get(Calendar.SECOND)
                    val currentMs = now.get(Calendar.MILLISECOND)
                    val secsUntilNext = step - (currentSec % step)
                    return ((secsUntilNext * 1000L) - currentMs).coerceAtLeast(100L)
                }
            }

            // Exact second: `30 * * * * *` → schedule at that second of next matching minute
            if (hasSeconds && cronSec != "*") {
                val targetSec = cronSec.toIntOrNull()
                if (targetSec != null && targetSec in 0..59) {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = now.timeInMillis
                        set(Calendar.SECOND, targetSec)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.timeInMillis <= now.timeInMillis) {
                        cal.add(Calendar.MINUTE, 1)
                    }
                    val maxIter = 525600
                    for (i in 0 until maxIter) {
                        if (fieldMatches(cronMin, cal.get(Calendar.MINUTE), 0, 59) &&
                            fieldMatches(cronHour, cal.get(Calendar.HOUR_OF_DAY), 0, 23) &&
                            fieldMatches(cronDom, cal.get(Calendar.DAY_OF_MONTH), 1, 31) &&
                            fieldMatches(cronMonth, cal.get(Calendar.MONTH) + 1, 1, 12) &&
                            dowMatches(cronDow, cal.get(Calendar.DAY_OF_WEEK))
                        ) {
                            return (cal.timeInMillis - now.timeInMillis).coerceAtLeast(100L)
                        }
                        cal.add(Calendar.MINUTE, 1)
                    }
                }
            }

            // Standard 5-field (or 6-field with seconds=*): start from next full minute
            val cal = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val maxIterations = 525600 // 1 year
            for (i in 0 until maxIterations) {
                if (fieldMatches(cronMin, cal.get(Calendar.MINUTE), 0, 59) &&
                    fieldMatches(cronHour, cal.get(Calendar.HOUR_OF_DAY), 0, 23) &&
                    fieldMatches(cronDom, cal.get(Calendar.DAY_OF_MONTH), 1, 31) &&
                    fieldMatches(cronMonth, cal.get(Calendar.MONTH) + 1, 1, 12) &&
                    dowMatches(cronDow, cal.get(Calendar.DAY_OF_WEEK))
                ) {
                    val delay = cal.timeInMillis - now.timeInMillis
                    return if (delay > 0) delay else 60000L
                }
                cal.add(Calendar.MINUTE, 1)
            }

            return 60000L // fallback
        }

        private fun fieldMatches(field: String, value: Int, min: Int, max: Int): Boolean {
            if (field == "*") return true
            if (field.startsWith("*/")) {
                val step = field.removePrefix("*/").toIntOrNull() ?: return false
                if (step <= 0) return false
                return value % step == 0
            }
            if (field.all { it.isDigit() }) {
                val num = field.toInt()
                return num in min..max && value == num
            }
            if (field.contains(",")) {
                return field.split(",").any { f -> fieldMatches(f.trim(), value, min, max) }
            }
            if (field.contains("-")) {
                val range = field.split("-")
                val start = range.getOrNull(0)?.toIntOrNull() ?: return false
                val end = range.getOrNull(1)?.toIntOrNull() ?: return false
                return value in start..end
            }
            return false
        }

        private fun dowMatches(field: String, calDow: Int): Boolean {
            if (field == "*") return true
            val cronDow = (calDow + 6) % 7 // Calendar 1=Sun..7=Sat → 0=Sun..6=Sat
            return fieldMatches(field, cronDow, 0, 6)
        }

        suspend fun scheduleNext(context: Context, automationId: String, cron: String) {
            try {
                val repo = AutomationRepository(context)
                val automation = repo.getAutomationById(automationId) ?: return
                if (!automation.isEnabled) return
                AutomationScheduler(context).schedule(automation)
            } catch (e: Exception) {
                AppLogger.e("AutomationScheduler", "scheduleNext failed for $automationId", e)
            }
        }

        suspend fun rescheduleAll(context: Context) {
            try {
                val repository = AutomationRepository(context)
                val scheduler = AutomationScheduler(context)
                val automations = repository.getAllAutomations()
                var count = 0
                for (automation in automations) {
                    if (automation.isEnabled) {
                        scheduler.schedule(automation)
                        count++
                    }
                }
                AppLogger.i("AutomationScheduler", "Rescheduled $count enabled automations")
            } catch (e: Exception) {
                AppLogger.e("AutomationScheduler", "Failed to reschedule automations", e)
            }
        }
    }
}
