package ai.deepcode.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.deepcode.android.data.local.ModelPriceProvider
import ai.deepcode.android.data.local.TokenUsageEntity
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.theme.*

@Composable
fun TokenUsageScreen(
    repository: DeepCodeRepository,
    onClose: () -> Unit
) {
    // Trigger backfill so existing zero-cost database sessions are updated with listed model rates
    LaunchedEffect(Unit) {
        repository.syncAndBackfillTokenUsage()
    }

    val lifetimeTotals by repository.tokenRepository.observeLifetimeTotals().collectAsStateWithLifecycle(null)
    val allSessions by repository.tokenRepository.observeAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentSessions = allSessions.take(30)

    // Compute live total cost across all sessions, calculating from listed pricing if DB hasn't backfilled yet
    val calculatedTotalCost = remember(lifetimeTotals, allSessions) {
        val dbCost = lifetimeTotals?.totalCost ?: 0.0
        if (dbCost > 0.0) {
            dbCost
        } else {
            allSessions.sumOf { s ->
                if (s.costUsd > 0.0) s.costUsd
                else ModelPriceProvider.calculateTurnCost(
                    modelId = s.modelId,
                    inputTokens = s.tokensInput.toInt(),
                    outputTokens = s.tokensOutput.toInt(),
                    reasoningTokens = s.tokensReasoning.toInt(),
                    cacheReadTokens = s.tokensCacheRead.toInt(),
                    cacheWriteTokens = s.tokensCacheWrite.toInt()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppScreenBg)
    ) {
        // App bar header: exact 56.dp standard bar with zero extra top status bar gap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = AppWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Token Usage",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AppWhite
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
        ) {
            lifetimeTotals?.let { totals ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Lifetime Usage", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppWhite)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat(label = "Total Tokens", value = formatTokenCount(totals.totalTokens))
                                Stat(label = "Total Cost", value = formatCost(calculatedTotalCost))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat(label = "Sessions", value = "${totals.totalSessions}")
                                Stat(label = "Turns", value = "${totals.totalTurns}")
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = AppDarkGray.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            Text("Input: ${formatTokenCount(totals.totalInput)}", fontSize = 12.sp, color = AppMuted)
                            Text("Output: ${formatTokenCount(totals.totalOutput)}", fontSize = 12.sp, color = AppMuted)
                            if (totals.totalReasoning > 0) {
                                Text("Reasoning: ${formatTokenCount(totals.totalReasoning)}", fontSize = 12.sp, color = AppMuted)
                            }
                            if (totals.totalCacheRead > 0 || totals.totalCacheWrite > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("Cache Read: ${formatTokenCount(totals.totalCacheRead)}", fontSize = 12.sp, color = AppPrimary)
                                Text("Cache Write: ${formatTokenCount(totals.totalCacheWrite)}", fontSize = 12.sp, color = AppPrimary)
                            }
                        }
                    }
                }
            }

            if (recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Sessions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppWhite,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(recentSessions, key = { it.sessionId }) { session ->
                    SessionUsageCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionUsageCard(session: TokenUsageEntity) {
    val totalTokens = session.tokensInput + session.tokensOutput + session.tokensReasoning
    val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(session.timeCreated))

    // Calculate cost based on model's listed price if session has 0.0
    val sessionCost = if (session.costUsd > 0.0) session.costUsd else {
        ModelPriceProvider.calculateTurnCost(
            modelId = session.modelId,
            inputTokens = session.tokensInput.toInt(),
            outputTokens = session.tokensOutput.toInt(),
            reasoningTokens = session.tokensReasoning.toInt(),
            cacheReadTokens = session.tokensCacheRead.toInt(),
            cacheWriteTokens = session.tokensCacheWrite.toInt()
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.modelId, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppWhite)
                Text(date, fontSize = 11.sp, color = AppMuted)
            }
            Text(session.providerName, fontSize = 11.sp, color = AppPrimary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatTokenCount(totalTokens)} total", fontSize = 12.sp, color = AppWhite)
                Text("${session.turnCount} turns", fontSize = 12.sp, color = AppMuted)
                Text(formatCost(sessionCost), fontSize = 12.sp, color = AppSuccess, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppWhite)
        Text(label, fontSize = 11.sp, color = AppMuted)
    }
}

private fun formatCost(costUsd: Double): String = when {
    costUsd <= 0.0 -> "$0.00"
    costUsd < 0.001 -> "< $0.001"
    costUsd < 1.0 -> "$%.4f".format(costUsd)
    else -> "$%.2f".format(costUsd)
}

private fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> "$count"
}
