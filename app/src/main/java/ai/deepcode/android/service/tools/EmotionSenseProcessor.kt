package ai.deepcode.android.service.tools

import java.util.Locale

/**
 * Result of emotion and sense processing on input text for neural speech synthesis.
 */
data class ProcessedSpeechInput(
    val cleanText: String,
    val detectedStyle: String,
    val speaker: String? = null
)

/**
 * Analyzes and transforms text input to unlock the full expressive potential of
 * neural TTS models like Gemini 3.8 Flash TTS and Gemini 3.8 Flash Lite TTS.
 *
 * Extracts emotional cues, stage directions (e.g., [whispering], [excited]),
 * punctuation cadence, and dramatic sense.
 */
object EmotionSenseProcessor {

    private val STAGE_DIRECTION_REGEX = Regex(
        """\[\s*(whispering|whisper|excited|cheerfully|cheerful|enthusiastic|sad|sorrowful|serious|urgent|alert|calm|softly|loudly|warmly|laughing|laugh|giggle|curious|thoughtful|dramatic|sarcastic|tender|gentle|reassuring|storyteller|fearful|panicked)[^\]]*\]""",
        RegexOption.IGNORE_CASE
    )

    private val ASTERISK_EMOTION_REGEX = Regex(
        """\*\s*(whispers|sighs|laughs|giggles|gasps|chuckles|smiles|pauses)\s*\*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Processes raw text into sanitized speech text and expressive style metadata.
     *
     * @param rawText The raw text to speak.
     * @param userEmotionMode Configured emotion mode: "auto", "expressive", "empathetic", "professional", "dramatic", "whisper".
     */
    fun process(rawText: String, userEmotionMode: String = "auto"): ProcessedSpeechInput {
        var text = rawText.trim()

        // 1. Check for explicit stage directions (e.g. "[whispering] Hello there" or "*sighs* It was tough")
        var explicitStyle: String? = null

        val bracketMatch = STAGE_DIRECTION_REGEX.find(text)
        if (bracketMatch != null) {
            val cue = bracketMatch.groupValues[1].lowercase(Locale.ROOT)
            explicitStyle = mapCueToStyle(cue)
            // Remove the bracketed tag from spoken text so it is acted out, not spoken
            text = text.replace(bracketMatch.value, "").trim()
        }

        val asteriskMatch = ASTERISK_EMOTION_REGEX.find(text)
        if (asteriskMatch != null) {
            val cue = asteriskMatch.groupValues[1].lowercase(Locale.ROOT)
            if (explicitStyle == null) {
                explicitStyle = mapCueToStyle(cue)
            }
            text = text.replace(asteriskMatch.value, "").trim()
        }

        // 2. Sanitize Markdown for natural speech flow
        text = sanitizeMarkdownForSpeech(text)

        // 3. Determine style
        val finalStyle = when {
            explicitStyle != null -> explicitStyle
            userEmotionMode.equals("expressive", ignoreCase = true) ->
                "highly expressive, vivid, dynamic inflection, animated"
            userEmotionMode.equals("empathetic", ignoreCase = true) ->
                "warm, gentle, reassuring, compassionate and supportive"
            userEmotionMode.equals("professional", ignoreCase = true) ->
                "clear, articulate, calm, professional and neutral"
            userEmotionMode.equals("dramatic", ignoreCase = true) ->
                "dramatic, cinematic, expressive storytelling with deliberate pauses"
            userEmotionMode.equals("whisper", ignoreCase = true) ->
                "whispering softly, intimate, quiet cadence"
            else -> detectEmotionAndSense(text)
        }

        return ProcessedSpeechInput(
            cleanText = text,
            detectedStyle = finalStyle
        )
    }

    private fun mapCueToStyle(cue: String): String {
        return when (cue) {
            "whisper", "whispering", "softly", "whispers" -> "whispering urgently, soft and intimate"
            "excited", "enthusiastic", "cheerfully", "cheerful" -> "highly excited, cheerful, energetic and joyful"
            "laugh", "laughing", "giggle", "giggles", "chuckles", "smiles" -> "laughing softly, playful, cheerful inflection"
            "sad", "sorrowful", "sighs" -> "melancholy, subdued, sympathetic, soft"
            "urgent", "alert", "panicked", "fearful", "gasps" -> "urgent, alert, sharp, fast-paced and serious"
            "serious", "pauses" -> "measured, serious, contemplative, authoritative"
            "warmly", "tender", "gentle", "reassuring" -> "warm, gentle, caring, reassuring tone"
            "curious", "thoughtful" -> "inquisitive, curious, thoughtful cadence"
            "dramatic", "storyteller" -> "dramatic, captivating storytelling, vivid pauses"
            "sarcastic" -> "sarcastic, wry, witty, slightly teasing tone"
            else -> "natural, warm, conversational, friendly and engaging"
        }
    }

    private fun detectEmotionAndSense(text: String): String {
        val lower = text.lowercase(Locale.ROOT)

        // Count excitement markers
        val exclamationCount = text.count { it == '!' }
        val questionCount = text.count { it == '?' }

        // Sentiment keywords
        val isJoyful = lower.contains("congratulations") || lower.contains("awesome") ||
                lower.contains("fantastic") || lower.contains("super excited") ||
                lower.contains("great news") || lower.contains("hooray") ||
                (exclamationCount >= 2 && (lower.contains("happy") || lower.contains("love") || lower.contains("yay")))

        if (isJoyful) {
            return "enthusiastic, cheerful, smiling tone, lively pacing"
        }

        val isEmpathetic = lower.contains("i understand") || lower.contains("don't worry") ||
                lower.contains("it's okay") || lower.contains("it is okay") ||
                lower.contains("take your time") || lower.contains("here to help") ||
                lower.contains("sorry to hear") || lower.contains("comfort")

        if (isEmpathetic) {
            return "warm, gentle, reassuring, compassionate and supportive"
        }

        val isUrgent = lower.contains("warning") || lower.contains("critical") ||
                lower.contains("danger") || lower.contains("alert") ||
                lower.contains("immediately") || lower.contains("fatal error") ||
                lower.contains("abort")

        if (isUrgent) {
            return "urgent, focused, serious, authoritative and alert"
        }

        val isCurious = (questionCount >= 2) ||
                lower.startsWith("what if") || lower.startsWith("have you ever") ||
                lower.contains("fascinating question") || lower.contains("let's explore") ||
                lower.contains("curious about")

        if (isCurious) {
            return "thoughtful, contemplative, curious inflection, engaging cadence"
        }

        val isStorytelling = lower.contains("once upon a time") || lower.contains("in a realm") ||
                lower.contains("suddenly the") || lower.contains("long ago") ||
                lower.contains("the story begins") || lower.contains("chapter ")

        if (isStorytelling) {
            return "expressive storytelling, dramatic pauses, dynamic inflection"
        }

        val isPlayful = lower.contains("haha") || lower.contains("hehe") ||
                lower.contains("just kidding") || lower.contains("fun fact") ||
                lower.contains("pun intended")

        if (isPlayful) {
            return "playful, humorous, witty, lighthearted tone"
        }

        val isTechnical = lower.contains("function ") || lower.contains("val ") ||
                lower.contains("class ") || lower.contains("gradle") ||
                lower.contains("database") || lower.contains("repository") ||
                lower.contains("api endpoint") || lower.contains("pipeline")

        if (isTechnical) {
            return "clear, articulate, professional, steady and instructional pacing"
        }

        // Natural conversational baseline with warm inflection
        return "natural, warm, conversational, friendly and engaging"
    }

    private fun sanitizeMarkdownForSpeech(raw: String): String {
        var s = raw
        // Strip code block fences ```kotlin ... ```
        s = s.replace(Regex("""```[a-zA-Z0-9_\-]*\n([\s\S]*?)```""")) { match ->
            "Code snippet: " + match.groupValues[1].lines().take(3).joinToString(". ")
        }
        // Strip inline backticks `val x = 1` -> val x = 1
        s = s.replace(Regex("""`([^`]+)`"""), "$1")
        // Strip Markdown bold / italic symbols
        s = s.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        s = s.replace(Regex("""\*([^*]+)\*"""), "$1")
        s = s.replace(Regex("""__([^_]+)__"""), "$1")
        s = s.replace(Regex("""_([^_]+)_"""), "$1")
        // Strip markdown links [Google](https://google.com) -> Google
        s = s.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
        // Strip markdown headers #, ##, ###
        s = s.replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
        // Strip bullet points * or -
        s = s.replace(Regex("""^[\*\-]\s+""", RegexOption.MULTILINE), "")
        // Strip excessive newlines
        s = s.replace(Regex("""\n{2,}"""), ". ")
        return s.trim()
    }
}
