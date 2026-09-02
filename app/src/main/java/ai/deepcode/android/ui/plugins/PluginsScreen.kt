package ai.deepcode.android.ui.plugins

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginRegistry
import ai.deepcode.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedPluginForDetails by remember { mutableStateOf<DeepCodePlugin?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (success, message) = PluginRegistry.importPluginFromUri(uri, context)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (success) refreshTrigger++
        }
    }

    val allPlugins = remember(refreshTrigger) { PluginRegistry.getAllPlugins() }
    val enabledCount = remember(refreshTrigger) { PluginRegistry.getEnabledCount() }
    val totalCount = remember(refreshTrigger) { PluginRegistry.getTotalCount() }

    val filteredPlugins = remember(allPlugins, searchQuery) {
        if (searchQuery.isBlank()) allPlugins
        else allPlugins.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val groupedPlugins = remember(filteredPlugins) {
        filteredPlugins.groupBy { it.category }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Header Section matching SS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Plugins",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$enabledCount of $totalCount enabled",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp
                )
            }

            // Top Chip Button for Plugin Import matching SS
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1610))
                    .border(1.dp, Color(0xFF5E411B), CircleShape)
                    .clickable { importLauncher.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "Import Plugin",
                    tint = AppPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search Input matching SS
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search plugins...", color = Color(0xFF636366), fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF636366),
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppBorder,
                unfocusedBorderColor = AppDivider,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = AppField,
                unfocusedContainerColor = AppField
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2-Column Grid matching SS
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            groupedPlugins.forEach { (category, plugins) ->
                item(key = category.name) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Gold vertical bar indicator
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(AppPrimary, RoundedCornerShape(1.5.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.name.uppercase(),
                            color = Color(0xFF636366),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                val pluginPairs = plugins.chunked(2)
                items(pluginPairs) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (plugin in pair) {
                            val isEnabled = PluginRegistry.isPluginEnabled(plugin.id)
                            Box(modifier = Modifier.weight(1f)) {
                                PluginGridCard(
                                    plugin = plugin,
                                    isEnabled = isEnabled,
                                    onToggle = { checked ->
                                        if (checked) PluginRegistry.enablePlugin(plugin.id, context)
                                        else PluginRegistry.disablePlugin(plugin.id, context)
                                        refreshTrigger++
                                    },
                                    onClick = { selectedPluginForDetails = plugin }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog when card is clicked
    selectedPluginForDetails?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { selectedPluginForDetails = null },
            onConfigChanged = { refreshTrigger++ }
        )
    }
}

@Composable
fun PluginGridCard(
    plugin: DeepCodePlugin,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppField)
            .border(1.dp, AppDivider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge matching SS
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF261D12)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getPluginIcon(plugin.id),
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${plugin.version}",
                    color = Color(0xFF636366),
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppPrimary,
                    uncheckedTrackColor = AppDivider,
                    uncheckedThumbColor = Color(0xFF636366),
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.scale(0.82f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = plugin.description,
            color = Color(0xFF8E8E93),
            fontSize = 11.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

@Composable
fun PluginDetailDialog(
    plugin: DeepCodePlugin,
    onDismiss: () -> Unit,
    onConfigChanged: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getPluginIcon(plugin.id),
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(plugin.displayName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Version ${plugin.version}", color = Color(0xFF8E8E93), fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plugin.description, color = Color.LightGray, fontSize = 13.sp)

                HorizontalDivider(color = AppDivider)

                Text("Tools Provided (${plugin.getTools().size}):", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                for (tool in plugin.getTools()) {
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text("• ${tool.name}", color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(tool.description, color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                }

                val configFields = plugin.getConfigFields()
                if (configFields.isNotEmpty()) {
                    HorizontalDivider(color = AppDivider)
                    Text("Configuration:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    for (field in configFields) {
                        var valState by remember { mutableStateOf(PluginRegistry.getConfigValue(plugin.id, field.key, field.defaultValue)) }
                        OutlinedTextField(
                            value = valState,
                            onValueChange = {
                                valState = it
                                PluginRegistry.setConfigValue(plugin.id, field.key, it)
                                onConfigChanged()
                            },
                            label = { Text(field.label, color = Color(0xFF8E8E93)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppDivider,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = AppField,
                                unfocusedContainerColor = AppField
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppPrimary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = AppCard,
        shape = RoundedCornerShape(16.dp)
    )
}

fun getPluginIcon(pluginId: String): ImageVector = when (pluginId) {
    "qr_code" -> Icons.Default.GridOn
    "zip_tools" -> Icons.Default.FolderZip
    "hash_plugin" -> Icons.Default.Shield
    "base64_plugin" -> Icons.Default.Code
    "unit_converter" -> Icons.Default.Straighten
    "text_transform" -> Icons.Default.Title
    "color_palette" -> Icons.Default.Palette
    "calendar_export" -> Icons.Default.Event
    "contact_card" -> Icons.Default.Person
    "md_to_pdf" -> Icons.Default.PictureAsPdf
    "json_formatter" -> Icons.Default.DataObject
    "csv_plugin" -> Icons.Default.TableChart
    "gmail_connector" -> Icons.Default.Email
    "google_calendar_connector" -> Icons.Default.CalendarMonth
    "google_drive_connector" -> Icons.Default.Cloud
    else -> Icons.Default.Extension
}
