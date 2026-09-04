package ai.deepcode.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.orchestrator.*
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.delay

private val accentColor get() = AppPrimary
private val successColor = Color(0xFF10B981)
private val errorColor = Color(0xFFEF4444)
private val warningColor = Color(0xFFF59E0B)

private val agentActionVerbs = mapOf(
    "researcher" to "Research",
    "code_executor" to "Build",
    "tools_agent" to "Execute",
    "critic" to "Audit",
    "planner" to "Plan",
    "skill_creator" to "Create",
    "integrations_agent" to "Integrate",
    "crypto_agent" to "Trade",
    "markets_agent" to "Market",
    "archivist" to "Archive",
    "help" to "Help",
    "orchestrator" to "Orchestrate"
)

private fun getActionVerb(agentType: String): String {
    return agentActionVerbs[agentType] ?: "Explore"
}

private fun getStatusIcon(status: AgentStatus): @Composable () -> Unit {
    return when (status) {
        AgentStatus.SUMMONED -> {
            {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "Summoned",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AgentStatus.RUNNING -> {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
            }
        }
        AgentStatus.COMPLETE -> {
            {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Complete",
                    tint = successColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AgentStatus.FAILED -> {
            {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = errorColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AgentStatus.TRUNCATED -> {
            {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Partial",
                    tint = warningColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun getStatusColor(status: AgentStatus): Color {
    return when (status) {
        AgentStatus.SUMMONED -> accentColor
        AgentStatus.RUNNING -> accentColor
        AgentStatus.COMPLETE -> successColor
        AgentStatus.FAILED -> errorColor
        AgentStatus.TRUNCATED -> warningColor
    }
}

private fun getStatusText(status: AgentStatus): String {
    return when (status) {
        AgentStatus.SUMMONED -> "Summoned"
        AgentStatus.RUNNING -> "Running"
        AgentStatus.COMPLETE -> "Done"
        AgentStatus.FAILED -> "Failed"
        AgentStatus.TRUNCATED -> "Partial"
    }
}

@Composable
fun AgentCard(
    agent: SubAgentInstance,
    index: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(animationSpec = tween(300)) { it / 2 }
    ) {
        val statusColor = getStatusColor(agent.status)
        val isRunning = agent.status == AgentStatus.RUNNING
        // Hoisted out of the `if`: rememberInfiniteTransition must not be
        // called conditionally (status flips RUNNING->COMPLETE would change
        // hook order). Always create it, only drive alpha when running so
        // idle cards cost zero choreographer callbacks.
        val glowTransition = rememberInfiniteTransition(label = "agentGlow")
        val pulsedAlpha by glowTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )
        val glowAlpha: Float = if (isRunning) pulsedAlpha else 0f

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (isRunning) Color(0xFF1A1A1A) else Color(0xFF141414)
                )
                .then(
                    if (isRunning) {
                        Modifier.border(
                            1.dp,
                            accentColor.copy(alpha = glowAlpha),
                            RoundedCornerShape(24.dp)
                        )
                    } else if (agent.status == AgentStatus.FAILED) {
                        Modifier.border(1.dp, errorColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    } else {
                        Modifier.border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(24.dp))
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) accentColor.copy(alpha = 0.15f)
                        else getStatusColor(agent.status).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                getStatusIcon(agent.status)()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getActionVerb(agent.subTask.agentType),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = agent.subTask.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = getStatusText(agent.status),
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun AgentPanel(
    plan: TaskPlan,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = plan.agents.count {
        it.status == AgentStatus.COMPLETE || it.status == AgentStatus.TRUNCATED
    }
    val failedCount = plan.agents.count { it.status == AgentStatus.FAILED }
    val totalCount = plan.agents.size
    val isRunning = plan.overallStatus == OverallStatus.RUNNING

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0F10))
                .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(16.dp))
                .clickable { onToggle() }
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "Agents Working..." else "Orchestration Summary",
                            color = AppWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "$completedCount / $totalCount done",
                            color = if (isRunning) accentColor else successColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle",
                            tint = AppMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (failedCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$failedCount agent(s) failed",
                        color = errorColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / totalCount.toFloat().coerceAtLeast(1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = Color(0xFF2E2E2E)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (totalCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u27A1 Running in parallel",
                            color = accentColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(0.5.dp)
                                .background(accentColor.copy(alpha = 0.3f))
                        )
                    }
                }

                plan.agents.forEachIndexed { index, agent ->
                    AgentCard(
                        agent = agent,
                        index = index
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Main agent: available \u2713",
                    color = successColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
