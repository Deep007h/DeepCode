package ai.deepcode.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.service.TerminalRunner
import ai.deepcode.android.ui.theme.*
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.RootSystem
import com.jarves.mh.runtime.NativeSpawnProcess
import com.jarves.mh.runtime.RuntimeInstaller
import com.jarves.mh.ui.TerminalOutputLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.BufferedWriter

enum class TerminalMode(val label: String) {
    LINUX_PROOT("Ubuntu Linux (PRoot)"),
    ROOT_SU("Root Shell (su)"),
    STANDARD_SH("Android Shell (sh)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    initialWorkingDir: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val installer = remember { RuntimeInstaller(context) }
    val isLinuxInstalled by remember { derivedStateOf { installer.isInstalled() } }
    val hasRoot = remember { RootSystem.isRootAvailable.value || RootSystem.isRootGranted.value }

    var selectedMode by rememberSaveable {
        mutableStateOf(
            if (installer.isInstalled()) TerminalMode.LINUX_PROOT
            else if (hasRoot) TerminalMode.ROOT_SU
            else TerminalMode.STANDARD_SH
        )
    }

    var lines by remember {
        mutableStateOf(
            listOf(
                TerminalOutputLine(
                    command = "welcome",
                    output = "DeepCode Terminal Studio v1.5\nSubsystem: ${if (installer.isInstalled()) "Ubuntu 20.04 LTS (PRoot Ready)" else "Standard Environment"}\nRoot status: ${if (hasRoot) "Available" else "Non-root"}\nType 'help' or run commands directly.\n"
                )
            )
        )
    }

    var currentLiveOutput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf(TextFieldValue()) }
    var commandHistory by remember { mutableStateOf(emptyList<String>()) }
    var historyIndex by remember { mutableStateOf(-1) }
    var ctrlActive by rememberSaveable { mutableStateOf(false) }
    var altActive by rememberSaveable { mutableStateOf(false) }

    // Active process handles
    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var activeWriter by remember { mutableStateOf<BufferedWriter?>(null) }

    val workingDir = remember(initialWorkingDir) {
        if (initialWorkingDir.isNotBlank()) initialWorkingDir
        else File(context.filesDir, "workspaces").apply { mkdirs() }.absolutePath
    }

    // Auto scroll to bottom
    LaunchedEffect(lines, currentLiveOutput) {
        snapshotFlow { scrollState.maxValue }.collectLatest { maxValue ->
            scrollState.scrollTo(maxValue)
        }
    }

    val runCommand: (String) -> Unit = { cmd ->
        val trimmed = cmd.trim()
        if (trimmed.isNotBlank() && !isRunning) {
            commandHistory = (commandHistory + trimmed).takeLast(50)
            historyIndex = -1
            isRunning = true
            currentLiveOutput = ""

            if (trimmed.lowercase() == "clear") {
                lines = emptyList()
                isRunning = false
            } else {
                scope.launch(Dispatchers.IO) {
                    try {
                        when (selectedMode) {
                            TerminalMode.LINUX_PROOT -> {
                                if (installer.isInstalled()) {
                                    val rt = installer.installedRuntime()
                                    rt.proot.setExecutable(true, false)
                                    val prootPath = rt.proot.absolutePath
                                    val rootfsPath = rt.rootfs.absolutePath
                                    val prootTemp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
                                    val env = mutableMapOf(
                                        "TERM" to "xterm-256color",
                                        "HOME" to "/root",
                                        "USER" to "root",
                                        "LANG" to "C.UTF-8",
                                        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                                        "LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir,
                                        "PROOT_NO_SECCOMP" to "1",
                                        "PROOT_TMP_DIR" to prootTemp.absolutePath,
                                        "PROOT_LOADER" to File(context.applicationInfo.nativeLibraryDir, "libprootloader.so").absolutePath,
                                        "GLIBC_TUNABLES" to "glibc.pthread.rseq=0"
                                    )
                                    val hostBinds = listOf("/system", "/apex", "/vendor", "/product").filter { File(it).exists() }
                                    val argv = buildList {
                                        add(prootPath)
                                        add("--kill-on-exit")
                                        add("-0")
                                        add("-r")
                                        add(rootfsPath)
                                        add("-b")
                                        add("/dev")
                                        add("-b")
                                        add("/proc")
                                        add("-b")
                                        add("/sys")
                                        for (h in hostBinds) {
                                            add("-b")
                                            add(h)
                                        }
                                        add("-b")
                                        add("${context.filesDir.absolutePath}:/data/data/${context.packageName}")
                                        add("-b")
                                        add("${File(workingDir).absolutePath}:/workspace")
                                        add("-w")
                                        add("/workspace")
                                        add("/usr/bin/bash")
                                        add("-c")
                                        add(trimmed)
                                    }
                                    val pb = ProcessBuilder(argv).redirectErrorStream(true)
                                    pb.environment().putAll(env)
                                    val proc = pb.start()
                                    activeProcess = proc
                                    activeWriter = proc.outputStream.bufferedWriter()
                                    val reader = InputStreamReader(proc.inputStream)
                                    val buffer = CharArray(1024)
                                    var count: Int
                                    val fullSb = StringBuilder()
                                    while (reader.read(buffer).also { count = it } != -1) {
                                        val chunk = String(buffer, 0, count)
                                        fullSb.append(chunk)
                                        withContext(Dispatchers.Main) {
                                            currentLiveOutput = fullSb.toString()
                                        }
                                    }
                                    val code = proc.waitFor()
                                    runCatching { activeWriter?.close() }
                                    withContext(Dispatchers.Main) {
                                        lines = lines + TerminalOutputLine(command = trimmed, output = fullSb.toString(), exitCode = code)
                                        currentLiveOutput = ""
                                        isRunning = false
                                        activeProcess = null
                                        activeWriter = null
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        lines = lines + TerminalOutputLine(command = trimmed, output = "PRoot Linux Subsystem is not yet installed. Go to Settings > Linux Subsystem to set it up.", exitCode = 1)
                                        isRunning = false
                                    }
                                }
                            }
                            TerminalMode.ROOT_SU -> {
                                val suCmd = RootSystem.getSuBinaryPath()
                                val pb = ProcessBuilder(suCmd, "-c", trimmed).directory(File(workingDir)).redirectErrorStream(true)
                                val proc = pb.start()
                                activeProcess = proc
                                activeWriter = proc.outputStream.bufferedWriter()
                                val reader = InputStreamReader(proc.inputStream)
                                val buffer = CharArray(1024)
                                var count: Int
                                val fullSb = StringBuilder()
                                while (reader.read(buffer).also { count = it } != -1) {
                                    val chunk = String(buffer, 0, count)
                                    fullSb.append(chunk)
                                    withContext(Dispatchers.Main) {
                                        currentLiveOutput = fullSb.toString()
                                    }
                                }
                                val code = proc.waitFor()
                                runCatching { activeWriter?.close() }
                                withContext(Dispatchers.Main) {
                                    lines = lines + TerminalOutputLine(command = trimmed, output = fullSb.toString(), exitCode = code)
                                    currentLiveOutput = ""
                                    isRunning = false
                                    activeProcess = null
                                    activeWriter = null
                                }
                            }
                            TerminalMode.STANDARD_SH -> {
                                val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed).directory(File(workingDir)).redirectErrorStream(true)
                                val proc = pb.start()
                                activeProcess = proc
                                activeWriter = proc.outputStream.bufferedWriter()
                                val reader = InputStreamReader(proc.inputStream)
                                val buffer = CharArray(1024)
                                var count: Int
                                val fullSb = StringBuilder()
                                while (reader.read(buffer).also { count = it } != -1) {
                                    val chunk = String(buffer, 0, count)
                                    fullSb.append(chunk)
                                    withContext(Dispatchers.Main) {
                                        currentLiveOutput = fullSb.toString()
                                    }
                                }
                                val code = proc.waitFor()
                                runCatching { activeWriter?.close() }
                                withContext(Dispatchers.Main) {
                                    lines = lines + TerminalOutputLine(command = trimmed, output = fullSb.toString(), exitCode = code)
                                    currentLiveOutput = ""
                                    isRunning = false
                                    activeProcess = null
                                    activeWriter = null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runCatching { activeWriter?.close() }
                        withContext(Dispatchers.Main) {
                            lines = lines + TerminalOutputLine(command = trimmed, output = "Error: ${e.message}", exitCode = 1)
                            currentLiveOutput = ""
                            isRunning = false
                            activeProcess = null
                            activeWriter = null
                        }
                    }
                }
            }
        }
    }

    val sendInput: (String) -> Unit = { input ->
        scope.launch(Dispatchers.IO) {
            try {
                activeWriter?.write(input + "\n")
                activeWriter?.flush()
            } catch (_: Exception) {}
        }
    }

    val interruptProcess = {
        try {
            activeProcess?.destroy()
        } catch (_: Exception) {}
        isRunning = false
        currentLiveOutput += "\n^C\n"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal Console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppWhite)
                        Text(selectedMode.label, style = MaterialTheme.typography.bodySmall, color = AppMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { lines = emptyList() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = AppMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        },
        containerColor = Color(0xFF0F141C)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Mode Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AppPrimary.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) AppPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedMode = mode }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = when (mode) {
                                TerminalMode.LINUX_PROOT -> "Ubuntu PRoot"
                                TerminalMode.ROOT_SU -> "Root (su)"
                                TerminalMode.STANDARD_SH -> "Shell (sh)"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppPrimary else AppMuted
                        )
                    }
                }
            }

            // Terminal Output Area
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(scrollState)
            ) {
                Column {
                    lines.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "➜ ",
                                color = Color(0xFF22C55E),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = line.command,
                                color = AppWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (line.output.isNotEmpty()) {
                            Text(
                                text = line.output,
                                color = if (line.exitCode == 0) Color(0xFFCBD5E1) else Color(0xFFF87171),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    if (currentLiveOutput.isNotEmpty()) {
                        Text(
                            text = currentLiveOutput,
                            color = Color(0xFF93C5FD),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    if (isRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = AppPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Running...",
                                color = AppPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Virtual Utility Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161D27))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VirtualKeyButton("CTRL", active = ctrlActive) { ctrlActive = !ctrlActive }
                VirtualKeyButton("ALT", active = altActive) { altActive = !altActive }
                VirtualKeyButton("TAB") {
                    commandInput = TextFieldValue(commandInput.text + "\t", TextRange(commandInput.text.length + 1))
                }
                VirtualKeyButton("ESC") {
                    commandInput = TextFieldValue("")
                }
                VirtualKeyButton("▲") {
                    if (commandHistory.isNotEmpty()) {
                        val nextIdx = if (historyIndex == -1) commandHistory.size - 1 else maxOf(0, historyIndex - 1)
                        historyIndex = nextIdx
                        val histCmd = commandHistory[nextIdx]
                        commandInput = TextFieldValue(histCmd, TextRange(histCmd.length))
                    }
                }
                VirtualKeyButton("▼") {
                    if (commandHistory.isNotEmpty() && historyIndex != -1) {
                        val nextIdx = historyIndex + 1
                        if (nextIdx < commandHistory.size) {
                            historyIndex = nextIdx
                            val histCmd = commandHistory[nextIdx]
                            commandInput = TextFieldValue(histCmd, TextRange(histCmd.length))
                        } else {
                            historyIndex = -1
                            commandInput = TextFieldValue("")
                        }
                    }
                }
                if (isRunning) {
                    VirtualKeyButton("Ctrl+C", color = Color(0xFFEF4444)) {
                        interruptProcess()
                    }
                }
                VirtualKeyButton("clear") {
                    lines = emptyList()
                }
            }

            // Command Input Bar
            Surface(
                color = Color(0xFF1A2230),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedMode == TerminalMode.ROOT_SU) "# " else "$ ",
                        color = if (selectedMode == TerminalMode.ROOT_SU) Color(0xFFEF4444) else AppPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFocusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = AppWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(AppPrimary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val cmd = commandInput.text
                                commandInput = TextFieldValue()
                                if (isRunning) {
                                    sendInput(cmd)
                                } else {
                                    runCommand(cmd)
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = {
                            val cmd = commandInput.text
                            commandInput = TextFieldValue()
                            if (isRunning) {
                                sendInput(cmd)
                            } else {
                                runCommand(cmd)
                            }
                        },
                        enabled = commandInput.text.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (commandInput.text.isNotBlank()) AppPrimary else AppMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyButton(
    label: String,
    active: Boolean = false,
    color: Color = AppWhite,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AppPrimary.copy(alpha = 0.3f) else Color(0xFF263244))
            .border(1.dp, if (active) AppPrimary else Color(0xFF334155), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) AppPrimary else color,
            fontFamily = FontFamily.Monospace
        )
    }
}
