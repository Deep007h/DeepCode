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

    fun schedule(automation: AutomationEntity, forceRecalculate: Boolean = false) {
        try {
            val now = System.currentTimeMillis()
            val nextRun = if (!forceRecalculate && automation.nextRunAt > now) {
                automation.nextRunAt
            } else {
                now + computeDelayMs(automation.cronExpression, now)
            }
            val delayMs = (nextRun - now).coerceAtLeast(100L)

            // Primary: Use AlarmManager setExactAndAllowWhileIdle so the device wakes up even when app is killed
            scheduleWithAlarm(automation.id, nextRun)

            // Keep WorkManager as backup if exact alarm permission is not granted
            val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true

            if (!canUseExactAlarm && workManager != null) {
                scheduleWithWorkManager(automation, delayMs)
            }

            // Sync nextRunAt in DB so UI shows accurate countdown
            val updated = automation.copy(nextRunAt = nextRun)
            val repo = AutomationRepository(context)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try { repo.insertAutomation(updated) } catch (_: Exception) {}
            }

            AppLogger.i("AutomationScheduler", "Scheduled '${automation.name}' trigger in ${delayMs}ms at $nextRun (cron=${automation.cronExpression})")
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
        AppLogger.i("AutomationScheduler", "WorkManager fallback scheduled '${automation.name}' with delay ${delayMs / 60000}min")
    }

    private fun scheduleWithAlarm(automationId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = "ai.deepcode.android.action.TRIGGER_AUTOMATION"
            putExtra("automation_id", automationId)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            AppLogger.i("AutomationScheduler", "Exact alarm set for $automationId at $triggerAtMillis")
        } catch (e: SecurityException) {
            AppLogger.w("AutomationScheduler", "setExact not allowed, using setWindow: ${e.message}")
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 5000, pendingIntent)
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
            action = "ai.deepcode.android.action.TRIGGER_AUTOMATION"
            putExtra("automation_id", id)
            setPackage(context.packageName)
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
        fun computeNextRunAt(cron: String, fromTime: Long = System.currentTimeMillis()): Long {
            return fromTime + computeDelayMs(cron, fromTime)
        }

        fun computeDelayMs(cron: String, fromTime: Long = System.currentTimeMillis()): Long {
            val cronTrimmed = cron.trim()
            
            // Handle special cron shortcuts
            return when (cronTrimmed) {
                "@yearly", "@annually" -> computeDelayForCron("0 0 1 1 *", fromTime)
                "@monthly" -> computeDelayForCron("0 0 1 * *", fromTime)
                "@weekly" -> computeDelayForCron("0 0 * * 0", fromTime)
                "@daily", "@midnight" -> computeDelayForCron("0 0 * * *", fromTime)
                "@hourly" -> computeDelayForCron("0 * * * *", fromTime)
                else -> computeDelayForCron(cronTrimmed, fromTime)
            }
        }

        private fun computeDelayForCron(cron: String, fromTime: Long): Long {
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

            val now = Calendar.getInstance().apply { timeInMillis = fromTime }

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
            if (field.contains(",")) {
                return field.split(",").any { f -> fieldMatches(f.trim(), value, min, max) }
            }
            if (field.contains("/")) {
                val parts = field.split("/")
                val step = parts.getOrNull(1)?.toIntOrNull() ?: return false
                if (step <= 0) return false
                val rangePart = parts[0]
                val (rangeMin, rangeMax) = if (rangePart == "*" || rangePart.isEmpty()) {
                    min to max
                } else if (rangePart.contains("-")) {
                    val rParts = rangePart.split("-")
                    (rParts.getOrNull(0)?.toIntOrNull() ?: min) to (rParts.getOrNull(1)?.toIntOrNull() ?: max)
                } else {
                    (rangePart.toIntOrNull() ?: min) to max
                }
                if (value !in rangeMin..rangeMax) return false
                return (value - rangeMin) % step == 0
            }
            if (field.contains("-")) {
                val range = field.split("-")
                val start = range.getOrNull(0)?.toIntOrNull() ?: return false
                val end = range.getOrNull(1)?.toIntOrNull() ?: return false
                return value in start..end
            }
            if (field.all { it.isDigit() }) {
                val num = field.toInt()
                return num in min..max && value == num
            }
            return false
        }

        private fun dowMatches(field: String, calDow: Int): Boolean {
            if (field == "*") return true
            val normalized = field.uppercase()
                .replace("SUN", "0")
                .replace("MON", "1")
                .replace("TUE", "2")
                .replace("WED", "3")
                .replace("THU", "4")
                .replace("FRI", "5")
                .replace("SAT", "6")
            val cronDow = (calDow + 6) % 7 // Calendar 1=Sun..7=Sat -> 0=Sun..6=Sat
            if (cronDow == 0 && (fieldMatches(normalized, 0, 0, 7) || fieldMatches(normalized, 7, 0, 7))) return true
            return fieldMatches(normalized, cronDow, 0, 7)
        }

        suspend fun scheduleNext(context: Context, automationId: String, cron: String) {
            try {
                val repo = AutomationRepository(context)
                val automation = repo.getAutomationById(automationId) ?: return
                if (!automation.isEnabled) return
                AutomationScheduler(context).schedule(automation, forceRecalculate = true)
            } catch (e: Exception) {
                AppLogger.e("AutomationScheduler", "scheduleNext failed for $automationId", e)
            }
        }

        fun triggerImmediately(context: Context, automationId: String) {
            val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
                action = "ai.deepcode.android.action.TRIGGER_AUTOMATION"
                putExtra("automation_id", automationId)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        suspend fun rescheduleAll(context: Context) {
            try {
                val repository = AutomationRepository(context)
                val scheduler = AutomationScheduler(context)
                val automations = repository.getAllAutomations()
                val now = System.currentTimeMillis()
                var count = 0
                for (automation in automations) {
                    if (automation.isEnabled) {
                        if (automation.nextRunAt in 1..now) {
                            AppLogger.i("AutomationScheduler", "Automation '${automation.name}' was overdue (nextRun=${automation.nextRunAt}, now=$now). Triggering immediately.")
                            triggerImmediately(context, automation.id)
                        } else {
                            scheduler.schedule(automation, forceRecalculate = false)
                        }
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
