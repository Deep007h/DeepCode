package ai.deepcode.android.data.local

data class ModelPricing(
    val promptPricePerMillion: Double,
    val completionPricePerMillion: Double,
    val contextLimit: Int,
    val outputLimit: Int = 16384,
    val supportsCaching: Boolean = false,
    val cacheWritePricePerMillion: Double? = null,
    val cacheReadPricePerMillion: Double? = null
)

object ModelPriceProvider {

    private val registry = mutableMapOf<String, ModelPricing>()

    init {
        // ── OpenAI ──────────────────────────────────────────────────────
        register("gpt-4o", ModelPricing(2.50, 10.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-4o-mini", ModelPricing(0.15, 0.60, 128_000, 16384, true, 0.25, 0.075))
        register("gpt-4o-2024-11-20", ModelPricing(2.50, 10.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-4-turbo", ModelPricing(10.00, 30.00, 128_000, 4096))
        register("gpt-4", ModelPricing(30.00, 60.00, 8192, 4096))
        register("gpt-3.5-turbo", ModelPricing(0.50, 1.50, 16385, 4096))
        register("o1", ModelPricing(15.00, 60.00, 200_000, 100_000))
        register("o1-mini", ModelPricing(1.10, 4.40, 128_000, 65536))
        register("o3-mini", ModelPricing(1.10, 4.40, 200_000, 100_000, true, 2.75, 0.55))

        // ── Anthropic ───────────────────────────────────────────────────
        register("claude-sonnet-4-20250514", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-sonnet-4", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-3.5-sonnet", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-3-opus", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-3-haiku", ModelPricing(0.25, 1.25, 200_000, 4096, true, 0.3125, 0.025))
        register("claude-3.5-haiku", ModelPricing(0.80, 4.00, 200_000, 8192, true, 1.00, 0.08))

        // ── Google Gemini ───────────────────────────────────────────────
        register("gemini-2.5-pro", ModelPricing(1.25, 5.00, 1_000_000, 8192))
        register("gemini-2.5-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-2.0-flash", ModelPricing(0.10, 0.40, 1_048_576, 8192))
        register("gemini-1.5-pro", ModelPricing(1.25, 5.00, 2_000_000, 8192))
        register("gemini-1.5-flash", ModelPricing(0.075, 0.30, 1_000_000, 8192))

        // ── DeepSeek ────────────────────────────────────────────────────
        register("deepseek-v4-flash-free", ModelPricing(0.0, 0.0, 1_000_000, 8192))
        register("deepseek-v3", ModelPricing(0.50, 2.00, 64_000, 8192))
        register("deepseek-r1", ModelPricing(0.50, 2.00, 64_000, 8192))
        register("deepseek-coder", ModelPricing(0.14, 0.28, 128_000, 8192))

        // ── OpenRouter ──────────────────────────────────────────────────
        register("openrouter/auto", ModelPricing(0.0, 0.0, 128_000, 8192))

        // ── Groq ────────────────────────────────────────────────────────
        register("llama-3.3-70b", ModelPricing(0.59, 0.79, 128_000, 8192))
        register("llama-3.1-8b", ModelPricing(0.05, 0.08, 128_000, 8192))
        register("mixtral-8x7b", ModelPricing(0.24, 0.24, 32768, 8192))

        // ── Cerebrus ────────────────────────────────────────────────────
        register("gpt-oss-120b", ModelPricing(0.0, 0.0, 128_000, 8192))
        register("gemma-4-31b", ModelPricing(0.0, 0.0, 128_000, 8192))
        register("zai-glm-4.7", ModelPricing(0.0, 0.0, 128_000, 8192))

        // ── Mistral ─────────────────────────────────────────────────────
        register("mistral-large", ModelPricing(2.00, 6.00, 128_000, 8192, true, 2.00, 1.00))
        register("mistral-small", ModelPricing(0.20, 0.60, 128_000, 8192, true, 0.20, 0.10))
        register("codestral", ModelPricing(1.00, 3.00, 256_000, 8192))

        // ── Anthropic via OAuth (Claude Pro/Max) ────────────────────────
        register("claude-pro", ModelPricing(0.0, 0.0, 200_000, 8192, true, 0.0, 0.0))
        register("claude-max", ModelPricing(0.0, 0.0, 200_000, 8192, true, 0.0, 0.0))
    }

    fun register(modelId: String, pricing: ModelPricing) {
        registry[modelId] = pricing
    }

    fun getPricing(modelId: String): ModelPricing? {
        return registry[modelId]
            ?: registry.entries.firstOrNull { modelId.startsWith(it.key) }?.value
            ?: registry[modelId.substringBefore("/")]
    }

    fun getContextLimit(modelId: String): Int {
        return getPricing(modelId)?.contextLimit ?: 128_000
    }

    fun getOutputLimit(modelId: String): Int {
        return getPricing(modelId)?.outputLimit ?: 16384
    }

    fun supportsCaching(modelId: String): Boolean {
        return getPricing(modelId)?.supportsCaching ?: false
    }

    fun calculatePromptCost(modelId: String, tokens: Int): Double {
        val pricing = getPricing(modelId) ?: return 0.0
        return (tokens.toDouble() / 1_000_000.0) * pricing.promptPricePerMillion
    }

    fun calculateCompletionCost(modelId: String, tokens: Int): Double {
        val pricing = getPricing(modelId) ?: return 0.0
        return (tokens.toDouble() / 1_000_000.0) * pricing.completionPricePerMillion
    }

    fun calculateTurnCost(
        modelId: String,
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        reasoningTokens: Int = 0,
        cacheReadTokens: Int = 0,
        cacheWriteTokens: Int = 0
    ): Double {
        val pricing = getPricing(modelId) ?: return 0.0

        var cost = 0.0
        cost += (inputTokens.toDouble() / 1_000_000.0) * pricing.promptPricePerMillion
        cost += (outputTokens.toDouble() / 1_000_000.0) * pricing.completionPricePerMillion
        cost += (reasoningTokens.toDouble() / 1_000_000.0) * pricing.completionPricePerMillion

        if (pricing.supportsCaching) {
            pricing.cacheReadPricePerMillion?.let { cost -= (cacheReadTokens.toDouble() / 1_000_000.0) * (pricing.promptPricePerMillion - it) }
            pricing.cacheWritePricePerMillion?.let { cost += (cacheWriteTokens.toDouble() / 1_000_000.0) * (it - pricing.promptPricePerMillion) }
        }

        return cost.coerceAtLeast(0.0)
    }

    val registeredModels: Set<String> get() = registry.keys
}

fun ModelPricing.formatPromptCost(tokens: Int): String {
    val cost = (tokens.toDouble() / 1_000_000.0) * promptPricePerMillion
    return if (cost < 0.001) "< $0.001" else "$%.4f".format(cost)
}

fun ModelPricing.formatCompletionCost(tokens: Int): String {
    val cost = (tokens.toDouble() / 1_000_000.0) * completionPricePerMillion
    return if (cost < 0.001) "< $0.001" else "$%.4f".format(cost)
}
