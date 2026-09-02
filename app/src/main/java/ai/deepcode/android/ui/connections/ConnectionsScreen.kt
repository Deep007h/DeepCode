package ai.deepcode.android.ui.connections
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.theme.*
import ai.deepcode.android.data.repository.DeepCodeRepository

// Brand colors and metadata mapping for Available Integrations
data class BrandConfig(
    val initials: String,
    val brandColor: Color,
    val description: String
)

val INTEGRATION_BRANDS = mapOf(
    "airtable" to BrandConfig("AI", Color(0xFF18BFFF), "Powerful data collaboration"),
    "asana" to BrandConfig("AS", Color(0xFFF06A6A), "Task management platform"),
    "discord" to BrandConfig("DI", Color(0xFF5865F2), "Community communication"),
    "dropbox" to BrandConfig("DR", Color(0xFF0061FE), "Cloud storage solution"),
    "github" to BrandConfig("GI", Color(0xFF9E9E9E), "Developer platform"),
    "gmail" to BrandConfig("GM", Color(0xFFEA4335), "Email integration"),
    "google_sheets" to BrandConfig("GS", Color(0xFF0F9D58), "Spreadsheet management"),
    "google_calendar" to BrandConfig("GC", Color(0xFF4285F4), "Calendar scheduling"),
    "slack" to BrandConfig("SF", Color(0xFFE01E5A), "Team communication"),
    "trello" to BrandConfig("TR", Color(0xFF0079BF), "Project management boards"),
    "notion" to BrandConfig("NT", Color(0xFF8B5CF6), "All-in-one workspace"),
    "zendesk" to BrandConfig("ZW", Color(0xFF03363D), "Customer support platform"),
    "google_account" to BrandConfig("GA", Color(0xFF4285F4), "Google authentication"),
    "google_drive" to BrandConfig("GD", Color(0xFF34A853), "Cloud storage drive"),
    "youtube_music" to BrandConfig("YT", Color(0xFFFF0000), "Music integration"),
    "spotify" to BrandConfig("SP", Color(0xFF1DB954), "Music streaming service"),
    "twitter" to BrandConfig("X", Color(0xFF1DA1F2), "Social integration"),
    "linear" to BrandConfig("LN", Color(0xFF5E6AD2), "Issue tracking"),
    "jira" to BrandConfig("JR", Color(0xFF0052CC), "Enterprise issue tracker"),
    "stripe" to BrandConfig("ST", Color(0xFF635BFF), "Payment processor"),
    "shopify" to BrandConfig("SH", Color(0xFF96BF48), "E-commerce integration"),
    "hubspot" to BrandConfig("HS", Color(0xFFFF7A59), "CRM and marketing tool")
)

fun getBrandInitials(appId: String): String {
    return INTEGRATION_BRANDS[appId.lowercase()]?.initials ?: appId.take(2).uppercase()
}

fun getBrandColor(appId: String): Color {
    return INTEGRATION_BRANDS[appId.lowercase()]?.brandColor ?: AppIntegrationPurple
}

fun getBrandDescription(appId: String): String {
    return INTEGRATION_BRANDS[appId.lowercase()]?.description ?: appId.replace("_", " ").replaceFirstChar { it.uppercase() }
}

fun decodeBase64ToBitmap(base64Str: String, context: android.content.Context): Bitmap? {
    val clean = base64Str.substringAfter("base64,").trim()
    return try {
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

@Composable
fun ConnectionsScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ConnectionsViewModel = viewModel { ConnectionsViewModel(context) }
    val integrations by viewModel.integrations.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val showNoGoogleAccountDialog by viewModel.showNoGoogleAccountDialog.collectAsStateWithLifecycle()

    var showTelegramDialog by remember { mutableStateOf(false) }
    var telegramTokenInput by remember { mutableStateOf("") }
    var showNotionDialog by remember { mutableStateOf(false) }
    var notionTokenInput by remember { mutableStateOf("") }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var showWhatsAppQRDialog by remember { mutableStateOf(false) }
    var isAutoResponderEnabled by remember { mutableStateOf(false) }

    val whatsAppConnected by viewModel.whatsAppConnected.collectAsStateWithLifecycle()
    val whatsAppPhone by viewModel.whatsAppPhone.collectAsStateWithLifecycle()
    val whatsAppQRCode by viewModel.whatsAppQRCode.collectAsStateWithLifecycle()

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onAccountPicked(result.data)
    }

    val activeConnections = integrations.filter { it.status == "connected" }
    val availableConnections = integrations.filter { it.status != "connected" }

    var showAllAvailable by remember { mutableStateOf(false) }
    val displayedAvailable = if (showAllAvailable) availableConnections else availableConnections.take(6)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Top Connection / Status Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppCard)
                    .border(1.dp, AppDivider, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppDivider),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (whatsAppConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (whatsAppConnected) Color(0xFF10B981) else Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (whatsAppConnected) "Connected" else "Disconnected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppWhite
                            )
                            Text(
                                text = if (whatsAppConnected) "WhatsApp Bridge Active" else "No active network",
                                fontSize = 12.sp,
                                color = AppMuted
                            )
                        }
                    }

                    // Refresh Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppDivider)
                            .clickable { viewModel.checkWhatsAppHealth() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AppWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pair Device Button (Green)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                            .clickable {
                                if (whatsAppConnected) {
                                    Toast.makeText(context, "Already connected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showWhatsAppQRDialog = true
                                    viewModel.connectWhatsApp()
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pair Device",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Auto-Responder Button (Orange)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, AppPrimary, RoundedCornerShape(10.dp))
                            .clickable {
                                isAutoResponderEnabled = !isAutoResponderEnabled
                                Toast.makeText(
                                    context,
                                    if (isAutoResponderEnabled) "Auto-responder enabled" else "Auto-responder disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AppPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Responder",
                                color = AppPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Active Connections Section Label
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Connections",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                }
                Text(
                    text = "View all >",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { }
                )
            }
        }

        // Active Connections List
        if (activeConnections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppCard)
                        .border(1.dp, AppDivider, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active connections. Connect available integrations below.",
                        fontSize = 12.sp,
                        color = AppMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(activeConnections, key = { it.id }) { connection ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppCard)
                        .border(1.dp, AppDivider, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Initials Icon with Brand Color
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(getBrandColor(connection.appId).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getBrandInitials(connection.appId),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = getBrandColor(connection.appId)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = connection.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AppWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Connected",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "  • Last synced: ${
                                        if (connection.lastSyncedAt > 0)
                                            "${(System.currentTimeMillis() - connection.lastSyncedAt) / 60000}m ago"
                                        else "never"
                                    }",
                                    fontSize = 11.sp,
                                    color = AppMuted
                                )
                            }
                        }
                    }

                    // Delete Trash Icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.disconnectIntegration(connection.appId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Disconnect",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Available Integrations Label
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = AppIntegrationPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Available Integrations",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppIntegrationPurple
                )
            }
        }

        // Available Integrations List Rows (1-column Layout)
        items(displayedAvailable, key = { it.id }) { item ->
            val brandColor = getBrandColor(item.appId)
            val initials = getBrandInitials(item.appId)
            val description = getBrandDescription(item.appId)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppCard)
                    .border(1.dp, AppDivider, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Circle Brand initials
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brandColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = item.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            fontSize = 11.sp,
                            color = AppMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Connect outline button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(brandColor.copy(alpha = 0.08f))
                        .border(1.dp, brandColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            when (item.appId) {
                                "whatsapp" -> {
                                    showWhatsAppQRDialog = true
                                    viewModel.connectWhatsApp()
                                }
                                "telegram" -> showTelegramDialog = true
                                "notion" -> showNotionDialog = true
                                "github" -> showGitHubDialog = true
                                "google_account", "gmail", "google_calendar", "google_drive" -> {
                                    val intent = viewModel.getAccountPickerIntent(item.appId)
                                    accountPickerLauncher.launch(intent)
                                }
                                else -> viewModel.connectIntegration(item.appId)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connect",
                            color = brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Show More / Show Less Toggle Button
        if (availableConnections.size > 6) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAllAvailable = !showAllAvailable }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (showAllAvailable) "Show less" else "Show more",
                            color = AppIntegrationPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (showAllAvailable) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AppIntegrationPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog configuration overlays (Telegram, Notion, GitHub, WhatsApp QR, etc.)
    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            title = { Text("Configure Telegram Bot", fontWeight = FontWeight.Bold, color = AppWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your Telegram Bot HTTP API Token to link the bridge:", fontSize = 13.sp, color = AppMuted)
                    OutlinedTextField(
                        value = telegramTokenInput,
                        onValueChange = { telegramTokenInput = it },
                        label = { Text("Bot API Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppDarkGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                FilledAppButton(
                    onClick = {
                        if (telegramTokenInput.isNotBlank()) {
                            viewModel.connectTelegramBot(telegramTokenInput.trim())
                            showTelegramDialog = false
                            telegramTokenInput = ""
                        }
                    },
                    text = "Verify",
                    backgroundColor = AppPrimary
                )
            },
            dismissButton = {
                OutlinedAppButton(
                    onClick = { showTelegramDialog = false },
                    text = "Cancel",
                    color = AppMuted
                )
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showNotionDialog) {
        AlertDialog(
            onDismissRequest = { showNotionDialog = false },
            title = { Text("Connect Notion", fontWeight = FontWeight.Bold, color = AppWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your Notion Integration Token to link your workspace:", fontSize = 13.sp, color = AppMuted)
                    Text(
                        "Create an integration at notion.so/my-integrations and copy the Internal Integration Secret.",
                        fontSize = 11.sp,
                        color = AppMuted.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notionTokenInput,
                        onValueChange = { notionTokenInput = it },
                        label = { Text("Integration Token (secret_...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppDarkGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                FilledAppButton(
                    onClick = {
                        if (notionTokenInput.isNotBlank()) {
                            viewModel.connectNotion(notionTokenInput.trim())
                            showNotionDialog = false
                            notionTokenInput = ""
                        }
                    },
                    text = "Connect",
                    backgroundColor = AppPrimary
                )
            },
            dismissButton = {
                OutlinedAppButton(
                    onClick = { showNotionDialog = false },
                    text = "Cancel",
                    color = AppMuted
                )
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showGitHubDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubDialog = false },
            title = { Text("Connect GitHub", fontWeight = FontWeight.Bold, color = AppWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your GitHub Personal Access Token:", fontSize = 13.sp, color = AppMuted)
                    Text(
                        "Create a token at github.com/settings/tokens with repo, workflow, and user scopes.",
                        fontSize = 11.sp,
                        color = AppMuted.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = gitHubTokenInput,
                        onValueChange = { gitHubTokenInput = it },
                        label = { Text("GitHub Token (ghp_...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppDarkGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                FilledAppButton(
                    onClick = {
                        if (gitHubTokenInput.isNotBlank()) {
                            viewModel.connectGitHub(gitHubTokenInput.trim())
                            showGitHubDialog = false
                            gitHubTokenInput = ""
                        }
                    },
                    text = "Connect",
                    backgroundColor = AppPrimary
                )
            },
            dismissButton = {
                OutlinedAppButton(
                    onClick = { showGitHubDialog = false },
                    text = "Cancel",
                    color = AppMuted
                )
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // WhatsApp QR Code Dialog
    if (showWhatsAppQRDialog) {
        Dialog(onDismissRequest = {
            showWhatsAppQRDialog = false
            if (whatsAppQRCode != null && !whatsAppConnected) {
                viewModel.refreshWhatsAppQR()
            }
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (whatsAppConnected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "WhatsApp Connected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = whatsAppPhone ?: "",
                            fontSize = 14.sp,
                            color = Color(0xFF25D366)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledAppButton(
                            onClick = { showWhatsAppQRDialog = false },
                            text = "Done",
                            backgroundColor = AppPrimary
                        )
                    } else if (whatsAppQRCode != null) {
                        Text(
                            text = "Scan QR Code",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Open WhatsApp on your phone and scan this code.",
                            fontSize = 12.sp,
                            color = AppMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val qrBitmap = decodeBase64ToBitmap(whatsAppQRCode ?: "", context)
                            if (qrBitmap != null) {
                                Image(
                                    painter = BitmapPainter(qrBitmap.asImageBitmap()),
                                    contentDescription = "WhatsApp QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for scan...",
                            fontSize = 11.sp,
                            color = AppMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedAppButton(
                                onClick = {
                                    viewModel.refreshWhatsAppQR()
                                },
                                text = "Refresh QR",
                                color = AppMuted
                            )
                            FilledAppButton(
                                onClick = { showWhatsAppQRDialog = false },
                                text = "Cancel",
                                backgroundColor = AppPrimary
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            color = AppPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connecting to WhatsApp Bridge...",
                            fontSize = 14.sp,
                            color = AppMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedAppButton(
                            onClick = {
                                showWhatsAppQRDialog = false
                                viewModel.refreshWhatsAppQR()
                            },
                            text = "Cancel",
                            color = AppMuted
                        )
                    }
                }
            }
        }
    }

    if (showNoGoogleAccountDialog) {
        Dialog(onDismissRequest = { viewModel.dismissNoGoogleAccountDialog() }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Google Account", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppWhite)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No Google account is registered on this device. Please add a Google account in system settings to use Google integrations.",
                        fontSize = 14.sp,
                        color = AppMuted
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { viewModel.dismissNoGoogleAccountDialog() }) {
                            Text("Cancel", color = AppMuted)
                        }
                        Button(
                            onClick = {
                                context.startActivity(Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).setClassName("com.android.settings", "com.android.settings.accounts.AddAccountSettings"))
                                viewModel.dismissNoGoogleAccountDialog()
                            }
                        ) {
                            Text("Add Account", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
