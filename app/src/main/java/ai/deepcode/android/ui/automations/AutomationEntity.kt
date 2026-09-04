package ai.deepcode.android.ui.automations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val description: String,
    val category: String,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "cron_expression") val cronExpression: String,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long,
    @ColumnInfo(name = "next_run_at") val nextRunAt: Long,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "config_json") val configJson: String,
    @ColumnInfo(name = "chat_session_id") val chatSessionId: String? = null
) {
    fun getEffectiveChatSessionId(): String? {
        if (!chatSessionId.isNullOrBlank()) return chatSessionId
        return try {
            com.google.gson.JsonParser.parseString(configJson).asJsonObject.get("chat_session_id")?.asString?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getEffectiveActionPrompt(): String? {
        return try {
            com.google.gson.JsonParser.parseString(configJson).asJsonObject.get("action_prompt")?.asString?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: description.takeIf { it.isNotBlank() }
    }
}
