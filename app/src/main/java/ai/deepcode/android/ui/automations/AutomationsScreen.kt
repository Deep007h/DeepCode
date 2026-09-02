package ai.deepcode.android.ui.automations
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.ui.components.NeoBrutalistButton
import ai.deepcode.android.ui.components.NeoBrutalistCard
import ai.deepcode.android.ui.components.AppToggle
import ai.deepcode.android.ui.components.gridBackground
import ai.deepcode.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AutomationsViewModel = viewModel { AutomationsViewModel(context) }
    val activeRules by viewModel.automations.collectAsStateWithLifecycle()

    var showAddRuleBottomSheet by remember { mutableStateOf(false) }

    // Bottom Sheet Fields
    var newRuleName by remember { mutableStateOf("") }
    var newRuleDesc by remember { mutableStateOf("") }
    var newRuleCategory by remember { mutableStateOf("MESSAGING") }
    var newRuleSchedulePreset by remember { mutableStateOf("Every hour") }
    var newRuleActionPrompt by remember { mutableStateOf("") }

    val categories = listOf("MESSAGING", "CONTENT", "SYSTEM", "DEVELOPER")
    val presets = listOf(
        "Every hour" to "0 * * * *",
        "Every 6h" to "0 */6 * * *",
        "Daily 7AM" to "0 7 * * *",
        "Daily 8AM" to "0 8 * * *",
        "Weekly Monday" to "0 9 * * 1",
        "Custom" to "*/30 * * * *"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppSurface)
                            .border(1.dp, AppBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = AppWhite, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Automations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite,
                        fontSize = 20.sp
                    )
                }

                // Add Rule Gold Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(AppPrimary)
                        .clickable { showAddRuleBottomSheet = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add Rule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }
            }
        }

        // Section 1: ACTIVE AUTOMATION RULES
        item {
            Text(
                text = "ACTIVE AUTOMATION RULES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppPrimary,
                letterSpacing = 1.2.sp
            )
        }

        if (activeRules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppCard)
                        .border(1.dp, AppBorder, RoundedCornerShape(20.dp))
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Slashed flash icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppSurfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, AppBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = AppMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "No active rules yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppWhite
                        )

                        Text(
                            text = "Automate your workflow by creating rules that trigger actions based on specific events.",
                            fontSize = 12.sp,
                            color = AppMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(Modifier.height(2.dp))

                        // Create your first rule Green button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAddRuleBottomSheet = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Create your first rule",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            items(activeRules, key = { it.id }) { rule ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppCard)
                        .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(rule.description, color = AppMuted, fontSize = 12.sp)
                            }
                            AppToggle(
                                checked = rule.isEnabled,
                                onCheckedChange = { viewModel.toggleAutomation(rule.id, it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Schedule: ${rule.cronExpression} | Last run: ${if (rule.lastRunAt > 0L) java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rule.lastRunAt)) else "never"}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppMuted.copy(alpha = 0.7f)
                            )
                            IconButton(onClick = { viewModel.deleteAutomation(rule.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = AppDestructive.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section 2: SUGGESTED TEMPLATES
        item {
            Text(
                text = "SUGGESTED TEMPLATES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppMuted,
                letterSpacing = 1.2.sp
            )
        }

        // Template 1: Forward to Email
        item {
            TemplateItemCard(
                title = "Forward to Email",
                subtitle = "Send chat history to your inbox weekly",
                icon = Icons.Default.Email,
                iconBgColor = Color(0xFF1E293B),
                iconColor = Color(0xFF3B82F6),
                onAdd = {
                    viewModel.addCustomRule(
                        name = "Forward to Email",
                        description = "Send chat history to your inbox weekly",
                        category = "MESSAGING",
                        cron = "0 9 * * 1",
                        actionPrompt = "Forward summary of weekly chats to email"
                    )
                }
            )
        }

        // Template 2: Auto-Responder
        item {
            TemplateItemCard(
                title = "Auto-Responder",
                subtitle = "Quick reply to common inquiries",
                icon = Icons.Default.SmartToy,
                iconBgColor = Color(0xFF2E1065),
                iconColor = Color(0xFFA855F7),
                onAdd = {
                    viewModel.addCustomRule(
                        name = "Auto-Responder",
                        description = "Quick reply to common inquiries",
                        category = "MESSAGING",
                        cron = "0 * * * *",
                        actionPrompt = "Check pending inquiries and reply automatically"
                    )
                }
            )
        }

    }

    if (showAddRuleBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddRuleBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Automation Rule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )

                OutlinedTextField(
                    value = newRuleName,
                    onValueChange = { newRuleName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = newRuleDesc,
                    onValueChange = { newRuleDesc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newRuleCategory,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Select Category")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    newRuleCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Schedule Preset Dropdown
                var presetExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newRuleSchedulePreset,
                        onValueChange = {},
                        label = { Text("Schedule") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { presetExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Select Schedule")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        presets.forEach { (label, _) ->
                            DropdownMenuItem(
                                text = { Text(label, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    newRuleSchedulePreset = label
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newRuleActionPrompt,
                    onValueChange = { newRuleActionPrompt = it },
                    label = { Text("Action Prompt") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NeoBrutalistButton(
                        onClick = {
                            if (newRuleName.isNotBlank() && newRuleActionPrompt.isNotBlank()) {
                                val cron = presets.firstOrNull { it.first == newRuleSchedulePreset }?.second ?: "0 * * * *"
                                viewModel.addCustomRule(
                                    name = newRuleName.trim(),
                                    description = newRuleDesc.trim().ifEmpty { "Custom automation rule" },
                                    category = newRuleCategory,
                                    cron = cron,
                                    actionPrompt = newRuleActionPrompt.trim()
                                )
                                // Clear inputs & dismiss
                                newRuleName = ""
                                newRuleDesc = ""
                                newRuleActionPrompt = ""
                                showAddRuleBottomSheet = false
                            }
                        },
                        backgroundColor = AppPrimary,
                        borderColor = AppDarkGray.copy(alpha = 0.4f),
                        shadowColor = Color.Transparent,
                        borderWidth = 1.dp,
                        shadowOffset = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Rule", color = AppWhite, fontWeight = FontWeight.Bold)
                    }

                    NeoBrutalistButton(
                        onClick = { showAddRuleBottomSheet = false },
                        backgroundColor = AppSurface,
                        borderColor = AppDarkGray.copy(alpha = 0.4f),
                        shadowColor = Color.Transparent,
                        borderWidth = 1.dp,
                        shadowOffset = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = AppPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    onAdd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppCard)
            .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(title, fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = AppMuted, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppSurfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, AppBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Template",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
