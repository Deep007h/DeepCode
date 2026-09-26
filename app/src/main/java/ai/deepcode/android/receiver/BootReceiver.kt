package ai.deepcode.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.deepcode.android.ui.automations.*
import ai.deepcode.android.ui.agents.AgentScheduler
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                val action = intent.action ?: "UNKNOWN"
                AppLogger.i("BootReceiver", "Boot action received: $action, rescheduling automations and agents")
                val pendingResult = goAsync()
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                scope.launch {
                    try {
                        AutomationScheduler.rescheduleAll(context)
                        AgentScheduler.rescheduleAll(context)
                    } finally {
                        pendingResult.finish()
                        scope.cancel()
                    }
                }
            }
            "ai.deepcode.android.action.TEST_AUTOMATION" -> {
                AppLogger.i("BootReceiver", "Test automation triggered via intent")
                val pendingResult = goAsync()
                val repo = AutomationRepository(context)
                val scheduler = AutomationScheduler(context)
                val entity = AutomationEntity(
                    id = UUID.randomUUID().toString(),
                    name = "TestEvery2Min",
                    description = "Test automation every 2 minutes",
                    category = "SYSTEM",
                    isEnabled = true,
                    cronExpression = "*/2 * * * *",
                    lastRunAt = 0L,
                    nextRunAt = System.currentTimeMillis() + 120000L,
                    templateId = "custom",
                    configJson = """{"action_prompt":"Say hello world from test automation"}"""
                )
                val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
                scope2.launch {
                    try {
                        repo.insertAutomation(entity)
                        AppLogger.i("BootReceiver", "Test automation inserted: ${entity.id}")
                        scheduler.schedule(entity)
                        AppLogger.i("BootReceiver", "Test automation scheduled")
                    } finally {
                        pendingResult.finish()
                        scope2.cancel()
                    }
                }
            }
        }
    }
}
