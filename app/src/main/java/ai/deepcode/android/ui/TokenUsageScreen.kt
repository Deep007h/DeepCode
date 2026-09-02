package ai.deepcode.android.ui
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.local.TokenUsageEntity
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.data.repository.TokenUsageRepository
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageScreen(
    repository: DeepCodeRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lifetimeTotals by repository.tokenRepository.observeLifetimeTotals().collectAsStateWithLifecycle(null)
    val allSessions by repository.tokenRepository.observeAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentSessions = allSessions.take(20)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Token Usage", fontWeight = FontWeight.Bold, color = AppWhite)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = AppWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
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
                                Stat(label = "Total Cost", value = totals.formattedCost())
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
                        "Recent Sessions",
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
                Text(session.run { SessionTokenSummary(sessionId, modelId, providerName, tokensInput, tokensOutput, tokensReasoning, tokensCacheRead, tokensCacheWrite, costUsd, turnCount, timeCreated, timeUpdated).formattedCost() }, fontSize = 12.sp, color = AppSuccess)
            }
        }
    }
}

private fun SessionTokenSummary(sessionId: String, modelId: String, providerName: String, tokensInput: Long, tokensOutput: Long, tokensReasoning: Long, tokensCacheRead: Long, tokensCacheWrite: Long, costUsd: Double, turnCount: Int, timeCreated: Long, timeUpdated: Long) = ai.deepcode.android.data.local.SessionTokenSummary(sessionId, modelId, providerName, tokensInput, tokensOutput, tokensReasoning, tokensCacheRead, tokensCacheWrite, costUsd, turnCount, timeCreated, timeUpdated)

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppWhite)
        Text(label, fontSize = 11.sp, color = AppMuted)
    }
}

private fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> "$count"
}
