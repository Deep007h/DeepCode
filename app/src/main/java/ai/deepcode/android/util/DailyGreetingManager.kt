package ai.deepcode.android.util

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.domain.model.Message
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DailyGreeting(
    val title: String,
    val subtitle: String,
    val tag: String,
    val isAiGenerated: Boolean = false,
    val date: String = ""
)

object DailyGreetingManager {

    private val _greetingState = MutableStateFlow<DailyGreeting>(getDefaultGreeting("User"))
    val greetingState: StateFlow<DailyGreeting> = _greetingState.asStateFlow()

    private const val PREF_KEY_DATE = "daily_greeting_date"
    private const val PREF_KEY_SUBTITLE = "daily_greeting_subtitle"
    private const val PREF_KEY_TAG = "daily_greeting_tag"
    private const val PREF_KEY_IS_AI = "daily_greeting_is_ai"

    private var fallbackIndex = (System.currentTimeMillis() % 10).toInt()

    private val INSIGHT_TAGS = listOf(
        "⚡ Daily AI Briefing",
        "💡 Developer Insight",
        "🚀 Productivity Boost",
        "🎯 Focus Mode",
        "✨ Intelligence Pulse",
        "🛡️ Architecture Tip"
    )

    private val FALLBACK_INSIGHTS = listOf(
        "Clean architecture and reactive state keep your Android app responsive at 120Hz.",
        "Zero-latency streaming turns complex code refactoring into an effortless flow state.",
        "Small, verified iterations lead to rock-solid codebases and bug-free releases.",
        "Measure twice, compose once. High UI performance comes from minimizing unnecessary recompositions.",
        "Mastering your tools and AI assistant unlocks 10x engineering velocity.",
        "Great software is built one clean function and well-tested component at a time.",
        "DeepCode is synced with your local environment — ready to build, debug, and automate.",
        "Your offline and cloud models are primed. Let's build something exceptional today.",
        "Automate the repetitive workflows so you can focus on high-impact architecture.",
        "Consistency compounds. Every line of clean code adds to your long-term engineering foundation."
    )

    fun initialize(context: Context, profileName: String) {
        val prefs = EncryptedPrefs.getInstance(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val firstName = profileName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: "Developer"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..21 -> "Good evening"
            else -> "Night shift"
        }
        val title = "$timeGreeting, $firstName"

        val savedDate = prefs.getSetting(PREF_KEY_DATE, "")
        val savedSubtitle = prefs.getSetting(PREF_KEY_SUBTITLE, "")
        val savedTag = prefs.getSetting(PREF_KEY_TAG, "")
        val savedIsAi = prefs.getBooleanSetting(PREF_KEY_IS_AI, false)

        if (savedDate == todayStr && savedSubtitle.isNotBlank()) {
            _greetingState.value = DailyGreeting(
                title = title,
                subtitle = savedSubtitle,
                tag = if (savedTag.isNotBlank()) savedTag else "⚡ Daily AI Briefing",
                isAiGenerated = savedIsAi,
                date = todayStr
            )
        } else {
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            fallbackIndex = dayOfYear % FALLBACK_INSIGHTS.size
            val fallbackText = FALLBACK_INSIGHTS[fallbackIndex]
            val fallbackTag = INSIGHT_TAGS[fallbackIndex % INSIGHT_TAGS.size]

            _greetingState.value = DailyGreeting(
                title = title,
                subtitle = fallbackText,
                tag = fallbackTag,
                isAiGenerated = false,
                date = todayStr
            )

            prefs.saveSetting(PREF_KEY_DATE, todayStr)
            prefs.saveSetting(PREF_KEY_SUBTITLE, fallbackText)
            prefs.saveSetting(PREF_KEY_TAG, fallbackTag)
            prefs.saveBooleanSetting(PREF_KEY_IS_AI, false)

            refreshDailyGreeting(context, profileName, forceRefresh = false)
        }
    }

    fun refreshDailyGreeting(context: Context, profileName: String, forceRefresh: Boolean = false) {
        val prefs = EncryptedPrefs.getInstance(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val firstName = profileName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: "Developer"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..21 -> "Good evening"
            else -> "Night shift"
        }

        if (forceRefresh) {
            fallbackIndex = (fallbackIndex + 1) % FALLBACK_INSIGHTS.size
            val nextInsight = FALLBACK_INSIGHTS[fallbackIndex]
            val nextTag = INSIGHT_TAGS[fallbackIndex % INSIGHT_TAGS.size]
            _greetingState.value = DailyGreeting(
                title = "$timeGreeting, $firstName",
                subtitle = nextInsight,
                tag = nextTag,
                isAiGenerated = false,
                date = todayStr
            )
            prefs.saveSetting(PREF_KEY_DATE, todayStr)
            prefs.saveSetting(PREF_KEY_SUBTITLE, nextInsight)
            prefs.saveSetting(PREF_KEY_TAG, nextTag)
            prefs.saveBooleanSetting(PREF_KEY_IS_AI, false)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!forceRefresh) {
                    val savedDate = prefs.getSetting(PREF_KEY_DATE, "")
                    val isAi = prefs.getBooleanSetting(PREF_KEY_IS_AI, false)
                    if (savedDate == todayStr && isAi) return@launch
                }

                val zenProvider = AIProviderFactory.providers.find { it.name.startsWith("Zen") }
                val apiKey = prefs.getSetting("api_key_zen", "zen-free")
                val modelId = ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE

                val prompt = "Write exactly ONE short, inspiring, unique developer tip or thought for $firstName. Max 15 words. No quotes, no markdown."
                val nowMs = System.currentTimeMillis()
                val messages = listOf(
                    Message(id = UUID.randomUUID().toString(), sessionId = "daily_greeting", role = "user", content = prompt, timestamp = nowMs)
                )

                val resultText = StringBuilder()
                zenProvider?.streamCompletion(
                    messages = messages,
                    model = modelId,
                    tools = null,
                    apiKey = apiKey,
                    customBaseUrl = null,
                    onToken = { token -> resultText.append(token) },
                    onToolCall = {},
                    onComplete = {
                        val cleaned = resultText.toString().trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        if (cleaned.isNotBlank() && cleaned.length >= 8 && !cleaned.contains("<think")) {
                            val tag = INSIGHT_TAGS[Random().nextInt(INSIGHT_TAGS.size)]
                            prefs.saveSetting(PREF_KEY_DATE, todayStr)
                            prefs.saveSetting(PREF_KEY_SUBTITLE, cleaned)
                            prefs.saveSetting(PREF_KEY_TAG, tag)
                            prefs.saveBooleanSetting(PREF_KEY_IS_AI, true)

                            _greetingState.value = DailyGreeting(
                                title = "$timeGreeting, $firstName",
                                subtitle = cleaned,
                                tag = tag,
                                isAiGenerated = true,
                                date = todayStr
                            )
                        }
                    },
                    onError = {
                        // Fallback already displayed
                    }
                )
            } catch (_: Exception) {
                // Fallback already displayed
            }
        }
    }

    private fun getDefaultGreeting(firstName: String): DailyGreeting {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..21 -> "Good evening"
            else -> "Night shift"
        }
        return DailyGreeting(
            title = "$timeGreeting, $firstName",
            subtitle = "Your AI workspace is primed and ready to build.",
            tag = "⚡ DeepCode Workspace",
            isAiGenerated = false,
            date = ""
        )
    }
}
