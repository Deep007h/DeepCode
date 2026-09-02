package ai.deepcode.android.ui

import ai.deepcode.android.data.repository.TokenUsageRepository
import ai.deepcode.android.data.local.TokenUsageEntity
import ai.deepcode.android.data.local.TurnTokenUsage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TokenUsageViewModel(
    private val repository: TokenUsageRepository
) : ViewModel() {

    val allSessions: StateFlow<List<TokenUsageEntity>> = repository
        .observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lifetimeCost = MutableStateFlow("$0.00")
    val lifetimeCost: StateFlow<String> = _lifetimeCost.asStateFlow()

    init {
        viewModelScope.launch {
            _lifetimeCost.value = repository.formatLifetimeCost()
        }
    }

    fun startSession(sessionId: String, modelId: String, providerName: String) {
        viewModelScope.launch {
            repository.startSession(sessionId, modelId, providerName)
        }
    }

    fun recordTurn(
        sessionId: String,
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        reasoningTokens: Int = 0,
        cacheReadTokens: Int = 0,
        cacheWriteTokens: Int = 0
    ) {
        viewModelScope.launch {
            repository.recordTurn(
                sessionId = sessionId,
                modelId = modelId,
                turnTokens = TurnTokenUsage(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheReadTokens = cacheReadTokens,
                    cacheWriteTokens = cacheWriteTokens
                )
            )
            _lifetimeCost.value = repository.formatLifetimeCost()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            _lifetimeCost.value = repository.formatLifetimeCost()
        }
    }

    class Factory(
        private val repository: TokenUsageRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TokenUsageViewModel(repository) as T
        }
    }
}
