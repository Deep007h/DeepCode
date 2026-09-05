package ai.deepcode.android.ui.automations

import ai.deepcode.android.R
import ai.deepcode.android.ui.components.NeoBrutalistButton
import ai.deepcode.android.ui.theme.*
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AutomationSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFF59E0B) else Color(0xFF333640),
        label = "switchTrackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "switchThumbOffset"
    )
    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

fun isChatGptAutomation(rule: AutomationEntity): Boolean {
    return rule.category.equals("CHATGPT", ignoreCase = true) ||
           rule.templateId.contains("chatgpt", ignoreCase = true) ||
           rule.configJson.contains("chatgpt", ignoreCase = true) ||
           rule.name.contains("chatgpt", ignoreCase = true) ||
           rule.description.contains("chatgpt", ignoreCase = true) ||
           rule.getEffectiveActionPrompt()?.contains("chatgpt", ignoreCase = true) == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    onBack: () -> Unit,
    onOpenChat: ((sessionId: String, isChatGpt: Boolean, ruleName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AutomationsViewModel = viewModel { AutomationsViewModel(context) }
    val activeRules by viewModel.automations.collectAsStateWithLifecycle()

    var showAddRuleBottomSheet by remember { mutableStateOf(false) }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var ruleToDelete by remember { mutableStateOf<AutomationEntity?>(null) }

    // Bottom Sheet Fields
    var newRuleName by remember { mutableStateOf("") }
    var newRuleDesc by remember { mutableStateOf("") }
    var newRuleCategory by remember { mutableStateOf("MESSAGING") }
    var newRuleSchedulePreset by remember { mutableStateOf("Every hour") }
    var customCronInput by remember { mutableStateOf("0 * * * *") }
    var newRuleActionPrompt by remember { mutableStateOf("") }

    val categories = listOf("MESSAGING", "CONTENT", "CHATGPT", "SYSTEM", "DEVELOPER")
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
            .background(Color(0xFF0D0E12))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            .background(Color(0xFF1E2028))
                            .border(1.dp, Color(0xFF2E323D), CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Automations",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Automate your tasks and let AI work for you.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }
                }

                // + Add Rule Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF59E0B))
                        .clickable {
                            editingRuleId = null
                            newRuleName = ""
                            newRuleDesc = ""
                            newRuleCategory = "MESSAGING"
                            newRuleSchedulePreset = "Every hour"
                            customCronInput = "0 * * * *"
                            newRuleActionPrompt = ""
                            showAddRuleBottomSheet = true
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add Rule",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Section 1: ACTIVE AUTOMATION RULES + Count Badge
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "ACTIVE AUTOMATION RULES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B),
                    letterSpacing = 1.2.sp
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E2028))
                        .border(1.dp, Color(0xFF2E323D), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${activeRules.size}",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (activeRules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF12141A))
                        .border(1.dp, Color(0xFF242731), RoundedCornerShape(20.dp))
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E2028))
                                .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "No active rules yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )

                        Text(
                            text = "Automate your workflow by creating rules that trigger actions based on specific events.",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    editingRuleId = null
                                    newRuleName = ""
                                    newRuleDesc = ""
                                    newRuleCategory = "MESSAGING"
                                    newRuleSchedulePreset = "Every hour"
                                    customCronInput = "0 * * * *"
                                    newRuleActionPrompt = ""
                                    showAddRuleBottomSheet = true
                                }
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
                val isGpt = isChatGptAutomation(rule)
                val nextRunText = remember(rule.nextRunAt) {
                    val now = System.currentTimeMillis()
                    if (rule.nextRunAt > now) {
                        val diff = rule.nextRunAt - now
                        val m = diff / 60000L
                        val h = m / 60L
                        val remM = m % 60L
                        if (h > 0) "in ${h}h ${remM}m" else "in ${m}m"
                    } else if (rule.nextRunAt > 0L) {
                        "due now"
                    } else {
                        "due now"
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF12141A))
                        .border(1.dp, Color(0xFF242731), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        // Top Row: Icon + Name/Desc + Toggle Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Icon Box
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isGpt -> Color(0xFF0D3327)
                                                rule.name.contains("morning", ignoreCase = true) || rule.name.contains("weather", ignoreCase = true) || rule.name.contains("briefing", ignoreCase = true) -> Color(0xFF3B2A11)
                                                rule.category.equals("MESSAGING", ignoreCase = true) || rule.name.contains("responder", ignoreCase = true) -> Color(0xFF2C1D4D)
                                                rule.category.equals("EMAIL", ignoreCase = true) || rule.name.contains("email", ignoreCase = true) -> Color(0xFF132338)
                                                else -> Color(0xFF1E2028)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isGpt -> Color(0xFF155E3E)
                                                rule.name.contains("morning", ignoreCase = true) || rule.name.contains("weather", ignoreCase = true) || rule.name.contains("briefing", ignoreCase = true) -> Color(0xFF5A411B)
                                                rule.category.equals("MESSAGING", ignoreCase = true) || rule.name.contains("responder", ignoreCase = true) -> Color(0xFF452E75)
                                                rule.category.equals("EMAIL", ignoreCase = true) || rule.name.contains("email", ignoreCase = true) -> Color(0xFF1E3A5F)
                                                else -> Color(0xFF2E323D)
                                            },
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isGpt) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_chatgpt),
                                            contentDescription = "ChatGPT",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else if (rule.name.contains("morning", ignoreCase = true) || rule.name.contains("weather", ignoreCase = true) || rule.name.contains("briefing", ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else if (rule.category.equals("MESSAGING", ignoreCase = true) || rule.name.contains("responder", ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = Color(0xFFA78BFA),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else if (rule.category.equals("EMAIL", ignoreCase = true) || rule.name.contains("email", ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.SmartToy,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = rule.description.ifEmpty { "Automation task" },
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            AutomationSwitch(
                                checked = rule.isEnabled,
                                onCheckedChange = { viewModel.toggleAutomation(rule.id, it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Schedule Box Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0C0E13))
                                .border(1.dp, Color(0xFF1E212A), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Schedule: ",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                                Text(
                                    text = rule.cronExpression,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = " • ",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                                Text(
                                    text = "Next: ",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                                Text(
                                    text = nextRunText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Row: Last run & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Last run: ${if (rule.lastRunAt > 0L) java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rule.lastRunAt)) else "never"}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF6B7280),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val chatId = rule.getEffectiveChatSessionId().takeIf { !it.isNullOrBlank() } ?: rule.id
                                if (onOpenChat != null) {
                                    val isGpt = isChatGptAutomation(rule)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A1C23))
                                            .border(1.dp, if (isGpt) Color(0xFF10A37F).copy(alpha = 0.5f) else Color(0xFF2A2D38), RoundedCornerShape(8.dp))
                                            .clickable { onOpenChat(chatId, isGpt, rule.name) }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Chat,
                                                "Chat",
                                                tint = if (isGpt) Color(0xFF10A37F) else Color(0xFFF59E0B),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                "Chat",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isGpt) Color(0xFF10A37F) else Color(0xFFF59E0B)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A1C23))
                                        .border(1.dp, Color(0xFF2A2D38), RoundedCornerShape(8.dp))
                                        .clickable {
                                            editingRuleId = rule.id
                                            newRuleName = rule.name
                                            newRuleDesc = rule.description
                                            newRuleCategory = rule.category
                                            newRuleActionPrompt = rule.getEffectiveActionPrompt() ?: ""
                                            val matchedPreset = presets.firstOrNull { it.second == rule.cronExpression }
                                            if (matchedPreset != null) {
                                                newRuleSchedulePreset = matchedPreset.first
                                                customCronInput = rule.cronExpression
                                            } else {
                                                newRuleSchedulePreset = "Custom"
                                                customCronInput = rule.cronExpression
                                            }
                                            showAddRuleBottomSheet = true
                                        }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit", tint = Color.White, modifier = Modifier.size(11.dp))
                                        Text("Edit", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F2D20))
                                        .border(1.dp, Color(0xFF155E3E), RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.runAutomationNow(rule.id)
                                            Toast.makeText(context, "Running '${rule.name}'...", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, "Run Now", tint = Color(0xFF34D399), modifier = Modifier.size(11.dp))
                                        Text("Run Now", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                    }
                                }

                                // Delete button matching screenshot (square red icon box)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF321417))
                                        .border(1.dp, Color(0xFF591C22), RoundedCornerShape(8.dp))
                                        .clickable { ruleToDelete = rule },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: SUGGESTED TEMPLATES
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "SUGGESTED TEMPLATES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA78BFA),
                    letterSpacing = 1.2.sp
                )
            }
        }

        // Template 1: Forward to Email
        item {
            TemplateItemCard(
                title = "Forward to Email",
                subtitle = "Send chat history to your inbox weekly",
                icon = Icons.Default.Email,
                iconBgColor = Color(0xFF132338),
                iconColor = Color(0xFF38BDF8),
                onAdd = {
                    viewModel.addCustomRule(
                        name = "Forward to Email",
                        description = "Send chat history to your inbox weekly",
                        category = "EMAIL",
                        cron = "0 9 * * 1",
                        actionPrompt = "Forward summary of weekly chats to email"
                    )
                    Toast.makeText(context, "Added 'Forward to Email'", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    editingRuleId = null
                    newRuleName = "Forward to Email"
                    newRuleDesc = "Send chat history to your inbox weekly"
                    newRuleCategory = "EMAIL"
                    newRuleSchedulePreset = "Weekly Monday"
                    customCronInput = "0 9 * * 1"
                    newRuleActionPrompt = "Forward summary of weekly chats to email"
                    showAddRuleBottomSheet = true
                }
            )
        }

        // Template 2: ChatGPT Daily Assistant
        item {
            TemplateItemCard(
                title = "ChatGPT Daily Assistant",
                subtitle = "Generate daily briefs, content & documents with AI",
                iconDrawableRes = R.drawable.ic_chatgpt,
                iconBgColor = Color(0xFF0D3327),
                iconColor = Color(0xFF34D399),
                onAdd = {
                    viewModel.addCustomRule(
                        name = "ChatGPT Daily Assistant",
                        description = "Daily AI brief and task execution with ChatGPT",
                        category = "CHATGPT",
                        cron = "0 8 * * *",
                        actionPrompt = "Generate a daily morning briefing with ChatGPT for {{datetime}}. Report top tech news, weather, and schedule summary."
                    )
                    Toast.makeText(context, "Added 'ChatGPT Daily Assistant'", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    editingRuleId = null
                    newRuleName = "ChatGPT Daily Assistant"
                    newRuleDesc = "Daily AI brief and task execution with ChatGPT"
                    newRuleCategory = "CHATGPT"
                    newRuleSchedulePreset = "Daily 8AM"
                    customCronInput = "0 8 * * *"
                    newRuleActionPrompt = "Generate a daily morning briefing with ChatGPT for {{datetime}}. Report top tech news, weather, and schedule summary."
                    showAddRuleBottomSheet = true
                }
            )
        }

        // Template 3: Auto-Responder
        item {
            TemplateItemCard(
                title = "Auto-Responder",
                subtitle = "Quick reply to common inquiries",
                icon = Icons.Default.SmartToy,
                iconBgColor = Color(0xFF25183E),
                iconColor = Color(0xFFA78BFA),
                onAdd = {
                    viewModel.addCustomRule(
                        name = "Auto-Responder",
                        description = "Quick reply to common inquiries",
                        category = "MESSAGING",
                        cron = "0 * * * *",
                        actionPrompt = "Check pending inquiries and reply automatically"
                    )
                    Toast.makeText(context, "Added 'Auto-Responder'", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    editingRuleId = null
                    newRuleName = "Auto-Responder"
                    newRuleDesc = "Quick reply to common inquiries"
                    newRuleCategory = "MESSAGING"
                    newRuleSchedulePreset = "Every hour"
                    customCronInput = "0 * * * *"
                    newRuleActionPrompt = "Check pending inquiries and reply automatically"
                    showAddRuleBottomSheet = true
                }
            )
        }

        // Browse More Templates Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF12141A))
                    .border(1.dp, Color(0xFF242731), RoundedCornerShape(16.dp))
                    .clickable {
                        editingRuleId = null
                        newRuleName = ""
                        newRuleDesc = ""
                        newRuleCategory = "MESSAGING"
                        newRuleSchedulePreset = "Every hour"
                        customCronInput = "0 * * * *"
                        newRuleActionPrompt = ""
                        showAddRuleBottomSheet = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Browse More Templates",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showAddRuleBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddRuleBottomSheet = false
                editingRuleId = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF16181F),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (editingRuleId != null) "Edit Automation Task"
                           else if (newRuleName.isNotBlank()) "Customize Task: $newRuleName"
                           else "Add Automation Rule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = newRuleName,
                    onValueChange = { newRuleName = it },
                    label = { Text("Task Name") },
                    placeholder = { Text("e.g. Daily Standup Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = newRuleDesc,
                    onValueChange = { newRuleDesc = it },
                    label = { Text("Description") },
                    placeholder = { Text("Brief description of this automation") },
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
                        label = { Text("Schedule Preset") },
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
                        presets.forEach { (label, cronValue) ->
                            DropdownMenuItem(
                                text = { Text("$label  ($cronValue)", fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    newRuleSchedulePreset = label
                                    if (label != "Custom") {
                                        customCronInput = cronValue
                                    }
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }

                // Custom Cron input field (shown always if Custom is selected or editable)
                if (newRuleSchedulePreset == "Custom") {
                    OutlinedTextField(
                        value = customCronInput,
                        onValueChange = { customCronInput = it },
                        label = { Text("Cron Expression") },
                        placeholder = { Text("e.g. */15 * * * * or 0 9 * * 1-5") },
                        supportingText = { Text("Format: min hour dom month dow (e.g. */30 * * * *)", fontSize = 10.sp, color = Color(0xFF9CA3AF)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newRuleActionPrompt,
                        onValueChange = { newRuleActionPrompt = it },
                        label = { Text("Action Prompt") },
                        placeholder = { Text("Prompt given to the AI agent on every scheduled run...") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Dynamic parameter chips
                    Text(
                        text = "Dynamic parameters (tap to insert):",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val chips = listOf("{{datetime}}", "{{date}}", "{{time}}", "{{battery}}", "{{network}}")
                        chips.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2028))
                                    .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(6.dp))
                                    .clickable {
                                        newRuleActionPrompt = if (newRuleActionPrompt.isBlank()) tag else "$newRuleActionPrompt $tag"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(tag, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFF59E0B))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NeoBrutalistButton(
                        onClick = {
                            if (newRuleName.isBlank()) {
                                Toast.makeText(context, "Please enter a rule name", Toast.LENGTH_SHORT).show()
                                return@NeoBrutalistButton
                            }
                            val actionPrompt = newRuleActionPrompt.trim().ifEmpty {
                                newRuleDesc.trim().ifEmpty { newRuleName.trim() }
                            }
                            val description = newRuleDesc.trim().ifEmpty { "Automation task: ${newRuleName.trim()}" }
                            val cron = if (newRuleSchedulePreset == "Custom") {
                                customCronInput.trim().ifEmpty { "0 * * * *" }
                            } else {
                                presets.firstOrNull { it.first == newRuleSchedulePreset }?.second ?: "0 * * * *"
                            }

                            if (editingRuleId != null) {
                                viewModel.updateAutomation(
                                    id = editingRuleId!!,
                                    name = newRuleName.trim(),
                                    description = description,
                                    category = newRuleCategory,
                                    cron = cron,
                                    actionPrompt = actionPrompt
                                )
                                Toast.makeText(context, "Updated '${newRuleName.trim()}'", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addCustomRule(
                                    name = newRuleName.trim(),
                                    description = description,
                                    category = newRuleCategory,
                                    cron = cron,
                                    actionPrompt = actionPrompt
                                )
                                Toast.makeText(context, "Created '${newRuleName.trim()}'", Toast.LENGTH_SHORT).show()
                            }

                            // Clear inputs & dismiss
                            editingRuleId = null
                            newRuleName = ""
                            newRuleDesc = ""
                            newRuleActionPrompt = ""
                            showAddRuleBottomSheet = false
                        },
                        backgroundColor = Color(0xFFF59E0B),
                        borderColor = Color(0xFFF59E0B).copy(alpha = 0.5f),
                        shadowColor = Color.Transparent,
                        borderWidth = 1.dp,
                        shadowOffset = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (editingRuleId != null) "Update Task" else "Save Rule",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    NeoBrutalistButton(
                        onClick = {
                            showAddRuleBottomSheet = false
                            editingRuleId = null
                        },
                        backgroundColor = Color(0xFF1E2028),
                        borderColor = Color(0xFF2E323D),
                        shadowColor = Color.Transparent,
                        borderWidth = 1.dp,
                        shadowOffset = 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (editingRuleId != null) {
                    val editId = editingRuleId!!
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF321417))
                            .border(1.dp, Color(0xFF591C22), RoundedCornerShape(12.dp))
                            .clickable {
                                val target = activeRules.firstOrNull { it.id == editId }
                                showAddRuleBottomSheet = false
                                editingRuleId = null
                                if (target != null) {
                                    ruleToDelete = target
                                } else {
                                    viewModel.deleteAutomation(editId)
                                    Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                            Text("Delete This Task", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (ruleToDelete != null) {
        val r = ruleToDelete!!
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            containerColor = Color(0xFF16181F),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("Delete Automation Task", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text("Are you sure you want to delete \"${r.name}\"?\nThis scheduled task will be removed permanently.", color = Color(0xFF9CA3AF), fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAutomation(r.id)
                    ruleToDelete = null
                    Toast.makeText(context, "Deleted \"${r.name}\"", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Delete", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}

@Composable
private fun TemplateItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconDrawableRes: Int? = null,
    iconBgColor: Color,
    iconColor: Color,
    onAdd: () -> Unit,
    onEdit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12141A))
            .border(1.dp, Color(0xFF242731), RoundedCornerShape(16.dp))
            .clickable { onEdit() }
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
                    if (iconDrawableRes != null) {
                        Icon(
                            painter = painterResource(id = iconDrawableRes),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Edit / Customize button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1C23))
                        .border(1.dp, Color(0xFF2A2D38), RoundedCornerShape(8.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Template",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Edit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Quick Add button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2D20))
                        .border(1.dp, Color(0xFF155E3E), RoundedCornerShape(8.dp))
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Template",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
