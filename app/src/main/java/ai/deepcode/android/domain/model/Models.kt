package ai.deepcode.android.domain.model

import androidx.compose.runtime.Stable

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val totalTokensInput: Long = 0L,
    val totalTokensOutput: Long = 0L,
    val totalCost: Double = 0.0,
    val compactionSummary: String? = null,
    val compactionTailMessageId: String? = null,
    val isCompacting: Boolean = false
)

data class Message(
    val id: String,
    val sessionId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long,
    val isToolCall: Boolean = false,
    val toolCallsJson: String? = null, // JSON representation of tool calls
    val toolResultsJson: String? = null, // JSON representation of tool results
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0
)

@Stable
data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val isFree: Boolean,
    val contextWindow: String,
    val badge: String,
    val isPinned: Boolean = false
)

data class ProjectFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val isAttached: Boolean = false
)

data class Tool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

