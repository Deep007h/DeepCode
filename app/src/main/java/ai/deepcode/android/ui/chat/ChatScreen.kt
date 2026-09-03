package ai.deepcode.android.ui.chat

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.*
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.domain.model.*
import ai.deepcode.android.orchestrator.*
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.settings.*
import ai.deepcode.android.ui.theme.*
import ai.deepcode.android.util.AppLogger
import android.content.Context
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val RE_THOUGHT_BLOCK = Regex("""(?is)<\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>[\s\S]*?<\s*/\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>""")
private val RE_THOUGHT_OPEN = Regex("""(?is)<\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>[\s\S]*""")
private val RE_BRACKET_THOUGHT = Regex("""(?is)\[\s*(?:thought|think|thinking|reasoning|plan)\s*\][\s\S]*?\[\s*/\s*(?:thought|think|thinking|reasoning|plan)\s*\]""")
private val RE_UNTAGGED_THINKING_HEADER = Regex(
    """(?is)\A\s*(?:(?:here'?s?|this is|there is|it'?s?|'s)?\s*(?:a\s+)?(?:thinking|thought|reasoning)\s+process\b|(?:thought|thinking|reasoning)\s*process\b|let'?s\s+think\s+step\s+by\s+step\b|chain\s+of\s+thought\b)""",
)
private val RE_THOUGHT_FINAL_ANSWER_MARKER = Regex(
    """(?is)(?:\n|\A)(?:(?:final\s+answer|direct\s+answer|response|output|answer)\s*:\s*|(?:so\s+)?(?:i'll|i\s+will)\s+(?:just\s+)?(?:say|respond|reply|output|give)[^\n]*[.\n\r]+(?:that's\s+fine[^\n]*[.\n\r]+)?(?:i'll\s+output\s+that[^\n]*[.\n\r]+)?)([\s\S]+)$"""
)
private val RE_INNER_THOUGHT_PREFIX = Regex(
    """(?is)\A(?:\s*(?:thought|thinking|reasoning|internal thoughts?|plan):\s*[^\n]*\n*|\s*(?:that's|that is|this is)\s+(?:a|an)\s+[^.!?\n]*[.!?\n]*|\s*(?:the\s+)?user\s+(?:is|wants|asked|said|just)\b[^.!?\n]*[.!?\n]*|\s*i\s+(?:should|will|need\s+to|must|'ll)\s+(?:respond|reply|answer|greet|help|ask|follow)\b[^.!?\n]*[.!?\n]*|\s*(?:ensure|keep)\s+(?:no\s+thinking|no\s+internal|final\s+response)\b[^.!?\n]*[.!?\n]*|\s*(?:just\s+)?direct\s+answer[.!?\n]*|\s*no\s+tools\s+needed\b[^.!?\n]*[.!?\n]*)+"""
)

fun extractThoughtAndCleanText(raw: String, isStreaming: Boolean = false): Pair<String, String> {
    if (raw.isBlank()) return Pair("", "")

    // 1. Tagged with <think>...</think>
    val thinkMatch = Regex("""(?is)<\s*(?:think|thought|thinking|reasoning|plan)\s*>([\s\S]*?)(?:<\s*/\s*(?:think|thought|thinking|reasoning|plan)\s*>|$)""").find(raw)
    if (thinkMatch != null) {
        val thought = thinkMatch.groups[1]?.value?.trim() ?: ""
        var clean = raw.replace(RE_THOUGHT_BLOCK, "")
        if (isStreaming) clean = clean.replace(RE_THOUGHT_OPEN, "")
        clean = clean.replace(RE_INNER_THOUGHT_PREFIX, "").trim()
        return Pair(thought, clean)
    }

    // 2. Tagged with [thought]...[/thought]
    val bracketMatch = Regex("""(?is)\[\s*(?:thought|think|thinking|reasoning|plan)\s*\]([\s\S]*?)(?:\[\s*/\s*(?:thought|think|thinking|reasoning|plan)\s*\]|$)""").find(raw)
    if (bracketMatch != null) {
        val thought = bracketMatch.groups[1]?.value?.trim() ?: ""
        var clean = raw.replace(RE_BRACKET_THOUGHT, "")
        clean = clean.replace(RE_INNER_THOUGHT_PREFIX, "").trim()
        return Pair(thought, clean)
    }

    // 3. Plain text untagged thinking process (e.g. "Here's a thinking process: ...")
    if (RE_UNTAGGED_THINKING_HEADER.containsMatchIn(raw)) {
        val answerMatch = RE_THOUGHT_FINAL_ANSWER_MARKER.find(raw)
        if (answerMatch != null) {
            val candidateAnswer = answerMatch.groups[1]?.value?.trim() ?: ""
            val thoughtPart = raw.substring(0, answerMatch.range.first).trim()
            val cleanAnswer = candidateAnswer.trimStart('✅', ' ', '\n', '\r')
            if (cleanAnswer.isNotEmpty()) {
                return Pair(thoughtPart, cleanAnswer)
            }
        }

        // Backward line scan for the final answer
        val lines = raw.lines()
        var answerLineIndex = -1
        for (i in lines.indices.reversed()) {
            val line = lines[i].trim()
            if (line.isEmpty() || line == "✅") continue
            val isMeta = line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") ||
                         line.startsWith("4.") || line.startsWith("5.") || line.startsWith("- ") ||
                         line.startsWith("* ") || line.contains("Analyze", ignoreCase = true) ||
                         line.contains("Check Rules", ignoreCase = true) || line.contains("thinking process", ignoreCase = true) ||
                         line.contains("Wait, the rules say", ignoreCase = true) || line.contains("I'll just say", ignoreCase = true) ||
                         line.contains("I'll output that", ignoreCase = true) || line.contains("internal monologue", ignoreCase = true)
            if (!isMeta) {
                answerLineIndex = i
                break
            }
        }

        if (answerLineIndex > 0) {
            val answer = lines.subList(answerLineIndex, lines.size).joinToString("\n").trim().trimStart('✅', ' ')
            val thought = lines.subList(0, answerLineIndex).joinToString("\n").trim()
            if (answer.isNotEmpty()) {
                return Pair(thought, answer)
            }
        }

        if (isStreaming) {
            return Pair(raw.trim(), "")
        } else {
            val quoted = Regex(""""([^"\n]{3,120})"""").findAll(raw).lastOrNull()?.groups?.get(1)?.value?.trim()
            if (!quoted.isNullOrEmpty() && !quoted.contains("analyze", ignoreCase = true)) {
                return Pair(raw.trim(), quoted)
            }
            return Pair(raw.trim(), "")
        }
    }

    // 4. Default: remove any inner thought prefix
    val cleaned = raw.replace(RE_INNER_THOUGHT_PREFIX, "").trim()
    return Pair("", cleaned)
}

fun stripThinkingProcess(raw: String, isStreaming: Boolean = false): String {
    return extractThoughtAndCleanText(raw, isStreaming).second
}

private val RE_IMAGE_TAG = Regex("""\[image:([^\]]+)\]""")
private val RE_FILE_TAG = Regex("""\[file:([^\]]+)\]""")
private val RE_AUDIO_TAG = Regex("""\[audio:([^\]]+)\]""")
private val RE_VIDEO_TAG = Regex("""\[video:([^\]]+)\]""")
private val RE_MARKDOWN_IMAGE = Regex("""!\[(.*?)\]\((.*?)\)""")
private val RE_DIRECT_IMG = Regex("""https?://\S+\.(?:jpg|jpeg|png|gif|webp|bmp)(\?\S*)?""", RegexOption.IGNORE_CASE)
private val RE_MEDIA_TAG = Regex("""\[(image|audio|video|file):[^\]]+\]""")
private val RE_MEDIA_ESCAPE = Regex("""\[(audio|file|image|video):[^\]]+\]""")
private val RE_PROMPT_PREFIX = Regex("""(?i)^(can you|please|help me with|i want to|could you|how to|what is|tell me|write a|create a|give me|make a|generate a|build a|show me)\s+""")
private val RE_PROMPT_NON_ALPHANUM = Regex("""[^\w\s\-]""")
private val RE_PROMPT_IMG_SUBJECT = Regex("""(?i)^(image|picture|photo|drawing|illustration)\s+(of\s+)?a?\s*""")
private val RE_WHITESPACE = Regex("""\s+""")
private val RE_UNTITLED_SESSION = Regex("""(?i)^(session\s*\d*|chat|new session|untitled)$""")

@Composable
fun PlaceholderFeatureCard(
    iconContent: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .width(210.dp)
            .height(200.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181D)),
        border = BorderStroke(1.dp, Color(0xFF282B34))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6D00)),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF9E9EA7),
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = actionText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF6D00)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFFF6D00),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    repository: DeepCodeRepository,
    activeSessionId: String,
    sessionTitle: String = "Chat",
    onMenuClick: () -> Unit = {},
    onOpenApiKeys: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel { ChatViewModel(repository) }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamingMessageId by viewModel.streamingMessageId.collectAsStateWithLifecycle()
    val pendingToolCall by viewModel.pendingToolCall.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val orchestrationPlan by viewModel.orchestrationPlan.collectAsStateWithLifecycle()
    val orchestrationExpanded by viewModel.orchestrationExpanded.collectAsStateWithLifecycle()
    val agentsWorking by viewModel.agentsWorking.collectAsStateWithLifecycle()
    val orchestratedResult by viewModel.orchestratedResult.collectAsStateWithLifecycle()

    var inputMsg by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    var showModelPicker by remember { mutableStateOf(false) }
    var showSessionPicker by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val personaState = remember {
        mutableStateOf(run {
            val prefs = repository.securePrefs
            val enabled = prefs.getSetting("persona_enabled", "false") == "true"
            val content = prefs.getSetting("custom_persona", "")
            if (enabled && content.isNotEmpty()) {
                val custom = try {
                    val raw = prefs.getSetting("saved_personas", "[]")
                    val json = org.json.JSONArray(raw)
                    (0 until json.length()).map { i ->
                        val obj = json.getJSONObject(i)
                        obj.getString("name") to obj.getString("content")
                    }
                } catch (_: Exception) { emptyList() }
                val all = builtInPersonas.map { it.name to it.content } + custom
                all.find { it.second == content }?.first ?: "Default"
            } else "Default"
        })
    }

    val sessions by repository.getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSessionTokenSummary by remember(activeSessionId) { repository.tokenRepository.observeSession(activeSessionId) }.collectAsStateWithLifecycle(initialValue = null)
    val userTurns = remember(messages) { messages.count { it.role == "user" } }
    val maxTurns = remember { repository.securePrefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8 }
    val imageCache by viewModel.imageCache.collectAsStateWithLifecycle()
    val mediaProcessingType by viewModel.mediaProcessingType.collectAsStateWithLifecycle()
    val mediaProcessingPrompt by viewModel.mediaProcessingPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { AppLogger.d("ChatScreen", "ChatScreen composed, session=$activeSessionId") }
    LaunchedEffect(context) { viewModel.initOrchestrator(context) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    var fileName = "unknown"
                    var fileSize = 0L
                    context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                    if (fileName.lowercase().endsWith(".pdf")) {
                        val destDir = File(context.filesDir, "attachments")
                        destDir.mkdirs()
                        val destFile = File(destDir, fileName)
                        context.contentResolver.openInputStream(targetUri)?.use { input ->
                            FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        withContext(Dispatchers.Main) {
                            viewModel.attachFile(fileName, "", fileSize, destFile.absolutePath)
                        }
                    } else {
                        val textContent = context.contentResolver.openInputStream(targetUri)?.bufferedReader()?.use { reader ->
                            reader.readText()
                        } ?: ""
                        withContext(Dispatchers.Main) {
                            viewModel.attachFile(fileName, textContent, fileSize)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(activeSessionId) {
        if (activeSessionId.isNotEmpty()) viewModel.switchSession(activeSessionId)
    }

    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to latest message when keyboard opens
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            kotlinx.coroutines.delay(100L)
            val total = lazyListState.layoutInfo.totalItemsCount
            if (total > 0 && !lazyListState.isScrollInProgress) {
                lazyListState.animateScrollToItem(total - 1)
            }
        }
    }

    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty() && !isStreaming) {
            val totalItems = lazyListState.layoutInfo.totalItemsCount
            if (totalItems > 0 && !lazyListState.isScrollInProgress) {
                val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisible >= totalItems - 3) lazyListState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    // Follow streaming output in real time, but only while the user is already
    // near the bottom (never yank the list if they scrolled up to read).
    val isNearBottom by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(Unit) {
        var lastScrollTime = 0L
        viewModel.streamedText.collect {
            val now = System.currentTimeMillis()
            if (now - lastScrollTime >= 60L && !lazyListState.isScrollInProgress && isNearBottom && isStreaming) {
                lastScrollTime = now
                val total = lazyListState.layoutInfo.totalItemsCount
                if (total > 0) {
                    val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                    if (lastVisibleItem != null && lastVisibleItem.index == total - 1) {
                        val overflow = (lastVisibleItem.offset + lastVisibleItem.size) - lazyListState.layoutInfo.viewportEndOffset
                        if (overflow > 0) {
                            lazyListState.scrollBy(overflow.toFloat())
                        }
                    } else {
                        lazyListState.scrollToItem(total - 1)
                    }
                }
            }
        }
    }

    val groupedItems = remember(messages) { groupChatMessages(messages) }

    val currentSessionTitle = remember(sessions, activeSessionId, sessionTitle) {
        sessions.find { it.id == activeSessionId }?.title ?: sessionTitle
    }

    val activeWallpaperId = remember { repository.securePrefs.getSetting("chat_wallpaper", "default") }
    val customWallpaperPath = remember { repository.securePrefs.getSetting("chat_wallpaper_custom", "") }

    Box(modifier = modifier.fillMaxSize()) {
        val wallpaperOpt = remember(activeWallpaperId) {
            ai.deepcode.android.ui.settings.PresetWallpapers.find { it.id == activeWallpaperId }
        }
        val customFile = remember(customWallpaperPath) {
            if (customWallpaperPath.isNotEmpty()) java.io.File(customWallpaperPath) else null
        }
        if (activeWallpaperId == "custom" && customFile != null && customFile.exists()) {
            // Decode off the main thread and downsample to ~screen size — a full-size
            // photo decoded synchronously in composition freezes frames and risks OOM.
            val config = LocalConfiguration.current
            val screenW = config.screenWidthDp.coerceAtLeast(1)
            val screenH = config.screenHeightDp.coerceAtLeast(1)
            val bm by produceState<android.graphics.Bitmap?>(initialValue = null, customFile.absolutePath) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(customFile.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= screenW || bounds.outHeight / (sample * 2) >= screenH) {
                            sample *= 2
                        }
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        android.graphics.BitmapFactory.decodeFile(customFile.absolutePath, opts)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val wallpaperBitmap = bm
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000)))
            }
        } else if (wallpaperOpt != null && wallpaperOpt.gradientColors != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(wallpaperOpt.gradientColors))
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000)))
        }
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(
                sessionTitle = currentSessionTitle,
                isStreaming = isStreaming,
                activeModel = activeModel,
                onMenuClick = onMenuClick,
                showSessionPicker = showSessionPicker,
                onToggleSessionPicker = { showSessionPicker = it },
                sessions = sessions,
                activeSessionId = activeSessionId,
                onNewSession = {
                    showSessionPicker = false
                    scope.launch {
                        val newId = repository.createSession("New Session")
                        viewModel.switchSession(newId)
                    }
                },
                onSwitchSession = {
                    showSessionPicker = false
                    viewModel.switchSession(it)
                },
                repository = repository,
                onModelSelected = { viewModel.changeActiveModel(it) },
                onOpenApiKeys = onOpenApiKeys
            )

        Column(Modifier.weight(1f).fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val showScrollToBottomButton by remember {
                derivedStateOf {
                    val lastVis = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = lazyListState.layoutInfo.totalItemsCount
                    total > 5 && lastVis < total - 2
                }
            }

            if (messages.isEmpty() && !isStreaming) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Section: Robot Icon + DeepCode Title + Subtitle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFF7A00), Color(0xFFFF5200))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(48.dp)) {
                                val w = size.width
                                val h = size.height

                                // Antenna post
                                val antennaW = w * 0.09f
                                val antennaH = h * 0.12f
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset((w - antennaW) / 2f, h * 0.08f),
                                    size = Size(antennaW, antennaH),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                                // Antenna top knob
                                val knobW = w * 0.18f
                                val knobH = h * 0.09f
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset((w - knobW) / 2f, h * 0.02f),
                                    size = Size(knobW, knobH),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )

                                // Side ears (left & right)
                                val earW = w * 0.09f
                                val earH = h * 0.32f
                                val earY = h * 0.36f
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(w * 0.06f, earY),
                                    size = Size(earW, earH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(w * 0.85f, earY),
                                    size = Size(earW, earH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )

                                // Head
                                val headW = w * 0.64f
                                val headH = h * 0.48f
                                val headX = (w - headW) / 2f
                                val headY = h * 0.28f
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(headX, headY),
                                    size = Size(headW, headH),
                                    cornerRadius = CornerRadius(14f, 14f)
                                )

                                // Eyes (orange cutouts)
                                val eyeSize = headW * 0.22f
                                val eyeY = headY + headH * 0.35f
                                val eyeCorner = 5f
                                drawRoundRect(
                                    color = Color(0xFFFF6400),
                                    topLeft = Offset(headX + headW * 0.20f, eyeY),
                                    size = Size(eyeSize, eyeSize),
                                    cornerRadius = CornerRadius(eyeCorner, eyeCorner)
                                )
                                drawRoundRect(
                                    color = Color(0xFFFF6400),
                                    topLeft = Offset(headX + headW * 0.58f, eyeY),
                                    size = Size(eyeSize, eyeSize),
                                    cornerRadius = CornerRadius(eyeCorner, eyeCorner)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Deep",
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = Color.White
                            )
                            Text(
                                "Code",
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = Color(0xFFFF6D00)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your AI coding companion",
                            color = Color(0xFF9CA3AF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // 2. Middle Cards (Horizontal Scrolling Carousel)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        item {
                            PlaceholderFeatureCard(
                                iconContent = {
                                    Text(
                                        "</>",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                title = "Explain this code",
                                subtitle = "Explain how recursion works with an example",
                                actionText = "Get explanation",
                                onClick = { inputMsg = "Explain how recursion works with an example" }
                            )
                        }
                        item {
                            PlaceholderFeatureCard(
                                iconContent = {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                title = "Debug help",
                                subtitle = "Why is my loop infinite?",
                                actionText = "Get help",
                                onClick = { inputMsg = "Why is my loop infinite?" }
                            )
                        }
                        item {
                            PlaceholderFeatureCard(
                                iconContent = {
                                    Text(
                                        "{ }",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                title = "Code style",
                                subtitle = "Validate code patterns and best practices",
                                actionText = "Check now",
                                onClick = { inputMsg = "Validate code patterns and best practices" }
                            )
                        }
                        item {
                            PlaceholderFeatureCard(
                                iconContent = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                title = "Generate code",
                                subtitle = "Write a function to parse JSON in Python",
                                actionText = "Generate",
                                onClick = { inputMsg = "Write a function to parse JSON in Python" }
                            )
                        }
                    }

                    // 3. Bottom Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF13151A))
                            .border(1.dp, Color(0xFF262933), RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2129))
                                    .border(1.dp, Color(0xFF323642), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "</>",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6D00)
                                )
                            }
                            Text(
                                "Built for developers. Powered by AI.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
            } else {
                    val streamingMsgId = streamingMessageId
                    val combinedItems = remember(groupedItems, isStreaming, streamingMsgId, orchestrationPlan, orchestrationExpanded) {
                        buildList {
                            addAll(groupedItems)
                            val p = orchestrationPlan
                            if (isStreaming && streamingMsgId.isNotEmpty()) {
                                add(ChatItem.Streaming(streamingMsgId))
                            } else if (p != null && p.overallStatus != OverallStatus.COMPLETE && p.overallStatus != OverallStatus.FAILED) {
                                add(ChatItem.OrchestrationPanel("orchestration"))
                            }
                        }.distinctBy { item ->
                            when (item) {
                                is ChatItem.NormalMessage -> "msg_${item.message.id}"
                                is ChatItem.ToolExecutionGroup -> "group_${item.groupId}"
                                is ChatItem.Streaming -> "msg_${item.messageId}"
                                is ChatItem.OrchestrationPanel -> "orch_${item.panelId}"
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    items(combinedItems, key = { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> "msg_${item.message.id}"
                            is ChatItem.ToolExecutionGroup -> "group_${item.groupId}"
                            is ChatItem.Streaming -> "msg_${item.messageId}"
                            is ChatItem.OrchestrationPanel -> "orch_${item.panelId}"
                        }
                    }, contentType = { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> "normal"
                            is ChatItem.ToolExecutionGroup -> "tool"
                            is ChatItem.Streaming -> "streaming"
                            is ChatItem.OrchestrationPanel -> "orchestration"
                        }
                    }) { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> {
                                val onSelect = remember(viewModel) { { layoutName: String -> viewModel.sendMessage("Use $layoutName layout") } }
                                val onSendSug = remember(viewModel) { { suggestion: String -> viewModel.sendMessage(suggestion) } }
                                MessageBubble(message = item.message, imageCache = imageCache, onSelectLayout = onSelect, onSendSuggestion = onSendSug)
                            }
                            is ChatItem.ToolExecutionGroup -> ToolExecutionGroupBubble(group = item)
                            is ChatItem.Streaming -> StreamingItem(
                                viewModel = viewModel,
                                imageCache = imageCache,
                                mediaProcessingType = mediaProcessingType,
                                mediaProcessingPrompt = mediaProcessingPrompt
                            )
                            is ChatItem.OrchestrationPanel -> {
                                val p = orchestrationPlan
                                if (p != null) {
                                    AgentPanel(
                                        plan = p,
                                        isExpanded = orchestrationExpanded,
                                        onToggle = { viewModel.toggleOrchestrationPanel() },
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ScrollToBottomButton(visible = showScrollToBottomButton, lazyListState = lazyListState)
        }

        if (agentsWorking) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppPrimary.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Agents working in background — you can still message me",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .clickable { viewModel.cancelOrchestration() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 8.dp)
        ) {
            if (attachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.width(4.dp))
                    for (file in attachedFiles) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { viewModel.removeAttachedFile(file.name) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }



            // 1:1 Floating Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Input capsule pill
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { filePickerLauncher.launch("*/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    BasicTextField(
                        value = inputMsg,
                        onValueChange = { inputMsg = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        maxLines = 5,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (inputMsg.isEmpty()) {
                                    Text(
                                        "Ask anything...",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputMsg.isNotEmpty() || attachedFiles.isNotEmpty()) {
                                viewModel.sendMessage(inputMsg)
                                inputMsg = ""
                            }
                        })
                    )

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                Toast.makeText(context, "Voice input...", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Right action button: Send circle when typing/streaming, Green Voice Wave circle when empty
                val isSendActive = inputMsg.isNotBlank() || attachedFiles.isNotEmpty() || isStreaming
                if (isSendActive) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isStreaming) Color(0xFFDC2626) else Color.White)
                            .clickable {
                                if (isStreaming) {
                                    viewModel.cancelActiveChat()
                                } else if (inputMsg.isNotEmpty() || attachedFiles.isNotEmpty()) {
                                    viewModel.sendMessage(inputMsg)
                                    inputMsg = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (isStreaming) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AppPrimary, AppPrimaryGradientEnd)))
                            .clickable {
                                Toast.makeText(context, "Voice mode activated", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        VoiceWaveIcon(color = Color.White)
                    }
                }
            }
        }
        }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentSelected = activeModel,
            onModelSelected = { viewModel.changeActiveModel(it); showModelPicker = false },
            onDismiss = { showModelPicker = false }
        )
    }

    if (showPersonaPicker) {
        PersonaPickerDialog(
            activePersonaName = personaState.value,
            onPersonaSelected = { name ->
                personaState.value = name
                showPersonaPicker = false
            },
            onDismiss = { showPersonaPicker = false },
            securePrefs = repository.securePrefs
        )
    }

    if (pendingToolCall != null) {
        AlertDialog(
            onDismissRequest = { viewModel.denyToolCall() },
            title = { Text("Permission Required") },
            text = {
                Column {
                    Text("The AI model wants to run a potentially destructive tool:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tool Name: ${pendingToolCall?.name}", color = MaterialTheme.colorScheme.primary)
                    Text("Arguments: ${pendingToolCall?.arguments}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.approveToolCall() }) { Text("Approve / Execute", color = MaterialTheme.colorScheme.primary) } },
            dismissButton = { TextButton(onClick = { viewModel.denyToolCall() }) { Text("Deny", color = Color.Red) } }
        )
    }
}

@Composable
private fun BoxScope.ScrollToBottomButton(visible: Boolean, lazyListState: LazyListState) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    ) {
        val scope = rememberCoroutineScope()
        FloatingActionButton(
            onClick = { scope.launch {
                val total = lazyListState.layoutInfo.totalItemsCount
                if (total > 0) lazyListState.animateScrollToItem(total - 1)
            }},
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun MenuTwoBarsIcon(color: Color = Color.White, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(18.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.width(18.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
fun VoiceWaveIcon(color: Color = Color.White, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.width(3.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

// ═══════════════════════════════════════════════
// Top Bar composable (1:1 with design)
// ═══════════════════════════════════════════════
@Composable
private fun TopBar(
    sessionTitle: String,
    isStreaming: Boolean,
    activeModel: AIModel,
    onMenuClick: () -> Unit,
    showSessionPicker: Boolean,
    onToggleSessionPicker: (Boolean) -> Unit,
    sessions: List<ChatSession>,
    activeSessionId: String,
    onNewSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    repository: DeepCodeRepository,
    onModelSelected: (AIModel) -> Unit,
    onOpenApiKeys: () -> Unit = {}
) {
    var expandedSelectorDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Circular dark button with 2 horizontal bars
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E))
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            MenuTwoBarsIcon(color = Color.White)
        }

        // Right: Model selection capsule pill [ deepseek v4 flash  ⋮ ]
        Box {
            val modelDisplayName = activeModel.name.lowercase().ifEmpty { "deepseek v4 flash" }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { expandedSelectorDropdown = !expandedSelectorDropdown }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelDisplayName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 170.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Select model",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expandedSelectorDropdown) {
                val imeInsets = WindowInsets.ime
                val density = LocalDensity.current
                val imeBottom by remember { derivedStateOf { imeInsets.getBottom(density) } }
                val offsetPx = with(density) { (44.dp.roundToPx() - imeBottom) }
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, offsetPx),
                    onDismissRequest = { expandedSelectorDropdown = false }
                ) {
                    ModelSelectionOverlay(
                        repository = repository,
                        activeModel = activeModel,
                        onModelSelected = { model ->
                            onModelSelected(model)
                            expandedSelectorDropdown = false
                        },
                        onOpenApiKeys = {
                            expandedSelectorDropdown = false
                            onOpenApiKeys()
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Message Bubble (User + Assistant)
// ═══════════════════════════════════════════════
@Composable
fun MessageBubble(
    message: Message,
    imageCache: Map<String, ImageBitmap> = emptyMap(),
    onSelectLayout: (String) -> Unit = {},
    onSendSuggestion: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    if (message.isToolCall || message.role == "tool") return

    val cleanedContent = remember(message.id, message.content, isUser) {
        val raw = if (message.content.endsWith("[INTERRUPTED]")) message.content.substringBeforeLast("[INTERRUPTED]").trim()
        else message.content
        if (isUser) raw.trim() else stripThinkingProcess(raw, isStreaming = false)
    }

    if (cleanedContent.isEmpty()) return

    val isInterrupted = message.content.endsWith("[INTERRUPTED]")
    val parsedParts = remember(cleanedContent, isUser) { parseMessageContent(cleanedContent, isUser) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!isInterrupted || cleanedContent.isNotEmpty()) {
            if (isUser) {
                UserBubble(cleanedContent = cleanedContent, parsedParts = parsedParts, message = message, context = context)
            } else {
                AiBubble(cleanedContent = cleanedContent, parsedParts = parsedParts, message = message, imageCache = imageCache, onSelectLayout = onSelectLayout, onSendSuggestion = onSendSuggestion)
            }
        }
        if (isInterrupted) InterruptedIndicator()
    }
}

@Composable
private fun UserBubble(
    cleanedContent: String,
    parsedParts: List<MessageContentPart>,
    message: Message,
    context: android.content.Context
) {
    val showMenu = remember { mutableStateOf(false) }
    val pressOffset = remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    val shouldTruncate = remember(cleanedContent) {
        cleanedContent.length > 200 || cleanedContent.lines().size > 6
    }

    val bubbleBg = if (isDarkThemeActive) {
        AppPrimary.copy(alpha = 0.32f).compositeOver(Color(0xFF0D0D0D))
    } else {
        AppPrimary.copy(alpha = 0.15f).compositeOver(Color.White)
    }

    Box(
        modifier = Modifier
            .widthIn(min = 48.dp, max = 300.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bubbleBg)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressOffset.value = offset
                    showMenu.value = true
                })
            }
    ) {
        Column {
            val contentToDisplay = if (shouldTruncate && !isExpanded) {
                val lines = cleanedContent.lines().take(5)
                lines.joinToString("\n")
            } else {
                cleanedContent
            }

            Text(
                text = contentToDisplay,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Color.White
            )

            if (shouldTruncate) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Show less ⌃" else "Show more ⌵",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (showMenu.value) {
            TextContextMenu(showMenu = showMenu, pressOffset = pressOffset, text = cleanedContent, context = context)
        }
    }
}

@Composable
private fun AiBubble(
    cleanedContent: String,
    parsedParts: List<MessageContentPart>,
    message: Message,
    imageCache: Map<String, ImageBitmap>,
    onSelectLayout: (String) -> Unit = {},
    onSendSuggestion: (String) -> Unit = {}
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.Start) {
        val showAiMenu = remember { mutableStateOf(false) }
        val pressOffset = remember { mutableStateOf(Offset.Zero) }
        val haptic = LocalHapticFeedback.current

        Box(modifier = Modifier.fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressOffset.value = offset; showAiMenu.value = true
                })
            }) {
            Column {
                val groupedParts = remember(parsedParts) {
                    val result = mutableListOf<Any>()
                    var currentTools = mutableListOf<MessageContentPart.ToolCall>()
                    for (part in parsedParts) {
                        if (part is MessageContentPart.ToolCall) {
                            currentTools.add(part)
                        } else {
                            if (currentTools.isNotEmpty()) {
                                result.add(currentTools.toList())
                                currentTools = mutableListOf()
                            }
                            result.add(part)
                        }
                    }
                    if (currentTools.isNotEmpty()) {
                        result.add(currentTools.toList())
                    }
                    result
                }

                groupedParts.forEach { item ->
                    when (item) {
                        is List<*> -> {
                            // Suppressed: Tool call execution cards are hidden
                        }
                        is MessageContentPart.Attachment -> {
                            Row(modifier = Modifier.padding(bottom = 10.dp).background(AppDivider, RoundedCornerShape(10.dp))
                                .border(1.dp, AppBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.List, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(item.filename, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, fontFamily = FontFamily.Monospace)
                            }
                        }
                        is MessageContentPart.Code -> CodeBlock(code = item.code, language = item.language)
                        is MessageContentPart.Markdown -> MarkdownText(text = item.text, imageCache = imageCache, onSendSuggestion = onSendSuggestion)
                        is MessageContentPart.Audio -> AudioPlayer(part = item)
                        is MessageContentPart.Video -> VideoPlayer(videoUrl = item.url)
                        is MessageContentPart.PlainText -> Text(item.text, fontSize = 16.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
                        is MessageContentPart.Thought -> {
                            // Suppressed: Thinking Process card is hidden
                        }
                        is MessageContentPart.FileAttachment -> FileCard(filePath = item.filePath)
                        is MessageContentPart.LayoutSelector -> LayoutSelectorCard(onSelect = onSelectLayout)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val timeStr = remember(message.timestamp) {
                    try { timeFormatter.get()?.format(java.util.Date(message.timestamp)) ?: "" } catch (e: Exception) { "" }
                }
                if (timeStr.isNotEmpty()) {
                    Text(timeStr, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.End))
                }
            }
            if (showAiMenu.value) {
                TextContextMenu(showMenu = showAiMenu, pressOffset = pressOffset, text = cleanedContent, context = context)
            }
        }
    }
}

@Composable
private fun AudioPlayer(part: MessageContentPart.Audio) {
    val context = LocalContext.current
    val isPlaying = remember { mutableStateOf(false) }
    val isPrepared = remember { mutableStateOf(false) }
    val speedList = remember { listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.5f) }
    val speedIndex = remember { mutableStateOf(0) }

    val cleanPath = remember(part.filePath) {
        part.filePath.removePrefix("file://").trim()
    }
    val audioFile = remember(cleanPath) { File(cleanPath) }

    fun applySpeed(mp: MediaPlayer?, speed: Float) {
        if (mp == null) return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isPrepared.value) {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
                if (!isPlaying.value) {
                    mp.pause()
                }
            }
        } catch (e: Exception) {
            ai.deepcode.android.util.AppLogger.e("AudioPlayer", "Error setting speed", e)
        }
    }

    val mediaPlayer = remember(cleanPath) {
        if (audioFile.exists()) {
            MediaPlayer().apply {
                try {
                    setDataSource(context, android.net.Uri.fromFile(audioFile))
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        isPrepared.value = true
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                val params = mp.playbackParams
                                params.speed = speedList[speedIndex.value]
                                mp.playbackParams = params
                                mp.pause()
                            }
                        } catch (_: Exception) {}
                    }
                    setOnCompletionListener { isPlaying.value = false }
                    setOnErrorListener { _, _, _ -> isPlaying.value = false; isPrepared.value = false; true }
                    prepareAsync()
                } catch (e: Exception) {
                    ai.deepcode.android.util.AppLogger.e("AudioPlayer", "Error preparing audio", e)
                }
            }
        } else null
    }

    DisposableEffect(cleanPath) {
        onDispose {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {}
        }
    }

    val speedText = remember(speedIndex.value) {
        val s = speedList[speedIndex.value]
        if (s == 1.0f || s == 2.0f) "${s.toInt()}x" else "${s}x"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AppDivider, RoundedCornerShape(10.dp))
            .border(1.dp, AppBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppPrimary.copy(alpha = 0.15f))
                .clickable {
                    if (!audioFile.exists()) {
                        Toast.makeText(context, "Audio file missing: ${audioFile.name}", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    val mp = mediaPlayer ?: return@clickable
                    try {
                        if (isPlaying.value) {
                            mp.pause()
                            isPlaying.value = false
                        } else {
                            if (!isPrepared.value) {
                                Toast.makeText(context, "Loading audio...", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            applySpeed(mp, speedList[speedIndex.value])
                            mp.start()
                            isPlaying.value = true
                        }
                    } catch (e: Exception) {
                        isPlaying.value = false
                        Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(6.dp)
        ) {
            Icon(
                imageVector = if (isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying.value) "Pause" else "Play",
                tint = AppPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = audioFile.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppField)
                .border(1.dp, AppBorder, RoundedCornerShape(8.dp))
                .clickable {
                    val nextIdx = (speedIndex.value + 1) % speedList.size
                    speedIndex.value = nextIdx
                    applySpeed(mediaPlayer, speedList[nextIdx])
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = speedText,
                color = AppPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


@Composable
private fun TextContextMenu(
    showMenu: MutableState<Boolean>,
    pressOffset: MutableState<Offset>,
    text: String,
    context: android.content.Context
) {
    Popup(alignment = Alignment.TopStart, offset = IntOffset(pressOffset.value.x.toInt(), pressOffset.value.y.toInt()),
        onDismissRequest = { showMenu.value = false }) {
        Surface(modifier = Modifier.widthIn(min = 100.dp, max = 160.dp), shape = RoundedCornerShape(12.dp),
            color = AppDivider, border = BorderStroke(1.dp, AppBorder)) {
            Column {
                DropdownMenuItem(text = { Text("Copy", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu.value = false
                        try { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DeepCode", text)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() } catch (_: Exception) { }
                    })
                DropdownMenuItem(text = { Text("Share", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu.value = false
                        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share message")) } catch (_: Exception) { }
                    })
                DropdownMenuItem(text = { Text("Save Image", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu.value = false
                        saveImagesFromMessage(text, context)
                    })
            }
        }
    }
}

private fun saveImagesFromMessage(text: String, context: Context) {
    val refs = mutableListOf<String>()
    RE_IMAGE_TAG.findAll(text).forEach { refs.add(it.groupValues[1].trim()) }
    RE_MARKDOWN_IMAGE.findAll(text).forEach {
        val url = it.groupValues[2].trim()
        if (url.startsWith("http") || url.startsWith("file")) refs.add(url)
    }
    RE_DIRECT_IMG.findAll(text).forEach { refs.add(it.value) }
    val unique = refs.distinct()
    if (unique.isEmpty()) {
        Toast.makeText(context, "No image in this message", Toast.LENGTH_SHORT).show()
        return
    }
    val saved = mutableListOf<String>()
    val failed = mutableListOf<String>()
    Thread {
        unique.forEach { ref ->
            try {
                val file = java.io.File(ref.removePrefix("file://"))
                if (file.exists()) saveImageFileToGallery(context, file)?.let { saved.add(it) } ?: failed.add(ref)
                else if (ref.startsWith("http")) downloadImageToGallery(context, ref)?.let { saved.add(it) } ?: failed.add(ref)
                else failed.add(ref)
            } catch (e: Exception) {
                AppLogger.e("SaveImage", "Failed to save $ref: ${e.message}")
                failed.add(ref)
            }
        }
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context,
                if (saved.isNotEmpty()) "Saved ${saved.size} image(s) to Pictures/DeepCode" else "Failed to save image",
                Toast.LENGTH_LONG).show()
        }
    }.start()
}

private fun saveImageFileToGallery(context: Context, file: java.io.File): String? {
    val bytes = file.readBytes()
    if (bytes.isEmpty()) return null
    return insertIntoMediaStore(context, bytes, file.extension.ifBlank { "png" })
}

private fun downloadImageToGallery(context: Context, url: String): String? {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 25000
    conn.readTimeout = 45000
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
    conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
    return try {
        if (conn.responseCode != 200) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        if (bytes.size <= 100) return null
        val ext = url.substringAfterLast(".", "png").substringBefore("?").takeIf { it.length in 3..5 } ?: "png"
        insertIntoMediaStore(context, bytes, ext)
    } finally {
        conn.disconnect()
    }
}

private fun insertIntoMediaStore(context: Context, bytes: ByteArray, ext: String): String? {
    val safeExt = if (ext.startsWith(".")) ext else ".$ext"
    val values = android.content.ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "DeepCode_${System.currentTimeMillis()}$safeExt")
        put(MediaStore.Images.Media.MIME_TYPE, "image/${ext.lowercase().replace("jpg", "jpeg")}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeepCode")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DeepCode")
            if (!dir.exists()) dir.mkdirs()
            put(MediaStore.Images.Media.DATA, java.io.File(dir, "DeepCode_${System.currentTimeMillis()}$safeExt").absolutePath)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        uri.toString()
    } catch (e: Exception) {
        context.contentResolver.delete(uri, null, null)
        null
    }
}

// ═══════════════════════════════════════════════
// Streaming Bubble
// ═══════════════════════════════════════════════
@Composable
private fun StreamingItem(
    viewModel: ChatViewModel,
    imageCache: Map<String, ImageBitmap>,
    mediaProcessingType: String?,
    mediaProcessingPrompt: String
) {
    val streamedText by viewModel.streamedText.collectAsStateWithLifecycle(initialValue = "")
    StreamingBubble(text = streamedText, imageCache = imageCache)
}

// ═══════════════════════════════════════════════
@Composable
fun StreamingBubble(text: String, imageCache: Map<String, ImageBitmap> = emptyMap()) {
    val (liveThought, cleanText) = remember(text) {
        extractThoughtAndCleanText(text, isStreaming = true)
    }
    val isImageGenerating = remember(cleanText) {
        cleanText.contains("Generating image", ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (cleanText.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Thinking status pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF16181D))
                        .border(1.dp, Color(0xFF262933), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PulsatingBrainIcon()
                    Text(
                        text = if (liveThought.isNotEmpty()) "Reasoning through solution..." else "Thinking...",
                        color = Color(0xFFFF6D00),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(2.dp))
                    ThreeDotLoader()
                }

                // If the model is outputting live reasoning tokens, stream them live
                if (liveThought.isNotEmpty()) {
                    LiveThoughtCard(thought = liveThought)
                }
            }
        } else if (isImageGenerating) {
            ImageGenerationSkeleton(statusText = cleanText)
        } else {
            if (liveThought.isNotEmpty()) {
                ThoughtBlock(thought = liveThought)
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    MarkdownText(text = cleanText, imageCache = imageCache)
                }
                BlinkingCursor()
            }
        }
    }
}

@Composable
fun PulsatingBrainIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = "Thinking",
        tint = Color(0xFFFF6D00),
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}

@Composable
fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp, bottom = 4.dp)
            .width(2.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(Color(0xFFFF6D00))
    )
}

@Composable
fun LiveThoughtCard(thought: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF262933), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = Color(0xFFFF6D00), modifier = Modifier.size(15.dp))
                Text(
                    "Thought Process",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9E9EA7),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                thought.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCCCCCC),
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ThreeDotLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "dotAnim")
    @Composable
    fun anim(delay: Int) = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = delay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )
    val dot1 = anim(0); val dot2 = anim(200); val dot3 = anim(400)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).alpha(dot1.value).background(Color(0xFFFF6D00), CircleShape))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(6.dp).alpha(dot2.value).background(Color(0xFFFF6D00), CircleShape))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(6.dp).alpha(dot3.value).background(Color(0xFFFF6D00), CircleShape))
    }
}

@Composable
fun BlinkingRobotIcon() {
    Icon(Icons.Default.SmartToy, "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
}

// ═══════════════════════════════════════════════
// Thought Block
// ═══════════════════════════════════════════════
@Composable
fun ThoughtBlock(thought: String) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .border(0.5.dp, AppBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = AppDivider.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thinking Process", style = MaterialTheme.typography.labelMedium, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            if (expanded || thought.length < 50) {
                Spacer(Modifier.height(6.dp))
                Text(thought.trim(), style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, fontStyle = FontStyle.Italic)
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Interrupted Indicator
// ═══════════════════════════════════════════════
@Composable
fun InterruptedIndicator() {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(0.6.dp).background(MaterialTheme.colorScheme.outline))
        Text("interrupted", Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Box(Modifier.weight(1f).height(0.6.dp).background(MaterialTheme.colorScheme.outline))
    }
}

// ═══════════════════════════════════════════════
// Model Picker Dialog
// ═══════════════════════════════════════════════
@Composable
fun ModelPickerDialog(
    currentSelected: AIModel,
    onModelSelected: (AIModel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    val allModels = remember { AIProviderFactory.providers.flatMap { it.models } }
    val filteredModels = allModels.filter {
        it.name.contains(searchFilter, ignoreCase = true) || it.provider.contains(searchFilter, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select AI Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(300.dp)) {
                OutlinedTextField(value = searchFilter, onValueChange = { searchFilter = it },
                    label = { Text("Search models...") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(filteredModels, key = { it.id }) { model ->
                        Row(Modifier.fillMaxWidth().clickable { onModelSelected(model) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(model.name, fontWeight = FontWeight.Bold,
                                    color = if (model.id == currentSelected.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                val displayProvider = if (model.provider == "Zen (Free)" || model.provider == "Zen AI") "Zen AI" else model.provider
                                Text("$displayProvider • Context: ${model.contextWindow}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            AssistChip(onClick = {}, label = { Text(model.badge, fontSize = 9.sp) })
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ═══════════════════════════════════════════════
// Persona Picker content + dialog
// ═══════════════════════════════════════════════
@Composable
fun PersonaPickerContent(
    activePersonaName: String,
    onPersonaSelected: (String) -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs
) {
    var searchFilter by remember { mutableStateOf("") }
    val allPersonas = remember {
        val custom = try {
            val raw = securePrefs.getSetting("saved_personas", "[]")
            val json = org.json.JSONArray(raw)
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                Persona(id = obj.getString("id"), name = obj.getString("name"), content = obj.getString("content"), isSystem = false)
            }
        } catch (_: Exception) { emptyList<Persona>() }
        builtInPersonas + custom
    }
    val filteredPersonas = allPersonas.filter {
        it.name.contains(searchFilter, ignoreCase = true)
    }

    Column(modifier = Modifier.width(280.dp).clip(RoundedCornerShape(12.dp))
        .background(AppCard).border(1.dp, AppDivider, RoundedCornerShape(12.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Text("Select Persona", fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = AppWhite, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))

        OutlinedTextField(value = searchFilter, onValueChange = { searchFilter = it },
            placeholder = { Text("Search...", fontSize = 12.sp, color = AppMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = AppWhite),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppMuted, modifier = Modifier.size(16.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppPrimary.copy(alpha = 0.5f),
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppField,
                unfocusedContainerColor = AppField
            ),
            shape = RoundedCornerShape(8.dp))

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                PersonaDarkRow(
                    label = "Default (DeepCode)",
                    isSelected = activePersonaName == "Default",
                    onClick = {
                        securePrefs.saveSetting("persona_enabled", "false")
                        securePrefs.saveSetting("custom_persona", "")
                        onPersonaSelected("Default")
                    }
                )
            }
            item {
                Text("Your Personas", fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = AppMuted, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp))
            }
            val customPersonas = filteredPersonas.filter { !it.isSystem }
            if (customPersonas.isEmpty()) {
                item {
                    Text("No custom personas", fontSize = 11.sp, color = AppMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
                }
            } else {
                items(customPersonas, key = { "custom_${it.name}" }) { persona ->
                    PersonaDarkRow(
                        label = persona.name, subtitle = "Custom",
                        isSelected = activePersonaName == persona.name,
                        onClick = {
                            securePrefs.saveSetting("custom_persona", persona.content)
                            securePrefs.saveSetting("persona_enabled", "true")
                            onPersonaSelected(persona.name)
                        }
                    )
                }
            }
            item {
                Text("Prebuilt Personas", fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = AppMuted, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp))
            }
            val systemPersonas = filteredPersonas.filter { it.isSystem }
            items(systemPersonas, key = { "system_${it.name}" }) { persona ->
                PersonaDarkRow(
                    label = persona.name, subtitle = "Prebuilt",
                    isSelected = activePersonaName == persona.name,
                    onClick = {
                        securePrefs.saveSetting("custom_persona", persona.content)
                        securePrefs.saveSetting("persona_enabled", "true")
                        onPersonaSelected(persona.name)
                    }
                )
            }
        }
    }
}

@Composable
fun PersonaPickerDialog(
    activePersonaName: String,
    onPersonaSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs
) {
    Popup(onDismissRequest = onDismiss, alignment = Alignment.TopCenter, offset = IntOffset(0, 160)) {
        PersonaPickerContent(
            activePersonaName = activePersonaName,
            onPersonaSelected = onPersonaSelected,
            securePrefs = securePrefs
        )
    }
}

@Composable
private fun PersonaDarkRow(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .then(if (isSelected) Modifier.background(AppPrimary.copy(alpha = 0.08f)).border(1.dp, AppPrimary, RoundedCornerShape(8.dp))
                else Modifier.background(AppField).border(1.dp, AppDivider, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = if (isSelected) AppPrimary else AppWhite)
            if (subtitle != null) {
                Text(subtitle, fontSize = 9.sp, color = AppMuted,
                    fontFamily = FontFamily.Monospace)
            }
        }
        if (isSelected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(AppPrimary))
        }
    }
}

// ═══════════════════════════════════════════════
// Tool Execution Group Bubble
// ═══════════════════════════════════════════════
@Composable
fun ToolExecutionGroupBubble(group: ChatItem.ToolExecutionGroup, modifier: Modifier = Modifier) {
    val toolMessages = group.toolMessages
    data class DisplayItem(val id: String, val name: String, val argsJson: String?, val result: String)
    val toolCalls = remember(toolMessages) {
        val list = mutableListOf<DisplayItem>()
        val toolCallMsgs = toolMessages.filter { it.isToolCall && it.toolCallsJson != null }
        for (callMsg in toolCallMsgs) {
            try {
                val jsonStr = callMsg.toolCallsJson ?: ""
                val element = com.google.gson.JsonParser.parseString(jsonStr)
                val jsonObject = when {
                    element.isJsonArray && element.asJsonArray.size() > 0 && element.asJsonArray.get(0).isJsonObject -> element.asJsonArray.get(0).asJsonObject
                    element.isJsonObject -> element.asJsonObject
                    else -> null
                }
                val id = jsonObject?.get("id")?.asString ?: ""
                val name = jsonObject?.get("name")?.asString ?: "tool"
                val args = jsonObject?.get("arguments")?.toString() ?: jsonObject?.get("args")?.toString()
                val responseMsg = toolMessages.find { msg ->
                    if (msg.role != "tool") return@find false
                    val resJsonStr = msg.toolCallsJson ?: ""
                    if (resJsonStr == id) true
                    else try {
                        val parsed = com.google.gson.JsonParser.parseString(resJsonStr)
                        if (parsed.isJsonObject) parsed.asJsonObject.get("id")?.asString == id else resJsonStr.contains(id)
                    } catch (_: Exception) {
                        resJsonStr.contains(id)
                    }
                }
                list.add(DisplayItem(id, name, args, responseMsg?.content ?: "Executing..."))
            } catch (_: Exception) {
                val idRegex = """ "id"\s*:\s*"([^"]+)" """.toRegex()
                val nameRegex = """ "name"\s*:\s*"([^"]+)" """.toRegex()
                val id = idRegex.find(callMsg.toolCallsJson ?: "")?.groups?.get(1)?.value ?: ""
                val name = nameRegex.find(callMsg.toolCallsJson ?: "")?.groups?.get(1)?.value ?: "tool"
                val responseMsg = toolMessages.find { msg ->
                    if (msg.role != "tool") return@find false
                    val resJsonStr = msg.toolCallsJson ?: ""
                    if (resJsonStr == id) true else resJsonStr.contains(id)
                }
                list.add(DisplayItem(id, name, null, responseMsg?.content ?: "Executing..."))
            }
        }
        if (list.isEmpty()) {
            toolMessages.forEach { msg ->
                if (msg.role == "tool") list.add(DisplayItem(msg.id, "Tool Output", null, msg.content))
                else if (msg.isToolCall) list.add(DisplayItem(msg.id, "Tool Call", null, msg.content))
            }
        }
        list
    }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        toolCalls.forEach { item ->
            val status = if (item.result.startsWith("Error") || item.result.contains("failed", ignoreCase = true)) "FAILED" else if (item.result == "Executing...") "RUNNING" else "SUCCESS"
            ToolCallCard(toolName = item.name, status = status, result = item.result, argsJson = item.argsJson, modifier = Modifier.fillMaxWidth())
            val mediaRx = remember(item.result) { Regex("""\[(image|audio|video|file):[^\]]+\]""") }
            if (mediaRx.containsMatchIn(item.result)) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    MarkdownText(text = item.result)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Model Selection Overlay
// ═══════════════════════════════════════════════
@Composable
fun ModelSelectionOverlay(
    repository: DeepCodeRepository,
    activeModel: AIModel,
    onModelSelected: (AIModel) -> Unit,
    onOpenApiKeys: () -> Unit = {}
) {
    val securePrefs = repository.securePrefs
    val catalog by ModelCatalog.models.collectAsStateWithLifecycle()

    fun isProviderConfigured(provider: AIProvider): Boolean {
        if (provider.isFree) return true
        val storageId = providerStorageId(provider.name)
        val key = ApiKeyRotator.getNextAvailableKey(securePrefs, storageId)?.first
            ?: securePrefs.getApiKey(storageId)
        if (key.isNotEmpty()) return true
        if (securePrefs.getSetting("oauth_token_$storageId", "").isNotEmpty()) return true
        if (securePrefs.getSetting("web_cookie_$storageId", "").isNotEmpty()) return true
        return false
    }

    val configuredProviders = remember {
        AIProviderFactory.providers.filter { isProviderConfigured(it) }
    }

    var expandedProviderName by remember {
        mutableStateOf(if (configuredProviders.any { it.name == activeModel.provider }) activeModel.provider else configuredProviders.firstOrNull()?.name ?: "")
    }

    LaunchedEffect(expandedProviderName) {
        if (expandedProviderName.isNotEmpty()) {
            val prov = configuredProviders.find { it.name == expandedProviderName }
            if (prov != null && !catalog.containsKey(expandedProviderName)) {
                val storageId = providerStorageId(expandedProviderName)
                var apiKey = ApiKeyRotator.getNextAvailableKey(securePrefs, storageId)?.first
                    ?: securePrefs.getApiKey(storageId)
                if (apiKey.isEmpty()) {
                    apiKey = securePrefs.getSetting("oauth_token_$storageId", "")
                }
                if (apiKey.isNotEmpty()) {
                    if (expandedProviderName == "Antigravity") {
                        val models = fetchAntigravityModels(apiKey.split("||")[0])
                        if (models.isNotEmpty()) ModelCatalog.setModels(expandedProviderName, models)
                    } else {
                        val baseUrl = providerDefaultBaseUrl(expandedProviderName)
                        val models = fetchModels(apiKey, baseUrl, expandedProviderName)
                        if (models.isNotEmpty()) ModelCatalog.setModels(expandedProviderName, models)
                    }
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.width(280.dp).heightIn(max = 480.dp).clip(RoundedCornerShape(12.dp))
        .background(AppCard).border(1.dp, AppDivider, RoundedCornerShape(12.dp))
        .verticalScroll(scrollState).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (configuredProviders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "No API Key",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No API Key Configured",
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Add an API key in Settings to unlock AI models and start chatting.",
                    color = AppMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B))
                        .clickable { onOpenApiKeys() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Add API Key",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            configuredProviders.forEach { provider ->
                val isProviderExpanded = expandedProviderName == provider.name
                val providerColor = when (provider.name) {
                    "Google Gemini" -> Color(0xFF8B5CF6); "Zen AI" -> Color(0xFF8B5CF6); "Zen (Free)" -> Color(0xFF8B5CF6)
                    "Mistral AI" -> Color(0xFFEC4899); "Ollama Cloud" -> Color(0xFF6B7280)
                    "Omniroute" -> Color(0xFF10B981); else -> Color(0xFF8B5CF6)
                }

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(AppField).border(1.dp, AppDivider, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expandedProviderName = if (isProviderExpanded) "" else provider.name }
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProviderMiniLogo(provider.name)
                            Spacer(Modifier.width(10.dp))
                            Text(if (provider.name == "Zen (Free)" || provider.name == "Zen AI") "Zen AI" else provider.name,
                                color = if (isProviderExpanded) providerColor else AppWhite,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Icon(if (isProviderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                            tint = if (isProviderExpanded) providerColor else AppMuted, modifier = Modifier.size(18.dp))
                    }

                    if (isProviderExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.animateContentSize()) {
                            val fetchedModels = catalog[provider.name].orEmpty()
                            val allModels = if (fetchedModels.isNotEmpty()) fetchedModels else provider.models
                            val filterPref = securePrefs.getSetting("model_filter_${provider.name}", "all")
                            val filteredModels = when (filterPref) {
                                "free" -> allModels.filter { it.isFree || it.badge == "Free" }
                                "paid" -> allModels.filter { !it.isFree && it.badge != "Free" }
                                else -> allModels
                            }
                            filteredModels.forEach { model ->
                                val isSelected = activeModel.id == model.id
                                val rowModifier = if (isSelected) {
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.08f))
                                        .border(1.dp, Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                                } else Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                Row(Modifier.then(rowModifier).clickable { onModelSelected(model) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(model.name, color = AppWhite, fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                                    else if (model.isFree) Text("FREE", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Icon(Icons.Default.Info, null, tint = AppMuted, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("All models are provided by ${if (provider.name == "Zen (Free)" || provider.name == "Zen AI") "Zen AI" else provider.name}.",
                                    fontSize = 10.sp, color = AppMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderMiniLogo(providerName: String) {
    val (bgColor, textColor, label) = when (providerName) {
        "Google Gemini" -> Triple(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6), "G")
        "Zen AI" -> Triple(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6), "Z")
        "Zen (Free)" -> Triple(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6), "Z")
        "Mistral AI" -> Triple(Color(0xFFEC4899).copy(alpha = 0.15f), Color(0xFFEC4899), "M")
        "Ollama Cloud" -> Triple(Color(0xFF6B7280).copy(alpha = 0.15f), Color(0xFF6B7280), "Ol")
        "Omniroute" -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "Om")
        "Antigravity" -> Triple(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6), "Ag")
        "Agent Router" -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "Ar")
        else -> Triple(AppDivider, AppWhite, providerName.take(2).uppercase())
    }
    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(bgColor),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ═══════════════════════════════════════════════
// ChatItem sealed class
// ═══════════════════════════════════════════════
sealed class ChatItem {
    data class NormalMessage(val message: Message) : ChatItem()
    data class ToolExecutionGroup(val groupId: String, val toolMessages: List<Message>) : ChatItem()
    data class Streaming(val messageId: String) : ChatItem()
    data class OrchestrationPanel(val panelId: String) : ChatItem()
}

// ═══════════════════════════════════════════════
// Group consecutive tool messages into ToolExecutionGroups
// ═══════════════════════════════════════════════
private fun groupChatMessages(messages: List<Message>): List<ChatItem> {
    val result = mutableListOf<ChatItem>()
    for (msg in messages) {
        if (!msg.isToolCall && msg.role != "tool") {
            result.add(ChatItem.NormalMessage(msg))
        }
    }
    return result
}

// ═══════════════════════════════════════════════
// ThreadLocal time formatter for message timestamps
// ═══════════════════════════════════════════════
val timeFormatter = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.getDefault()) }

// ═══════════════════════════════════════════════
// Attached File data class
// ═══════════════════════════════════════════════
data class AttachedFile(
    val name: String,
    val content: String,
    val size: Long,
    val filePath: String? = null
)

// ═══════════════════════════════════════════════
// ChatViewModel
// ═══════════════════════════════════════════════
class ChatViewModel(private val repository: DeepCodeRepository) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _pendingToolCall = MutableStateFlow<ToolCall?>(null)
    val pendingToolCall = _pendingToolCall.asStateFlow()

    private val _activeModel = MutableStateFlow(AIModel("", "", "Zen AI", false, "1M", "Paid"))
    val activeModel = _activeModel.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles = _attachedFiles.asStateFlow()

    private val _orchestrationPlan = MutableStateFlow<TaskPlan?>(null)
    val orchestrationPlan = _orchestrationPlan.asStateFlow()

    private val _orchestrationExpanded = MutableStateFlow(false)
    val orchestrationExpanded = _orchestrationExpanded.asStateFlow()

    private val _agentsWorking = MutableStateFlow(false)
    val agentsWorking = _agentsWorking.asStateFlow()

    private val _orchestratedResult = MutableStateFlow<String?>(null)
    val orchestratedResult = _orchestratedResult.asStateFlow()

    private val _streamedText = MutableStateFlow("")
    // Direct StateFlow emission for zero-lag 60fps token streaming with Compose snapshot coalescing
    val streamedText: StateFlow<String> = _streamedText.asStateFlow()

    private val _streamingMessageId = MutableStateFlow("")
    val streamingMessageId = _streamingMessageId.asStateFlow()

    private var _deferredResponse = ""
    private var toolCallDepth = 0
    private val maxToolCallDepth = 5
    // Tracks consecutive web_search calls in a single turn (reset on each sendMessage)
    private var consecutiveWebSearches = 0
    private val executedToolSignatures = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile
    private var lastExecutedToolName = ""

    private val _imageCache = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val imageCache = _imageCache.asStateFlow()

    private val _mediaProcessingType = MutableStateFlow<String?>(null)
    val mediaProcessingType = _mediaProcessingType.asStateFlow()
    private val _mediaProcessingPrompt = MutableStateFlow("")
    val mediaProcessingPrompt = _mediaProcessingPrompt.asStateFlow()

    private var activeSessionId = ""
    private var messagesJob: Job? = null
    private var orchestrator: OrchestratorEngine? = null

    init {
        val prefs = repository.securePrefs
        val savedProvider = prefs.getSetting("chat_provider", "Zen AI")
        val savedModelId = prefs.getSetting("chat_model", "")
        if (savedModelId.isNotEmpty()) {
            val isFree = savedModelId.contains("free", ignoreCase = true)
            val friendlyName = savedModelId.split("/").lastOrNull()?.replace("-", " ")?.replaceFirstChar { it.uppercase() } ?: savedModelId
            _activeModel.value = AIModel(
                id = savedModelId,
                name = friendlyName,
                provider = savedProvider,
                isFree = isFree,
                contextWindow = "128k",
                badge = if (isFree) "Free" else "Paid"
            )
        }
    }

    fun initOrchestrator(context: Context) {
        if (orchestrator == null) {
            orchestrator = OrchestratorEngine(context.applicationContext)
        }
    }

    fun switchSession(sessionId: String) {
        activeSessionId = sessionId
        messagesJob?.cancel()
        _attachedFiles.value = emptyList()

        // Reset ALL streaming/loading state to prevent cross-session leaking
        _isStreaming.value = false
        _streamedText.value = ""
        _streamingMessageId.value = ""
        _mediaProcessingType.value = null
        _mediaProcessingPrompt.value = ""
        _pendingToolCall.value = null
        _orchestrationPlan.value = null
        _orchestrationExpanded.value = false
        _agentsWorking.value = false
        _orchestratedResult.value = null
        _deferredResponse = ""
        toolCallDepth = 0
        consecutiveWebSearches = 0

        val prefs = repository.securePrefs
        val sessionProvider = prefs.getSetting("session_provider_$sessionId", "")
        val sessionModelId = prefs.getSetting("session_model_$sessionId", "")

        val targetProvider = sessionProvider.ifEmpty { prefs.getSetting("chat_provider", "Zen AI") }
        val targetModelId = sessionModelId.ifEmpty { prefs.getSetting("chat_model", "") }

        if (targetModelId.isNotEmpty()) {
            val isFree = targetModelId.contains("free", ignoreCase = true)
            val friendlyName = targetModelId.split("/").lastOrNull()?.replace("-", " ")?.replaceFirstChar { it.uppercase() } ?: targetModelId
            _activeModel.value = AIModel(
                id = targetModelId,
                name = friendlyName,
                provider = targetProvider,
                isFree = isFree,
                contextWindow = "128k",
                badge = if (isFree) "Free" else "Paid"
            )
        }

        messagesJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getMessagesForSession(sessionId).collect { msgList ->
                _messages.value = msgList
            }
        }
    }

    fun changeActiveModel(model: AIModel) {
        _activeModel.value = model
        repository.securePrefs.saveSetting("chat_provider", model.provider)
        repository.securePrefs.saveSetting("chat_model", model.id)
        if (activeSessionId.isNotEmpty()) {
            repository.securePrefs.saveSetting("session_provider_$activeSessionId", model.provider)
            repository.securePrefs.saveSetting("session_model_$activeSessionId", model.id)
        }
    }

    private fun generateDynamicTitle(prompt: String): String {
        val raw = prompt.trim()
        if (raw.isEmpty()) return "New Session"

        val clean = raw
            .replace(RE_PROMPT_PREFIX, "")
            .replace(RE_PROMPT_NON_ALPHANUM, "")
            .trim()

        val lower = clean.lowercase()
        if (lower in listOf("hi", "hello", "hey", "greetings", "good morning", "good evening", "howdy", "sup", "yo")) {
            return "Greetings"
        }

        if (lower.contains("pdf") || lower.contains("document") || lower.contains("docx")) {
            if (lower.contains("poem")) return "PDF Poem Generation"
            return "PDF Generation"
        }
        if (lower.contains("image") || lower.contains("draw") || lower.contains("picture") || lower.contains("photo") || lower.contains("dog") || lower.contains("cat")) {
            val subject = clean.replace(RE_PROMPT_IMG_SUBJECT, "").take(20).trim()
            if (subject.isNotEmpty()) return "${subject.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Image"
            return "Image Generation"
        }

        val words = clean.split(RE_WHITESPACE).filter { it.isNotBlank() }.take(4)
        if (words.isEmpty()) return "New Session"

        val title = words.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        return title.take(28)
    }

    fun sendMessage(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var sessionId = activeSessionId
            val dynamicTitle = generateDynamicTitle(text)
            if (sessionId.isEmpty()) {
                sessionId = repository.createSession(dynamicTitle)
                activeSessionId = sessionId
                messagesJob?.cancel()
                messagesJob = viewModelScope.launch {
                    repository.getMessagesForSession(sessionId).collect { msgList ->
                        _messages.value = msgList
                    }
                }
            } else {
                val currentSession = repository.getSessionById(sessionId)
                if (currentSession != null && (currentSession.title.isBlank() || currentSession.title.matches(RE_UNTITLED_SESSION))) {
                    repository.renameSession(sessionId, dynamicTitle)
                }
            }
            val msgText = if (_attachedFiles.value.isNotEmpty()) {
                val filesSection = _attachedFiles.value.joinToString("\n") { f ->
                    if (f.filePath != null) "\uD83D\uDCCE ${f.name}\n[File: ${f.filePath}]"
                    else "\uD83D\uDCCE ${f.name}\n${f.content}"
                }
                "$filesSection\n\n$text"
            } else text

            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = msgText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)
            _attachedFiles.value = emptyList()
            _streamedText.value = ""
            toolCallDepth = 0
            consecutiveWebSearches = 0
            executedToolSignatures.clear()
            lastExecutedToolName = ""
            val msgId = UUID.randomUUID().toString()
            _streamingMessageId.value = msgId
            _isStreaming.value = true

            val model = _activeModel.value
            val provider = AIProviderFactory.providers.find { it.name == model.provider }
            if (provider == null) {
                _streamedText.value = "Provider ${model.provider} not available"
                _isStreaming.value = false
                appendAssistantMessage(_streamedText.value)
                return@launch
            }

            val storageId = providerStorageId(model.provider)
            var currentKeySlot = 0
            var apiKey = ""
            val rotatorResult = ApiKeyRotator.getNextAvailableKey(repository.securePrefs, storageId)
            if (rotatorResult != null) {
                apiKey = rotatorResult.first
                currentKeySlot = rotatorResult.second
            }
            if (apiKey.isEmpty()) {
                apiKey = repository.securePrefs.getSetting("oauth_token_$storageId", "")
            }
            if (apiKey.isNotEmpty() && model.provider == "Antigravity") {
                val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                if (projectId.isNotEmpty()) {
                    apiKey += "||$projectId"
                }
            }
            if (apiKey.isEmpty() && (model.provider == "Zen AI" || model.provider == "Zen" || model.provider == "Zen (Free)")) {
                apiKey = repository.securePrefs.getApiKey("zen").ifEmpty { "zen-free" }
            }
            if (apiKey.isEmpty() && (model.provider == "Ollama" || model.provider == "OllamaCloud")) {
                apiKey = "ollama"
            }
            if (apiKey.isEmpty()) {
                _isStreaming.value = false
                _streamedText.value = "No API key configured for ${model.provider}. Go to Settings to add one."
                appendAssistantMessage(_streamedText.value)
                _streamedText.value = ""
                return@launch
            }
            val baseUrl = providerDefaultBaseUrl(model.provider)
            val messagesForApi = buildMessageList(sessionId, msgText)

            var retryApiKey = apiKey
            var retrySlot = currentKeySlot
            var attempts = 0
            val totalConfiguredKeys = ApiKeyRotator.getConfiguredKeyCount(repository.securePrefs, storageId)
            val maxAttempts = (totalConfiguredKeys * 2).coerceAtLeast(3)

            while (attempts < maxAttempts) {
                attempts++
                try {
                    _streamedText.value = ""
                    var streamHadToolCall = false
                    var lastFlushTime = 0L
                    val tokenBuffer = StringBuilder()
                    val bufferLock = Any()

                    provider.streamCompletion(
                        messages = messagesForApi,
                        model = model.id,
                        tools = repository.getDeclaredTools(),
                        apiKey = retryApiKey,
                        customBaseUrl = baseUrl,
                        onToken = { token ->
                            if (token == "\u200B") {
                                synchronized(bufferLock) { tokenBuffer.setLength(0) }
                                _streamedText.value = ""
                            } else {
                                val shouldFlushImmediately = _streamedText.value.isEmpty()
                                val now = System.currentTimeMillis()
                                val toAppend: String
                                synchronized(bufferLock) {
                                    tokenBuffer.append(token)
                                    if (shouldFlushImmediately || (now - lastFlushTime >= 35L)) {
                                        lastFlushTime = now
                                        toAppend = tokenBuffer.toString()
                                        tokenBuffer.setLength(0)
                                    } else {
                                        toAppend = ""
                                    }
                                }
                                if (toAppend.isNotEmpty()) {
                                    _streamedText.update { it + toAppend }
                                }
                            }
                        },
                        onToolCall = { toolCall ->
                            streamHadToolCall = true
                            maybeAutoApproveTool(toolCall)
                        },
                        onComplete = { fullResponse ->
                            val leftover = synchronized(bufferLock) {
                                val s = tokenBuffer.toString()
                                tokenBuffer.setLength(0)
                                s
                            }
                            if (leftover.isNotEmpty()) {
                                _streamedText.update { it + leftover }
                            }
                            _deferredResponse = fullResponse
                        },
                        onError = { error ->
                            val msg = error.message.orEmpty()
                            val isRotatableError = error is RateLimitException ||
                                    msg.contains("401") ||
                                    msg.contains("403") ||
                                    msg.contains("429") ||
                                    msg.contains("rate limit", ignoreCase = true) ||
                                    msg.contains("quota", ignoreCase = true) ||
                                    msg.contains("FreeUsageLimitError", ignoreCase = true) ||
                                    msg.contains("limit exceeded", ignoreCase = true)
                            if (isRotatableError) {
                                throw (error as? RateLimitException) ?: RateLimitException(model.provider, 429, error.message ?: "Rate limited")
                            }
                            _isStreaming.value = false
                            val errMsg = "Error: ${error.message}"
                            _streamedText.value = errMsg
                            viewModelScope.launch { appendAssistantMessage(errMsg) }
                            _streamedText.value = ""
                        },
                        onUsage = { usage ->
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.recordTokenUsage(sessionId, model.id, model.provider, usage)
                            }
                        }
                    )
                    // Save to DB FIRST, then clear streaming state to avoid UI gap
                    val textToSave = if (_streamedText.value.isNotBlank()) _streamedText.value else _deferredResponse
                    if (textToSave.isNotBlank()) {
                        appendAssistantMessage(textToSave)
                    }
                    if (!streamHadToolCall) {
                        _isStreaming.value = false
                    }
                    _streamedText.value = ""
                    _deferredResponse = ""
                    break  // Success — exit retry loop
                } catch (e: RateLimitException) {
                    // Instant silent key rotation
                    if (retrySlot > 0) {
                        ApiKeyRotator.markKeyExhausted(storageId, retrySlot)
                    }
                    val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, retrySlot)
                    if (nextKey != null && attempts < maxAttempts) {
                        val sameKey = nextKey.second == retrySlot
                        retryApiKey = nextKey.first
                        retrySlot = nextKey.second
                        if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                            val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                            if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                        }
                        _streamedText.value = ""  // Clear partial output from failed attempt
                        if (sameKey && totalConfiguredKeys <= 1) {
                            kotlinx.coroutines.delay(1000)
                        }
                        continue  // Instantly retry with rotated key
                    }
                    // All keys exhausted after max rotation attempts
                    _isStreaming.value = false
                    _streamedText.value = "Error: ${model.provider} rate limit reached across all keys. Please try again shortly."
                    appendAssistantMessage(_streamedText.value)
                    _streamedText.value = ""
                    break
                } catch (e: Exception) {
                    _isStreaming.value = false
                    _streamedText.value = "Error: ${e.message}"
                    appendAssistantMessage(_streamedText.value)
                    _streamedText.value = ""
                    break
                }
            }
        }
    }

    fun cancelActiveChat() {
        _isStreaming.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val text = _streamedText.value
            if (text.isNotEmpty()) {
                appendAssistantMessage(text + "\n\n[INTERRUPTED]")
            }
            _streamedText.value = ""
        }
    }

    fun approveToolCall() {
        val toolCall = _pendingToolCall.value ?: return
        _pendingToolCall.value = null
        when (toolCall.name) {
            "generate_image" -> {
                val prompt = try {
                    com.google.gson.JsonParser.parseString(toolCall.arguments).asJsonObject.get("prompt")?.asString ?: ""
                } catch (_: Exception) { "" }
                _mediaProcessingType.value = "image"
                _mediaProcessingPrompt.value = prompt
            }
            "generate_video" -> {
                val prompt = try {
                    com.google.gson.JsonParser.parseString(toolCall.arguments).asJsonObject.get("prompt")?.asString ?: ""
                } catch (_: Exception) { "" }
                _mediaProcessingType.value = "video"
                _mediaProcessingPrompt.value = prompt
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Insert assistant message with tool_calls BEFORE tool result
            val assistantToolCallMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = activeSessionId,
                role = "assistant",
                content = "",
                timestamp = System.currentTimeMillis(),
                isToolCall = true,
                toolCallsJson = """[{"id":"${toolCall.id}","name":"${toolCall.name}","arguments":${com.google.gson.Gson().toJson(toolCall.arguments)}}]"""
            )
            repository.insertMessage(assistantToolCallMsg)

            val result = try {
                repository.executeTool(toolCall.name, toolCall.arguments, repository.getDefaultProjectPath())
            } catch (e: Exception) {
                "Error executing ${toolCall.name}: ${e.message}"
            } finally {
                if (toolCall.name in setOf("generate_image", "generate_video")) {
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                }
            }
            val mediaMatch = RE_MEDIA_TAG.find(result)
            if (mediaMatch != null) {
                _streamedText.update { current ->
                    if (!current.contains(mediaMatch.value)) "$current\n\n${mediaMatch.value}" else current
                }
            }
            val toolResultMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = activeSessionId,
                role = "tool",
                content = result,
                timestamp = System.currentTimeMillis(),
                isToolCall = true,
                toolCallsJson = toolCall.id
            )
            repository.insertMessage(toolResultMsg)
            // Feed the tool result back to the AI so it can respond
            viewModelScope.launch(Dispatchers.IO) { continueWithToolResult(toolResultMsg) }
        }
    }

    private suspend fun continueWithToolResult(toolResultMsg: Message) {
        if (activeSessionId.isEmpty()) return
        _isStreaming.value = true
        toolCallDepth++
        if (toolCallDepth > maxToolCallDepth) {
            _isStreaming.value = false
            appendAssistantMessage("Completed after $maxToolCallDepth tool call(s).")
            return
        }

        // Track consecutive web_search calls — detect loop and correct course
        val lastToolName = lastExecutedToolName

        if (lastToolName == "web_search") {
            consecutiveWebSearches++
        } else {
            consecutiveWebSearches = 0
        }

        val model = _activeModel.value
        val provider = AIProviderFactory.providers.find { it.name == model.provider }
        if (provider == null) {
            _isStreaming.value = false
            appendAssistantMessage("Provider ${model.provider} not available.")
            return
        }
        val storageId = providerStorageId(model.provider)
        var currentKeySlot = 0
        var apiKey = ""
        val rotatorResult = ApiKeyRotator.getNextAvailableKey(repository.securePrefs, storageId)
        if (rotatorResult != null) {
            apiKey = rotatorResult.first
            currentKeySlot = rotatorResult.second
        }
        if (apiKey.isEmpty()) {
            apiKey = repository.securePrefs.getSetting("oauth_token_$storageId", "")
        }
        if (apiKey.isNotEmpty() && model.provider == "Antigravity") {
            val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
            if (projectId.isNotEmpty()) apiKey += "||$projectId"
        }
        if (apiKey.isEmpty()) {
            _isStreaming.value = false
            appendAssistantMessage("No API key configured for ${model.provider}.")
            return
        }
        val baseUrl = providerDefaultBaseUrl(model.provider)
        val history = repository.getMessagesListForSession(activeSessionId)
        val systemMsg = Message(id = "system", sessionId = activeSessionId, role = "system",
            content = buildSystemPrompt(), timestamp = 0L)

        // If the AI has called web_search 2+ times in a row, inject a strong correction message
        val correctionMsg: Message? = if (consecutiveWebSearches >= 2) {
            val userWantsPdf = history.any { it.role == "user" && it.content.lowercase().contains("pdf") }
            Message(
                id = UUID.randomUUID().toString(),
                sessionId = activeSessionId,
                role = "system",
                content = if (userWantsPdf) {
                    "STOP SEARCHING THE WEB. You have enough information. Call create_pdf RIGHT NOW using your training knowledge. Do not call web_search again."
                } else {
                    "STOP SEARCHING THE WEB. You have enough search results from your web_search calls above. Synthesize the final answer now and reply directly to the user with the facts you gathered. Do NOT call web_search or any search tool again."
                },
                timestamp = System.currentTimeMillis()
            )
        } else null

        val messagesForApi = buildList {
            add(systemMsg)
            addAll(history)
            if (correctionMsg != null) add(correctionMsg)
        }

        _streamedText.value = ""
        val msgId = UUID.randomUUID().toString()
        _streamingMessageId.value = msgId
        _isStreaming.value = true

        var retryApiKey = apiKey
        var retrySlot = currentKeySlot
        var attempts = 0
        val totalConfiguredKeys = ApiKeyRotator.getConfiguredKeyCount(repository.securePrefs, storageId)
        val maxAttempts = (totalConfiguredKeys * 2).coerceAtLeast(3)

        while (attempts < maxAttempts) {
            attempts++
            try {
                _streamedText.value = ""
                var nextStreamHadToolCall = false
                provider.streamCompletion(
                    messages = messagesForApi,
                    model = model.id,
                    tools = if (consecutiveWebSearches >= 2 || toolCallDepth >= maxToolCallDepth) emptyList() else repository.getDeclaredTools(),
                    apiKey = retryApiKey,
                    customBaseUrl = baseUrl,
                    onToken = { token -> _streamedText.value += token },
                    onToolCall = { tc ->
                        nextStreamHadToolCall = true
                        maybeAutoApproveTool(tc)
                    },
                    onComplete = { fullResponse ->
                        _deferredResponse = fullResponse
                    },
                    onError = { error ->
                        val msg = error.message.orEmpty()
                        val isRotatableError = error is RateLimitException ||
                                msg.contains("401") ||
                                msg.contains("403") ||
                                msg.contains("429") ||
                                msg.contains("rate limit", ignoreCase = true) ||
                                msg.contains("quota", ignoreCase = true) ||
                                msg.contains("FreeUsageLimitError", ignoreCase = true) ||
                                msg.contains("limit exceeded", ignoreCase = true)
                        if (isRotatableError) {
                            throw (error as? RateLimitException) ?: RateLimitException(model.provider, 429, error.message ?: "Rate limited")
                        }
                        _isStreaming.value = false
                        val errMsg = "Error: ${error.message}"
                        _streamedText.value = errMsg
                        viewModelScope.launch { appendAssistantMessage(errMsg) }
                        _streamedText.value = ""
                    },
                    onUsage = { usage ->
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.recordTokenUsage(activeSessionId, model.id, model.provider, usage)
                        }
                    }
                )
                val textToSave = if (_streamedText.value.isNotBlank()) _streamedText.value else _deferredResponse
                if (textToSave.isNotBlank()) {
                    appendAssistantMessage(textToSave)
                }
                if (!nextStreamHadToolCall) {
                    _isStreaming.value = false
                }
                _streamedText.value = ""
                _deferredResponse = ""
                break
            } catch (e: RateLimitException) {
                if (retrySlot > 0) {
                    ApiKeyRotator.markKeyExhausted(storageId, retrySlot)
                }
                val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, retrySlot)
                if (nextKey != null && attempts < maxAttempts) {
                    val sameKey = nextKey.second == retrySlot
                    retryApiKey = nextKey.first
                    retrySlot = nextKey.second
                    if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                        val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                        if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                    }
                    _streamedText.value = ""
                    if (sameKey && totalConfiguredKeys <= 1) {
                        kotlinx.coroutines.delay(1000)
                    }
                    continue
                }
                _isStreaming.value = false
                _streamedText.value = "Error: ${model.provider} rate limit reached across all keys. Please try again shortly."
                appendAssistantMessage(_streamedText.value)
                _streamedText.value = ""
                break
            } catch (e: Exception) {
                _isStreaming.value = false
                _streamedText.value = "Error: ${e.message}"
                appendAssistantMessage(_streamedText.value)
                _streamedText.value = ""
                break
            }
        }
    }

    fun denyToolCall() {
        _pendingToolCall.value = null
        viewModelScope.launch(Dispatchers.IO) {
            appendAssistantMessage("Tool call was denied by user.")
        }
    }

    fun attachFile(name: String, content: String, size: Long) {
        _attachedFiles.value = _attachedFiles.value + AttachedFile(name, content, size)
    }

    fun attachFile(name: String, content: String, size: Long, filePath: String) {
        _attachedFiles.value = _attachedFiles.value + AttachedFile(name, content, size, filePath)
    }

    fun removeAttachedFile(name: String) {
        _attachedFiles.value = _attachedFiles.value.filter { it.name != name }
    }

    fun toggleOrchestrationPanel() {
        _orchestrationExpanded.value = !_orchestrationExpanded.value
    }

    fun cancelOrchestration() {
        orchestrator?.cancelExecution()
        _orchestrationPlan.value = null
        _agentsWorking.value = false
    }

    private fun isMetaReferenceText(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.length > 150) return false
        val metaPatterns = listOf(
            "last response", "previous response", "last message", "previous message",
            "last reply", "previous reply", "that response", "your response", "my last response",
            "work last response", "create audio of last response", "audio of last response",
            "read last response", "read the last response", "speak last response", "audio of that",
            "audio of it", "convert that", "convert it", "read that", "read it", "speak that", "speak it",
            "last answer", "previous answer", "your last reply", "your previous message",
            "what you said", "what you wrote", "make audio of last response", "convert last message"
        )
        if (metaPatterns.any { clean.contains(it) }) return true
        val hasMetaTarget = clean.contains("last") || clean.contains("previous") || clean.contains("that") || clean.contains("what you")
        val hasMetaAction = clean.contains("response") || clean.contains("message") || clean.contains("reply") || clean.contains("answer") || clean.contains("audio") || clean.contains("speak") || clean.contains("read") || clean.contains("convert")
        return hasMetaTarget && hasMetaAction
    }

    private suspend fun buildMessageList(sessionId: String, newUserText: String): List<Message> {
        val history = repository.getMessagesListForSession(sessionId)
        val systemMsg = Message(
            id = "system", sessionId = sessionId, role = "system",
            content = buildSystemPrompt(), timestamp = 0L
        )
        val userMsg = Message(
            id = UUID.randomUUID().toString(), sessionId = sessionId,
            role = "user", content = newUserText, timestamp = System.currentTimeMillis()
        )

        val ttsHintMsg: Message? = if (isMetaReferenceText(newUserText)) {
            val lastAssistant = history.lastOrNull { msg ->
                msg.role == "assistant" &&
                !msg.isToolCall &&
                !msg.content.startsWith("Executing tool") &&
                !msg.content.startsWith("Running tool") &&
                !msg.content.startsWith("I've completed") &&
                !msg.content.startsWith("Tool result:") &&
                msg.content.replace(RE_MEDIA_TAG, "").trim().length > 3
            }
            if (lastAssistant != null) {
                val cleanContent = lastAssistant.content
                    .replace(RE_AUDIO_TAG, "")
                    .replace(RE_FILE_TAG, "")
                    .replace(RE_IMAGE_TAG, "")
                    .replace(RE_VIDEO_TAG, "")
                    .trim()
                Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "system",
                    content = "The user is requesting audio/TTS for your previous response.\n" +
                        "Here is the EXACT content of that response that you MUST pass as the `text` argument to `edge_tts`:\n\n" +
                        "=== PREVIOUS RESPONSE START ===\n" +
                        cleanContent +
                        "\n=== PREVIOUS RESPONSE END ===\n\n" +
                        "CRITICAL: Call `edge_tts` with the exact response text above. Do NOT pass literal words like 'last response'.",
                    timestamp = System.currentTimeMillis()
                )
            } else null
        } else null

        val result = mutableListOf<Message>()
        result.add(systemMsg)
        result.addAll(history)
        if (ttsHintMsg != null) {
            result.add(ttsHintMsg)
        }
        val lastMsg = history.lastOrNull()
        if (lastMsg == null || lastMsg.role != "user" || lastMsg.content != newUserText) {
            result.add(userMsg)
        }
        return result
    }

    private fun buildSystemPrompt(): String {
        val prefs = repository.securePrefs
        val personaEnabled = prefs.getSetting("persona_enabled", "false") == "true"
        val persona = if (personaEnabled) {
            prefs.getSetting("custom_persona", "You are DeepCode, an AI coding assistant.")
        } else {
            "You are DeepCode, an AI coding assistant."
        }

        return """
$persona

CRITICAL INSTRUCTIONS:
- Be fast, helpful, and concise. Respond immediately and directly to the user without preamble.
- NEVER write out a "thinking process", chain-of-thought, internal monologue, or audit rules out loud.
- If you need to reason or think before replying, you MUST place ALL reasoning strictly inside <think>...</think> tags. Outside of <think> tags, provide ONLY the clean final response.
- When asked to create or provide ANY PDF document (poem, study notes, report, resume, etc.), immediately call `create_pdf` with the full content and reply with "Here is your PDF document: [file:/path/to/doc.pdf]".
- When tools return file/image tags (e.g. `[image:...]` or `[file:...]`), stop calling tools and provide a brief confirmation.
- Audio/Speech (edge_tts): When the user asks for audio, resolve the full text and pass it to `edge_tts`.
- Video Generation (generate_video): Call `generate_video` with a prompt describing the scene.
""".trim()
    }

    private suspend fun appendAssistantMessage(content: String) = withContext(Dispatchers.IO) {
        val (thought, clean) = extractThoughtAndCleanText(content, isStreaming = false)
        var finalContent = if (thought.isNotEmpty()) "<think>$thought</think>\n\n$clean" else clean
        try {
            val history = repository.getMessagesListForSession(activeSessionId)
            val lastAssistantIndex = history.indexOfLast { it.role == "assistant" && !it.isToolCall }
            val toolMessages = if (lastAssistantIndex >= 0) {
                history.subList(lastAssistantIndex + 1, history.size)
            } else {
                history
            }.filter { it.role == "tool" }

            val tagsToInject = mutableListOf<String>()

            toolMessages.forEach { msg ->
                RE_IMAGE_TAG.findAll(msg.content).forEach { match ->
                    val path = match.value
                    if (!finalContent.contains(path) && !finalContent.contains(match.groupValues[1])) {
                        tagsToInject.add(path)
                    }
                }
                RE_FILE_TAG.findAll(msg.content).forEach { match ->
                    val path = match.value
                    if (!finalContent.contains(path) && !finalContent.contains(match.groupValues[1])) {
                        tagsToInject.add(path)
                    }
                }
                RE_AUDIO_TAG.findAll(msg.content).forEach { match ->
                    val path = match.value
                    if (!finalContent.contains(path) && !finalContent.contains(match.groupValues[1])) {
                        tagsToInject.add(path)
                    }
                }
                RE_VIDEO_TAG.findAll(msg.content).forEach { match ->
                    val path = match.value
                    if (!finalContent.contains(path) && !finalContent.contains(match.groupValues[1])) {
                        tagsToInject.add(path)
                    }
                }
            }

            if (tagsToInject.isNotEmpty()) {
                val distinctTags = tagsToInject.distinct()
                finalContent = if (finalContent.isEmpty()) {
                    distinctTags.joinToString("\n")
                } else {
                    finalContent + "\n\n" + distinctTags.joinToString("\n")
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        if (finalContent.isEmpty()) return@withContext

        val id = _streamingMessageId.value.ifEmpty { UUID.randomUUID().toString() }
        val msg = Message(
            id = id,
            sessionId = activeSessionId,
            role = "assistant",
            content = finalContent,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessage(msg)
    }

    private fun maybeAutoApproveTool(toolCall: ToolCall) {
        val signature = "${toolCall.name}:${toolCall.arguments.trim()}"
        val count = executedToolSignatures.getOrDefault(signature, 0)

        if (count >= 2) {
            ai.deepcode.android.util.AppLogger.w("ChatViewModel", "Prevented duplicate tool execution loop for: $signature")
            _isStreaming.value = false
            viewModelScope.launch {
                appendAssistantMessage("I've completed the tool actions (prevented repeated duplicate execution of '${toolCall.name}'). The results are shown above.")
            }
            return
        }

        executedToolSignatures[signature] = count + 1

        // Set media processing overlay for image/video generation
        when (toolCall.name) {
            "generate_image" -> {
                val prompt = try {
                    com.google.gson.JsonParser.parseString(toolCall.arguments).asJsonObject.get("prompt")?.asString ?: ""
                } catch (_: Exception) { "" }
                _mediaProcessingType.value = "image"
                _mediaProcessingPrompt.value = prompt
            }
            "generate_video" -> {
                val prompt = try {
                    com.google.gson.JsonParser.parseString(toolCall.arguments).asJsonObject.get("prompt")?.asString ?: ""
                } catch (_: Exception) { "" }
                _mediaProcessingType.value = "video"
                _mediaProcessingPrompt.value = prompt
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assistantToolCallMsg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = activeSessionId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isToolCall = true,
                    toolCallsJson = """[{"id":"${toolCall.id}","name":"${toolCall.name}","arguments":${com.google.gson.Gson().toJson(toolCall.arguments)}}]"""
                )
                repository.insertMessage(assistantToolCallMsg)

                val result = try {
                    repository.executeTool(toolCall.name, toolCall.arguments, repository.getDefaultProjectPath())
                } catch (e: Exception) {
                    "Error executing ${toolCall.name}: ${e.message}"
                }

                val isError = result.startsWith("Error", ignoreCase = true) || result.startsWith("Exception", ignoreCase = true)
                if (isError) {
                    ai.deepcode.android.util.AppLogger.w("ChatViewModel", "Tool '${toolCall.name}' returned error: ${result.take(150)}")
                }

                val toolResultMsg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = activeSessionId,
                    role = "tool",
                    content = result,
                    timestamp = System.currentTimeMillis(),
                    isToolCall = true,
                    toolCallsJson = toolCall.id
                )
                repository.insertMessage(toolResultMsg)
                lastExecutedToolName = toolCall.name
                continueWithToolResult(toolResultMsg)
            } catch (e: Exception) {
                ai.deepcode.android.util.AppLogger.e("ChatViewModel", "maybeAutoApproveTool crashed: ${e.message}", e)
                _isStreaming.value = false
                appendAssistantMessage("Error: ${e.message}")
            } finally {
                if (toolCall.name in setOf("generate_image", "generate_video")) {
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                }
            }
        }
    }

    companion object {
        private val AUTO_APPROVE_TOOLS = setOf(
            "web_search", "web_fetch", "create_pdf", "analyze_pdf",
            "list_pdf_layouts", "create_pdf_layout", "create_pdf_from_reference",
            "read_file", "list_directory", "grep_search", "search_image",
            "list_automations", "memory_read", "edge_tts",
            "generate_image", "generate_video"
        )
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
    }
}
