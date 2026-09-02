package ai.deepcode.android.ui.agents
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.deepcode.android.ui.components.NeoBrutalistCard
import ai.deepcode.android.ui.components.AppToggle
import ai.deepcode.android.ui.components.gridBackground
import ai.deepcode.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AgentsViewModel = viewModel { AgentsViewModel(context) }
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val agent = agents.firstOrNull { it.agentId == agentId }

    if (agent == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Agent not found", color = AppMuted)
        }
        return
    }

    val tierColor = when (agent.agentTier) {
        "chat" -> AppPrimary
        "reasoning" -> Color(0xFFF59E0B)
        else -> AppSuccess
    }
    val tierLabel = when (agent.agentTier) {
        "chat" -> "CHAT"
        "reasoning" -> "REASONING"
        else -> agent.agentTier.uppercase()
    }

    val toolList = agent.tools.split(",").filter { it.isNotBlank() }
    val subagentList = agent.subagents.split(",").filter { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .gridBackground(gridColor = AppWhite.copy(alpha = 0.04f))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppSurface)
                        .border(1.dp, AppDarkGray.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppWhite, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = agent.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                    if (agent.isBuiltin) {
                        Text(
                            text = "Built-in Agent",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppMuted
                        )
                    }
                }
            }
            AppToggle(
                checked = agent.isEnabled,
                onCheckedChange = { viewModel.toggleAgent(agent.agentId, it) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Agent Info Card
            NeoBrutalistCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AppSurface,
                borderColor = AppDarkGray.copy(alpha = 0.4f),
                shadowColor = Color.Transparent,
                borderWidth = 1.dp,
                shadowOffset = 0.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoChip("Tier", tierLabel, tierColor)
                        InfoChip("Model", agent.modelHint.uppercase(), AppPrimary)
                        InfoChip("Max Iters", "${agent.maxIterations}", tierColor)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoChip("Sandbox", agent.sandboxMode.uppercase(), tierColor)
                        InfoChip("Runs", "${agent.runCount}", AppMuted)
                        InfoChip("Temp", "${agent.temperature}", AppMuted)
                    }
                }
            }

            // Description
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppWhite
            )
            Text(
                text = agent.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AppMuted
            )

            // System Prompt
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppWhite
            )
            NeoBrutalistCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AppSurface,
                borderColor = AppDarkGray.copy(alpha = 0.4f),
                shadowColor = Color.Transparent,
                borderWidth = 1.dp,
                shadowOffset = 0.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = agent.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AppMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Tools
            if (toolList.isNotEmpty()) {
                Text(
                    text = "Tools (${toolList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
                NeoBrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AppSurface,
                    borderColor = AppDarkGray.copy(alpha = 0.4f),
                    shadowColor = Color.Transparent,
                    borderWidth = 1.dp,
                    shadowOffset = 0.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        toolList.forEach { tool ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Build,
                                    null,
                                    tint = AppPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tool,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = AppWhite
                                )
                            }
                        }
                    }
                }
            }

            // Sub-agents
            if (subagentList.isNotEmpty()) {
                Text(
                    text = "Sub-agents (${subagentList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
                NeoBrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AppSurface,
                    borderColor = AppDarkGray.copy(alpha = 0.4f),
                    shadowColor = Color.Transparent,
                    borderWidth = 1.dp,
                    shadowOffset = 0.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        subagentList.forEach { sub ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Group,
                                    null,
                                    tint = AppSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sub,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = AppWhite
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = AppMuted,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}
