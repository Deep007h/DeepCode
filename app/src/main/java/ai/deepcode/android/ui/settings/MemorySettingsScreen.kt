package ai.deepcode.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.memory.MemoryChunk
import ai.deepcode.android.ui.components.AppCard
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySettingsScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val memoryManager = repository.memoryManager

    var isMemoryEnabled by remember { mutableStateOf(memoryManager.isMemoryEnabled()) }
    var isInAppEnabled by remember { mutableStateOf(memoryManager.isInAppMemoryEnabled()) }
    var isTelegramEnabled by remember { mutableStateOf(memoryManager.isTelegramMemoryEnabled()) }

    val allMemories by memoryManager.getAllMemoryChunksFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedSourceFilter by remember { mutableStateOf("all") } // "all", "inapp", "telegram", "manual"
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Filtered memories list
    val displayedMemories = remember(allMemories, searchQuery, selectedSourceFilter) {
        allMemories.filter { chunk ->
            val matchesSource = when (selectedSourceFilter) {
                "inapp" -> chunk.source.equals("inapp", ignoreCase = true) || chunk.source.equals("inapp_chat", ignoreCase = true)
                "telegram" -> chunk.source.equals("telegram", ignoreCase = true)
                "manual" -> chunk.source.equals("manual", ignoreCase = true) || chunk.source.equals("agent", ignoreCase = true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                chunk.title.contains(searchQuery, ignoreCase = true) ||
                chunk.content.contains(searchQuery, ignoreCase = true) ||
                chunk.tags.contains(searchQuery, ignoreCase = true)
            }
            matchesSource && matchesSearch
        }.sortedByDescending { it.updatedAt }
    }

    val inAppCount = remember(allMemories) {
        allMemories.count { it.source.equals("inapp", ignoreCase = true) || it.source.equals("inapp_chat", ignoreCase = true) }
    }
    val telegramCount = remember(allMemories) {
        allMemories.count { it.source.equals("telegram", ignoreCase = true) }
    }
    val manualCount = remember(allMemories) {
        allMemories.count { it.source.equals("manual", ignoreCase = true) || it.source.equals("agent", ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppScreenBg)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Cross-Chat Memory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = MaterialTheme.colorScheme.primary)
                }
                if (allMemories.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Master Controls Card
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Cross-Chat Long-Term Memory",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Shares ongoing projects, tasks, and preferences seamlessly between In-App and Telegram chats.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = isMemoryEnabled,
                                onCheckedChange = { checked ->
                                    isMemoryEnabled = checked
                                    memoryManager.setMemoryEnabled(checked)
                                }
                            )
                        }

                        if (isMemoryEnabled) {
                            HorizontalDivider(color = AppDivider, thickness = 0.5.dp)

                            // Granular toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "In-App Chat Memory",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = isInAppEnabled,
                                    onCheckedChange = { checked ->
                                        isInAppEnabled = checked
                                        memoryManager.setInAppMemoryEnabled(checked)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Telegram Chat Memory",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = isTelegramEnabled,
                                    onCheckedChange = { checked ->
                                        isTelegramEnabled = checked
                                        memoryManager.setTelegramMemoryEnabled(checked)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Stats Grid (2x2)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBox(
                            label = "Total Memories",
                            count = allMemories.size,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            label = "In-App Chat",
                            count = inAppCount,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBox(
                            label = "Telegram Bot",
                            count = telegramCount,
                            color = Color(0xFF29B6F6),
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            label = "Manual / Custom",
                            count = manualCount,
                            color = Color(0xFFFFA726),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search stored memories...", color = AppMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AppMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear search", tint = AppMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppWhite,
                        unfocusedTextColor = AppWhite,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppBorder,
                        focusedContainerColor = AppField,
                        unfocusedContainerColor = AppField,
                        cursorColor = AppPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Source Filter Chips (Horizontally scrollable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSourceFilter == "all",
                        onClick = { selectedSourceFilter = "all" },
                        label = { Text("All (${allMemories.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = AppPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedSourceFilter == "inapp",
                        onClick = { selectedSourceFilter = "inapp" },
                        label = { Text("In-App ($inAppCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF4CAF50)
                        )
                    )
                    FilterChip(
                        selected = selectedSourceFilter == "telegram",
                        onClick = { selectedSourceFilter = "telegram" },
                        label = { Text("Telegram ($telegramCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF29B6F6).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF29B6F6)
                        )
                    )
                    FilterChip(
                        selected = selectedSourceFilter == "manual",
                        onClick = { selectedSourceFilter = "manual" },
                        label = { Text("Manual ($manualCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFA726).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFFFA726)
                        )
                    )
                }
            }

            // Memory list header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Stored Knowledge Chunks (${displayedMemories.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Memory items or empty state
            if (displayedMemories.isEmpty()) {
                item {
                    AppCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                if (searchQuery.isNotEmpty()) "No matching memories found" else "No memories recorded yet",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (searchQuery.isNotEmpty()) "Try a different search term"
                                else "DeepCode automatically extracts key tasks, ongoing projects, and facts from your conversations on Telegram and In-App.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(displayedMemories, key = { it.id }) { memory ->
                    MemoryChunkCard(
                        memory = memory,
                        onDelete = {
                            coroutineScope.launch {
                                memoryManager.deleteMemoryChunk(memory.id)
                                Toast.makeText(context, "Memory deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content, tags, source ->
                coroutineScope.launch {
                    val newChunk = MemoryChunk(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        content = content,
                        source = source,
                        tags = tags,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        embeddingHint = tags
                    )
                    memoryManager.insertMemoryChunk(newChunk)
                    Toast.makeText(context, "Memory saved", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Memories?") },
            text = { Text("This will permanently delete all cross-chat long-term memories (${allMemories.size} items) across In-App and Telegram chats. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            memoryManager.clearAll()
                            Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                            showClearConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = color
            )
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryChunkCard(
    memory: MemoryChunk,
    onDelete: () -> Unit
) {
    val sourceColor = when (memory.source.lowercase()) {
        "telegram" -> Color(0xFF29B6F6)
        "inapp", "inapp_chat" -> Color(0xFF4CAF50)
        else -> Color(0xFFFFA726)
    }
    val sourceLabel = when (memory.source.lowercase()) {
        "telegram" -> "Telegram"
        "inapp", "inapp_chat" -> "In-App"
        else -> "Manual"
    }

    val ageMs = System.currentTimeMillis() - memory.updatedAt
    val timeStr = when {
        ageMs < 60_000L -> "just now"
        ageMs < 3_600_000L -> "${ageMs / 60_000L}m ago"
        ageMs < 86_400_000L -> "${ageMs / 3_600_000L}h ago"
        else -> "${ageMs / 86_400_000L}d ago"
    }

    val context = LocalContext.current

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(sourceColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            sourceLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = sourceColor
                        )
                    }
                    Text(
                        memory.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Memory", "${memory.title}\n${memory.content}")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                memory.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (memory.tags.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    memory.tags.split(",").take(4).forEach { tag ->
                        val cleanTag = tag.trim()
                        if (cleanTag.isNotEmpty()) {
                            Text(
                                "#$cleanTag",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, tags: String, source: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("manual") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Long-Term Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Memory Source", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("manual" to "Manual", "inapp" to "In-App", "telegram" to "Telegram").forEach { (srcKey, srcLabel) ->
                        val selected = source == srcKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AppPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    width = 1.dp,
                                    color = if (selected) AppPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { source = srcKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                srcLabel,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AppPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Topic / Title") },
                    placeholder = { Text("e.g. Current Project, Preference") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Content / Fact") },
                    placeholder = { Text("e.g. User is currently testing new AI providers...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    placeholder = { Text("project, testing, providers") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title.trim(), content.trim(), tags.trim(), source)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
