package ai.deepcode.android.ui.editor
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.data.repository.DeepCodeRepository
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class EditorViewModel(private val repository: DeepCodeRepository) : ViewModel() {
    private val _fileContent = MutableStateFlow<String>("")
    val fileContent = _fileContent.asStateFlow()

    private val _filePath = MutableStateFlow<String>("")
    val filePath = _filePath.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("")
    val statusMessage = _statusMessage.asStateFlow()

    fun openFile(path: String) {
        _filePath.value = path
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.executeTool("read_file", "{\"path\":\"$path\"}", File(path).parent ?: "")
            if (result.startsWith("Error: File does not exist") || result.startsWith("File does not exist")) {
                _fileContent.value = ""
                _statusMessage.value = "File is empty or not found"
            } else {
                _fileContent.value = result
                _statusMessage.value = "Loaded successfully"
            }
        }
    }

    fun saveFile(content: String) {
        val path = _filePath.value
        if (path.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.executeTool(
                "write_file",
                "{\"path\":\"$path\",\"content\":${com.google.gson.Gson().toJson(content)}}",
                File(path).parent ?: ""
            )
            _statusMessage.value = result
        }
    }

    fun sendToAi(sessionId: String, content: String) {
        val fileName = File(_filePath.value).name
        val formattedMsg = "Here is the code content of `$fileName`:\n\n```kotlin\n$content\n```"
        viewModelScope.launch {
            repository.insertMessage(
                ai.deepcode.android.domain.model.Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = formattedMsg,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        _statusMessage.value = "Sent code to current session"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    repository: DeepCodeRepository,
    selectedFilePath: String,
    activeSessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: EditorViewModel = viewModel { EditorViewModel(repository) }
    val fileContent by viewModel.fileContent.collectAsStateWithLifecycle()
    val filePath by viewModel.filePath.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var editorInstance: CodeEditor? by remember { mutableStateOf(null) }
    var loadedPath by remember { mutableStateOf("") }
    var lastPushedContent by remember { mutableStateOf("") }
    var searchVal by remember { mutableStateOf("") }
    var replaceVal by remember { mutableStateOf("") }
    var showFindReplace by remember { mutableStateOf(false) }

    var softWrap by remember { mutableStateOf(true) }
    val editScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            softWrap = repository.securePrefs.getBooleanSetting("soft_wrap", true)
        }
    }

    LaunchedEffect(selectedFilePath) {
        if (selectedFilePath.isNotEmpty()) {
            viewModel.openFile(selectedFilePath)
        }
    }

    // Set editor soft wrap state when preferences change
    LaunchedEffect(softWrap, editorInstance) {
        editorInstance?.isWordwrap = softWrap
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF121212))) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (filePath.isNotEmpty()) File(filePath).name else "No File Opened",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = {
                    editorInstance?.let { editor ->
                        viewModel.saveFile(editor.text.toString())
                    }
                }
            ) {
                Text("Save", color = Color(0xFF00FF9C), fontSize = 14.sp)
            }

            IconButton(
                onClick = {
                    if (activeSessionId.isNotEmpty()) {
                        editorInstance?.let { editor ->
                            viewModel.sendToAi(activeSessionId, editor.text.toString())
                        }
                    }
                }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Send to AI", tint = Color(0xFF00E5FF))
            }
        }

        // Sub Actions Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = { editorInstance?.undo() }) {
                Text("Undo", fontSize = 12.sp, color = Color.White)
            }
            TextButton(onClick = { editorInstance?.redo() }) {
                Text("Redo", fontSize = 12.sp, color = Color.White)
            }
            TextButton(onClick = { showFindReplace = !showFindReplace }) {
                Text("Find/Replace", fontSize = 12.sp, color = Color.White)
            }
            TextButton(
                onClick = {
                    softWrap = !softWrap
                    editScope.launch(Dispatchers.IO) {
                        repository.securePrefs.saveBooleanSetting("soft_wrap", softWrap)
                    }
                }
            ) {
                Text(if (softWrap) "Unwrap Lines" else "Soft Wrap", fontSize = 12.sp, color = Color.White)
            }
        }

        // Search & Replace UI
        if (showFindReplace) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2E2E2E)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchVal,
                    onValueChange = { searchVal = it },
                    label = { Text("Find", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF9C)
                    )
                )

                OutlinedTextField(
                    value = replaceVal,
                    onValueChange = { replaceVal = it },
                    label = { Text("Replace", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF9C)
                    )
                )

                Button(
                    onClick = {
                        editorInstance?.let { editor ->
                            val currentText = editor.text.toString()
                            if (searchVal.isNotEmpty()) {
                                val replaced = currentText.replace(searchVal, replaceVal)
                                editor.setText(replaced)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Go", color = Color.Black)
                }
            }
        }

        // Code Editor View
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                CodeEditor(context).apply {
                    setTextSize(14f)
                    isLineNumberEnabled = true
                    isWordwrap = softWrap
                    colorScheme = EditorColorScheme() // Use default dark theme colors
                    editorInstance = this
                }
            },
            update = { editor ->
                // filePath is set synchronously but fileContent loads asynchronously
                // on an IO coroutine. Committing loadedPath as soon as filePath
                // changes (while content is still "") would skip the real content
                // when it arrives. Only mark the file as loaded once content has
                // actually been pushed into the editor to avoid a blank editor.
                if (filePath.isNotEmpty() && (filePath != loadedPath || lastPushedContent != fileContent)) {
                    editor.setText(fileContent)
                    lastPushedContent = fileContent
                    loadedPath = filePath
                }
            }
        )
    }
}
