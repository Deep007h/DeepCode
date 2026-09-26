package ai.deepcode.android.ui.preview

import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.deepcode.android.ui.theme.*

private fun String.isLoopbackHost(): Boolean {
    val h = lowercase()
    return h == "localhost" || h == "127.0.0.1" || h == "::1" || h.startsWith("127.")
}

private fun Uri.isLoopbackPreviewUrl(): Boolean {
    val h = host ?: return false
    return h.isLoopbackHost()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(
    initialUrl: String = "http://localhost:3000",
    onBack: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(initialUrl) }
    var activeUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showLogsSheet by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<String>() }

    val navigate: () -> Unit = {
        val raw = address.trim()
        val withScheme = if ("://" in raw) raw else "http://$raw"
        val parsed = runCatching { Uri.parse(withScheme) }.getOrNull()
        if (parsed == null || !parsed.isLoopbackPreviewUrl() || parsed.host.isNullOrBlank()) {
            addressError = "Please enter a valid local URL (e.g. localhost:3000, 127.0.0.1:5173)"
        } else {
            addressError = null
            address = withScheme
            activeUrl = withScheme
        }
    }

    val quickPorts = listOf(3000, 5173, 8080, 8000, 4200)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Live Web Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppWhite)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showLogsSheet = !showLogsSheet }) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Console Logs",
                            tint = if (consoleLogs.isNotEmpty()) AppPrimary else AppMuted
                        )
                    }
                    IconButton(onClick = { webView?.reload() ?: navigate() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = AppWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        },
        containerColor = AppScreenBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // URL Bar & Port Selector
            Surface(
                color = AppSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = {
                                address = it
                                addressError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Local Server URL") },
                            placeholder = { Text("http://localhost:3000") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (activeUrl.isNotBlank()) Color(0xFF22C55E) else AppMuted)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = navigate) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = AppPrimary)
                                }
                            },
                            isError = addressError != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { navigate() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite,
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurface,
                                unfocusedContainerColor = AppSurface
                            )
                        )
                    }

                    if (addressError != null) {
                        Text(
                            text = addressError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }

                    // Port shortcut chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ports:",
                            fontSize = 11.sp,
                            color = AppMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        quickPorts.forEach { port ->
                            val isSelected = address.contains(":$port")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AppPrimary.copy(alpha = 0.2f) else AppSurface)
                                    .border(1.dp, if (isSelected) AppPrimary else AppBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        address = "http://localhost:$port"
                                        navigate()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = ":$port",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) AppPrimary else AppWhite
                                )
                            }
                        }
                    }

                    if (loading) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            color = AppPrimary,
                        )
                    }
                }
            }

            // Webview Display
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    loading = newProgress < 100
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        val line = "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                                        consoleLogs.add(line)
                                    }
                                    return super.onConsoleMessage(consoleMessage)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val target = request?.url ?: return true
                                    if (!target.isLoopbackPreviewUrl()) {
                                        addressError = "External web navigation is restricted to localhost"
                                        return true
                                    }
                                    address = target.toString()
                                    return false
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val target = request?.url ?: return null
                                    if (!target.isLoopbackPreviewUrl()) {
                                        return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "Blocked by security filter".byteInputStream())
                                    }
                                    return null
                                }
                            }

                            loadUrl(activeUrl)
                        }
                    },
                    update = { current ->
                        webView = current
                        if (current.url != activeUrl) {
                            current.loadUrl(activeUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Console Logs Overlay Sheet
                if (showLogsSheet) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFF0F141C),
                        tonalElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Console Telemetry (${consoleLogs.size})", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 12.sp)
                                Row {
                                    TextButton(onClick = { consoleLogs.clear() }) {
                                        Text("Clear", fontSize = 11.sp, color = AppMuted)
                                    }
                                    IconButton(onClick = { showLogsSheet = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(consoleLogs) { logLine ->
                                    Text(
                                        text = logLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = if (logLine.contains("ERROR")) Color(0xFFF87171) else Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
