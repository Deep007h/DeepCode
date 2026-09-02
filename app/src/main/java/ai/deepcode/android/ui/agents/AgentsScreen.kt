package ai.deepcode.android.ui.agents
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun AgentsScreen(
    onBack: () -> Unit,
    onAgentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AgentsViewModel = viewModel { AgentsViewModel(context) }
    val builtinAgents by viewModel.builtinAgents.collectAsStateWithLifecycle()
    val customAgents by viewModel.customAgents.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.seedBuiltinAgentsIfNeeded()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .gridBackground(gridColor = AppWhite.copy(alpha = 0.04f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "Agents",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                }
            }
        }

        item {
            Text(
                text = "Built-in Agents",
                style = MaterialTheme.typography.titleMedium,
                color = AppPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        if (builtinAgents.isEmpty()) {
            item {
                NeoBrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AppSurface,
                    borderColor = AppDarkGray.copy(alpha = 0.4f),
                    shadowColor = Color.Transparent,
                    borderWidth = 1.dp,
                    shadowOffset = 0.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No agents available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(builtinAgents, key = { it.agentId }) { agent ->
                AgentCard(
                    agent = agent,
                    onClick = { onAgentClick(agent.agentId) },
                    onToggle = { viewModel.toggleAgent(agent.agentId, it) }
                )
            }
        }

        if (customAgents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Custom Agents",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(customAgents, key = { it.agentId }) { agent ->
                AgentCard(
                    agent = agent,
                    onClick = { onAgentClick(agent.agentId) },
                    onToggle = { viewModel.toggleAgent(agent.agentId, it) }
                )
            }
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentEntity,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val tierColor = when (agent.agentTier) {
        "chat" -> AppPrimary
        "reasoning" -> Color(0xFFF59E0B)
        else -> AppSuccess
    }
    val tierLabel = when (agent.agentTier) {
        "chat" -> "CHAT"
        "reasoning" -> "REASON"
        else -> agent.agentTier.uppercase()
    }

    val agentIcon = when (agent.agentId) {
        "archivist" -> Icons.Default.Star
        "code_executor" -> Icons.Default.Terminal
        "critic" -> Icons.Default.Shield
        "crypto_agent" -> Icons.Default.MonetizationOn
        "help" -> Icons.Default.Chat
        "webbridge" -> Icons.Default.Language
        "document_creator" -> Icons.Default.Description
        else -> if (agent.isBuiltin) Icons.Default.Star else Icons.Default.Code
    }

    val agentIconColor = when (agent.agentId) {
        "archivist" -> AppSuccess
        "code_executor" -> AppIntegrationPurple
        "critic" -> Color(0xFF2EA6DA)
        "crypto_agent" -> AppPrimary
        "help" -> AppSuccess
        "webbridge" -> Color(0xFF10B981)
        "document_creator" -> Color(0xFF3F51B5)
        else -> tierColor
    }

    NeoBrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppSurface,
        borderColor = AppDarkGray.copy(alpha = 0.4f),
        shadowColor = Color.Transparent,
        borderWidth = 1.dp,
        shadowOffset = 0.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(agentIconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = agentIcon,
                            contentDescription = null,
                            tint = agentIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = agent.displayName,
                            fontWeight = FontWeight.Bold,
                            color = AppWhite,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = agent.description,
                            color = AppMuted,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                }
                AppToggle(
                    checked = agent.isEnabled,
                    onCheckedChange = onToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(tierColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tierLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = tierColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(AppWhite.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${agent.maxIterations} iters",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppMuted
                        )
                    }
                }
                Text(
                    text = "Runs: ${agent.runCount}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppMuted.copy(alpha = 0.7f)
                )
            }
        }
    }
}
