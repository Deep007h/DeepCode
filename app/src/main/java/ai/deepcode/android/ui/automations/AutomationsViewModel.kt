package ai.deepcode.android.ui.automations

import ai.deepcode.android.data.local.EncryptedPrefs
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AutomationsViewModel(private val context: Context) : ViewModel() {
    private val repository = AutomationRepository(context)
    private val scheduler = AutomationScheduler(context)
    private val securePrefs = EncryptedPrefs.getInstance(context)

    val automations: StateFlow<List<AutomationEntity>> = repository.getAllAutomationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun defaultTelegramChatId(): String? {
        val id = securePrefs.getSetting("telegram_default_chat_id", "")
        return id.ifBlank { null }
    }

    fun enableTemplate(template: TemplateData) {
        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            val session = ai.deepcode.android.data.local.SessionEntity(
                id = sessionId,
                title = "🤖 ${template.name}",
                createdAt = System.currentTimeMillis()
            )
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
            db.sessionDao().insertSession(session)

            val config = JsonObject().apply {
                addProperty("action_prompt", template.description)
                addProperty("chat_session_id", sessionId)
                defaultTelegramChatId()?.let { addProperty("telegram_chat_id", it) }
            }
            val nextRun = AutomationScheduler.computeNextRunAt(template.cron)
            val entity = AutomationEntity(
                id = UUID.randomUUID().toString(),
                name = template.name,
                description = template.description,
                category = template.category,
                isEnabled = true,
                cronExpression = template.cron,
                lastRunAt = 0L,
                nextRunAt = nextRun,
                templateId = template.id,
                configJson = Gson().toJson(config),
                chatSessionId = sessionId
            )
            repository.insertAutomation(entity)
            scheduler.schedule(entity, forceRecalculate = true)
        }
    }

    fun toggleAutomation(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabledStatus(id, isEnabled)
            val automation = repository.getAutomationById(id)
            if (automation != null) {
                if (isEnabled) {
                    scheduler.schedule(automation, forceRecalculate = false)
                } else {
                    scheduler.cancel(automation.id)
                }
            }
        }
    }

    fun deleteAutomation(id: String) {
        viewModelScope.launch {
            scheduler.cancel(id)
            repository.deleteAutomation(id)
        }
    }

    fun addCustomRule(name: String, description: String, category: String, cron: String, actionPrompt: String) {
        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            val session = ai.deepcode.android.data.local.SessionEntity(
                id = sessionId,
                title = "🤖 $name",
                createdAt = System.currentTimeMillis()
            )
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
            db.sessionDao().insertSession(session)

            val config = JsonObject().apply {
                addProperty("action_prompt", actionPrompt)
                addProperty("chat_session_id", sessionId)
                defaultTelegramChatId()?.let { addProperty("telegram_chat_id", it) }
            }
            val nextRun = AutomationScheduler.computeNextRunAt(cron)
            val entity = AutomationEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                category = category,
                isEnabled = true,
                cronExpression = cron,
                lastRunAt = 0L,
                nextRunAt = nextRun,
                templateId = "custom",
                configJson = Gson().toJson(config),
                chatSessionId = sessionId
            )
            repository.insertAutomation(entity)
            scheduler.schedule(entity, forceRecalculate = true)
        }
    }

    fun updateAutomation(
        id: String,
        name: String,
        description: String,
        category: String,
        cron: String,
        actionPrompt: String
    ) {
        viewModelScope.launch {
            val existing = repository.getAutomationById(id) ?: return@launch
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
            val sessionDao = db.sessionDao()

            var sessionId = existing.getEffectiveChatSessionId()
            if (sessionId.isNullOrBlank()) {
                val newSessionId = UUID.randomUUID().toString()
                val session = ai.deepcode.android.data.local.SessionEntity(
                    id = newSessionId,
                    title = "🤖 $name",
                    createdAt = System.currentTimeMillis()
                )
                sessionDao.insertSession(session)
                sessionId = newSessionId
            } else {
                sessionDao.renameSession(sessionId, "🤖 $name")
            }

            val configObj = try {
                com.google.gson.JsonParser.parseString(existing.configJson).asJsonObject
            } catch (_: Exception) {
                JsonObject()
            }.apply {
                addProperty("action_prompt", actionPrompt)
                addProperty("chat_session_id", sessionId)
                if (!has("telegram_chat_id")) {
                    defaultTelegramChatId()?.let { addProperty("telegram_chat_id", it) }
                }
            }

            val nextRun = AutomationScheduler.computeNextRunAt(cron)
            val updated = existing.copy(
                name = name,
                description = description,
                category = category,
                cronExpression = cron,
                nextRunAt = nextRun,
                configJson = Gson().toJson(configObj),
                chatSessionId = sessionId
            )

            repository.insertAutomation(updated)
            if (updated.isEnabled) {
                scheduler.schedule(updated, forceRecalculate = true)
            } else {
                scheduler.cancel(updated.id)
            }
        }
    }

    fun runAutomationNow(id: String) {
        viewModelScope.launch {
            AutomationScheduler.triggerImmediately(context, id)
        }
    }
}

data class TemplateData(
    val id: String,
    val name: String,
    val category: String,
    val cron: String,
    val description: String
)
