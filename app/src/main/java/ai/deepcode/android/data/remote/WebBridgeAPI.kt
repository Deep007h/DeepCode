package ai.deepcode.android.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ── v1.1 Failover-aware Task Input ──

data class WebBridgeTask(
    val task_id: String,
    val triggered_by: String = "primary_ai", // "primary_ai" | "app_watchdog" | "user_direct" | "recovery_system"
    val failover_context: WebBridgeFailoverContext? = null,
    val task_type: String = "text_generation", // text_generation, image_generation, image_analysis, audio_generation, document_analysis, search, custom
    val target_site: String = "auto", // chatgpt, gemini, claude, midjourney, firefly, leonardo, perplexity, elevenlabs, suno, auto
    val user_prompt: String,
    val attachments: List<WebBridgeAttachment> = emptyList(),
    val credential_key: String = "",
    val output_format: String = "text", // text, image_url, base64_image, audio_url, markdown, json
    val wait_for_completion: Boolean = true,
    val timeout_seconds: Int = 120,
    val additional_instructions: String? = null,
    val new_conversation: Boolean = true,

    // Legacy v1.0 compat fields — mapped to/from v1.1 equivalents
    val capability: String? = null,
    val preferred_site: String? = null,
    val prompt: String? = null,
    val constraints: WebBridgeConstraints? = null,
    val session_hint: String? = null
)

data class WebBridgeFailoverContext(
    val primary_ai_status: String = "unknown", // timeout, crash, rate_limited, network_error, unknown
    val silence_duration_seconds: Double = 0.0,
    val original_user_message: String = "",
    val conversation_history: List<WebBridgeChatTurn>? = null,
    val preferred_fallback_site: String = "auto",
    val notify_user: Boolean = false,
    val reply_tone: String = "match_primary_ai" // match_primary_ai, neutral, friendly
)

data class WebBridgeChatTurn(
    val role: String, // "user" | "assistant"
    val content: String
)

data class WebBridgeAttachment(
    val type: String, // "image" | "file" | "url"
    val ref: String
)

data class WebBridgeConstraints(
    val max_wait_seconds: Int = 120,
    val output_format: String = "text",
    val quality: String = "best"
)

// ── v1.1 Failover-aware Task Output ──

data class WebBridgeResult(
    val task_id: String,
    val status: String, // "SUCCESS" | "PARTIAL" | "FAILED"
    val mode: String = "NORMAL", // "NORMAL" | "FAILOVER" | "RECOVERY_RELAY"
    val site_used: String = "",
    val target_site_used: String = "",
    val capability: String = "",
    val output_type: String = "text",
    val output: WebBridgeOutputContent? = null,
    val result: WebBridgeOutputResult? = null, // legacy compat
    val failover_metadata: WebBridgeFailoverMetadata? = null,
    val recovery_hint: WebBridgeRecoveryHint? = null,
    val needs_user_action: WebBridgeUserAction? = null,
    val diagnostics: WebBridgeDiagnostics? = null,
    val metadata: Map<String, Any>? = null,
    val error: String? = null
)

data class WebBridgeOutputContent(
    val text_content: String? = null,
    val image_urls: List<String>? = null,
    val base64_images: List<String>? = null,
    val audio_url: String? = null,
    val video_url: String? = null,
    val description: String? = null
)

data class WebBridgeFailoverMetadata(
    val primary_ai_status: String = "",
    val silence_duration_seconds: Double = 0.0,
    val fallback_site_selected: String = "",
    val failover_triggered_at: String = "",
    val context_passed_to_site: Boolean = false,
    val user_notified: Boolean = false
)

data class WebBridgeRecoveryHint(
    val primary_ai_should_resume: Boolean = true,
    val buffered_exchange: List<WebBridgeChatTurn>? = null
)

// ── Legacy models (kept for backward compat) ──

data class WebBridgeOutputResult(
    val text: String?,
    val image_urls: List<String>?,
    val file_refs: List<String>?,
    val description: String?
)

data class WebBridgeUserAction(
    val reason: String,
    val instructions: String
)

data class WebBridgeDiagnostics(
    val attempts: Int,
    val wait_seconds: Int,
    val notes: String
)

data class TaskStatus(
    val task_id: String,
    val status: String,
    val progress: String?,
    val result: WebBridgeResult?
)

data class CredentialEntry(
    val platform: String,
    val email: String,
    val secret: String,
    val extra: String? = null
)

data class ConfirmResponse(
    val success: Boolean,
    val message: String
)

interface WebBridgeAPI {
    @POST("task")
    suspend fun submitTask(@Body task: WebBridgeTask): Response<WebBridgeResult>

    @GET("task/{taskId}/status")
    suspend fun getTaskStatus(@Path("taskId") taskId: String): Response<TaskStatus>

    @POST("credentials")
    suspend fun addCredential(@Body credential: CredentialEntry): Response<ConfirmResponse>
}
