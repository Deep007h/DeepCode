package ai.deepcode.android.ui.agents

import android.content.Context
import androidx.work.*
import ai.deepcode.android.agent.AgentEngine
import ai.deepcode.android.agent.AgentRepository as AgentSessionRepository
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AgentScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun schedule(agent: AgentEntity, cronExpression: String = "0 7 * * *") {
        val intervalMs = calculateIntervalMs(cronExpression)
        val initialDelayMs = calculateInitialDelayMs(cronExpression)

        val data = Data.Builder()
            .putString("agent_id", agent.agentId)
            .putString("agent_name", agent.displayName)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<AgentRunner>(
            intervalMs, TimeUnit.MILLISECONDS
        )
            .setInputData(data)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag("agent_${agent.agentId}")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "agent_${agent.agentId}",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        AppLogger.i("AgentScheduler", "Scheduled agent ${agent.displayName} with interval ${intervalMs}ms")
    }

    fun cancel(agentId: String) {
        workManager.cancelUniqueWork("agent_${agentId}")
        AppLogger.i("AgentScheduler", "Cancelled scheduled agent $agentId")
    }

    companion object {
        suspend fun rescheduleAll(context: Context) {
            val repository = AgentRepository(context)
            val scheduler = AgentScheduler(context)
            val agents = repository.getAllAgents()
            var count = 0
            for (agent in agents) {
                if (agent.isEnabled && (agent.agentId == "morning_briefing" || agent.agentId == "daily_news_brief")) {
                    scheduler.schedule(agent)
                    count++
                }
            }
            AppLogger.i("AgentScheduler", "Rescheduled $count enabled agents")
        }
    }

    private fun calculateIntervalMs(cron: String): Long {
        return when {
            cron.contains("*/30") -> 30 * 60000L
            cron.contains("0 */6") -> 6 * 3600000L
            cron.contains("0 8") || cron.contains("0 7") || cron.contains("0 9") -> 24 * 3600000L
            else -> 24 * 3600000L
        }
    }

    private fun calculateInitialDelayMs(cron: String): Long {
        if (cron.contains("0 8") || cron.contains("0 7") || cron.contains("0 9")) {
            val hour = when {
                cron.contains("0 8") -> 8
                cron.contains("0 7") -> 7
                cron.contains("0 9") -> 9
                else -> 8
            }
            val calendar = java.util.Calendar.getInstance()
            val now = calendar.timeInMillis
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            if (calendar.timeInMillis <= now) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis - now
        }
        return 0L
    }
}

class AgentRunner(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val agentId = inputData.getString("agent_id") ?: return Result.failure()
        val agentName = inputData.getString("agent_name") ?: agentId
        AppLogger.i("AgentRunner", "Running scheduled agent: $agentName")

        return try {
            val agentRepo = AgentRepository(applicationContext)
            val agent = agentRepo.getAgentById(agentId)
            if (agent == null || !agent.isEnabled) {
                AppLogger.w("AgentRunner", "Agent disabled or not found, skipping")
                return Result.success()
            }

            val sessionRepo = AgentSessionRepository(applicationContext)
            val sessionId = sessionRepo.createSession("Scheduled: ${agent.displayName}")

            val prompt = buildAgentPrompt(agent)
            val engine = AgentEngine(applicationContext)
            engine.run(sessionId, prompt).collect { }

            agentRepo.recordRun(agentId, System.currentTimeMillis())

            AppLogger.i("AgentRunner", "Agent $agentName completed")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("AgentRunner", "Agent $agentName failed", e)
            Result.retry()
        }
    }

    private fun buildAgentPrompt(agent: AgentEntity): String {
        val dateStr = java.text.SimpleDateFormat(
            "EEEE, MMMM d, yyyy 'at' HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        return when (agent.agentId) {
            "morning_briefing", "daily_news_brief" -> """
                It's $dateStr. Deliver your scheduled Daily Morning News Brief of the day!
                
                Please cover these 4 key topics with fresh updates, engaging headlines, and relevant emojis:
                1. 🪙 Crypto: Bitcoin, Ethereum, Solana, and major altcoin price movements, market swings, and trending crypto stories.
                2. 🇮🇳 Indian News (All Genres): Top national headlines spanning politics, business & economy, tech/startups, sports (cricket & athletes), entertainment (Bollywood/cinema), and quirky viral stories.
                3. 🤖 AI News: Cutting-edge model releases, AI breakthroughs, industry moves, developer tools, and big tech drama.
                4. ⚔️ War & Conflict News: Global geopolitical conflicts, defense developments, diplomacy, and verified updates (maintain factual accuracy and respect).

                Style & Tone:
                - Energetic, engaging, and well-structured using clear markdown sections, bullet points, and lively emojis!
                - Feel free to use funny commentary, witty roasts, or humorous takes for appropriate news (like crazy crypto volatility, quirky Indian news, or AI hype/drama), while keeping war and conflict news objective and respectful.
            """.trimIndent()
            else -> "It's $dateStr. Execute your scheduled task as defined in your system prompt."
        }
    }
}
