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
            val config = JsonObject().apply {
                addProperty("action_prompt", template.description)
                defaultTelegramChatId()?.let { addProperty("telegram_chat_id", it) }
            }
            val entity = AutomationEntity(
                id = UUID.randomUUID().toString(),
                name = template.name,
                description = template.description,
                category = template.category,
                isEnabled = true,
                cronExpression = template.cron,
                lastRunAt = 0L,
                nextRunAt = System.currentTimeMillis() + calculateIntervalMs(template.cron),
                templateId = template.id,
                configJson = Gson().toJson(config)
            )
            repository.insertAutomation(entity)
            scheduler.schedule(entity)
        }
    }

    fun toggleAutomation(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabledStatus(id, isEnabled)
            val automation = repository.getAutomationById(id)
            if (automation != null) {
                if (isEnabled) {
                    scheduler.schedule(automation)
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
            val config = JsonObject().apply {
                addProperty("action_prompt", actionPrompt)
                defaultTelegramChatId()?.let { addProperty("telegram_chat_id", it) }
            }
            val entity = AutomationEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                category = category,
                isEnabled = true,
                cronExpression = cron,
                lastRunAt = 0L,
                nextRunAt = System.currentTimeMillis() + calculateIntervalMs(cron),
                templateId = "custom",
                configJson = Gson().toJson(config)
            )
            repository.insertAutomation(entity)
            scheduler.schedule(entity)
        }
    }

    private fun calculateIntervalMs(cron: String): Long {
        return when {
            cron.contains("*/30") -> 30 * 60000L
            cron.contains("0 */6") -> 6 * 3600000L
            cron.contains("0 8") || cron.contains("0 7") || cron.contains("0 9") -> 24 * 3600000L
            else -> 60 * 60000L
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
