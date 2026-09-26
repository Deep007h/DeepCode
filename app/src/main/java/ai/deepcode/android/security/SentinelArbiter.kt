package ai.deepcode.android.security

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger
import com.google.gson.JsonObject

/**
 * Deterministic Risk Tier Taxonomy inspired by Meta Muse's Sentinel Arbiter.
 */
enum class RiskTier(val tierNumber: Int, val title: String, val requiresApproval: Boolean) {
    TIER_0_READ_ONLY(0, "Read-Only Action", false),
    TIER_1_LOCAL_MUTATION(1, "Local Safe Mutation", false),
    TIER_2_EXTERNAL_COMMUNICATION(2, "External Communication", false),
    TIER_3_HIGH_RISK_DESTRUCTIVE(3, "High-Risk Destructive Action", true)
}

data class ArbiterDecision(
    val allowed: Boolean,
    val tier: RiskTier,
    val reason: String,
    val blockedReason: String? = null
)

/**
 * Sentinel Security Arbiter:
 * Evaluates tool calls, system commands, and state mutations before execution.
 * Enforces Safe Mode rules, intercepts catastrophic commands, and prevents prompt-injected destruction.
 */
object SentinelArbiter {
    private const val TAG = "SentinelArbiter"

    // Catastrophic shell/root commands that must never be executed automatically
    private val DANGEROUS_COMMAND_PATTERNS = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*r[a-zA-Z]*f*|-[a-zA-Z]*f[a-zA-Z]*r*)\s+.*(/|/\*|/system|/data|/boot|/recovery|/vendor|/storage|/sdcard)$""", RegexOption.IGNORE_CASE),
        Regex("""\brm\s+-[a-zA-Z]*r[a-zA-Z]*f*\s+(/\*|/\s*)$""", RegexOption.IGNORE_CASE),
        Regex("""\bdd\s+if=.*\sof=(/dev/block|/dev/sd|/dev/nvme)""", RegexOption.IGNORE_CASE),
        Regex("""\bmkfs(\.[a-zA-Z0-9]+)?\s+""", RegexOption.IGNORE_CASE),
        Regex("""\bformat\s+""", RegexOption.IGNORE_CASE),
        Regex("""\breboot\s+(recovery|bootloader|fastboot)?""", RegexOption.IGNORE_CASE),
        Regex("""\bpm\s+uninstall\s+(com\.android\.|com\.google\.android\.|android)""", RegexOption.IGNORE_CASE),
        Regex("""\bpm\s+disable-user\s+(--user\s+\d+\s+)?(com\.android\.settings|com\.android\.systemui|android)""", RegexOption.IGNORE_CASE),
        Regex("""\bsetenforce\s+0\b""", RegexOption.IGNORE_CASE),
        Regex("""\bflash_erase\b""", RegexOption.IGNORE_CASE)
    )

    fun evaluateToolCall(
        toolName: String,
        args: JsonObject,
        useRoot: Boolean,
        context: Context?
    ): ArbiterDecision {
        val prefs = context?.let { EncryptedPrefs.getInstance(it) }
        val safeMode = prefs?.getBooleanSetting("setting_safe_mode", false) ?: false
        val confirmRoot = prefs?.getBooleanSetting("setting_confirm_root_cmds", false) ?: false

        if (toolName in listOf("run_command", "shell", "adb_command", "terminal_command", "adb")) {
            val cmd = listOf("command", "cmd", "script", "input", "code", "CommandLine", "command_line", "cmd_line")
                .firstNotNullOfOrNull { key ->
                    try {
                        val el = args.get(key)?.takeIf { !it.isJsonNull }
                        if (el?.isJsonPrimitive == true) el.asString else el?.toString()
                    } catch (_: Exception) { null }
                }?.trim().orEmpty()

            // Check for catastrophic patterns
            for (pattern in DANGEROUS_COMMAND_PATTERNS) {
                if (pattern.containsMatchIn(cmd)) {
                    AppLogger.w(TAG, "BLOCKED Tier 3 command matching dangerous pattern: $cmd")
                    return ArbiterDecision(
                        allowed = false,
                        tier = RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE,
                        reason = "Command contains catastrophic/destructive pattern",
                        blockedReason = "[Sentinel Block] Destructive system modification blocked by Sentinel Arbiter: '$cmd'"
                    )
                }
            }

            if (safeMode && (cmd.contains("rm ", true) || cmd.contains("delete", true) || cmd.contains("format", true))) {
                return ArbiterDecision(
                    allowed = false,
                    tier = RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE,
                    reason = "Safe Mode is active; destructive command intercepted",
                    blockedReason = "[Sentinel Safe Mode] Intercepted potentially destructive command: '$cmd'. Disable Safe Mode in Security Settings to permit."
                )
            }

            if (useRoot && confirmRoot) {
                AppLogger.i(TAG, "Auditing root command: $cmd")
            }

            val tier = if (useRoot) RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE else RiskTier.TIER_1_LOCAL_MUTATION
            return ArbiterDecision(allowed = true, tier = tier, reason = "Command passed validation checks")
        }

        // 2. Classify other tools
        return when (toolName) {
            "read_file", "file_read", "list_directory", "grep_search", "web_search",
            "notion_search", "notion_read", "notion_list_databases", "github_get_user",
            "system_battery_info", "system_memory_info", "system_device_info", "video_inspect" -> {
                ArbiterDecision(allowed = true, tier = RiskTier.TIER_0_READ_ONLY, reason = "Read-only inspection tool")
            }
            "delete_file" -> {
                if (safeMode) {
                    ArbiterDecision(
                        allowed = false,
                        tier = RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE,
                        reason = "File deletion blocked by Safe Mode",
                        blockedReason = "[Sentinel Safe Mode] Deletion of files is restricted in Safe Mode."
                    )
                } else {
                    ArbiterDecision(allowed = true, tier = RiskTier.TIER_1_LOCAL_MUTATION, reason = "Local file deletion")
                }
            }
            "write_file", "edit_file", "create_file", "apply_patch", "system_screenshot",
            "video_extract_frames", "video_extract_audio" -> {
                ArbiterDecision(allowed = true, tier = RiskTier.TIER_1_LOCAL_MUTATION, reason = "Local filesystem mutation")
            }
            "telegram_send_message", "create_connection", "create_calendar_event", "send_gmail" -> {
                ArbiterDecision(allowed = true, tier = RiskTier.TIER_2_EXTERNAL_COMMUNICATION, reason = "Outbound external communication")
            }
            "system_app_control" -> {
                val action = args.get("action")?.asString ?: ""
                if (safeMode && action.equals("freeze", true)) {
                    ArbiterDecision(
                        allowed = false,
                        tier = RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE,
                        reason = "Freezing apps restricted in Safe Mode",
                        blockedReason = "[Sentinel Safe Mode] App freeze action restricted in Safe Mode."
                    )
                } else {
                    ArbiterDecision(allowed = true, tier = RiskTier.TIER_3_HIGH_RISK_DESTRUCTIVE, reason = "App lifecycle modification")
                }
            }
            else -> ArbiterDecision(allowed = true, tier = RiskTier.TIER_1_LOCAL_MUTATION, reason = "Standard tool invocation")
        }
    }
}
