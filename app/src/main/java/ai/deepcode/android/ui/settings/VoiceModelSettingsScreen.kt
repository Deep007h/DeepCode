package ai.deepcode.android.ui.settings

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.ui.theme.depthCard
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VoiceOption(
    val id: String,
    val name: String,
    val description: String,
    val gender: String
)

data class ProviderTtsModel(
    val id: String,
    val name: String,
    val provider: String,
    val badge: String,
    val description: String,
    val isExpressive: Boolean = false
)

val GEMINI_TTS_MODELS = listOf(
    ProviderTtsModel(
        id = "gemini-3.8-flash-tts",
        name = "Gemini 3.8 Flash TTS",
        provider = "Google Gemini",
        badge = "Expressive / Emotion",
        description = "Latest Gemini neural speech engine. Supports dynamic sentiment, emotion sensing, and dramatic pauses.",
        isExpressive = true
    ),
    ProviderTtsModel(
        id = "gemini-3.8-flash-lite-tts",
        name = "Gemini 3.8 Flash Lite TTS",
        provider = "Google Gemini",
        badge = "Ultra Low Latency",
        description = "High-speed expressive neural audio synthesis optimized for rapid conversational turns.",
        isExpressive = true
    )
)

val OPENAI_TTS_MODELS = listOf(
    ProviderTtsModel(
        id = "tts-1",
        name = "OpenAI TTS-1",
        provider = "OpenAI",
        badge = "Standard",
        description = "Real-time speech synthesis optimized for responsive interactions.",
        isExpressive = false
    ),
    ProviderTtsModel(
        id = "tts-1-hd",
        name = "OpenAI TTS-1 HD",
        provider = "OpenAI",
        badge = "HD Audio",
        description = "High definition studio-grade neural voice synthesis.",
        isExpressive = false
    )
)

val DEFAULT_TTS_MODELS = listOf(
    ProviderTtsModel(
        id = "edge_tts",
        name = "Microsoft Edge Neural TTS",
        provider = "Default",
        badge = "Free / Built-in",
        description = "High fidelity multi-lingual neural speech. 400+ voices, 0 API key required.",
        isExpressive = false
    ),
    ProviderTtsModel(
        id = "kokoro",
        name = "Kokoro Neural TTS",
        provider = "Default",
        badge = "Local Server",
        description = "Local or LAN Kokoro neural audio inference server.",
        isExpressive = false
    ),
    ProviderTtsModel(
        id = "android",
        name = "Android System TTS",
        provider = "Default",
        badge = "Offline",
        description = "On-device native Android TextToSpeech engine.",
        isExpressive = false
    )
)

val GEMINI_VOICES = listOf(
    VoiceOption("Puck", "Puck", "Warm, natural & engaging conversational tone", "Male"),
    VoiceOption("Charon", "Charon", "Deep, authoritative, confident baritone", "Male"),
    VoiceOption("Kore", "Kore", "Calm, soothing, elegant feminine cadence", "Female"),
    VoiceOption("Fenrir", "Fenrir", "Energetic, dynamic, sharp inflection", "Male"),
    VoiceOption("Aoede", "Aoede", "Lyrical, clear, melodious and articulate", "Female")
)

val OPENAI_VOICES = listOf(
    VoiceOption("alloy", "Alloy", "Neutral, versatile, balanced tone", "Neutral"),
    VoiceOption("echo", "Echo", "Warm, smooth, conversational", "Male"),
    VoiceOption("fable", "Fable", "Expressive, accented, British tone", "Neutral"),
    VoiceOption("onyx", "Onyx", "Deep, authoritative, robust tone", "Male"),
    VoiceOption("nova", "Nova", "Energetic, bright, lively cadence", "Female"),
    VoiceOption("shimmer", "Shimmer", "Clear, expressive, gentle tone", "Female")
)

val EMOTION_MODES = listOf(
    "auto" to "Adaptive AI (Auto Emotion & Sense Detection)",
    "expressive" to "Vivid & Highly Expressive",
    "empathetic" to "Empathetic, Gentle & Warm",
    "professional" to "Clear, Professional & Instructional",
    "dramatic" to "Dramatic Storyteller (Theatrical Pauses)",
    "whisper" to "Soft & Intimate Whisper"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModelSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { EncryptedPrefs.getInstance(context) }
    val scope = rememberCoroutineScope()

    var ttsPriority by remember { mutableStateOf(prefs.getSetting("tts_priority", "provider_first")) }
    var ttsProvider by remember { mutableStateOf(prefs.getSetting("tts_provider", "Google Gemini")) }
    var ttsModel by remember { mutableStateOf(prefs.getSetting("tts_model", "gemini-3.8-flash-tts")) }
    var geminiVoice by remember { mutableStateOf(prefs.getSetting("tts_gemini_voice", "Puck")) }
    var geminiEmotionMode by remember { mutableStateOf(prefs.getSetting("tts_gemini_emotion_mode", "auto")) }
    var openAiVoice by remember { mutableStateOf(prefs.getSetting("tts_openai_voice", "alloy")) }

    var isTestingAudio by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeMediaPlayer?.stop()
                activeMediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Voice / Speech Model",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppWhite
                        )
                        Text(
                            text = "Neural TTS, Expressive Emotion & Priority Engine",
                            fontSize = 11.sp,
                            color = AppMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppScreenBg
                )
            )
        },
        containerColor = AppScreenBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Priority Menu
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionTitle(
                    title = "Synthesis Priority",
                    subtitle = "Select whether your chosen provider model or the default built-in engine takes precedence"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = true)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PriorityOptionCard(
                        title = "Selected Provider Model First",
                        subtitle = "Uses your selected API provider TTS (e.g. Gemini 3.8 Flash TTS with emotion sensing, OpenAI HD). Automatically falls back to built-in Edge Neural TTS if offline or quota is exceeded.",
                        icon = Icons.Default.AutoAwesome,
                        isSelected = ttsPriority == "provider_first",
                        onClick = {
                            ttsPriority = "provider_first"
                            prefs.saveSetting("tts_priority", "provider_first")
                            Toast.makeText(context, "Priority set to Provider Model First", Toast.LENGTH_SHORT).show()
                        }
                    )

                    PriorityOptionCard(
                        title = "Default Built-in Engine First",
                        subtitle = "Uses Microsoft Edge Neural TTS directly. 100% free, zero latency, 400+ voices, and consumes zero provider API credits.",
                        icon = Icons.Default.Bolt,
                        isSelected = ttsPriority == "default_first",
                        onClick = {
                            ttsPriority = "default_first"
                            prefs.saveSetting("tts_priority", "default_first")
                            Toast.makeText(context, "Priority set to Default Engine First", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Section 2: Audio Test & Preview Bar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = true)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlayingAudio) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AppPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Preview Voice & Emotion",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppWhite
                                )
                                Text(
                                    text = when (ttsPriority) {
                                        "provider_first" -> "Active: $ttsProvider • $ttsModel"
                                        else -> "Active: Microsoft Edge Neural TTS (Default)"
                                    },
                                    fontSize = 11.sp,
                                    color = AppMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isPlayingAudio) {
                                    try {
                                        activeMediaPlayer?.stop()
                                        activeMediaPlayer?.release()
                                    } catch (_: Exception) {}
                                    activeMediaPlayer = null
                                    isPlayingAudio = false
                                } else {
                                    isTestingAudio = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val executor = ToolExecutor(context)
                                            val sampleText = if (ttsModel.contains("gemini", ignoreCase = true)) {
                                                "[excited] Hello! I am Gemini 3.8 Flash Speech with expressive dynamic emotion sensing. How does my cadence sound?"
                                            } else {
                                                "Hello! This is a test of your configured text to speech voice in DeepCode."
                                            }
                                            val result = if (ttsPriority == "provider_first" && ttsModel.contains("gemini", ignoreCase = true)) {
                                                executor.executeGeminiTts(sampleText, ttsModel, prefs, context)
                                            } else if (ttsPriority == "provider_first" && ttsModel.startsWith("tts-", ignoreCase = true)) {
                                                executor.executeOpenAiTts(sampleText, ttsModel, prefs, context)
                                            } else {
                                                executor.executeTool("edge_tts", """{"text":"$sampleText"}""", "", false)
                                            }

                                            withContext(Dispatchers.Main) {
                                                isTestingAudio = false
                                                if (result != null && result.contains("[audio:")) {
                                                    val path = result.substringAfter("[audio:").substringBefore("]")
                                                    val file = File(path)
                                                    if (file.exists()) {
                                                        try {
                                                            activeMediaPlayer?.release()
                                                            val player = MediaPlayer()
                                                            player.setDataSource(file.absolutePath)
                                                            player.prepare()
                                                            player.setOnCompletionListener {
                                                                isPlayingAudio = false
                                                            }
                                                            player.start()
                                                            activeMediaPlayer = player
                                                            isPlayingAudio = true
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Synthesis failed: $result", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isTestingAudio = false
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAudio) Color(0xFFEF4444) else AppPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            enabled = !isTestingAudio
                        ) {
                            if (isTestingAudio) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AppWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isPlayingAudio) "Stop" else "Test Voice",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppWhite
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Google Gemini TTS Models
            item {
                SectionTitle(
                    title = "Google Gemini Speech Models",
                    subtitle = "Expressive Neural Audio with real-time emotion & style sensing"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = true)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GEMINI_TTS_MODELS.forEach { model ->
                        val isSelected = ttsModel == model.id
                        ModelSelectionCard(
                            model = model,
                            isSelected = isSelected,
                            onClick = {
                                ttsModel = model.id
                                ttsProvider = "Google Gemini"
                                prefs.saveSetting("tts_model", model.id)
                                prefs.saveSetting("tts_provider", "Google Gemini")
                            }
                        )
                    }

                    // Gemini Voice & Emotion Customization
                    AnimatedVisibility(
                        visible = ttsModel.contains("gemini", ignoreCase = true),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppScreenBg.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Gemini Prebuilt Voice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppWhite
                            )

                            // Voice Selector Chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GEMINI_VOICES.forEach { voice ->
                                    val isVoiceSelected = geminiVoice == voice.id
                                    VoiceChipRow(
                                        voice = voice,
                                        isSelected = isVoiceSelected,
                                        onClick = {
                                            geminiVoice = voice.id
                                            prefs.saveSetting("tts_gemini_voice", voice.id)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = AppDivider, thickness = 1.dp)

                            Text(
                                text = "Emotion & Sense Processing Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppWhite
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                EMOTION_MODES.forEach { (modeId, modeTitle) ->
                                    val isModeSelected = geminiEmotionMode == modeId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isModeSelected) AppPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable {
                                                geminiEmotionMode = modeId
                                                prefs.saveSetting("tts_gemini_emotion_mode", modeId)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = modeTitle,
                                            fontSize = 12.sp,
                                            fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isModeSelected) AppPrimary else AppWhite
                                        )
                                        if (isModeSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AppPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Informational hint
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppPrimary.copy(alpha = 0.08f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gemini 3.8 Flash automatically interprets stage tags like [whispering], [excited], *laughs*, and question cadence.",
                                    fontSize = 11.sp,
                                    color = AppMuted,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: OpenAI TTS Models
            item {
                SectionTitle(
                    title = "OpenAI Speech Models",
                    subtitle = "OpenAI Audio API speech synthesis models"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = true)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OPENAI_TTS_MODELS.forEach { model ->
                        val isSelected = ttsModel == model.id
                        ModelSelectionCard(
                            model = model,
                            isSelected = isSelected,
                            onClick = {
                                ttsModel = model.id
                                ttsProvider = "OpenAI"
                                prefs.saveSetting("tts_model", model.id)
                                prefs.saveSetting("tts_provider", "OpenAI")
                            }
                        )
                    }

                    // OpenAI Voice Selector
                    AnimatedVisibility(
                        visible = ttsModel.startsWith("tts-", ignoreCase = true),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppScreenBg.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "OpenAI Voice Selection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppWhite
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OPENAI_VOICES.forEach { voice ->
                                    val isVoiceSelected = openAiVoice == voice.id
                                    VoiceChipRow(
                                        voice = voice,
                                        isSelected = isVoiceSelected,
                                        onClick = {
                                            openAiVoice = voice.id
                                            prefs.saveSetting("tts_openai_voice", voice.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Default Built-in Engine
            item {
                SectionTitle(
                    title = "Default Built-in Engine",
                    subtitle = "Always free, high-speed neural TTS engines with zero API setup"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = true)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DEFAULT_TTS_MODELS.forEach { model ->
                        val isSelected = ttsModel == model.id
                        ModelSelectionCard(
                            model = model,
                            isSelected = isSelected,
                            onClick = {
                                ttsModel = model.id
                                ttsProvider = "Default"
                                prefs.saveSetting("tts_model", model.id)
                                prefs.saveSetting("tts_provider", "Default")
                                prefs.saveSetting("tts_backend", model.id)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AppWhite
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = AppMuted
        )
    }
}

@Composable
private fun PriorityOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else AppScreenBg.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isSelected) AppPrimary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) AppPrimary.copy(alpha = 0.2f) else AppDivider),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AppPrimary else AppMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) AppPrimary else AppWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = AppMuted,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppPrimary,
                unselectedColor = AppMuted
            )
        )
    }
}

@Composable
private fun ModelSelectionCard(
    model: ProviderTtsModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else AppScreenBg.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isSelected) AppPrimary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) AppPrimary else AppWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (model.isExpressive) Color(0xFF10B981).copy(alpha = 0.15f) else AppPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = model.badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (model.isExpressive) Color(0xFF10B981) else AppPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.description,
                fontSize = 11.sp,
                color = AppMuted,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppPrimary,
                unselectedColor = AppMuted
            )
        )
    }
}

@Composable
private fun VoiceChipRow(
    voice: VoiceOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = voice.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) AppPrimary else AppWhite
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${voice.gender})",
                    fontSize = 10.sp,
                    color = AppMuted
                )
            }
            Text(
                text = voice.description,
                fontSize = 11.sp,
                color = AppMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
