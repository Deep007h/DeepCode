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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.ui.connections.IntegrationEntity
import ai.deepcode.android.ui.automations.AutomationRepository
import ai.deepcode.android.ui.automations.AutomationEntity
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

private val RE_THOUGHT_BLOCK = Regex("""(?is)<\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>[\s\S]*?(?:<\s*/\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>|$)""")
private val RE_THOUGHT_OPEN = Regex("""(?is)<\s*(?:think|thought|thinking|reasoning|plan|reflection)\s*>[\s\S]*""")
private val RE_BRACKET_THOUGHT = Regex("""(?is)\[\s*(?:thought|think|thinking|reasoning|plan)\s*\][\s\S]*?(?:\[\s*/\s*(?:thought|think|thinking|reasoning|plan)\s*\]|$)""")
private val RE_PARTIAL_THINK_OPEN = Regex("""<\s*/?\s*(?:t(?:h(?:i(?:n(?:k(?:i(?:n(?:g)?)?)?)?)?)?)?|r(?:e(?:a(?:s(?:o(?:n(?:i(?:n(?:g)?)?)?)?)?)?)?)?|p(?:l(?:a(?:n)?)?)?)\s*$""", RegexOption.IGNORE_CASE)
private val RE_UNTAGGED_THINKING_HEADER = Regex(
    """(?is)\A\s*(?:(?:here'?s?|this is|there is|it'?s?|'s)?\s*(?:a\s+)?(?:thinking|thought|reasoning)\s+process\b|(?:thought|thinking|reasoning)\s*process\b|let'?s\s+think\s+step\s+by\s+step\b|chain\s+of\s+thought\b)""",
)
private val RE_THOUGHT_FINAL_ANSWER_MARKER = Regex(
    """(?is)(?:\n|\A)(?:(?:final\s+answer|direct\s+answer|response|output|answer)\s*:\s*|(?:so\s+)?(?:i'll|i\s+will)\s+(?:just\s+)?(?:say|respond|reply|output|give)[^\n]*[.\n\r]+(?:that's\s+fine[^\n]*[.\n\r]+)?(?:i'll\s+output\s+that[^\n]*[.\n\r]+)?)([\s\S]+)$"""
)
private val RE_INNER_THOUGHT_PREFIX = Regex(
    """(?is)\A(?:\s*(?:thought|thinking|reasoning|internal thoughts?|plan):\s*[^\n]*\n*|\s*(?:that's|that is|this is)\s+(?:a|an)\s+[^.!?\n]*[.!?\n]*|\s*(?:the\s+)?user\s+(?:is|wants|asked|said|just)\b[^.!?\n]*[.!?\n]*|\s*i\s+(?:should|will|need\s+to|must|'ll)\s+(?:respond|reply|answer|greet|help|ask|follow)\b[^.!?\n]*[.!?\n]*|\s*(?:ensure|keep)\s+(?:no\s+thinking|no\s+internal|final\s+response)\b[^.!?\n]*[.!?\n]*|\s*(?:just\s+)?direct\s+answer[.!?\n]*|\s*no\s+tools\s+needed\b[^.!?\n]*[.!?\n]*)+"""
)

fun stripThinkingProcess(raw: String, isStreaming: Boolean = false): String {
    if (raw.isBlank()) return ""

    var text = raw

    // 1. Remove all tagged thought blocks (both closed and trailing unclosed)
    text = text.replace(RE_THOUGHT_BLOCK, "")
    text = text.replace(RE_BRACKET_THOUGHT, "")

    // 2. Remove open tags or partial tags
    text = text.replace(RE_THOUGHT_OPEN, "")
    if (isStreaming) {
        text = text.replace(RE_PARTIAL_THINK_OPEN, "")
    } else {
        text = text.replace(Regex("""<tool_calls?>.*?</tool_calls?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("""<invoke\s+name=[^>]*>.*?</invoke>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    }

    // 3. Untagged thinking process (e.g. "Here's a thinking process: ...")
    if (RE_UNTAGGED_THINKING_HEADER.containsMatchIn(text)) {
        val answerMatch = RE_THOUGHT_FINAL_ANSWER_MARKER.find(text)
        if (answerMatch != null) {
            val candidateAnswer = answerMatch.groups[1]?.value?.trim() ?: ""
            val cleanAnswer = candidateAnswer.trimStart('✅', ' ', '\n', '\r')
            if (cleanAnswer.isNotEmpty()) {
                text = cleanAnswer
            }
        } else {
            // Backward line scan for the final answer
            val lines = text.lines()
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
                text = if (answer.isNotEmpty()) answer else ""
            } else if (isStreaming) {
                text = ""
            } else {
                val quoted = Regex(""""([^"\n]{3,120})"""").findAll(text).lastOrNull()?.groups?.get(1)?.value?.trim()
                text = if (!quoted.isNullOrEmpty() && !quoted.contains("analyze", ignoreCase = true)) quoted else ""
            }
        }
    }

    // 4. Remove inner thought prefixes
    text = text.replace(RE_INNER_THOUGHT_PREFIX, "").trim()

    // 5. Cleanup any stray/dangling think tags
    if (text.startsWith("<think", ignoreCase = true) || text.startsWith("<thought", ignoreCase = true)) {
        text = text.substringAfter(">", "").trim()
    }
    if (text.endsWith("</think>", ignoreCase = true) || text.endsWith("</thought>", ignoreCase = true)) {
        text = text.substringBeforeLast("<").trim()
    }

    return text
}

fun extractThoughtAndCleanText(raw: String, isStreaming: Boolean = false): Pair<String, String> {
    return Pair("", stripThinkingProcess(raw, isStreaming))
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

data class DynamicSuggestionCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val actionText: String,
    val iconBackgroundColor: Color = Color(0xFFFF6D00),
    val iconContent: @Composable () -> Unit,
    val onClick: () -> Unit
)

@Composable
fun PlaceholderFeatureCard(
    iconContent: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBackgroundColor: Color = Color(0xFFFF6D00)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .width(168.dp)
            .height(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .depthCard(
                shape = RoundedCornerShape(22.dp),
                elevation = 3.5.dp,
                isDark = true
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF9E9EA7),
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = actionText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF6D00)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFFF6D00),
                    modifier = Modifier.size(13.dp)
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
    onSessionChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 72.dp
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

    val currentSessionId by viewModel.activeSessionIdFlow.collectAsStateWithLifecycle()
    LaunchedEffect(currentSessionId) {
        if (currentSessionId.isNotEmpty() && activeSessionId.isEmpty()) {
            onSessionChanged(currentSessionId)
        }
    }

    var inputMsg by rememberSaveable { mutableStateOf("") }
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
    val integrationRepo = remember { IntegrationRepository(context) }
    val integrations by integrationRepo.getAllIntegrationsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val automationRepo = remember { AutomationRepository(context) }
    val automations by automationRepo.getAllAutomationsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
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
                    val lowerName = fileName.lowercase()
                    val isImage = lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                            lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") ||
                            lowerName.endsWith(".gif") || lowerName.endsWith(".bmp")
                    if (lowerName.endsWith(".pdf") || isImage) {
                        val destDir = File(context.filesDir, "attachments")
                        destDir.mkdirs()
                        val destFile = File(destDir, "${System.currentTimeMillis()}_$fileName")
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

    val isNearBottom by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val lastVis = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            if (lastVis.index < info.totalItemsCount - 1) return@derivedStateOf false
            val viewportBottom = info.viewportEndOffset - info.afterContentPadding
            val itemBottom = lastVis.offset + lastVis.size
            itemBottom <= viewportBottom + 350
        }
    }

    var lastScrolledSessionId by remember { mutableStateOf("") }
    LaunchedEffect(messages.size, activeSessionId) {
        if (messages.isNotEmpty()) {
            val isNewSession = (activeSessionId != lastScrolledSessionId)
            if (isNewSession) {
                lastScrolledSessionId = activeSessionId
                val total = lazyListState.layoutInfo.totalItemsCount
                if (total > 0) {
                    try {
                        lazyListState.scrollToItem(total - 1, 0)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    var shouldScrollToBottomOnSend by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, isStreaming, shouldScrollToBottomOnSend) {
        if (shouldScrollToBottomOnSend) {
            shouldScrollToBottomOnSend = false
            kotlinx.coroutines.yield()
            val total = lazyListState.layoutInfo.totalItemsCount
            if (total > 0) {
                try {
                    lazyListState.animateScrollToItem(total - 1)
                } catch (_: Exception) {
                    try {
                        lazyListState.scrollToItem(total - 1)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && isNearBottom) {
            val total = lazyListState.layoutInfo.totalItemsCount
            if (total > 0 && !lazyListState.isScrollInProgress) {
                try {
                    lazyListState.animateScrollToItem(total - 1)
                } catch (_: Exception) {}
            }
        }
    }

    // Follow streaming text gently without micro-jitter by scrolling to bottom edge
    LaunchedEffect(isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        var lastScrollTime = 0L
        viewModel.streamedText.collect {
            val now = System.currentTimeMillis()
            if (now - lastScrollTime >= 140L && isNearBottom && !lazyListState.isScrollInProgress) {
                lastScrollTime = now
                val total = lazyListState.layoutInfo.totalItemsCount
                if (total > 0) {
                    try {
                        val lastItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                        val scrollOffset = if (lastItem != null && lastItem.index == total - 1) {
                            lastItem.size
                        } else 0
                        lazyListState.scrollToItem(total - 1, scrollOffset)
                    } catch (_: Exception) {}
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

    Box(modifier = modifier.fillMaxSize().background(AppScreenBg)) {
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
            val density = LocalDensity.current
            // bounds.outWidth/Height are PIXELS; screenWidthDp is DP — convert
            // before comparing, else sample is under-estimated and we decode
            // a far larger bitmap than needed (jank + OOM risk).
            val screenWPx = with(density) { config.screenWidthDp.dp.roundToPx().coerceAtLeast(1) }
            val screenHPx = with(density) { config.screenHeightDp.dp.roundToPx().coerceAtLeast(1) }
            val bm by produceState<android.graphics.Bitmap?>(initialValue = null, customFile.absolutePath) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(customFile.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= screenWPx || bounds.outHeight / (sample * 2) >= screenHPx) {
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
                Box(modifier = Modifier.fillMaxSize().background(AppScreenBg))
            }
        } else if (wallpaperOpt != null && wallpaperOpt.gradientColors != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(wallpaperOpt.gradientColors))
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(AppScreenBg))
        }
        Column(Modifier.fillMaxSize()) {
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
                        onSessionChanged(newId)
                    }
                },
                onSwitchSession = {
                    showSessionPicker = false
                    viewModel.switchSession(it)
                    onSessionChanged(it)
                },
                repository = repository,
                onModelSelected = { viewModel.changeActiveModel(it) },
                onOpenApiKeys = onOpenApiKeys
            )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val showScrollToBottomButton by remember {
                derivedStateOf {
                    val info = lazyListState.layoutInfo
                    info.totalItemsCount > 1 && !isNearBottom
                }
            }

            val isChatEmpty = messages.isEmpty() && !isStreaming
            if (isChatEmpty) {
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

                    // 2. Dynamic Middle Cards (Horizontal Scrolling Carousel - 0.8x scaled & dynamic)
                    val dynamicSuggestions = remember(integrations, sessions, automations) {
                        val items = mutableListOf<DynamicSuggestionCard>()

                        // 1. Available Connections
                        val connectedIntegrations = integrations.filter { it.status.equals("connected", ignoreCase = true) }

                        // GitHub
                        if (connectedIntegrations.any { it.appId.contains("github", ignoreCase = true) }) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "conn_github",
                                    title = "GitHub Repos",
                                    subtitle = "Browse and inspect your connected repositories",
                                    actionText = "View repos",
                                    iconBackgroundColor = Color(0xFF24292E),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "List my GitHub repositories and show their status" }
                                )
                            )
                        }

                        // Telegram
                        if (connectedIntegrations.any { it.appId.contains("telegram", ignoreCase = true) }) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "conn_telegram",
                                    title = "Telegram Bot",
                                    subtitle = "Check bot status, alerts & message history",
                                    actionText = "Check bot",
                                    iconBackgroundColor = Color(0xFF0284C7),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "Check my Telegram bot status and recent messages" }
                                )
                            )
                        }

                        // Gmail
                        if (connectedIntegrations.any { it.appId.contains("gmail", ignoreCase = true) || it.appId.contains("google_mail", ignoreCase = true) }) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "conn_gmail",
                                    title = "Inbox Digest",
                                    subtitle = "Summarize unread emails and priorities",
                                    actionText = "Check mail",
                                    iconBackgroundColor = Color(0xFFDC2626),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "Summarize my unread emails" }
                                )
                            )
                        }

                        // WhatsApp
                        if (connectedIntegrations.any { it.appId.contains("whatsapp", ignoreCase = true) }) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "conn_whatsapp",
                                    title = "WhatsApp Bridge",
                                    subtitle = "Check bridge status and incoming chats",
                                    actionText = "Bridge status",
                                    iconBackgroundColor = Color(0xFF10B981),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "Check WhatsApp bridge status" }
                                )
                            )
                        }

                        // 2. Automations & Morning News Brief
                        val hasNewsBrief = automations.any {
                            it.name.contains("news brief", ignoreCase = true) ||
                            it.name.contains("morning brief", ignoreCase = true)
                        }
                        if (hasNewsBrief) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "auto_morning_news",
                                    title = "Morning Brief",
                                    subtitle = "Crypto 🪙, India 🇮🇳, AI 🤖 & Conflict ⚔️ news",
                                    actionText = "Get brief",
                                    iconBackgroundColor = Color(0xFFF59E0B),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "Deliver today's daily morning news brief" }
                                )
                            )
                        }

                        // 3. Previous Sessions & Topics
                        val validRecent = sessions
                            .filter { it.title.isNotBlank() && !RE_UNTITLED_SESSION.matches(it.title.trim()) && it.id != activeSessionId }
                            .sortedByDescending { it.createdAt }
                            .distinctBy { it.title.trim().lowercase() }
                            .take(2)

                        for (s in validRecent) {
                            val cleanTitle = s.title.removePrefix("🤖 ").trim()
                            items.add(
                                DynamicSuggestionCard(
                                    id = "session_${s.id}",
                                    title = "Continue: ${cleanTitle.take(14)}",
                                    subtitle = "Resume: \"${cleanTitle.take(28)}\"",
                                    actionText = "Resume",
                                    iconBackgroundColor = Color(0xFF8B5CF6),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { onSessionChanged(s.id) }
                                )
                            )
                        }

                        // 4. Topic Discovery from Sessions
                        val knownTopics = listOf("Python", "Kotlin", "Android", "React", "Bug", "API", "Database", "Music", "UI", "Git", "Compose")
                        val detectedTopic = sessions
                            .flatMap { it.title.split(Regex("""[\s\-_/]+""")) }
                            .map { it.trim() }
                            .firstOrNull { word -> knownTopics.any { it.equals(word, ignoreCase = true) } }

                        if (detectedTopic != null) {
                            val topicProper = knownTopics.first { it.equals(detectedTopic, ignoreCase = true) }
                            items.add(
                                DynamicSuggestionCard(
                                    id = "topic_$topicProper",
                                    title = "$topicProper Topic",
                                    subtitle = "Deep dive into $topicProper architecture & patterns",
                                    actionText = "Ask now",
                                    iconBackgroundColor = Color(0xFFEC4899),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "Help me design and optimize a $topicProper solution" }
                                )
                            )
                        }

                        // 5. Core Coding Suggestions
                        items.add(
                            DynamicSuggestionCard(
                                id = "code_explain",
                                title = "Explain code",
                                subtitle = "Explain how recursion works with an example",
                                actionText = "Get explanation",
                                iconBackgroundColor = Color(0xFFFF6D00),
                                iconContent = {
                                    Text(
                                        "</>",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                onClick = { inputMsg = "Explain how recursion works with an example" }
                            )
                        )

                        items.add(
                            DynamicSuggestionCard(
                                id = "code_debug",
                                title = "Debug help",
                                subtitle = "Why is my loop infinite? Find logic bugs",
                                actionText = "Get help",
                                iconBackgroundColor = Color(0xFFEF4444),
                                iconContent = {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                },
                                onClick = { inputMsg = "Why is my loop infinite? Help me debug it" }
                            )
                        )

                        items.add(
                            DynamicSuggestionCard(
                                id = "code_style",
                                title = "Code style",
                                subtitle = "Validate code patterns and best practices",
                                actionText = "Check now",
                                iconBackgroundColor = Color(0xFF6366F1),
                                iconContent = {
                                    Text(
                                        "{ }",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                onClick = { inputMsg = "Validate code patterns and best practices" }
                            )
                        )

                        items.add(
                            DynamicSuggestionCard(
                                id = "code_generate",
                                title = "Generate code",
                                subtitle = "Write a function to parse JSON in Python",
                                actionText = "Generate",
                                iconBackgroundColor = Color(0xFF3B82F6),
                                iconContent = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                },
                                onClick = { inputMsg = "Write a function to parse JSON in Python" }
                            )
                        )

                        // If no connections are connected, offer a connection setup suggestion
                        if (connectedIntegrations.isEmpty()) {
                            items.add(
                                DynamicSuggestionCard(
                                    id = "conn_explore",
                                    title = "Connect Tools",
                                    subtitle = "Link GitHub, Gmail or Telegram to unlock tools",
                                    actionText = "Explore",
                                    iconBackgroundColor = Color(0xFF14B8A6),
                                    iconContent = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    onClick = { inputMsg = "How do I connect GitHub, Gmail, or Telegram in DeepCode?" }
                                )
                            )
                        }

                        items
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(dynamicSuggestions, key = { it.id }) { card ->
                            PlaceholderFeatureCard(
                                iconContent = card.iconContent,
                                title = card.title,
                                subtitle = card.subtitle,
                                actionText = card.actionText,
                                iconBackgroundColor = card.iconBackgroundColor,
                                onClick = card.onClick
                            )
                        }
                    }

                    // 3. Bottom Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .depthCard(shape = RoundedCornerShape(24.dp), elevation = 3.dp, isDark = true)
                            .padding(vertical = 12.dp, horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E2129))
                                    .border(1.dp, Color(0xFF323642), RoundedCornerShape(10.dp)),
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
                            if (isStreaming && streamingMsgId.isNotEmpty() && groupedItems.none { it is ChatItem.NormalMessage && it.message.id == streamingMsgId }) {
                                add(ChatItem.Streaming(streamingMsgId))
                            } else if (p != null && p.overallStatus != OverallStatus.COMPLETE && p.overallStatus != OverallStatus.FAILED) {
                                add(ChatItem.OrchestrationPanel("orchestration"))
                            }
                        }.distinctBy { item ->
                            when (item) {
                                is ChatItem.NormalMessage -> "msg_${item.message.id}"
                                is ChatItem.ToolExecutionGroup -> "group_${item.groupId}"
                                is ChatItem.Streaming -> "streaming_${item.messageId}"
                                is ChatItem.OrchestrationPanel -> "orch_${item.panelId}"
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        reverseLayout = false,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    items(combinedItems, key = { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> "msg_${item.message.id}"
                            is ChatItem.ToolExecutionGroup -> "group_${item.groupId}"
                            is ChatItem.Streaming -> "streaming_${item.messageId}"
                            is ChatItem.OrchestrationPanel -> "orch_${item.panelId}"
                        }
                    }, contentType = { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> if (item.message.role == "user") "user_msg" else "ai_msg"
                            is ChatItem.ToolExecutionGroup -> "tool"
                            is ChatItem.Streaming -> "streaming"
                            is ChatItem.OrchestrationPanel -> "orchestration"
                        }
                    }) { item ->
                        when (item) {
                            is ChatItem.NormalMessage -> {
                                val onSelect = remember(viewModel) { { layoutName: String ->
                                    shouldScrollToBottomOnSend = true
                                    viewModel.sendMessage("Use $layoutName layout")
                                    Unit
                                } }
                                val onSendSug = remember(viewModel) { { suggestion: String ->
                                    shouldScrollToBottomOnSend = true
                                    viewModel.sendMessage(suggestion)
                                    Unit
                                } }
                                MessageBubble(
                                    message = item.message,
                                    showTimestamp = true,
                                    imageCache = imageCache,
                                    onSelectLayout = onSelect,
                                    onSendSuggestion = onSendSug
                                )
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

        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val effectiveBottomPadding = maxOf(bottomBarHeight, imeBottom)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 8.dp)
                .padding(bottom = effectiveBottomPadding)
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
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                // Input capsule pill with tactile depth
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .depthInputBar(shape = RoundedCornerShape(32.dp), elevation = 6.dp, isDark = true)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .depthPill(shape = CircleShape, elevation = 2.dp, isDark = true)
                            .bouncyClickable(provideHaptic = true) { filePickerLauncher.launch("*/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

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
                                        color = Color(0xFF9E9EA5),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardActions = KeyboardActions(onSend = {
                            if ((inputMsg.isNotEmpty() || attachedFiles.isNotEmpty()) && !viewModel.isStreaming.value) {
                                val toSend = inputMsg
                                inputMsg = ""
                                shouldScrollToBottomOnSend = true
                                viewModel.sendMessage(toSend)
                            }
                        })
                    )

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .depthPill(shape = CircleShape, elevation = 2.dp, isDark = true)
                            .bouncyClickable(provideHaptic = true) {
                                Toast.makeText(context, "Voice input...", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val infinitePulse = rememberInfiniteTransition(label = "stopPulse")
                val pulseScale by infinitePulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.14f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                // Right action button: Send circle when typing/streaming, Green Voice Wave circle when empty
                val isSendActive = inputMsg.isNotBlank() || attachedFiles.isNotEmpty() || isStreaming
                AnimatedContent(
                    targetState = isSendActive,
                    transitionSpec = {
                        (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()).togetherWith(
                            scaleOut(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        )
                    },
                    label = "actionButtonMorph"
                ) { sendActive ->
                    if (sendActive) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .graphicsLayer {
                                    if (isStreaming) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                                .depthPill(
                                    shape = CircleShape,
                                    elevation = 5.dp,
                                    customGradient = if (isStreaming) listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                                                     else listOf(Color(0xFFFFFFFF), Color(0xFFEDEDED)),
                                    highlightAlpha = if (isStreaming) 0.35f else 0.45f,
                                    isDark = isStreaming
                                )
                                .bouncyClickable(provideHaptic = true) {
                                    if (isStreaming) {
                                        viewModel.cancelActiveChat()
                                    } else if (inputMsg.isNotEmpty() || attachedFiles.isNotEmpty()) {
                                        val toSend = inputMsg
                                        inputMsg = ""
                                        shouldScrollToBottomOnSend = true
                                        viewModel.sendMessage(toSend)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                contentDescription = if (isStreaming) "Stop" else "Send",
                                tint = if (isStreaming) Color.White else Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .depthPill(
                                    shape = CircleShape,
                                    elevation = 5.dp,
                                    customGradient = listOf(AppPrimary, AppPrimaryGradientEnd),
                                    highlightAlpha = 0.40f,
                                    isDark = true
                                )
                                .bouncyClickable(provideHaptic = true) {
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .depthPill(
                    shape = CircleShape,
                    elevation = 4.dp,
                    isDark = true
                )
                .bouncyClickable(provideHaptic = true) {
                    scope.launch {
                        val total = lazyListState.layoutInfo.totalItemsCount
                        if (total > 0) {
                            val lastVis = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                            val offset = if (lastVis != null && lastVis.index == total - 1) lastVis.size else 0
                            lazyListState.animateScrollToItem(total - 1, offset)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll to bottom",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
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
                .depthPill(shape = CircleShape, elevation = 3.dp, isDark = true)
                .bouncyClickable(provideHaptic = true) { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            MenuTwoBarsIcon(color = Color.White)
        }

        // Right: Model selection capsule pill [ deepseek v4 flash  ⋮ ]
        Box {
            val isGpt = activeModel.provider.equals("ChatGPT", ignoreCase = true) ||
                    activeModel.id.equals("chatgpt-4o", ignoreCase = true) ||
                    activeModel.name.contains("chatgpt", ignoreCase = true)
            val modelDisplayName = if (isGpt) "chatgpt" else activeModel.name.lowercase().ifEmpty { "deepseek v4 flash" }
            Row(
                modifier = Modifier
                    .depthPill(
                        shape = RoundedCornerShape(24.dp),
                        elevation = 3.dp,
                        customGradient = if (isGpt) listOf(Color(0xFF1B3D34), Color(0xFF0F2620)) else null,
                        highlightAlpha = if (isGpt) 0.35f else 0.22f,
                        isDark = true
                    )
                    .bouncyClickable(provideHaptic = true) { expandedSelectorDropdown = !expandedSelectorDropdown }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGpt) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = ai.deepcode.android.R.drawable.ic_chatgpt),
                        contentDescription = "ChatGPT",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = modelDisplayName,
                    color = if (isGpt) Color(0xFF10A37F) else Color.White,
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
                    tint = if (isGpt) Color(0xFF10A37F) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expandedSelectorDropdown) {
                // Fixed anchor below the pill. Previously offset was
                // 44.dp - imeBottom, so opening the picker while the keyboard
                // was up pushed it far off-screen (unusable while typing).
                val density = LocalDensity.current
                val offsetPx = with(density) { 52.dp.roundToPx() }
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
    showTimestamp: Boolean = false,
    imageCache: Map<String, ImageBitmap> = emptyMap(),
    onSelectLayout: (String) -> Unit = {},
    onSendSuggestion: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    if (message.isToolCall || message.role == "tool") return

    // Thinking is stripped entirely — never shown, not even in a box.
    val cleanedContent = remember(message.id, message.content, isUser) {
        val raw = if (message.content.endsWith("[INTERRUPTED]")) message.content.substringBeforeLast("[INTERRUPTED]").trim()
        else message.content
        if (isUser) raw.trim()
        else stripThinkingProcess(raw, isStreaming = false)
    }

    if (cleanedContent.isEmpty()) return

    val isInterrupted = message.content.endsWith("[INTERRUPTED]")
    val parsedParts = remember(cleanedContent, isUser) { parseMessageContent(cleanedContent, isUser) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!isInterrupted || cleanedContent.isNotEmpty()) {
            if (isUser) {
                UserBubble(cleanedContent = cleanedContent, parsedParts = parsedParts, message = message, showTimestamp = showTimestamp, context = context)
            } else {
                if (cleanedContent.isNotEmpty()) {
                    AiBubble(cleanedContent = cleanedContent, parsedParts = parsedParts, message = message, showTimestamp = showTimestamp, imageCache = imageCache, onSelectLayout = onSelectLayout, onSendSuggestion = onSendSuggestion)
                }
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
    showTimestamp: Boolean = false,
    context: android.content.Context
) {
    val showMenu = remember { mutableStateOf(false) }
    val pressOffset = remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    val shouldTruncate = remember(cleanedContent) {
        cleanedContent.length > 250 || cleanedContent.lines().size > 6
    }

    val topColor = if (isDarkThemeActive) {
        AppPrimary.copy(alpha = 0.32f).compositeOver(Color(0xFF16161A))
    } else {
        AppPrimary.copy(alpha = 0.22f).compositeOver(Color(0xFFF6F6F9))
    }
    val bottomColor = if (isDarkThemeActive) {
        AppPrimary.copy(alpha = 0.18f).compositeOver(Color(0xFF0C0C0F))
    } else {
        AppPrimary.copy(alpha = 0.12f).compositeOver(Color(0xFFE9E9EE))
    }

    val userBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp)

    Box(
        modifier = Modifier
            .widthIn(min = 48.dp, max = 330.dp)
            .depthCard(
                shape = userBubbleShape,
                elevation = 2.dp,
                isDark = isDarkThemeActive,
                customGradient = listOf(topColor, bottomColor),
                customBorderColor = if (isDarkThemeActive) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.40f)
            )
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressOffset.value = offset
                    showMenu.value = true
                })
            }
    ) {
        Column {
            Text(
                text = cleanedContent,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Color.White,
                maxLines = if (isExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )

            if (shouldTruncate) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Show less ⌃" else "Show more ⌵",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (showTimestamp) {
                val timeStr = remember(message.timestamp) {
                    try { timeFormatter.get()?.format(java.util.Date(message.timestamp)) ?: "" } catch (e: Exception) { "" }
                }
                if (timeStr.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.End)
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
    showTimestamp: Boolean = false,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
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
                                Icon(Icons.AutoMirrored.Filled.List, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
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
                if (showTimestamp) {
                    val timeStr = remember(message.timestamp) {
                        try { timeFormatter.get()?.format(java.util.Date(message.timestamp)) ?: "" } catch (e: Exception) { "" }
                    }
                    if (timeStr.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            timeStr,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
            if (showAiMenu.value) {
                TextContextMenu(showMenu = showAiMenu, pressOffset = pressOffset, text = cleanedContent, context = context)
            }
        }
    }
}

private object GlobalAudioPlaybackManager {
    private var currentPlayer: MediaPlayer? = null
    private var onStopCallback: (() -> Unit)? = null

    @Synchronized
    fun play(player: MediaPlayer, onStop: () -> Unit) {
        if (currentPlayer != null && currentPlayer != player) {
            try {
                if (currentPlayer?.isPlaying == true) {
                    currentPlayer?.pause()
                }
            } catch (_: Exception) {}
            try {
                onStopCallback?.invoke()
            } catch (_: Exception) {}
        }
        currentPlayer = player
        onStopCallback = onStop
    }

    @Synchronized
    fun onStopped(player: MediaPlayer) {
        if (currentPlayer == player) {
            currentPlayer = null
            onStopCallback = null
        }
    }

    @Synchronized
    fun release(player: MediaPlayer) {
        if (currentPlayer == player) {
            currentPlayer = null
            onStopCallback = null
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

private fun generateSyntheticWaveform(seedKey: String, barCount: Int = 38): List<Float> {
    val rng = java.util.Random(seedKey.hashCode().toLong())
    val result = FloatArray(barCount)
    var level = 0.4f
    for (i in 0 until barCount) {
        val normalizedPos = i.toFloat() / barCount
        val envelope = (kotlin.math.sin(normalizedPos * Math.PI.toFloat())).coerceIn(0.25f, 1f)
        val step = (rng.nextFloat() - 0.48f) * 0.45f
        level = (level + step).coerceIn(0.2f, 0.95f)
        val isPause = (i % 8 == 0 || i % 13 == 0) && i > 3 && i < barCount - 3
        val rawAmp = if (isPause) (0.15f + rng.nextFloat() * 0.12f) else (level * envelope)
        result[i] = rawAmp.coerceIn(0.12f, 0.98f)
    }
    result[0] = 0.15f
    result[1] = 0.22f
    result[barCount - 2] = 0.20f
    result[barCount - 1] = 0.15f
    return result.toList()
}

private fun extractWaveformAmplitudes(file: File, barCount: Int = 38): List<Float> {
    if (!file.exists() || file.length() < 44) {
        return generateSyntheticWaveform(file.name, barCount)
    }
    return try {
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            val length = file.length()
            val dataSize = length - 44
            if (dataSize <= 0) return generateSyntheticWaveform(file.name, barCount)

            java.io.RandomAccessFile(file, "r").use { raf ->
                val step = (dataSize / barCount).coerceAtLeast(2)
                val buffer = ByteArray(256)
                val rawAmps = FloatArray(barCount)

                for (i in 0 until barCount) {
                    val pos = 44L + i * step
                    raf.seek(pos.coerceIn(44L, (length - buffer.size).coerceAtLeast(44L)))
                    val read = raf.read(buffer)
                    var maxAmp = 0
                    var j = 0
                    while (j < read - 1) {
                        val sample = (buffer[j].toInt() and 0xFF) or (buffer[j + 1].toInt() shl 8)
                        val shortVal = sample.toShort().toInt()
                        val absVal = kotlin.math.abs(shortVal)
                        if (absVal > maxAmp) maxAmp = absVal
                        j += 2
                    }
                    rawAmps[i] = maxAmp.toFloat() / 32768f
                }

                val max = rawAmps.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
                rawAmps.map { amp ->
                    val normalized = (amp / max).coerceIn(0f, 1f)
                    0.15f + normalized * 0.85f
                }
            }
        } else {
            generateSyntheticWaveform(file.name, barCount)
        }
    } catch (e: Exception) {
        generateSyntheticWaveform(file.name, barCount)
    }
}

@Composable
private fun AudioPlayer(part: MessageContentPart.Audio) {
    val context = LocalContext.current
    val isPlaying = remember { mutableStateOf(false) }
    val isPrepared = remember { mutableStateOf(false) }
    val speedList = remember { listOf(1.0f, 1.25f, 1.5f, 2.0f) }
    val speedIndex = remember { mutableStateOf(0) }
    val currentPosMs = remember { mutableStateOf(0) }
    val totalDurationMs = remember { mutableStateOf(0) }
    val showMoreMenu = remember { mutableStateOf(false) }

    val cleanPath = remember(part.filePath) {
        part.filePath.removePrefix("file://").trim()
    }
    val audioFile = remember(cleanPath) { File(cleanPath) }

    val amplitudes = remember(cleanPath) {
        mutableStateOf(generateSyntheticWaveform(cleanPath, 38))
    }

    LaunchedEffect(cleanPath) {
        if (audioFile.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(audioFile.absolutePath)
                    val durStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val dur = durStr?.toIntOrNull() ?: 0
                    if (dur > 0) {
                        totalDurationMs.value = dur
                    }
                    mmr.release()
                } catch (e: Exception) {
                    AppLogger.e("AudioPlayer", "MMR duration error", e)
                }

                val extracted = extractWaveformAmplitudes(audioFile, 38)
                amplitudes.value = extracted
            }
        }
    }

    fun applySpeed(mp: MediaPlayer?, speed: Float) {
        if (mp == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPrepared.value && isPlaying.value) {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
            }
        } catch (e: Exception) {
            AppLogger.e("AudioPlayer", "Error setting speed", e)
        }
    }

    val mediaPlayer = remember(cleanPath) {
        if (audioFile.exists()) {
            MediaPlayer().apply {
                try {
                    setDataSource(context, Uri.fromFile(audioFile))
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        isPrepared.value = true
                        if (mp.duration > 0) {
                            totalDurationMs.value = mp.duration
                        }
                    }
                    setOnCompletionListener {
                        isPlaying.value = false
                        currentPosMs.value = 0
                        GlobalAudioPlaybackManager.onStopped(this)
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying.value = false
                        isPrepared.value = false
                        GlobalAudioPlaybackManager.onStopped(this)
                        true
                    }
                    prepareAsync()
                } catch (e: Exception) {
                    AppLogger.e("AudioPlayer", "Error preparing audio", e)
                }
            }
        } else null
    }

    DisposableEffect(cleanPath) {
        onDispose {
            try {
                mediaPlayer?.let {
                    GlobalAudioPlaybackManager.release(it)
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying.value) {
        if (isPlaying.value) {
            while (isPlaying.value) {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            currentPosMs.value = mp.currentPosition
                            if (mp.duration > 0 && totalDurationMs.value <= 0) {
                                totalDurationMs.value = mp.duration
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(40)
            }
        }
    }

    fun seekToFraction(fraction: Float) {
        val dur = totalDurationMs.value
        if (dur > 0) {
            val targetMs = (fraction.coerceIn(0f, 1f) * dur).toInt()
            currentPosMs.value = targetMs
            mediaPlayer?.let { mp ->
                try {
                    if (isPrepared.value) {
                        mp.seekTo(targetMs)
                    }
                } catch (e: Exception) {
                    AppLogger.e("AudioPlayer", "Error seeking", e)
                }
            }
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
            .depthCard(
                shape = RoundedCornerShape(22.dp),
                elevation = 2.dp,
                isDark = true,
                customGradient = listOf(Color(0xFF17181C), Color(0xFF0E0F12)),
                customBorderColor = Color.White.copy(alpha = 0.08f)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Play / Pause Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .depthPill(
                    shape = CircleShape,
                    elevation = 2.dp,
                    isDark = true,
                    customGradient = listOf(Color(0xFF332D1E), Color(0xFF201B0F)),
                    customBorderColor = Color(0xFFEAA315).copy(alpha = 0.35f)
                )
                .bouncyClickable(provideHaptic = true) {
                    if (!audioFile.exists()) {
                        Toast.makeText(context, "Audio file not ready", Toast.LENGTH_SHORT).show()
                        return@bouncyClickable
                    }
                    val mp = mediaPlayer ?: return@bouncyClickable
                    try {
                        if (isPlaying.value) {
                            mp.pause()
                            isPlaying.value = false
                            GlobalAudioPlaybackManager.onStopped(mp)
                        } else {
                            if (!isPrepared.value) {
                                Toast.makeText(context, "Loading audio...", Toast.LENGTH_SHORT).show()
                                return@bouncyClickable
                            }
                            GlobalAudioPlaybackManager.play(mp) {
                                isPlaying.value = false
                            }
                            if (currentPosMs.value == 0 || (totalDurationMs.value > 0 && currentPosMs.value >= totalDurationMs.value - 400)) {
                                mp.seekTo(0)
                                currentPosMs.value = 0
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val params = mp.playbackParams
                                    params.speed = speedList[speedIndex.value]
                                    mp.playbackParams = params
                                } catch (_: Exception) {}
                            }
                            mp.start()
                            isPlaying.value = true
                        }
                    } catch (e: Exception) {
                        isPlaying.value = false
                        GlobalAudioPlaybackManager.onStopped(mp)
                        Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying.value) "Pause" else "Play",
                tint = Color(0xFFEAA315),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // 2. Waveform + Timestamps
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val amps = amplitudes.value
            val progress = if (totalDurationMs.value > 0) {
                (currentPosMs.value.toFloat() / totalDurationMs.value.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(totalDurationMs.value) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                seekToFraction(offset.x / size.width)
                            }
                        }
                    }
                    .pointerInput(totalDurationMs.value) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                seekToFraction(change.position.x / size.width)
                            }
                        }
                    }
            ) {
                val barCount = amps.size
                if (barCount > 0 && size.width > 0) {
                    val step = size.width / barCount
                    val barWidth = (step * 0.45f).coerceIn(2.dp.toPx(), 3.2.dp.toPx())
                    val centerY = size.height / 2f
                    val maxHeight = size.height - 4.dp.toPx()

                    for (i in 0 until barCount) {
                        val barCenterX = (i + 0.5f) * step
                        val isPlayed = (barCenterX / size.width) <= progress
                        val barColor = if (isPlayed) Color.White else Color.White.copy(alpha = 0.28f)
                        val barHeight = (amps[i] * maxHeight).coerceAtLeast(3.dp.toPx())

                        drawLine(
                            color = barColor,
                            start = Offset(barCenterX, centerY - barHeight / 2f),
                            end = Offset(barCenterX, centerY + barHeight / 2f),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatAudioTime(currentPosMs.value),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = formatAudioTime(totalDurationMs.value),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // 3. Speed Pill
        Box(
            modifier = Modifier
                .depthPill(
                    shape = RoundedCornerShape(16.dp),
                    elevation = 1.5.dp,
                    isDark = true,
                    customGradient = listOf(Color(0xFF1B1C20), Color(0xFF101114)),
                    customBorderColor = Color.White.copy(alpha = 0.08f)
                )
                .bouncyClickable(provideHaptic = true) {
                    val nextIdx = (speedIndex.value + 1) % speedList.size
                    speedIndex.value = nextIdx
                    applySpeed(mediaPlayer, speedList[nextIdx])
                }
                .padding(horizontal = 11.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = speedText,
                color = Color(0xFFEAA315),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(4.dp))

        // 4. More Options Menu
        Box {
            IconButton(
                onClick = { showMoreMenu.value = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMoreMenu.value,
                onDismissRequest = { showMoreMenu.value = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Share Audio") },
                    leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMoreMenu.value = false
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                audioFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save to Downloads") },
                    leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMoreMenu.value = false
                        try {
                            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), audioFile.name)
                            audioFile.copyTo(dest, overwrite = true)
                            Toast.makeText(context, "Saved: ${dest.name}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy File Path") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMoreMenu.value = false
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Audio Path", audioFile.absolutePath))
                        Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }
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
        if (url.startsWith("http") || url.startsWith("file") || url.startsWith("/")) refs.add(url)
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
    StreamingBubble(
        text = streamedText,
        imageCache = imageCache,
        mediaProcessingType = mediaProcessingType,
        mediaProcessingPrompt = mediaProcessingPrompt
    )
}

// ═══════════════════════════════════════════════
@Composable
fun StreamingBubble(
    text: String,
    imageCache: Map<String, ImageBitmap> = emptyMap(),
    mediaProcessingType: String? = null,
    mediaProcessingPrompt: String = ""
) {
    // Thinking stripped entirely — stream only the final answer text.
    val cleanText = remember(text) {
        stripThinkingProcess(text, isStreaming = true)
    }
    val isImageGenerating = remember(cleanText, mediaProcessingType) {
        mediaProcessingType == "image" || cleanText.contains("Generating image", ignoreCase = true)
    }
    val isAudioGenerating = remember(cleanText, mediaProcessingType) {
        mediaProcessingType == "audio" || cleanText.contains("Generating audio", ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        // NOTE: intentionally NO animateContentSize here. During streaming this
        // composable recomposes per token; animating size on every frame
        // restarts a spring each time and fights the LazyColumn measurement,
        // which reads as vertical jitter. Text growth without animation is
        // already smooth because the follow-scroll keeps the tail pinned.
        horizontalAlignment = Alignment.Start
    ) {
        if (isImageGenerating) {
            ImageGenerationSkeleton(statusText = "Creating image")
        } else if (cleanText.isEmpty() || isAudioGenerating) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // transitions.dev P28: Ambient breathing thinking status pill
                AnimatedThinkingPill(
                    statusText = "Thinking...",
                    accentColor = AppPrimary
                )
            }
        } else {
            val codeBg = if (isDarkThemeActive) Color(0xFF232530) else Color(0xFFEFF0F4)
            val codeColor = AppPrimary
            val linkColor = Color(0xFF3B82F6)
            val textColor = MaterialTheme.colorScheme.onSurface

            val streamingAnnotated = remember(cleanText, codeBg, codeColor, linkColor, textColor) {
                buildAnnotatedString {
                    append(
                        buildStreamingMarkdown(
                            text = cleanText,
                            codeBg = codeBg,
                            codeColor = codeColor,
                            linkColor = linkColor,
                            textColor = textColor
                        )
                    )
                    withStyle(
                        SpanStyle(
                            color = codeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    ) {
                        append(" ▍")
                    }
                }
            }

            Text(
                text = streamingAnnotated,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
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
    ReasoningAccordion(
        thought = thought,
        isExpanded = true,
        onToggle = {},
        isLiveStreaming = true,
        accentColor = AppPrimary
    )
}

@Composable
fun ThreeDotLoader() {
    TravelingWaveLoader(dotColor = AppPrimary)
}

@Composable
fun BlinkingRobotIcon() {
    Icon(Icons.Default.SmartToy, "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
}

// ═══════════════════════════════════════════════
// Thought Block (transitions.dev P21 Accordion & P28 Reasoning stream)
// ═══════════════════════════════════════════════
@Composable
fun ThoughtBlock(thought: String) {
    var expanded by remember { mutableStateOf(false) }
    ReasoningAccordion(
        thought = thought,
        isExpanded = expanded,
        onToggle = { expanded = !expanded },
        isLiveStreaming = false,
        accentColor = AppPrimary
    )
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
    val filteredModels = remember(searchFilter, allModels) {
        if (searchFilter.isBlank()) allModels
        else allModels.filter {
            it.name.contains(searchFilter, ignoreCase = true) || it.provider.contains(searchFilter, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = AppCard,
            border = BorderStroke(1.dp, AppBorder),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Select AI Model",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = AppWhite
                        )
                        Text(
                            "${allModels.size} models available",
                            fontSize = 11.sp,
                            color = AppMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search models or providers...", fontSize = 13.sp, color = AppMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = AppWhite),
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = AppMuted, modifier = Modifier.size(16.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPrimary.copy(alpha = 0.6f),
                        unfocusedBorderColor = AppBorder,
                        focusedContainerColor = AppField,
                        unfocusedContainerColor = AppField
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Models list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        val isSelected = model.id == currentSelected.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else AppField.copy(alpha = 0.4f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) AppPrimary.copy(alpha = 0.4f) else AppBorder.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .bouncyClickable(provideHaptic = true) {
                                    onModelSelected(model)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (isSelected) AppPrimary else AppWhite
                                )
                                val displayProvider = if (model.provider == "Zen (Free)" || model.provider == "Zen AI") "Zen AI" else model.provider
                                Text(
                                    "$displayProvider • Context: ${model.contextWindow}",
                                    fontSize = 11.sp,
                                    color = AppMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AppPrimary.copy(alpha = 0.2f) else AppCard)
                                    .border(1.dp, if (isSelected) AppPrimary.copy(alpha = 0.4f) else AppBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    model.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) AppPrimary else AppMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
        list.filterNot { it.name == "edge_tts" || it.name == "tool" || it.result.contains("[audio:") }
    }
    if (toolCalls.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        toolCalls.forEach { item ->
            val status = if (item.result.startsWith("Error") || item.result.contains("failed", ignoreCase = true)) "FAILED" else if (item.result == "Executing...") "RUNNING" else "SUCCESS"
            ToolCallCard(toolName = item.name, status = status, result = item.result, argsJson = item.argsJson, modifier = Modifier.fillMaxWidth())
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = repository.securePrefs
    val catalog by ModelCatalog.models.collectAsStateWithLifecycle()

    var refreshingProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshingAll by remember { mutableStateOf(false) }

    // Load previously cached models from preferences once when the overlay is created
    LaunchedEffect(Unit) {
        ModelCatalog.loadFromPrefs(securePrefs)
    }

    fun isProviderConfigured(provider: AIProvider): Boolean {
        if (provider.isFree) return true
        val storageId = providerStorageId(provider.name)
        val aliasKeys = mutableListOf(storageId)
        if (storageId.contains("-")) aliasKeys.add(storageId.replace("-", ""))
        if (storageId == "cerebras") aliasKeys.add("cerebrus")
        if (storageId == "cerebrus") aliasKeys.add("cerebras")
        if (storageId == "gemini") aliasKeys.add("google gemini")
        if (storageId == "gmi") aliasKeys.add("gmi-cloud")
        if (storageId == "gmi-cloud") aliasKeys.add("gmi")
        if (storageId == "aimlapi") aliasKeys.add("aiml-api")
        if (storageId == "nebius") aliasKeys.add("nebius-ai")
        if (storageId == "friendliai") aliasKeys.add("friendli-ai")
        if (storageId == "together") aliasKeys.add("together-ai")
        if (storageId == "fireworks") aliasKeys.add("fireworks-ai")
        if (storageId == "nvidia") aliasKeys.add("nvidia-nim")

        for (k in aliasKeys) {
            val key = ApiKeyRotator.getNextAvailableKey(securePrefs, k)?.first
                ?: securePrefs.getApiKey(k)
            if (key.isNotEmpty()) return true
            if (securePrefs.getSetting("oauth_token_$k", "").isNotEmpty()) return true
            if (securePrefs.getSetting("web_cookie_$k", "").isNotEmpty()) return true
        }
        return false
    }

    val configuredProviders = remember {
        AIProviderFactory.providers.filter { isProviderConfigured(it) }
    }

    var expandedProviderName by remember {
        mutableStateOf(if (configuredProviders.any { it.name == activeModel.provider }) activeModel.provider else configuredProviders.firstOrNull()?.name ?: "")
    }

    fun refreshSingleProvider(provider: AIProvider) {
        if (refreshingProviders.contains(provider.name)) return
        refreshingProviders = refreshingProviders + provider.name
        scope.launch(Dispatchers.IO) {
            try {
                val storageId = providerStorageId(provider.name)
                var apiKey = ApiKeyRotator.getNextAvailableKey(securePrefs, storageId)?.first
                    ?: securePrefs.getApiKey(storageId)
                if (apiKey.isEmpty()) {
                    apiKey = securePrefs.getSetting("oauth_token_$storageId", "")
                }
                if (apiKey.isEmpty() && (provider.name == "Zen AI" || provider.name == "Zen (Free)")) {
                    apiKey = "zen-free"
                }
                val models = if (provider.name == "Antigravity") {
                    fetchAntigravityModels(apiKey.split("||")[0])
                } else {
                    val baseUrl = providerDefaultBaseUrl(provider.name)
                    fetchModels(apiKey, baseUrl, provider.name)
                }
                withContext(Dispatchers.Main) {
                    if (models.isNotEmpty()) {
                        ModelCatalog.setModels(provider.name, models, securePrefs)
                        Toast.makeText(context, "Loaded ${models.size} models for ${provider.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No models returned for ${provider.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to refresh ${provider.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    refreshingProviders = refreshingProviders - provider.name
                }
            }
        }
    }

    fun refreshAllProviders() {
        if (isRefreshingAll) return
        isRefreshingAll = true
        scope.launch(Dispatchers.IO) {
            var totalCount = 0
            for (provider in configuredProviders) {
                try {
                    val storageId = providerStorageId(provider.name)
                    var apiKey = ApiKeyRotator.getNextAvailableKey(securePrefs, storageId)?.first
                        ?: securePrefs.getApiKey(storageId)
                    if (apiKey.isEmpty()) {
                        apiKey = securePrefs.getSetting("oauth_token_$storageId", "")
                    }
                    if (apiKey.isEmpty() && (provider.name == "Zen AI" || provider.name == "Zen (Free)")) {
                        apiKey = "zen-free"
                    }
                    val models = if (provider.name == "Antigravity") {
                        fetchAntigravityModels(apiKey.split("||")[0])
                    } else {
                        val baseUrl = providerDefaultBaseUrl(provider.name)
                        fetchModels(apiKey, baseUrl, provider.name)
                    }
                    if (models.isNotEmpty()) {
                        ModelCatalog.setModels(provider.name, models, securePrefs)
                        totalCount += models.size
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                isRefreshingAll = false
                Toast.makeText(context, "Refreshed all models ($totalCount total)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .width(280.dp)
        .heightIn(max = 480.dp)
        .depthCard(
            shape = RoundedCornerShape(22.dp),
            elevation = 6.dp,
            isDark = isDarkThemeActive
        )
        .verticalScroll(scrollState)
        .padding(10.dp),
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
                        .depthPill(
                            shape = CircleShape,
                            elevation = 2.dp,
                            customGradient = listOf(Color(0xFFF59E0B).copy(alpha = 0.25f), Color(0xFFF59E0B).copy(alpha = 0.10f)),
                            customBorderColor = Color(0xFFF59E0B).copy(alpha = 0.5f)
                        ),
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
                        .depthPill(
                            shape = RoundedCornerShape(12.dp),
                            elevation = 2.5.dp,
                            customGradient = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
                            customBorderColor = Color.White.copy(alpha = 0.35f)
                        )
                        .bouncyClickable(provideHaptic = true) { onOpenApiKeys() }
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
            // Header: "All Providers" with global refresh icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "All Providers",
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                IconButton(
                    onClick = { refreshAllProviders() },
                    modifier = Modifier.size(28.dp),
                    enabled = !isRefreshingAll && refreshingProviders.isEmpty()
                ) {
                    if (isRefreshingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AppPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh all providers",
                            tint = AppMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = AppDivider, modifier = Modifier.padding(bottom = 2.dp))

            configuredProviders.forEach { provider ->
                val isProviderExpanded = expandedProviderName == provider.name
                val providerColor = when (provider.name) {
                    "Google Gemini" -> Color(0xFF8B5CF6); "Zen AI" -> Color(0xFF8B5CF6); "Zen (Free)" -> Color(0xFF8B5CF6)
                    "Mistral AI" -> Color(0xFFEC4899); "Ollama Cloud" -> Color(0xFF6B7280)
                    "Omniroute" -> Color(0xFF10B981); else -> Color(0xFF8B5CF6)
                }

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 1.5.dp,
                        isDark = isDarkThemeActive
                    )
                    .padding(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { expandedProviderName = if (isProviderExpanded) "" else provider.name }
                                .padding(vertical = 4.dp)
                        ) {
                            ProviderMiniLogo(provider.name)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (provider.name == "Zen (Free)" || provider.name == "Zen AI") "Zen AI" else provider.name,
                                color = if (isProviderExpanded) providerColor else AppWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isRefreshingThis = refreshingProviders.contains(provider.name) || isRefreshingAll
                            IconButton(
                                onClick = { refreshSingleProvider(provider) },
                                modifier = Modifier.size(28.dp),
                                enabled = !isRefreshingThis
                            ) {
                                if (isRefreshingThis) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = providerColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh ${provider.name} models",
                                        tint = if (isProviderExpanded) providerColor else AppMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { expandedProviderName = if (isProviderExpanded) "" else provider.name },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isProviderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isProviderExpanded) "Collapse" else "Expand",
                                    tint = if (isProviderExpanded) providerColor else AppMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isProviderExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.animateContentSize()) {
                            val fetchedModels = catalog[provider.name].orEmpty().ifEmpty {
                                ModelCatalog.getModelsForProvider(provider.name, securePrefs)
                            }
                            val allModels = (if (fetchedModels.isNotEmpty()) fetchedModels else provider.models)
                                .filterNot { DecommissionedModels.isDecommissioned(it.id) }
                            val filterPref = securePrefs.getSetting("model_filter_${provider.name}", "all")
                            val filteredModels = when (filterPref) {
                                "free" -> allModels.filter { it.isFree || it.badge == "Free" }
                                "paid" -> allModels.filter { !it.isFree && it.badge != "Free" }
                                else -> allModels
                            }
                            val storageId = providerStorageId(provider.name)
                            val selectedIdsStr = securePrefs.getSetting("selected_models_$storageId", "")
                            val finalModels = if (selectedIdsStr.isNotBlank()) {
                                val selectedIdSet = selectedIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                val matched = filteredModels.filter { it.id in selectedIdSet }
                                if (matched.isNotEmpty()) matched else filteredModels
                            } else {
                                filteredModels
                            }
                            finalModels.forEach { model ->
                                val isSelected = activeModel.id == model.id
                                val rowModifier = if (isSelected) {
                                    Modifier
                                        .fillMaxWidth()
                                        .depthPill(
                                            shape = RoundedCornerShape(14.dp),
                                            elevation = 1.5.dp,
                                            isDark = isDarkThemeActive,
                                            customGradient = listOf(Color(0xFF8B5CF6).copy(alpha = 0.25f), Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                                            customBorderColor = Color(0xFF8B5CF6).copy(alpha = 0.6f)
                                        )
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                }
                                Row(
                                    Modifier
                                        .then(rowModifier)
                                        .bouncyClickable(provideHaptic = true) { onModelSelected(model) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
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
        "TokenHarbor", "Token Harbor" -> Triple(Color(0xFF0EA5E9).copy(alpha = 0.15f), Color(0xFF0EA5E9), "Th")
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
    val currentToolGroup = mutableListOf<Message>()

    fun flushToolGroup() {
        if (currentToolGroup.isNotEmpty()) {
            val valid = currentToolGroup.filterNot { msg ->
                val json = msg.toolCallsJson ?: ""
                json.contains("edge_tts") || json.contains("\"tool\"") || msg.content.contains("[audio:")
            }
            if (valid.isNotEmpty()) {
                result.add(ChatItem.ToolExecutionGroup(valid.first().id, valid.toList()))
            }
            currentToolGroup.clear()
        }
    }

    for (msg in messages) {
        if (msg.isToolCall || msg.role == "tool") {
            currentToolGroup.add(msg)
        } else {
            flushToolGroup()
            result.add(ChatItem.NormalMessage(msg))
        }
    }
    flushToolGroup()
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

    private val _activeSessionIdFlow = MutableStateFlow("")
    val activeSessionIdFlow = _activeSessionIdFlow.asStateFlow()
    var activeSessionId: String
        get() = _activeSessionIdFlow.value
        private set(value) {
            _activeSessionIdFlow.value = value
        }

    private var messagesJob: Job? = null
    private var sendJob: Job? = null
    private var orchestrator: OrchestratorEngine? = null

    init {
        val prefs = repository.securePrefs
        val savedProvider = prefs.getSetting("chat_provider", "Zen AI")
        val rawSavedModelId = prefs.getSetting("chat_model", "")
        val savedModelId = if (rawSavedModelId.isNotEmpty()) DecommissionedModels.sanitize(rawSavedModelId) else ""
        if (savedModelId != rawSavedModelId && savedModelId.isNotEmpty()) {
            prefs.saveSetting("chat_model", savedModelId)
        }
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

    private fun updateModelForSession(sessionId: String) {
        val prefs = repository.securePrefs
        val sessionProvider = prefs.getSetting("session_provider_$sessionId", "")
        val rawSessionModelId = prefs.getSetting("session_model_$sessionId", "")
        val sessionModelId = if (rawSessionModelId.isNotEmpty()) DecommissionedModels.sanitize(rawSessionModelId) else ""
        if (sessionModelId != rawSessionModelId && sessionModelId.isNotEmpty()) {
            prefs.saveSetting("session_model_$sessionId", sessionModelId)
        }

        if (sessionProvider.equals("ChatGPT", ignoreCase = true) || sessionModelId.equals("chatgpt-4o", ignoreCase = true)) {
            _activeModel.value = AIModel(
                id = "chatgpt-4o",
                name = "ChatGPT",
                provider = "ChatGPT",
                isFree = true,
                contextWindow = "128k",
                badge = "GPT"
            )
            return
        }

        val targetProvider = sessionProvider.ifEmpty { prefs.getSetting("chat_provider", "Zen AI") }
        val rawTargetModelId = sessionModelId.ifEmpty { prefs.getSetting("chat_model", "") }
        val targetModelId = if (rawTargetModelId.isNotEmpty()) DecommissionedModels.sanitize(rawTargetModelId) else ""

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
    }

    fun switchSession(sessionId: String) {
        if (sessionId.isBlank()) return
        val isSameSession = (sessionId == activeSessionId)
        if (isSameSession && messagesJob?.isActive == true) {
            // Already observing this session, keep sendJob running, but ensure model matches session settings!
            updateModelForSession(sessionId)
            return
        }
        activeSessionId = sessionId

        if (!isSameSession) {
            messagesJob?.cancel()
            _messages.value = emptyList() // Clear immediately to avoid displaying stale messages from previous session
            // Don't leak in-flight send into the new session (was writing tool results to wrong session).
            sendJob?.cancel()
            sendJob = null
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
        }

        updateModelForSession(sessionId)

        if (messagesJob?.isActive != true) {
            messagesJob = viewModelScope.launch(Dispatchers.IO) {
                repository.getMessagesForSession(sessionId).collect { msgList ->
                    _messages.update { current ->
                        val currentIds = current.map { it.id }
                        val newIds = msgList.map { it.id }
                        if (currentIds == newIds && current.lastOrNull()?.content == msgList.lastOrNull()?.content) {
                            current
                        } else if (current.size > msgList.size && current.take(msgList.size).map { it.id } == newIds) {
                            current
                        } else {
                            msgList
                        }
                    }
                }
            }
        }
    }

    fun changeActiveModel(model: AIModel) {
        val sanitizedId = DecommissionedModels.sanitize(model.id)
        val sanitizedModel = if (sanitizedId != model.id) model.copy(id = sanitizedId) else model
        _activeModel.value = sanitizedModel
        repository.securePrefs.saveSetting("chat_provider", sanitizedModel.provider)
        repository.securePrefs.saveSetting("chat_model", sanitizedModel.id)
        repository.securePrefs.saveSetting("agent_provider", sanitizedModel.provider)
        repository.securePrefs.saveSetting("agent_model", sanitizedModel.id)
        if (activeSessionId.isNotEmpty()) {
            repository.securePrefs.saveSetting("session_provider_$activeSessionId", sanitizedModel.provider)
            repository.securePrefs.saveSetting("session_model_$activeSessionId", sanitizedModel.id)
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
        // Prevent concurrent sends racing on _streamedText / _streamingMessageId (was overwriting + DB REPLACE collision).
        if (_isStreaming.value) return
        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            var sessionId = activeSessionId
            val dynamicTitle = generateDynamicTitle(text)
            if (sessionId.isEmpty()) {
                sessionId = repository.createSession(dynamicTitle)
                activeSessionId = sessionId
                messagesJob?.cancel()
                messagesJob = viewModelScope.launch(Dispatchers.IO) {
                    repository.getMessagesForSession(sessionId).collect { msgList ->
                        _messages.update { current ->
                            val currentIds = current.map { it.id }
                            val newIds = msgList.map { it.id }
                            if (currentIds == newIds && current.lastOrNull()?.content == msgList.lastOrNull()?.content) {
                                current
                            } else if (current.size > msgList.size && current.take(msgList.size).map { it.id } == newIds) {
                                current
                            } else {
                                msgList
                            }
                        }
                    }
                }
            } else {
                val currentSession = repository.getSessionById(sessionId)
                if (currentSession != null && (currentSession.title.isBlank() || currentSession.title.matches(RE_UNTITLED_SESSION))) {
                    repository.renameSession(sessionId, dynamicTitle)
                }
            }
            val currentAttachments = _attachedFiles.value
            val isImageAtt = { f: AttachedFile ->
                val n = f.name.lowercase()
                val p = f.filePath?.lowercase() ?: ""
                n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
                n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") ||
                p.endsWith(".webp") || p.endsWith(".gif") || p.endsWith(".bmp")
            }
            val uploadedImage = currentAttachments.firstOrNull { isImageAtt(it) }

            val msgText = if (currentAttachments.isNotEmpty()) {
                val filesSection = currentAttachments.joinToString("\n") { f ->
                    if (isImageAtt(f) && f.filePath != null) {
                        "[image:${f.filePath}]"
                    } else if (f.filePath != null) {
                        "📎 ${f.name}\n[File: ${f.filePath}]"
                    } else {
                        "📎 ${f.name}\n${f.content}"
                    }
                }
                if (text.isBlank()) filesSection else "$filesSection\n\n$text"
            } else text

            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = msgText,
                timestamp = System.currentTimeMillis()
            )
            // 1. Synchronously update in-memory message list so UI renders userMsg instantly
            _messages.update { current ->
                if (current.none { it.id == userMsg.id }) current + userMsg else current
            }
            _attachedFiles.value = emptyList()
            _streamedText.value = ""
            toolCallDepth = 0
            consecutiveWebSearches = 0
            executedToolSignatures.clear()
            lastExecutedToolName = ""
            val msgId = UUID.randomUUID().toString()
            _streamingMessageId.value = msgId
            _isStreaming.value = true

            // 2. Persist to Room SQLite in background
            repository.insertMessage(userMsg)

            if (isAudioCreationRequest(text)) {
                val isMeta = isMetaReferenceText(text)
                val targetText: String? = if (isMeta) {
                    val history = repository.getMessagesListForSession(sessionId)
                    val lastAssistant = history.lastOrNull { msg ->
                        msg.role == "assistant" &&
                        !msg.isToolCall &&
                        !msg.content.startsWith("Executing tool") &&
                        !msg.content.startsWith("Running tool") &&
                        !msg.content.startsWith("I've completed") &&
                        !msg.content.startsWith("Tool result:") &&
                        msg.content.replace(RE_MEDIA_TAG, "").trim().length > 3
                    }
                    lastAssistant?.content
                        ?.replace(RE_AUDIO_TAG, "")
                        ?.replace(RE_FILE_TAG, "")
                        ?.replace(RE_IMAGE_TAG, "")
                        ?.replace(RE_VIDEO_TAG, "")
                        ?.trim()
                } else {
                    val directMatch = Regex("""^(?:create\s+audio\s*(?:of|for|from)?\s*:\s*|read\s+(?:this|aloud)?\s*:\s*|speak\s*(?:this)?\s*:\s*)(.+)$""", RegexOption.IGNORE_CASE).find(text.trim())
                    directMatch?.groupValues?.get(1)?.trim()
                }

                if (!targetText.isNullOrBlank()) {
                    _mediaProcessingType.value = "audio"
                    _mediaProcessingPrompt.value = "Thinking..."
                    _streamedText.value = ""

                    val ttsArgs = com.google.gson.JsonObject().apply {
                        addProperty("text", targetText)
                    }.toString()

                    val result = try {
                        repository.executeTool("edge_tts", ttsArgs, repository.getDefaultProjectPath())
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }

                    val mediaMatch = RE_MEDIA_TAG.find(result)
                    val audioTag = mediaMatch?.value ?: if (result.contains("[audio:")) result else null
                    if (audioTag != null) {
                        appendAssistantMessage(audioTag, sessionId)
                    } else {
                        appendAssistantMessage("Failed to generate audio: $result", sessionId)
                    }
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                    return@launch
                }
            }

            if (isImageCreationRequest(text)) {
                val imagePrompt = extractImagePrompt(text)
                if (imagePrompt.isNotBlank()) {
                    _mediaProcessingType.value = "image"
                    _mediaProcessingPrompt.value = "Generating image with ChatGPT..."
                    _streamedText.value = ""

                    val imgArgs = com.google.gson.JsonObject().apply {
                        addProperty("prompt", imagePrompt)
                    }.toString()

                    val result = try {
                        repository.executeTool("generate_image", imgArgs, repository.getDefaultProjectPath())
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }

                    val mediaMatch = RE_MEDIA_TAG.find(result)
                    val imgTag = mediaMatch?.value ?: if (result.contains("[image:")) result else null
                    if (imgTag != null) {
                        appendAssistantMessage(imgTag, sessionId)
                    } else {
                        appendAssistantMessage("Failed to generate image: $result", sessionId)
                    }
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                    return@launch
                }
            }

            // Direct Automation creation fast-path (instant scheduling)
            val autoHandler = try { ai.deepcode.android.service.schedule.AutomationHandler(repository.appContext) } catch (_: Exception) { null }
            val autoIntent = autoHandler?.parse(text)
            if (autoIntent != null) {
                _mediaProcessingType.value = "tool"
                _mediaProcessingPrompt.value = "Scheduling automation..."
                _streamedText.value = ""
                val autoResponse = try {
                    autoHandler.create(text)
                } catch (e: Exception) {
                    "Failed to schedule task: ${e.message}"
                }
                if (autoResponse.isNotBlank()) {
                    appendAssistantMessage(autoResponse, sessionId)
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                    return@launch
                }
            }

            // Direct GitHub action fast-path (instant query resolution)
            val ghQuery = try { ai.deepcode.android.service.github.GitHubHandler(repository.appContext).parse(text) } catch (_: Exception) { null }
            if (ghQuery != null) {
                _mediaProcessingType.value = "tool"
                _mediaProcessingPrompt.value = "Fetching from GitHub..."
                _streamedText.value = ""
                val ghResponse = try {
                    ai.deepcode.android.service.github.GitHubHandler(repository.appContext).fetch(text)
                } catch (e: Exception) {
                    "GitHub Error: ${e.message}"
                }
                if (ghResponse.isNotBlank()) {
                    appendAssistantMessage(ghResponse, sessionId)
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                    return@launch
                }
            }

            val isChatGptSession = _activeModel.value.provider.equals("ChatGPT", ignoreCase = true) ||
                    _activeModel.value.id.equals("chatgpt-4o", ignoreCase = true) ||
                    repository.securePrefs.getSetting("session_provider_$sessionId", "").equals("ChatGPT", ignoreCase = true)

            // Route uploaded images or image action buttons to ChatGPT, or route all messages in dedicated ChatGPT session
            val isImageAction = text.contains("Remove background", ignoreCase = true) ||
                    text.contains("Erase the", ignoreCase = true) ||
                    text.contains("Resize and reframe", ignoreCase = true) ||
                    text.contains("For this image:", ignoreCase = true)
            val hasImageTag = RE_IMAGE_TAG.containsMatchIn(msgText) || RE_MARKDOWN_IMAGE.containsMatchIn(msgText)
            val shouldRouteToChatGPT = isChatGptSession || uploadedImage != null || isImageAction || (hasImageTag && !isAudioCreationRequest(text))

            if (shouldRouteToChatGPT) {
                val targetImagePath = uploadedImage?.filePath
                    ?: RE_IMAGE_TAG.find(msgText)?.groupValues?.get(1)?.trim()
                    ?: RE_MARKDOWN_IMAGE.find(msgText)?.groupValues?.get(2)?.trim()

                _mediaProcessingType.value = "chatgpt"
                _mediaProcessingPrompt.value = if (targetImagePath != null) "ChatGPT analyzing image..." else "ChatGPT thinking..."
                _streamedText.value = ""

                val promptForGpt = text.replace(RE_IMAGE_TAG, "").replace(RE_MARKDOWN_IMAGE, "").trim().ifEmpty {
                    if (targetImagePath != null) "Describe and analyze this image in detail." else text
                }

                val bridge = ai.deepcode.android.service.chatgpt.ChatGPTBridge.getInstance(repository.appContext)
                val accumulated = StringBuilder()

                bridge.streamTurn(
                    prompt = promptForGpt,
                    imagePath = targetImagePath,
                    onToken = { token ->
                        _mediaProcessingType.value = null
                        _mediaProcessingPrompt.value = ""
                        accumulated.append(token)
                        _streamedText.value = accumulated.toString()
                    },
                    onComplete = { fullText ->
                        _isStreaming.value = false
                        _streamingMessageId.value = ""
                        _mediaProcessingType.value = null
                        _mediaProcessingPrompt.value = ""
                        _streamedText.value = ""
                        val finalText = if (fullText.isNotBlank()) fullText else accumulated.toString().trim()
                        viewModelScope.launch(Dispatchers.IO) {
                            if (finalText.isNotBlank()) {
                                appendAssistantMessage(finalText, sessionId)
                            } else {
                                appendAssistantMessage("ChatGPT completed without text.", sessionId)
                            }
                        }
                    },
                    onError = { err ->
                        if (err is kotlin.coroutines.cancellation.CancellationException) return@streamTurn
                        _isStreaming.value = false
                        _streamingMessageId.value = ""
                        _mediaProcessingType.value = null
                        _mediaProcessingPrompt.value = ""
                        _streamedText.value = ""
                        viewModelScope.launch(Dispatchers.IO) {
                            appendAssistantMessage("ChatGPT Error: ${err.message ?: "Unknown error"}", sessionId)
                        }
                    }
                )
                return@launch
            }

            val model = _activeModel.value
            val provider = AIProviderFactory.providers.find { it.name.equals(model.provider, ignoreCase = true) }
                ?: OPENAI_PROVIDERS.find { it.name.equals(model.provider, ignoreCase = true) }?.let { GenericOpenAIProvider(it) }
            if (provider == null) {
                _streamedText.value = "Provider ${model.provider} not available"
                _isStreaming.value = false
                appendAssistantMessage(_streamedText.value)
                return@launch
            }

            val storageId = providerStorageId(model.provider)
            var currentKeySlot = 0
            var apiKey = ""
            val aliasKeys = mutableListOf(storageId)
            if (storageId.contains("-")) aliasKeys.add(storageId.replace("-", ""))
            if (storageId == "cerebras") aliasKeys.add("cerebrus")
            if (storageId == "cerebrus") aliasKeys.add("cerebras")
            if (storageId == "gemini") aliasKeys.add("google gemini")
            if (storageId == "gmi") aliasKeys.add("gmi-cloud")
            if (storageId == "gmi-cloud") aliasKeys.add("gmi")
            if (storageId == "aimlapi") aliasKeys.add("aiml-api")
            if (storageId == "nebius") aliasKeys.add("nebius-ai")
            if (storageId == "friendliai") aliasKeys.add("friendli-ai")
            if (storageId == "together") aliasKeys.add("together-ai")
            if (storageId == "fireworks") aliasKeys.add("fireworks-ai")
            if (storageId == "nvidia") aliasKeys.add("nvidia-nim")

            for (k in aliasKeys) {
                val rotatorResult = ApiKeyRotator.getNextAvailableKey(repository.securePrefs, k)
                if (rotatorResult != null && rotatorResult.first.isNotEmpty()) {
                    apiKey = rotatorResult.first
                    currentKeySlot = rotatorResult.second
                    break
                }
                val raw = repository.securePrefs.getApiKey(k)
                if (raw.isNotEmpty()) {
                    apiKey = raw
                    currentKeySlot = 1
                    break
                }
                val oauth = repository.securePrefs.getSetting("oauth_token_$k", "")
                if (oauth.isNotEmpty()) {
                    apiKey = oauth
                    break
                }
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
            if (currentKeySlot == 0 && apiKey.isNotEmpty()) {
                currentKeySlot = ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, apiKey)
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
                var pacingJob: kotlinx.coroutines.Job? = null
                try {
                    _streamedText.value = ""
                    var streamHadToolCall = false
                    // Cross-thread flag: written on the provider's network callback
                    // thread and read on the pacing coroutine / other callbacks.
                    // AtomicBoolean gives visibility without requiring bufferLock.
                    val streamHadAudioTool = java.util.concurrent.atomic.AtomicBoolean(false)
                    val rawBuffer = StringBuilder()
                    val bufferLock = Any()
                    var isStreamComplete = false
                    val minCharsThreshold = 220

                    pacingJob = launch {
                        var currentEmittedLength = 0
                        while (isActive) {
                            val readyToStream = synchronized(bufferLock) {
                                rawBuffer.length >= minCharsThreshold || isStreamComplete
                            }
                            if (!readyToStream) {
                                delay(35L)
                                continue
                            }

                            val nextWord: String? = synchronized(bufferLock) {
                                if (currentEmittedLength >= rawBuffer.length) {
                                    null
                                } else {
                                    val rem = rawBuffer.substring(currentEmittedLength)
                                    var idx = 0
                                    while (idx < rem.length && !rem[idx].isWhitespace()) {
                                        idx++
                                    }
                                    while (idx < rem.length && rem[idx].isWhitespace()) {
                                        idx++
                                    }
                                    if (idx == 0) idx = 1.coerceAtMost(rem.length)
                                    val wordChunk = rem.substring(0, idx)
                                    currentEmittedLength += wordChunk.length
                                    wordChunk
                                }
                            }

                            if (nextWord != null) {
                                if (!streamHadAudioTool.get()) {
                                    _streamedText.update { it + nextWord }
                                }
                                val isDone = synchronized(bufferLock) { isStreamComplete }
                                delay(if (isDone) 12L else 28L)
                            } else {
                                val finished = synchronized(bufferLock) { isStreamComplete && currentEmittedLength >= rawBuffer.length }
                                if (finished) break
                                delay(30L)
                            }
                        }
                    }

                    provider.streamCompletion(
                        messages = messagesForApi,
                        model = model.id,
                        tools = repository.getDeclaredTools(),
                        apiKey = retryApiKey,
                        customBaseUrl = baseUrl,
                        onToken = { token ->
                            if (streamHadAudioTool.get()) return@streamCompletion
                            if (token == "\u200B") {
                                synchronized(bufferLock) { rawBuffer.setLength(0) }
                                _streamedText.value = ""
                            } else {
                                synchronized(bufferLock) {
                                    rawBuffer.append(token)
                                }
                            }
                        },
                        onToolCall = { toolCall ->
                            streamHadToolCall = true
                            if (toolCall.name == "edge_tts") {
                                streamHadAudioTool.set(true)
                                _streamedText.value = ""
                            }
                            maybeAutoApproveTool(toolCall)
                        },
                        onComplete = { fullResponse ->
                            if (streamHadAudioTool.get()) {
                                _streamedText.value = ""
                                _deferredResponse = ""
                                synchronized(bufferLock) { isStreamComplete = true }
                                return@streamCompletion
                            }
                            synchronized(bufferLock) {
                                if (fullResponse.length > rawBuffer.length && fullResponse.startsWith(rawBuffer.toString())) {
                                    rawBuffer.setLength(0)
                                    rawBuffer.append(fullResponse)
                                }
                                isStreamComplete = true
                            }
                            _deferredResponse = fullResponse
                        },
                        onError = { error ->
                            pacingJob?.cancel()
                            val msg = error.message.orEmpty()
                            val isRotatableError = ApiKeyRotator.isRotatableError(error, null, msg)
                            if (isRotatableError) {
                                throw (error as? RateLimitException) ?: RateLimitException(model.provider, 429, error.message ?: "Rate limited")
                            }
                            _isStreaming.value = false
                            val errMsg = "Error: ${error.message}"
                            _streamedText.value = errMsg
                            viewModelScope.launch { appendAssistantMessage(errMsg, sessionId) }
                            _streamedText.value = ""
                        },
                        onUsage = { usage ->
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.recordTokenUsage(sessionId, model.id, model.provider, usage)
                            }
                        }
                    )
                    pacingJob?.join()
                    if (streamHadAudioTool.get()) {
                        _streamedText.value = ""
                        _deferredResponse = ""
                        break
                    }
                    // Save to DB FIRST, then clear streaming state to avoid UI gap
                    val textToSave = if (_streamedText.value.isNotBlank()) _streamedText.value else _deferredResponse
                    if (textToSave.isNotBlank()) {
                        // Pin to the originating session — activeSessionId may have changed on switch.
                        appendAssistantMessage(textToSave, sessionId)
                        _streamingMessageId.value = ""
                    }
                    if (!streamHadToolCall) {
                        _isStreaming.value = false
                        _streamingMessageId.value = ""
                    }
                    _streamedText.value = ""
                    _deferredResponse = ""
                    break  // Success — exit retry loop
                } catch (e: RateLimitException) {
                    pacingJob?.cancel()
                    val actualSlot = if (retrySlot > 0) retrySlot else ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, retryApiKey)
                    ApiKeyRotator.markKeyExhausted(storageId, actualSlot, retryApiKey)
                    val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, actualSlot)
                    if (nextKey != null && attempts < maxAttempts) {
                        val sameKey = nextKey.second == actualSlot
                        retryApiKey = nextKey.first
                        retrySlot = nextKey.second
                        if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                            val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                            if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                        }
                        _streamedText.value = ""  // Clear partial output from failed attempt
                        _deferredResponse = ""
                        ai.deepcode.android.util.AppLogger.i("ChatScreen", "Rotating $storageId key: slot $actualSlot -> slot ${nextKey.second} (attempt $attempts/$maxAttempts)")
                        if (sameKey && totalConfiguredKeys <= 1) {
                            kotlinx.coroutines.delay(1000)
                        }
                        continue  // Instantly retry with rotated key
                    }
                    // All keys exhausted after max rotation attempts
                    _isStreaming.value = false
                    _streamedText.value = "Error: ${model.provider} rate limit reached across all keys. Please try again shortly."
                    appendAssistantMessage(_streamedText.value, sessionId)
                    _streamedText.value = ""
                    _deferredResponse = ""
                    break
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    pacingJob?.cancel()
                    // Stop / session switch — don't save a phantom error bubble.
                    _isStreaming.value = false
                    throw e
                } catch (e: Exception) {
                    pacingJob?.cancel()
                    if (ApiKeyRotator.isRotatableError(e, null, e.message)) {
                        val actualSlot = if (retrySlot > 0) retrySlot else ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, retryApiKey)
                        ApiKeyRotator.markKeyExhausted(storageId, actualSlot, retryApiKey)
                        val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, actualSlot)
                        if (nextKey != null && attempts < maxAttempts) {
                            val sameKey = nextKey.second == actualSlot
                            retryApiKey = nextKey.first
                            retrySlot = nextKey.second
                            if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                                val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                                if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                            }
                            _streamedText.value = ""
                            _deferredResponse = ""
                            ai.deepcode.android.util.AppLogger.i("ChatScreen", "Rotating $storageId key on rotatable exception: slot $actualSlot -> slot ${nextKey.second}")
                            if (sameKey && totalConfiguredKeys <= 1) {
                                kotlinx.coroutines.delay(1000)
                            }
                            continue
                        }
                    }
                    _isStreaming.value = false
                    _streamedText.value = "Error: ${e.message}"
                    appendAssistantMessage(_streamedText.value, sessionId)
                    _streamedText.value = ""
                    _deferredResponse = ""
                    break
                }
            }
        }
    }

    fun cancelActiveChat() {
        // Cancel the in-flight network loop first so onToken stops appending after Stop.
        sendJob?.cancel()
        sendJob = null
        _isStreaming.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val text = _streamedText.value
            if (text.isNotEmpty()) {
                appendAssistantMessage(text + "\n\n[INTERRUPTED]")
            }
            _streamedText.value = ""
            _streamingMessageId.value = ""
            _deferredResponse = ""
        }
    }

    fun approveToolCall() {
        val toolCall = _pendingToolCall.value ?: return
        _pendingToolCall.value = null

        if (toolCall.name == "edge_tts") {
            _mediaProcessingType.value = "audio"
            _mediaProcessingPrompt.value = "Thinking..."
            val priorContent = if (_deferredResponse.isNotBlank()) _deferredResponse else _streamedText.value
            _streamedText.value = ""
            _isStreaming.value = true

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = try {
                        repository.executeTool(toolCall.name, toolCall.arguments, repository.getDefaultProjectPath())
                    } catch (e: Exception) {
                        "Error executing audio generation: ${e.message}"
                    }
                    val mediaMatch = RE_MEDIA_TAG.find(result)
                    val audioTag = mediaMatch?.value ?: if (result.contains("[audio:")) result else null
                    val cleanPrior = priorContent.trim()
                    val textToSave = if (audioTag != null) {
                        if (cleanPrior.isNotEmpty()) "$cleanPrior\n\n$audioTag" else audioTag
                    } else {
                        if (cleanPrior.isNotEmpty()) "$cleanPrior\n\nFailed to generate audio: $result" else "Failed to generate audio: $result"
                    }
                    appendAssistantMessage(textToSave)
                } catch (e: Exception) {
                    ai.deepcode.android.util.AppLogger.e("ChatViewModel", "edge_tts approve crashed: ${e.message}", e)
                    appendAssistantMessage("Error: ${e.message}")
                } finally {
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                }
            }
            return
        }

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
            val argsString = toolCall.arguments.trim()
            val encodedArgs = com.google.gson.Gson().toJson(argsString)
            // Insert assistant message with tool_calls BEFORE tool result
            val assistantToolCallMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = activeSessionId,
                role = "assistant",
                content = "",
                timestamp = System.currentTimeMillis(),
                isToolCall = true,
                toolCallsJson = """[{"id":"${toolCall.id}","name":"${toolCall.name}","arguments":$encodedArgs}]"""
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
                toolCallsJson = """{"id":"${toolCall.id}","name":"${toolCall.name}"}"""
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
        val provider = AIProviderFactory.providers.find { it.name.equals(model.provider, ignoreCase = true) }
            ?: OPENAI_PROVIDERS.find { it.name.equals(model.provider, ignoreCase = true) }?.let { GenericOpenAIProvider(it) }
        if (provider == null) {
            _isStreaming.value = false
            appendAssistantMessage("Provider ${model.provider} not available.")
            return
        }
        val storageId = providerStorageId(model.provider)
        var currentKeySlot = 0
        var apiKey = ""
        val aliasKeys = mutableListOf(storageId)
        if (storageId.contains("-")) aliasKeys.add(storageId.replace("-", ""))
        if (storageId == "cerebras") aliasKeys.add("cerebrus")
        if (storageId == "cerebrus") aliasKeys.add("cerebras")
        if (storageId == "gemini") aliasKeys.add("google gemini")
        if (storageId == "gmi") aliasKeys.add("gmi-cloud")
        if (storageId == "gmi-cloud") aliasKeys.add("gmi")
        if (storageId == "aimlapi") aliasKeys.add("aiml-api")
        if (storageId == "nebius") aliasKeys.add("nebius-ai")
        if (storageId == "friendliai") aliasKeys.add("friendli-ai")
        if (storageId == "together") aliasKeys.add("together-ai")
        if (storageId == "fireworks") aliasKeys.add("fireworks-ai")
        if (storageId == "nvidia") aliasKeys.add("nvidia-nim")

        for (k in aliasKeys) {
            val rotatorResult = ApiKeyRotator.getNextAvailableKey(repository.securePrefs, k)
            if (rotatorResult != null && rotatorResult.first.isNotEmpty()) {
                apiKey = rotatorResult.first
                currentKeySlot = rotatorResult.second
                break
            }
            val raw = repository.securePrefs.getApiKey(k)
            if (raw.isNotEmpty()) {
                apiKey = raw
                currentKeySlot = 1
                break
            }
            val oauth = repository.securePrefs.getSetting("oauth_token_$k", "")
            if (oauth.isNotEmpty()) {
                apiKey = oauth
                break
            }
        }
        if (apiKey.isNotEmpty() && model.provider == "Antigravity") {
            val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
            if (projectId.isNotEmpty()) apiKey += "||$projectId"
        }
        if (apiKey.isEmpty() && (model.provider == "Zen AI" || model.provider == "Zen" || model.provider == "Zen (Free)")) {
            apiKey = repository.securePrefs.getApiKey("zen").ifEmpty { "zen-free" }
        }
        if (apiKey.isEmpty() && (model.provider == "Ollama" || model.provider == "OllamaCloud")) {
            apiKey = "ollama"
        }
        if (currentKeySlot == 0 && apiKey.isNotEmpty()) {
            currentKeySlot = ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, apiKey)
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
            var pacingJob: kotlinx.coroutines.Job? = null
            try {
                _streamedText.value = ""
                var nextStreamHadToolCall = false
                // Cross-thread flag (see streamHadAudioTool above).
                val nextStreamHadAudioTool = java.util.concurrent.atomic.AtomicBoolean(false)
                val rawBuffer = StringBuilder()
                val bufferLock = Any()
                var isStreamComplete = false
                val minCharsThreshold = 220

                pacingJob = viewModelScope.launch {
                    var currentEmittedLength = 0
                    while (isActive) {
                        val readyToStream = synchronized(bufferLock) {
                            rawBuffer.length >= minCharsThreshold || isStreamComplete
                        }
                        if (!readyToStream) {
                            delay(35L)
                            continue
                        }

                        val nextWord: String? = synchronized(bufferLock) {
                            if (currentEmittedLength >= rawBuffer.length) {
                                null
                            } else {
                                val rem = rawBuffer.substring(currentEmittedLength)
                                var idx = 0
                                while (idx < rem.length && !rem[idx].isWhitespace()) {
                                    idx++
                                }
                                while (idx < rem.length && rem[idx].isWhitespace()) {
                                    idx++
                                }
                                if (idx == 0) idx = 1.coerceAtMost(rem.length)
                                val wordChunk = rem.substring(0, idx)
                                currentEmittedLength += wordChunk.length
                                wordChunk
                            }
                        }

                        if (nextWord != null) {
                            if (!nextStreamHadAudioTool.get()) {
                                _streamedText.update { it + nextWord }
                            }
                            val isDone = synchronized(bufferLock) { isStreamComplete }
                            delay(if (isDone) 12L else 28L)
                        } else {
                            val finished = synchronized(bufferLock) { isStreamComplete && currentEmittedLength >= rawBuffer.length }
                            if (finished) break
                            delay(30L)
                        }
                    }
                }

                provider.streamCompletion(
                    messages = messagesForApi,
                    model = model.id,
                    tools = if (consecutiveWebSearches >= 2 || toolCallDepth >= maxToolCallDepth) emptyList() else repository.getDeclaredTools(),
                    apiKey = retryApiKey,
                    customBaseUrl = baseUrl,
                    onToken = { token ->
                        if (nextStreamHadAudioTool.get()) return@streamCompletion
                        if (token == "\u200B") {
                            synchronized(bufferLock) { rawBuffer.setLength(0) }
                            _streamedText.value = ""
                        } else {
                            synchronized(bufferLock) {
                                rawBuffer.append(token)
                            }
                        }
                    },
                    onToolCall = { tc ->
                        nextStreamHadToolCall = true
                        if (tc.name == "edge_tts") {
                            nextStreamHadAudioTool.set(true)
                            _streamedText.value = ""
                        }
                        maybeAutoApproveTool(tc)
                    },
                    onComplete = { fullResponse ->
                        if (nextStreamHadAudioTool.get()) {
                            _streamedText.value = ""
                            _deferredResponse = ""
                            synchronized(bufferLock) { isStreamComplete = true }
                            return@streamCompletion
                        }
                        synchronized(bufferLock) {
                            if (fullResponse.length > rawBuffer.length && fullResponse.startsWith(rawBuffer.toString())) {
                                rawBuffer.setLength(0)
                                rawBuffer.append(fullResponse)
                            }
                            isStreamComplete = true
                        }
                        _deferredResponse = fullResponse
                    },
                    onError = { error ->
                        pacingJob?.cancel()
                        val msg = error.message.orEmpty()
                        val isRotatableError = ApiKeyRotator.isRotatableError(error, null, msg)
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
                pacingJob?.join()
                if (nextStreamHadAudioTool.get()) {
                    _streamedText.value = ""
                    _deferredResponse = ""
                    break
                }
                val textToSave = if (_streamedText.value.isNotBlank()) _streamedText.value else _deferredResponse
                if (!nextStreamHadToolCall && textToSave.isNotBlank()) {
                    val parsedCalls = parseToolCallsFromText(textToSave)
                    if (parsedCalls.isNotEmpty()) {
                        nextStreamHadToolCall = true
                        _streamedText.value = ""
                        _deferredResponse = ""
                        for (tc in parsedCalls) {
                            maybeAutoApproveTool(tc)
                        }
                        break
                    }
                }
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
                pacingJob?.cancel()
                val actualSlot = if (retrySlot > 0) retrySlot else ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, retryApiKey)
                ApiKeyRotator.markKeyExhausted(storageId, actualSlot, retryApiKey)
                val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, actualSlot)
                if (nextKey != null && attempts < maxAttempts) {
                    val sameKey = nextKey.second == actualSlot
                    retryApiKey = nextKey.first
                    retrySlot = nextKey.second
                    if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                        val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                        if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                    }
                    _streamedText.value = ""
                    _deferredResponse = ""
                    ai.deepcode.android.util.AppLogger.i("ChatScreen", "Rotating $storageId key: slot $actualSlot -> slot ${nextKey.second} (tool continuation attempt $attempts/$maxAttempts)")
                    if (sameKey && totalConfiguredKeys <= 1) {
                        kotlinx.coroutines.delay(1000)
                    }
                    continue
                }
                _isStreaming.value = false
                _streamedText.value = "Error: ${model.provider} rate limit reached across all keys. Please try again shortly."
                appendAssistantMessage(_streamedText.value)
                _streamedText.value = ""
                _deferredResponse = ""
                break
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                pacingJob?.cancel()
                _isStreaming.value = false
                throw e
            } catch (e: Exception) {
                pacingJob?.cancel()
                if (ApiKeyRotator.isRotatableError(e, null, e.message)) {
                    val actualSlot = if (retrySlot > 0) retrySlot else ApiKeyRotator.findSlotForKey(repository.securePrefs, storageId, retryApiKey)
                    ApiKeyRotator.markKeyExhausted(storageId, actualSlot, retryApiKey)
                    val nextKey = ApiKeyRotator.getAvailableKeyAfter(repository.securePrefs, storageId, actualSlot)
                    if (nextKey != null && attempts < maxAttempts) {
                        val sameKey = nextKey.second == actualSlot
                        retryApiKey = nextKey.first
                        retrySlot = nextKey.second
                        if (retryApiKey.isNotEmpty() && model.provider == "Antigravity") {
                            val projectId = repository.securePrefs.getSetting("oauth_project_$storageId", "")
                            if (projectId.isNotEmpty()) retryApiKey += "||$projectId"
                        }
                        _streamedText.value = ""
                        _deferredResponse = ""
                        ai.deepcode.android.util.AppLogger.i("ChatScreen", "Rotating $storageId key on rotatable exception in tool continuation: slot $actualSlot -> slot ${nextKey.second}")
                        if (sameKey && totalConfiguredKeys <= 1) {
                            kotlinx.coroutines.delay(1000)
                        }
                        continue
                    }
                }
                _isStreaming.value = false
                _streamedText.value = "Error: ${e.message}"
                appendAssistantMessage(_streamedText.value)
                _streamedText.value = ""
                _deferredResponse = ""
                break
            }
        }
    }

    fun denyToolCall() {
        _pendingToolCall.value = null
        _isStreaming.value = false
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

    private fun isAudioCreationRequest(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.length > 250) return false
        val isMeta = isMetaReferenceText(clean)
        val hasAudioWord = clean.contains("audio") || clean.contains("speak") ||
                clean.contains("read") || clean.contains("voice") || clean.contains("tts")
        if (isMeta && hasAudioWord) return true

        val audioCommandRegex = Regex("""\b(create|generate|make|convert|produce|read|speak)\s+(an?\s+)?(audio|voice|speech|tts|sound)\b""")
        return audioCommandRegex.containsMatchIn(clean)
    }

    private fun isImageCreationRequest(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.length > 350) return false
        val imageCommandRegex = Regex(
            """\b(create|generate|make|draw|paint|render|produce)\s+(an?\s+)?(image|picture|photo|illustration|drawing|painting|art)\b|^(?:picture|photo|image|drawing)\s+of\b|\b(draw|paint)\s+(?:me\s+)?(?:an?\s+)?([a-z0-9\s]+)""",
            RegexOption.IGNORE_CASE
        )
        return imageCommandRegex.containsMatchIn(clean)
    }

    private fun extractImagePrompt(input: String): String {
        val clean = input.trim()
        val regex = Regex(
            """^(?:please\s+|can\s+you\s+)?(?:(?:create|generate|make|draw|paint|render|produce)\s+(?:an?\s+)?(?:image|picture|photo|illustration|drawing|painting|art)|picture|photo|image|drawing|draw|paint)\s*(?:of|for|about|showing|with|me)?\s*[:,-]?\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(clean)
        return match?.groupValues?.get(1)?.trim() ?: clean
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

        val isGitHubConnected = try {
            val t = prefs.getSetting("github_token", "").trim()
            if (t.isNotEmpty()) true
            else {
                val entity = ai.deepcode.android.data.local.AppDatabase.getDatabase(repository.appContext).integrationDao().getIntegrationByAppIdSync("github")
                entity != null && !entity.accessToken.isNullOrBlank()
            }
        } catch (_: Exception) { false }

        val githubSection = if (isGitHubConnected) {
            """
- GitHub Integration: ACTIVE & CONNECTED. The user has an active, authenticated GitHub connection.
When the user asks about GitHub repositories, account profile, issues, pull requests, commits, branches, releases, files, or gists, ALWAYS call the corresponding GitHub tool immediately. NEVER say you cannot access GitHub.
TOOL CALLING FORMAT:
If native tool calling is supported, use it. If not, output tool calls using this exact format:
<tool_call>
{"name": "tool_name", "arguments": {"param1": "val1"}}
</tool_call>
Example for listing repositories:
<tool_call>
{"name": "github_list_repos", "arguments": {}}
</tool_call>
Example for user profile:
<tool_call>
{"name": "github_get_user", "arguments": {}}
</tool_call>
Available tools: github_get_user, github_list_repos, github_get_repo, github_create_repo, github_delete_repo, github_fork_repo, github_list_repo_contents, github_get_file_content, github_create_or_update_file, github_delete_file, github_list_branches, github_create_branch, github_list_commits, github_get_commit, github_list_pull_requests, github_get_pull_request, github_get_pr_diff, github_create_pull_request, github_update_pull_request, github_merge_pull_request, github_list_issues, github_get_issue, github_create_issue, github_update_issue, github_list_issue_comments, github_create_issue_comment, github_list_releases, github_get_latest_release, github_create_release, github_list_workflows, github_trigger_workflow, github_list_workflow_runs, github_check_workflow_status, github_download_artifact, github_list_gists, github_create_gist, github_search_code, github_search_repositories, github_search_issues."""
        } else {
            "- GitHub Integration: Not connected. If the user asks for GitHub data, instruct them to connect GitHub in the Connections screen."
        }

        return """
$persona

CRITICAL INSTRUCTIONS:
- Be fast, helpful, and concise. Respond immediately and directly to the user without preamble.
- NEVER output thinking, reasoning, chain-of-thought, internal monologue, audit rules, <think> tags, or thinking boxes. Output ONLY the clean final response.
- When asked to create or provide ANY PDF document (poem, study notes, report, resume, etc.), immediately call `create_pdf` with the full content and reply with "Here is your PDF document: [file:/path/to/doc.pdf]".
- When tools return file/image tags (e.g. `[image:...]` or `[file:...]`), stop calling tools and provide a brief confirmation.
- Audio/Speech (edge_tts): When the user asks for audio, resolve the full text and pass it to `edge_tts`.
- Image Generation (generate_image): When the user asks for an image, picture, photo, illustration, drawing, or artwork, ALWAYS call the `generate_image` tool with a detailed prompt describing what to render. NEVER fabricate, hallucinate, or make up local file paths or [image:...] tags yourself.
- Documents (generate_chatgpt_document): When asked to generate a document or specification with ChatGPT, call `generate_chatgpt_document`.
- Video Generation (generate_video): Call `generate_video` with a prompt describing the scene.
$githubSection
""".trim()
    }

    private suspend fun appendAssistantMessage(content: String, sessionId: String? = null) = withContext(Dispatchers.IO) {
        val targetSessionId = sessionId ?: activeSessionId
        if (targetSessionId.isEmpty()) return@withContext
        // Persist only the final answer — thinking is discarded entirely, never stored.
        var finalContent = stripThinkingProcess(content, isStreaming = false)
        try {
            val history = repository.getMessagesListForSession(targetSessionId)
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
            sessionId = targetSessionId,
            role = "assistant",
            content = finalContent,
            timestamp = System.currentTimeMillis()
        )
        _messages.update { current ->
            if (current.none { it.id == msg.id }) current + msg else current
        }
        repository.insertMessage(msg)
    }

    private fun parseToolCallsFromText(rawText: String): List<ToolCall> {
        val text = rawText.trim()
        val lower = text.lowercase()
        if (!lower.contains("tool_call") && !lower.contains("tool_calls") && !lower.contains("invoke") && !lower.contains("github_")) return emptyList()
        val result = mutableListOf<ToolCall>()

        // Format 1: XML invoke — <invoke name="tool_name"><parameter name="param">val</parameter></invoke>
        val invokeRegex = Regex("""<invoke\s+name="([^"]+)"[^>]*>(.*?)</invoke>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in invokeRegex.findAll(text)) {
            val name = m.groupValues[1].trim()
            val paramsBlock = m.groupValues[2]
            val args = com.google.gson.JsonObject()
            val paramRegex = Regex("""<parameter\s+name="([^"]+)"[^>]*>(.*?)</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in paramRegex.findAll(paramsBlock)) {
                args.addProperty(pm.groupValues[1].trim(), pm.groupValues[2].trim())
            }
            result.add(ToolCall("tc_${UUID.randomUUID().toString().take(8)}", name, args.toString()))
        }
        if (result.isNotEmpty()) return result

        // Format 2: JSON inside <tool_call> — <tool_call>{"name": "...", "arguments": {...}}</tool_call>
        val jsonToolCallRegex = Regex("""<tool_calls?>\s*(\{[^<]+\})\s*</tool_calls?>""", RegexOption.IGNORE_CASE)
        for (m in jsonToolCallRegex.findAll(text)) {
            val jsonContent = m.groupValues[1].trim()
            try {
                val obj = com.google.gson.JsonParser.parseString(jsonContent).asJsonObject
                val name = obj.get("name")?.asString ?: ""
                val argsObj = obj.get("arguments")
                val argsStr = when {
                    argsObj == null -> "{}"
                    argsObj.isJsonPrimitive -> argsObj.asString
                    else -> com.google.gson.Gson().toJson(argsObj)
                }
                if (name.isNotEmpty()) {
                    result.add(ToolCall("tc_${UUID.randomUUID().toString().take(8)}", name, argsStr))
                }
            } catch (_: Exception) {}
        }
        if (result.isNotEmpty()) return result

        // Format 3: Named tool call with JSON — <tool_call> tool_name {"arg": "val"} </tool_call>
        val namedJsonRegex = Regex("""<tool_calls?>\s*([a-zA-Z0-9_-]+)\s*(\{[^<]*\})\s*</tool_calls?>""", RegexOption.IGNORE_CASE)
        for (m in namedJsonRegex.findAll(text)) {
            val name = m.groupValues[1].trim()
            val jsonArgs = m.groupValues[2].trim()
            result.add(ToolCall("tc_${UUID.randomUUID().toString().take(8)}", name, jsonArgs))
        }
        if (result.isNotEmpty()) return result

        // Format 4: Pipe-delimited — <tool_call> name [key1:val1 | key2:val2] </tool_call>
        val pipeRegex = Regex("""<tool_calls?>\s*([a-zA-Z0-9_-]+)\s*\[([^\]]*)\]\s*</tool_calls?>""", RegexOption.IGNORE_CASE)
        for (m in pipeRegex.findAll(text)) {
            val name = m.groupValues[1].trim()
            val argsText = m.groupValues[2].trim()
            val args = if (argsText.startsWith("{") && argsText.endsWith("}")) {
                argsText
            } else {
                val obj = com.google.gson.JsonObject()
                for (p in argsText.split("|")) {
                    val ci = p.indexOf(':')
                    if (ci > 0) obj.addProperty(p.substring(0, ci).trim(), p.substring(ci + 1).trim())
                }
                obj.toString()
            }
            result.add(ToolCall("tc_${UUID.randomUUID().toString().take(8)}", name, args))
        }

        return result
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

        if (toolCall.name == "edge_tts") {
            _mediaProcessingType.value = "audio"
            _mediaProcessingPrompt.value = "Thinking..."
            val priorContent = if (_deferredResponse.isNotBlank()) _deferredResponse else _streamedText.value
            _streamedText.value = ""
            _isStreaming.value = true

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = try {
                        repository.executeTool(toolCall.name, toolCall.arguments, repository.getDefaultProjectPath())
                    } catch (e: Exception) {
                        "Error executing audio generation: ${e.message}"
                    }
                    val mediaMatch = RE_MEDIA_TAG.find(result)
                    val audioTag = mediaMatch?.value ?: if (result.contains("[audio:")) result else null
                    val cleanPrior = priorContent.trim()
                    val textToSave = if (audioTag != null) {
                        if (cleanPrior.isNotEmpty()) "$cleanPrior\n\n$audioTag" else audioTag
                    } else {
                        if (cleanPrior.isNotEmpty()) "$cleanPrior\n\nFailed to generate audio: $result" else "Failed to generate audio: $result"
                    }
                    appendAssistantMessage(textToSave)
                } catch (e: Exception) {
                    ai.deepcode.android.util.AppLogger.e("ChatViewModel", "edge_tts auto-approve crashed: ${e.message}", e)
                    appendAssistantMessage("Error: ${e.message}")
                } finally {
                    _isStreaming.value = false
                    _streamingMessageId.value = ""
                    _mediaProcessingType.value = null
                    _mediaProcessingPrompt.value = ""
                }
            }
            return
        }

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
                val argsString = toolCall.arguments.trim()
                val encodedArgs = com.google.gson.Gson().toJson(argsString)
                val assistantToolCallMsg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = activeSessionId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isToolCall = true,
                    toolCallsJson = """[{"id":"${toolCall.id}","name":"${toolCall.name}","arguments":$encodedArgs}]"""
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
            "create_automation", "list_automations", "delete_automation",
            "schedule_chatgpt_task", "cron_add", "cron_list", "cron_remove",
            "memory_read", "edge_tts", "generate_image", "generate_video"
        )
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
    }
}
