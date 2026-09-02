package ai.deepcode.android.ui
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import ai.deepcode.android.ui.theme.AppScreenBg
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.graphics.Rect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.data.local.ProfileManager
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.domain.model.ChatSession
import ai.deepcode.android.ui.chat.ChatScreen
import ai.deepcode.android.ui.chat.PersonaPickerContent
import ai.deepcode.android.ui.editor.EditorScreen
import ai.deepcode.android.ui.TokenUsageScreen
import ai.deepcode.android.ui.settings.SettingsScreen
import ai.deepcode.android.ui.settings.LogViewerScreen
import ai.deepcode.android.ui.settings.PersonasScreen
import ai.deepcode.android.ui.settings.PersonaDetailPage
import ai.deepcode.android.ui.settings.Persona
import ai.deepcode.android.ui.settings.ManageTemplatesScreen
import ai.deepcode.android.ui.settings.TemplateDetailPage
import ai.deepcode.android.ui.settings.VpnSettingsScreen
import ai.deepcode.android.ui.settings.ApiKeysScreen
import ai.deepcode.android.ui.settings.CloudflareSettingsScreen
import ai.deepcode.android.ui.settings.ThemesAndWallpapersScreen
import ai.deepcode.android.ui.connections.ConnectionsScreen
import ai.deepcode.android.ui.connections.ConnectionsViewModel
import ai.deepcode.android.ui.automations.AutomationsScreen
import ai.deepcode.android.ui.theme.DeepCodeTheme
import ai.deepcode.android.ui.theme.AppBackground
import ai.deepcode.android.ui.theme.AppWhite
import ai.deepcode.android.ui.theme.AppSurface
import ai.deepcode.android.ui.theme.AppSurfaceVariant
import ai.deepcode.android.ui.theme.AppBorder
import ai.deepcode.android.ui.theme.AppMuted
import ai.deepcode.android.ui.theme.AppPrimary
import ai.deepcode.android.ui.theme.AppDarkGray
import ai.deepcode.android.ui.theme.AppSuccess
import ai.deepcode.android.ui.components.BottomNavBar
import ai.deepcode.android.ui.components.gridBackground
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import ai.deepcode.android.ui.dashboard.DashboardScreen
import ai.deepcode.android.ui.LoginScreen
import android.content.Intent
import ai.deepcode.android.service.telegram.BotConfigStore
import ai.deepcode.android.service.telegram.TelegramBridgeService
import ai.deepcode.android.ui.connections.OAuthManager
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.PermissionHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import ai.deepcode.android.ui.agents.AgentsScreen
import ai.deepcode.android.ui.agents.AgentDetailScreen
import androidx.activity.compose.BackHandler
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private var hasRequestedPermissionsThisInstance = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "deepcode" && uri.host == "oauth") {
                val code = uri.getQueryParameter("code")
                val appId = uri.getQueryParameter("state")
                if (code != null && appId != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val oauthManager = OAuthManager(this@MainActivity, lifecycleScope)
                        val success = oauthManager.handleCallback(appId, code)
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this@MainActivity, "${appId.replace("_", " ").replaceFirstChar { it.uppercase() }} connected successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to connect ${appId.replace("_", " ")}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTraceStr = android.util.Log.getStackTraceString(throwable)
            val fatalMsg = "FATAL UNCAUGHT CRASH on [${thread.name}]: ${throwable.message}\n$stackTraceStr"
            android.util.Log.e("FATAL_CRASH_GUARD", fatalMsg)
            AppLogger.e("GlobalCrashGuard", fatalMsg, throwable)
            try {
                val crashFile = java.io.File(filesDir, "fatal_crash.txt")
                crashFile.writeText("${java.util.Date()}\n$fatalMsg")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        handleOAuthIntent(intent)
        AppLogger.i("MainActivity", "App starting...")

        var repoState by mutableStateOf<DeepCodeRepository?>(null)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repo = DeepCodeRepository.getInstance(applicationContext)
                withContext(Dispatchers.Main) {
                    repoState = repo
                }
                AppLogger.i("MainActivity", "Repository initialized asynchronously")

                try {
                    ai.deepcode.android.util.VpnManager.init(this@MainActivity, repo.securePrefs)
                    AppLogger.i("MainActivity", "VpnManager initialized")
                } catch (_: Exception) {}

                val botStore = BotConfigStore(this@MainActivity)
                val integrationRepo = IntegrationRepository(this@MainActivity)
                val telegramIntegration = integrationRepo.getIntegrationByAppId("telegram")
                var hasToken = botStore.getBots().isNotEmpty()
                if (telegramIntegration != null && telegramIntegration.status == "connected") {
                    val token = telegramIntegration.accessToken
                    if (token.isNotEmpty()) {
                        if (!botStore.getTokens().contains(token)) {
                            botStore.addBot(token)
                            AppLogger.i("MainActivity", "Seeded Telegram bot token from database to BotConfigStore")
                        }
                        hasToken = true
                    }
                }
                if (hasToken) {
                    TelegramBridgeService.start(this@MainActivity)
                    AppLogger.i("MainActivity", "Telegram bridge started")
                }
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Async initialization failed", e)
            }
        }

        val profileManager = ProfileManager(this)

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (!hasRequestedPermissionsThisInstance) {
            hasRequestedPermissionsThisInstance = true
            try {
                checkAndRequestPermissions()
                requestNotificationPermission()
                AppLogger.i("MainActivity", "Permissions checked")
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Permission request failed (non-fatal)", e)
            }
        }

        setContent {
            DeepCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val activeRepo = repoState
                    if (activeRepo != null) {
                        // Sync persisted theme prefs into the global theme state
                        val themeMode by activeRepo.securePrefs.themeFlow.collectAsStateWithLifecycle()
                        val accentId by activeRepo.securePrefs.accentFlow.collectAsStateWithLifecycle()
                        LaunchedEffect(themeMode, accentId) {
                            ai.deepcode.android.ui.theme.AppThemeMode = themeMode
                            ai.deepcode.android.ui.theme.AppAccentId = accentId
                        }
                        AppMainLayout(activeRepo, profileManager)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }

        AppLogger.stopTimer("onCreate", "MainActivity")
    }

    private fun checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        addCategory(android.content.Intent.CATEGORY_DEFAULT)
                        data = android.net.Uri.parse("package:${applicationContext.packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needed = permissions.filter {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 101)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 102)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppMainLayout(repository: DeepCodeRepository, profileManager: ProfileManager) {
    val appState: AppState = viewModel()
    val selectedTab by appState.selectedTab.collectAsStateWithLifecycle()
    val showLogViewer by appState.showLogViewer.collectAsStateWithLifecycle()
    val showTokenUsage by appState.showTokenUsage.collectAsStateWithLifecycle()
    val selectedFilePath by appState.selectedFilePath.collectAsStateWithLifecycle()
    val showFileExplorer by appState.showFileExplorer.collectAsStateWithLifecycle()
    val showAgents by appState.showAgents.collectAsStateWithLifecycle()
    val selectedAgentId by appState.selectedAgentId.collectAsStateWithLifecycle()
    val showPersonas by appState.showPersonas.collectAsStateWithLifecycle()
    val selectedPersona by appState.selectedPersona.collectAsStateWithLifecycle()
    val showManageTemplates by appState.showManageTemplates.collectAsStateWithLifecycle()
    val selectedTemplateId by appState.selectedTemplateId.collectAsStateWithLifecycle()
    val showVpnSettings by appState.showVpnSettings.collectAsStateWithLifecycle()
    val showApiKeys by appState.showApiKeys.collectAsStateWithLifecycle()
    val showCloudflare by appState.showCloudflare.collectAsStateWithLifecycle()
    val showPlugins by appState.showPlugins.collectAsStateWithLifecycle()
    val showThemesAndWallpapers by appState.showThemesAndWallpapers.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isOverlayOpen = selectedTab != 0 ||
        showLogViewer || showTokenUsage ||
        selectedFilePath.isNotEmpty() || showFileExplorer ||
        showAgents || selectedAgentId.isNotEmpty() ||
        showPersonas || selectedPersona != null ||
        showManageTemplates || selectedTemplateId.isNotEmpty() ||
        showVpnSettings || showApiKeys || showCloudflare ||
        showPlugins || showThemesAndWallpapers

    BackHandler(enabled = isOverlayOpen) {
        when {
            showThemesAndWallpapers -> appState.setShowThemesAndWallpapers(false)
            showPlugins -> appState.setShowPlugins(false)
            selectedPersona != null -> appState.setSelectedPersona(null)
            showPersonas -> appState.setShowPersonas(false)
            selectedTemplateId.isNotEmpty() -> appState.setSelectedTemplateId("")
            showManageTemplates -> appState.setShowManageTemplates(false)
            showVpnSettings -> appState.setShowVpnSettings(false)
            showApiKeys -> appState.setShowApiKeys(false)
            showCloudflare -> appState.setShowCloudflare(false)
            selectedAgentId.isNotEmpty() -> appState.setSelectedAgentId("")
            showAgents -> appState.setShowAgents(false)
            showTokenUsage -> appState.setShowTokenUsage(false)
            showLogViewer -> appState.setShowLogViewer(false)
            selectedFilePath.isNotEmpty() -> appState.setSelectedFilePath("")
            showFileExplorer -> appState.setShowFileExplorer(false)
            selectedTab != 0 -> appState.selectTab(0)
        }
    }

    val sessions by repository.getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    var activeSessionId by remember { mutableStateOf("") }

    var showPersonaPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }

    LaunchedEffect(sessions) {
        if (sessions.isNotEmpty() && activeSessionId.isEmpty()) {
            activeSessionId = sessions.first().id
        }
    }

    val activeSessionName = sessions.firstOrNull { it.id == activeSessionId }?.title ?: "Chat"

    var sessionsExpanded by remember { mutableStateOf(true) }
    var pastSessionsExpanded by remember { mutableStateOf(true) }
    var chatControlExpanded by remember { mutableStateOf(true) }
    var drawerControlsExpanded by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Shared ConnectionsViewModel for Dashboard and Connections screens
    val viewModelContext = LocalContext.current.applicationContext
    val connectionsViewModel: ConnectionsViewModel = viewModel { ConnectionsViewModel(viewModelContext) }

    val integrations by connectionsViewModel.integrations.collectAsStateWithLifecycle()
    val activeConnections = integrations.filter { it.status == "connected" }

    val activeProfile = remember(profileManager) { profileManager.getActiveProfile() }
    var showLogin by remember { mutableStateOf(activeProfile == null) }
    val showLoginSwitch by appState.showLogin.collectAsStateWithLifecycle()

    if (showLogin || showLoginSwitch) {
        LoginScreen(
            profileManager = profileManager,
            onProfileSelected = {
                showLogin = false
                appState.setShowLogin(false)
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GlobalWallpaperBackground(repository = repository)
        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AppScreenBg,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(),
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppScreenBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Header Row: DEEPCODE + Edit Icon Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DEEPCODE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            fontFamily = FontFamily.SansSerif
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppSurface.copy(alpha = 0.6f))
                                .border(1.dp, AppBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        val newId = repository.createSession("Session ${sessions.size + 1}")
                                        activeSessionId = newId
                                        drawerState.close()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Session",
                                tint = AppPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        placeholder = { Text("Search...", color = AppMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = AppMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .background(AppSurfaceVariant, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, AppBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "⌘ K",
                                    color = AppMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder,
                            focusedContainerColor = AppSurfaceVariant,
                            unfocusedContainerColor = AppSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Scrollable LazyColumn for categories
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // TOOLS Category
                        item {
                            DrawerCategoryHeader1(
                                title = "Tools",
                                isExpanded = drawerControlsExpanded,
                                onToggle = { drawerControlsExpanded = !drawerControlsExpanded }
                            )
                        }

                        item {
                            if (drawerControlsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val isChatActive = selectedTab == 1 && !showLogViewer && selectedFilePath.isEmpty() && !showFileExplorer && !showAgents
                                    val isFileExplorerActive = selectedFilePath.isNotEmpty() || showFileExplorer
                                    val isLogViewerActive = showLogViewer

                                    DrawerItem1(
                                        title = "Chat",
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        isSelected = isChatActive,
                                        onClick = {
                                            appState.selectTab(1)
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                    DrawerItem1(
                                        title = "File Explorer",
                                        icon = Icons.AutoMirrored.Filled.List,
                                        isSelected = isFileExplorerActive,
                                        onClick = {
                                            appState.setShowFileExplorer(true)
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                    DrawerItem1(
                                        title = "Log Viewer",
                                        icon = Icons.AutoMirrored.Filled.List,
                                        isSelected = isLogViewerActive,
                                        onClick = {
                                            appState.setShowLogViewer(true)
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                    Box {
                                        DrawerItem1(
                                            title = "Personas",
                                            icon = Icons.Default.Person,
                                            isSelected = false,
                                            onClick = {
                                                showPersonaPicker = true
                                            }
                                        )
                                        if (showPersonaPicker) {
                                            val personaEnabled = repository.securePrefs.getSetting("persona_enabled", "false") == "true"
                                            val activeContent = repository.securePrefs.getSetting("custom_persona", "")
                                            val activeName = if (personaEnabled && activeContent.isNotEmpty()) {
                                                val custom = try {
                                                    val raw = repository.securePrefs.getSetting("saved_personas", "[]")
                                                    val json = org.json.JSONArray(raw)
                                                    (0 until json.length()).map { i ->
                                                        val obj = json.getJSONObject(i)
                                                        obj.getString("name") to obj.getString("content")
                                                    }
                                                } catch (_: Exception) { emptyList() }
                                                val all = listOf("Default" to "") + ai.deepcode.android.ui.settings.builtInPersonas.map { it.name to it.content } + custom
                                                all.find { it.second == activeContent }?.first ?: "Default"
                                            } else "Default"
                                            Popup(
                                                onDismissRequest = { showPersonaPicker = false; scope.launch { drawerState.close() } },
                                                alignment = Alignment.BottomStart
                                            ) {
                                                PersonaPickerContent(
                                                    activePersonaName = activeName,
                                                    onPersonaSelected = { showPersonaPicker = false; scope.launch { drawerState.close() } },
                                                    securePrefs = repository.securePrefs
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // RECENT SESSIONS Category
                        item {
                            DrawerCategoryHeader1(
                                title = "Recent Sessions",
                                isExpanded = sessionsExpanded,
                                onToggle = { sessionsExpanded = !sessionsExpanded }
                            )
                        }

                        item {
                            if (sessionsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val activeSession = remember(filteredSessions, activeSessionId) {
                                        filteredSessions.find { it.id == activeSessionId } ?: filteredSessions.firstOrNull()
                                    }
                                    if (activeSession != null) {
                                        ActiveSessionCard(
                                            session = activeSession,
                                            onClick = {
                                                activeSessionId = activeSession.id
                                                appState.selectTab(1)
                                                scope.launch { drawerState.close() }
                                            },
                                            onRename = { showRenameDialog = activeSession },
                                            onDelete = {
                                                scope.launch {
                                                    repository.deleteSession(activeSession.id)
                                                    if (activeSessionId == activeSession.id) activeSessionId = ""
                                                }
                                            }
                                        )
                                    }

                                    // + New Session Button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AppSurfaceVariant)
                                            .border(1.dp, AppPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                scope.launch {
                                                    val newId = repository.createSession("Session ${sessions.size + 1}")
                                                    activeSessionId = newId
                                                    drawerState.close()
                                                }
                                            }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = AppPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "New Session",
                                                color = AppPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // PAST SESSIONS Category
                        item {
                            DrawerCategoryHeader1(
                                title = "Past Sessions",
                                isExpanded = pastSessionsExpanded,
                                onToggle = { pastSessionsExpanded = !pastSessionsExpanded }
                            )
                        }

                        if (pastSessionsExpanded) {
                            val activeSession = filteredSessions.find { it.id == activeSessionId } ?: filteredSessions.firstOrNull()
                            val otherSessions = filteredSessions.filter { it.id != activeSession?.id }

                            if (otherSessions.isEmpty()) {
                                item(key = "no_extra_sessions") {
                                    Text(
                                        text = "No additional sessions",
                                        color = AppMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                // One lazy item per session with stable keys so
                                // renames/deletes only recompose the affected row.
                                items(
                                    items = otherSessions,
                                    key = { "session_${it.id}" },
                                    contentType = { "session_row" }
                                ) { session ->
                                    ChatHistorySessionRow(
                                        session = session,
                                        onClick = {
                                            activeSessionId = session.id
                                            appState.selectTab(1)
                                            scope.launch { drawerState.close() }
                                        },
                                        onRename = { showRenameDialog = session },
                                        onDelete = {
                                            scope.launch {
                                                repository.deleteSession(session.id)
                                                if (activeSessionId == session.id) activeSessionId = ""
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = AppBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // Settings Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                appState.selectTab(4)
                                scope.launch { drawerState.close() }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = AppWhite,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Settings",
                                color = AppWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AppMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // User Profile Card
                    val drawerProfile = remember(profileManager) { profileManager.getActiveProfile() }
                    val drawerProfileName = drawerProfile?.name ?: "deep"
                    val profileInitial = remember(drawerProfileName) {
                        drawerProfileName.firstOrNull()?.uppercase() ?: "D"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppSurfaceVariant)
                            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                appState.setShowLogin(true)
                                scope.launch { drawerState.close() }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (drawerProfile != null) {
                                ProfileAvatar(
                                    profile = drawerProfile,
                                    profileManager = profileManager,
                                    size = 38.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(AppPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profileInitial,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = drawerProfileName,
                                    color = AppWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to switch profile",
                                    color = AppMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch profile",
                            tint = AppMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        ) {
        Scaffold(
            bottomBar = {
                BottomNavBar(
                    activeTab = selectedTab,
                    onTabSelected = { index ->
                        appState.setShowAgents(false)
                        appState.setShowLogViewer(false)
                        appState.setShowTokenUsage(false)
                        appState.setSelectedFilePath("")
                        appState.setShowFileExplorer(false)
                        appState.setShowPersonas(false)
                        appState.setSelectedPersona(null)
                        appState.setShowManageTemplates(false)
                        appState.setSelectedTemplateId("")
                        appState.setShowVpnSettings(false)
                        appState.setShowApiKeys(false)
                        appState.setShowCloudflare(false)
                        appState.setShowPlugins(false)
                        appState.selectTab(index)
                    }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding()
                    )
                    // Tell descendants the bottom-bar inset is already applied,
                    // otherwise ChatScreen's imePadding() stacks on top of it and
                    // the input bar floats a whole navbar above the keyboard.
                    .consumeWindowInsets(padding)
            ) {
                when (selectedTab) {
                        0 -> DashboardScreen(
                            activeConnections = activeConnections,
                            integrationsCount = integrations.size,
                            onTabSelect = { appState.selectTab(it) },
                            repository = repository,
                            onShowTokenUsage = { appState.setShowTokenUsage(true) }
                        )
                        1 -> ChatScreen(
                            repository = repository,
                            activeSessionId = activeSessionId,
                            sessionTitle = activeSessionName,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onOpenApiKeys = { appState.setShowApiKeys(true) }
                        )
                        2 -> AutomationsScreen(
                            onBack = { appState.selectTab(0) }
                        )
                        3 -> ConnectionsScreen(
                            repository = repository,
                            onBack = { appState.selectTab(0) },
                            onNavigateToSettings = { appState.selectTab(4) }
                        )
                        4 -> SettingsScreen(
                            repository = repository,
                            profileManager = profileManager,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onViewLogs = { appState.setShowLogViewer(true) },
                            onViewAgents = { appState.setShowAgents(true) },
                            onManagePersonas = { appState.setShowPersonas(true) },
                            onManageTemplates = { appState.setShowManageTemplates(true) },
                            onNavigateToVpn = { appState.setShowVpnSettings(true) },
                            onNavigateToApiKeys = { appState.setShowApiKeys(true) },
                            onNavigateToCloudflare = { appState.setShowCloudflare(true) },
                            onNavigateToPlugins = { appState.setShowPlugins(true) },
                            onNavigateToThemesAndWallpapers = { appState.setShowThemesAndWallpapers(true) }
                        )
                    }

                    val activeScreenKey by remember {
                        derivedStateOf {
                            when {
                                showThemesAndWallpapers -> "themes_wallpapers"
                                showTokenUsage -> "token_usage"
                                showPlugins -> "plugins"
                                showVpnSettings -> "vpn"
                                showCloudflare -> "cloudflare"
                                showApiKeys -> "api_keys"
                                showPersonas && selectedPersona != null -> "persona_detail"
                                showPersonas -> "personas"
                                showManageTemplates && selectedTemplateId.isNotEmpty() -> "template_detail"
                                showManageTemplates -> "templates"
                                showLogViewer -> "log_viewer"
                                selectedFilePath.isNotEmpty() || showFileExplorer -> "editor"
                                showAgents && selectedAgentId.isNotEmpty() -> "agent_detail"
                                showAgents -> "agents"
                                else -> null
                            }
                        }
                    }

                    if (activeScreenKey != null) {
                        val screenLevels = remember {
                            mapOf(
                                "themes_wallpapers" to 1,
                                "vpn" to 1,
                                "plugins" to 1,
                                "cloudflare" to 1,
                                "api_keys" to 1,
                                "personas" to 1,
                                "templates" to 1,
                                "log_viewer" to 1,
                                "editor" to 1,
                                "agents" to 1,
                                "token_usage" to 1,
                                "persona_detail" to 2,
                                "template_detail" to 2,
                                "agent_detail" to 2
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize().background(AppScreenBg)) {
                            when (activeScreenKey) {
                                "themes_wallpapers" -> {
                                    ThemesAndWallpapersScreen(
                                        repository = repository,
                                        onBack = { appState.setShowThemesAndWallpapers(false) }
                                    )
                                }
                            "token_usage" -> {
                                TokenUsageScreen(
                                    repository = repository,
                                    onClose = { appState.setShowTokenUsage(false) }
                                )
                            }
                            "plugins" -> {
                                ai.deepcode.android.ui.plugins.PluginsScreen(
                                    onBack = { appState.setShowPlugins(false) }
                                )
                            }
                            "vpn" -> {
                                VpnSettingsScreen(
                                    repository = repository,
                                    onBack = { appState.setShowVpnSettings(false) }
                                )
                            }
                            "cloudflare" -> {
                                CloudflareSettingsScreen(
                                    repository = repository,
                                    onBack = { appState.setShowCloudflare(false) }
                                )
                            }
                            "api_keys" -> {
                                ApiKeysScreen(
                                    repository = repository,
                                    onBack = { appState.setShowApiKeys(false) }
                                )
                            }
                            "persona_detail" -> {
                                PersonaDetailPage(
                                    persona = selectedPersona!!,
                                    repository = repository,
                                    onBack = { appState.setSelectedPersona(null) },
                                    onUpdated = { updated ->
                                        appState.setSelectedPersona(null)
                                    }
                                )
                            }
                            "personas" -> {
                                PersonasScreen(
                                    repository = repository,
                                    onBack = { appState.setShowPersonas(false) },
                                    onPersonaClick = { persona ->
                                        appState.setSelectedPersona(persona)
                                    }
                                )
                            }
                            "template_detail" -> {
                                TemplateDetailPage(
                                    templateId = selectedTemplateId,
                                    repository = repository,
                                    onBack = { appState.setSelectedTemplateId("") }
                                )
                            }
                            "templates" -> {
                                ManageTemplatesScreen(
                                    repository = repository,
                                    onBack = { appState.setShowManageTemplates(false) },
                                    onTemplateClick = { templateId ->
                                        appState.setSelectedTemplateId(templateId)
                                    }
                                )
                            }
                            "log_viewer" -> {
                                LogViewerScreen(
                                    onBack = { appState.setShowLogViewer(false) }
                                )
                            }
                            "editor" -> {
                                EditorScreen(
                                    repository = repository,
                                    selectedFilePath = selectedFilePath,
                                    activeSessionId = activeSessionId,
                                    onBack = {
                                        appState.setSelectedFilePath("")
                                        appState.setShowFileExplorer(false)
                                    }
                                )
                            }
                            "agent_detail" -> {
                                AgentDetailScreen(
                                    agentId = selectedAgentId,
                                    onBack = {
                                        appState.setSelectedAgentId("")
                                        appState.setShowAgents(true)
                                    }
                                )
                            }
                            "agents" -> {
                                AgentsScreen(
                                    onBack = { appState.setShowAgents(false) },
                                    onAgentClick = { agentId ->
                                        appState.setSelectedAgentId(agentId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    val currentRenameSession = showRenameDialog
    if (currentRenameSession != null) {
        RenameSessionDialog(
            session = currentRenameSession,
            onDismiss = { showRenameDialog = null },
            onRename = { id, name ->
                scope.launch { repository.renameSession(id, name) }
                showRenameDialog = null
            }
        )
    }

    // Persona picker is rendered inline in the drawer near the Personas menu item
    }
}
}

@Composable
fun SessionRow(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    
    val icon = when {
        session.title.contains("Telegram", ignoreCase = true) -> Icons.Default.Send
        session.title.contains("Crypto", ignoreCase = true) -> Icons.Default.MonetizationOn
        else -> Icons.AutoMirrored.Filled.Chat
    }
    
    val iconTint = if (isActive) AppPrimary else AppPrimary.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) AppPrimary.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isActive) AppPrimary else AppDarkGray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column {
                Text(
                    text = session.title,
                    color = AppWhite,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(AppSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOCAL",
                            color = AppSuccess,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2m ago",
                        color = AppMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Session Menu",
                    tint = AppMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .background(AppSurface)
                    .border(1.dp, AppDarkGray, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = AppWhite, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = AppWhite, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun RenameSessionDialog(
    session: ChatSession?,
    onDismiss: () -> Unit,
    onRename: (id: String, name: String) -> Unit
) {
    var renameValue by remember { mutableStateOf(session?.title ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session", color = AppWhite) },
        text = {
            OutlinedTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                label = { Text("Session Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppWhite,
                    unfocusedTextColor = AppWhite,
                    focusedBorderColor = AppPrimary,
                    unfocusedBorderColor = AppDarkGray
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (renameValue.isNotEmpty()) {
                        onRename(session?.id ?: "", renameValue)
                    }
                }
            ) {
                Text("Rename", color = AppPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppMuted)
            }
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(16.dp)
            )
        }

@Composable
fun DrawerCategoryHeader1(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.Bold,
            color = AppPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.sp
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = AppPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun DrawerItem1(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) AppPrimary else AppWhite
    val bgColor = if (isSelected) AppSurfaceVariant else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(AppPrimary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0) return "2m ago"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

private fun formatExactTime(timestamp: Long): String {
    if (timestamp <= 0) return "10:52 AM"
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun ActiveSessionCard(
    session: ChatSession,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface.copy(alpha = 0.6f))
            .border(1.dp, AppPrimary, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = session.title,
                        color = AppWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF042F22), RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LOCAL",
                                color = Color(0xFF10B981),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatSessionTime(session.createdAt),
                            color = AppMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = AppMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = AppSurfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = AppWhite, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = AppPrimary, modifier = Modifier.size(14.dp)) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatHistorySessionRow(
    session: ChatSession,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = session.title,
                color = AppWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatExactTime(session.createdAt),
                color = AppMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = AppMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = AppSurfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = AppWhite, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = AppPrimary, modifier = Modifier.size(14.dp)) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerTokenUsage(repository: DeepCodeRepository, activeSessionId: String, onClick: () -> Unit = {}) {
    val sessionTokens by repository.tokenRepository.observeSession(activeSessionId)
        .collectAsStateWithLifecycle(null)
    val lifetimeTotals by repository.tokenRepository.observeLifetimeTotals()
        .collectAsStateWithLifecycle(null)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = AppPrimary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tokens", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppPrimary)
                val totalTokens = sessionTokens?.let {
                    it.tokensInput + it.tokensOutput + it.tokensReasoning
                } ?: 0L
                if (totalTokens > 0) {
                    Text(formatTokenCount(totalTokens), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppWhite)
                } else {
                    Text("waiting...", fontSize = 11.sp, color = AppMuted)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cost", fontSize = 10.sp, color = AppMuted)
                Text(formattedCost(sessionTokens?.costUsd ?: 0.0), fontSize = 10.sp, color = AppSuccess)
            }
            sessionTokens?.let { s ->
                if (s.turnCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Turns", fontSize = 10.sp, color = AppMuted)
                        Text("${s.turnCount}", fontSize = 10.sp, color = AppWhite)
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = AppDarkGray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Input", fontSize = 10.sp, color = AppMuted)
                        Text(formatTokenCount(s.tokensInput), fontSize = 10.sp, color = AppWhite)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Output", fontSize = 10.sp, color = AppMuted)
                        Text(formatTokenCount(s.tokensOutput), fontSize = 10.sp, color = AppWhite)
                    }
                    if (s.tokensReasoning > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reasoning", fontSize = 10.sp, color = AppMuted)
                            Text(formatTokenCount(s.tokensReasoning), fontSize = 10.sp, color = AppPrimary)
                        }
                    }
                }
            }
            lifetimeTotals?.let { lt ->
                if (lt.totalTokens > 0 || (sessionTokens?.turnCount ?: 0) == 0) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = AppDarkGray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("All sessions", fontSize = 9.sp, color = AppMuted)
                        Text("${formatTokenCount(lt.totalTokens)} · ${lt.formattedCost()}", fontSize = 9.sp, color = AppMuted)
                    }
                }
            }
        }
    }
}

private fun formattedCost(cost: Double): String = when {
    cost == 0.0 -> "Free"
    cost < 0.001 -> "< $0.001"
    cost < 1.0 -> "$%.4f".format(cost)
    else -> "$%.3f".format(cost)
}

private fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> "$count"
}

@Composable
fun GlobalWallpaperBackground(repository: DeepCodeRepository) {
    if (!ai.deepcode.android.ui.theme.isDarkThemeActive) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F6F9)))
        return
    }
    val activeWallpaperId by repository.securePrefs.wallpaperFlow.collectAsStateWithLifecycle()
    val customWallpaperPath by repository.securePrefs.customWallpaperFlow.collectAsStateWithLifecycle()
    val wallpaperOpt = remember(activeWallpaperId) {
        ai.deepcode.android.ui.settings.PresetWallpapers.find { it.id == activeWallpaperId }
    }
    val customFile = remember(customWallpaperPath) {
        if (customWallpaperPath.isNotEmpty()) java.io.File(customWallpaperPath) else null
    }

    if (activeWallpaperId == "custom" && customFile != null && customFile.exists()) {
        val bm = remember(customFile.absolutePath) {
            try { android.graphics.BitmapFactory.decodeFile(customFile.absolutePath) } catch (_: Exception) { null }
        }
        if (bm != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bm.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
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
}
