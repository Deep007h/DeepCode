package ai.deepcode.android

import android.app.Application
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.agents.AgentRepository
import ai.deepcode.android.sync.SyncScheduler
import ai.deepcode.android.ui.automations.AutomationScheduler
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import android.os.StrictMode
import android.os.Build

class DeepCodeApp : Application() {
    companion object {
        lateinit var instance: DeepCodeApp
            private set

        fun getAppContext(): android.content.Context = instance.applicationContext
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Defer StrictMode installation until after AppLogger.init so the logger's
        // own bootstrap (mkdirs / createNewFile) runs on the IO dispatcher and
        // does not trip the new thread policy on the main thread.
        AppLogger.init(this)
        AppLogger.startLogcat()
        AppLogger.i("DeepCodeApp", "Starting — ${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyListener(java.util.concurrent.Executors.newSingleThreadExecutor()) { v ->
                        AppLogger.w("StrictMode", "Thread violation: $v")
                    }
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyListener(java.util.concurrent.Executors.newSingleThreadExecutor()) { v ->
                        AppLogger.w("StrictMode", "VM violation: $v")
                    }
                    .build()
            )
        }

        DeepCodeRepository.prewarm(this)
        
        try {
            ai.deepcode.android.plugin.PluginRegistry.init(this)
            ai.deepcode.android.util.AppLogger.i("DeepCodeApp", "Plugin system initialized")
        } catch (e: Exception) {
            ai.deepcode.android.util.AppLogger.e("DeepCodeApp", "Plugin init failed: ${e.message}")
        }

        appScope.launch { SyncScheduler.schedule(this@DeepCodeApp) }
        appScope.launch { AutomationScheduler.rescheduleAll(this@DeepCodeApp) }
        appScope.launch { seedBuiltinAgents() }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.f("CrashHandler", "Uncaught exception in ${thread.name}", throwable)
                AppLogger.exportCrashLog(throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private suspend fun seedBuiltinAgents() {
        try {
            val repository = AgentRepository(this@DeepCodeApp)
            val existing = repository.getAllAgents()
            val existingMap = existing.associateBy { it.agentId }
            val jsonText = assets.open("agents/builtin_agents.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonText)
            val now = System.currentTimeMillis()
            val agents = mutableListOf<ai.deepcode.android.ui.agents.AgentEntity>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val agentId = obj.getString("agent_id")
                val existingAgent = existingMap[agentId]

                val tools = obj.optJSONArray("tools")?.let { arr ->
                    (0 until arr.length()).joinToString(",") { arr.getString(it) }
                } ?: ""

                val subagents = obj.optJSONArray("subagents")?.let { arr ->
                    (0 until arr.length()).joinToString(",") { arr.getString(it) }
                } ?: ""

                val delegateName = obj.optString("delegate_name", "").ifEmpty { null }
                    .takeIf { obj.has("delegate_name") }

                agents.add(ai.deepcode.android.ui.agents.AgentEntity(
                    agentId = agentId,
                    displayName = obj.getString("display_name"),
                    description = obj.getString("description"),
                    agentTier = obj.optString("agent_tier", "worker"),
                    temperature = obj.optDouble("temperature", 0.4),
                    maxIterations = obj.optInt("max_iterations", 6),
                    sandboxMode = obj.optString("sandbox_mode", "none"),
                    omitIdentity = obj.optBoolean("omit_identity", true),
                    omitMemoryContext = obj.optBoolean("omit_memory_context", true),
                    omitSafetyPreamble = obj.optBoolean("omit_safety_preamble", true),
                    omitSkillsCatalog = obj.optBoolean("omit_skills_catalog", true),
                    omitProfile = obj.optBoolean("omit_profile", false),
                    omitMemoryMd = obj.optBoolean("omit_memory_md", false),
                    modelHint = obj.optString("model_hint", "agentic"),
                    delegateName = delegateName,
                    systemPrompt = obj.getString("system_prompt"),
                    tools = tools,
                    subagents = subagents,
                    isBuiltin = obj.optBoolean("is_builtin", true),
                    isEnabled = existingAgent?.isEnabled ?: true,
                    createdAt = existingAgent?.createdAt ?: now,
                    updatedAt = now,
                    lastRunAt = existingAgent?.lastRunAt ?: 0L,
                    runCount = existingAgent?.runCount ?: 0
                ))
            }

            if (agents.isNotEmpty()) {
                repository.insertAgents(agents)
                AppLogger.i("DeepCodeApp", "Seeded ${agents.size} builtin agents")
            }
        } catch (e: Exception) {
            AppLogger.e("DeepCodeApp", "Failed to seed builtin agents", e)
        }
    }
}
