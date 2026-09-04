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

    // Default fallback pricing for any completely unknown model: $0.20/1M prompt, $0.60/1M completion
    private val defaultFallbackPricing = ModelPricing(0.20, 0.60, 128_000, 16384)

    init {
        // ── MiniMax ─────────────────────────────────────────────────────
        register("minimax-m3", ModelPricing(0.20, 1.10, 1_000_000, 16384))
        register("minimaxai/minimax-m3", ModelPricing(0.20, 1.10, 1_000_000, 16384))
        register("minimax-m2.7", ModelPricing(0.20, 1.00, 256_000, 16384))
        register("minimaxai/minimax-m2.7", ModelPricing(0.20, 1.00, 256_000, 16384))
        register("minimax-m2.5", ModelPricing(0.15, 0.80, 128_000, 16384))
        register("minimax-m2.1", ModelPricing(0.15, 0.80, 128_000, 16384))

        // ── OpenAI ──────────────────────────────────────────────────────
        register("gpt-4o", ModelPricing(2.50, 10.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-4o-mini", ModelPricing(0.15, 0.60, 128_000, 16384, true, 0.25, 0.075))
        register("gpt-4o-2024-11-20", ModelPricing(2.50, 10.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-5.4", ModelPricing(3.00, 12.00, 128_000, 16384, true, 3.75, 1.25))
        register("openai/gpt-5.4", ModelPricing(3.00, 12.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-5.5", ModelPricing(3.00, 12.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-5.6-sol", ModelPricing(3.00, 12.00, 128_000, 16384, true, 3.75, 1.25))
        register("gpt-4-turbo", ModelPricing(10.00, 30.00, 128_000, 4096))
        register("gpt-4", ModelPricing(30.00, 60.00, 8192, 4096))
        register("gpt-3.5-turbo", ModelPricing(0.50, 1.50, 16385, 4096))
        register("o1", ModelPricing(15.00, 60.00, 200_000, 100_000))
        register("o1-mini", ModelPricing(1.10, 4.40, 128_000, 65536))
        register("o3-mini", ModelPricing(1.10, 4.40, 200_000, 100_000, true, 2.75, 0.55))
        register("openai/gpt-oss-120b", ModelPricing(0.60, 1.20, 128_000, 16384))
        register("gpt-oss-120b", ModelPricing(0.60, 1.20, 128_000, 16384))
        register("openai/gpt-oss-20b", ModelPricing(0.10, 0.20, 128_000, 16384))
        register("gpt-oss-20b", ModelPricing(0.10, 0.20, 128_000, 16384))

        // ── Anthropic ───────────────────────────────────────────────────
        register("claude-sonnet-4-6", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-sonnet-5", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("anthropic/claude-sonnet-4.6", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-sonnet-4-20250514", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-sonnet-4", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-3.5-sonnet", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-fable-5", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-opus-5", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-opus-4-8", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-opus-4-7", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-opus-4-6", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-3-opus", ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50))
        register("claude-3.5-haiku", ModelPricing(0.80, 4.00, 200_000, 8192, true, 1.00, 0.08))
        register("claude-3-haiku", ModelPricing(0.25, 1.25, 200_000, 4096, true, 0.3125, 0.025))
        register("claude-pro", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))
        register("claude-max", ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30))

        // ── Google Gemini ───────────────────────────────────────────────
        register("gemini-3.8-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("google/gemini-3.8-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-3.7-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-3.6-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-3.5-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-2.5-pro", ModelPricing(1.25, 5.00, 1_000_000, 8192))
        register("gemini-2.5-flash", ModelPricing(0.10, 0.40, 1_000_000, 8192))
        register("gemini-2.0-flash", ModelPricing(0.10, 0.40, 1_048_576, 8192))
        register("gemini-2.0-flash-lite", ModelPricing(0.075, 0.30, 1_048_576, 8192))
        register("gemini-2.0-pro-exp-02-05", ModelPricing(1.25, 5.00, 2_000_000, 8192))
        register("gemini-1.5-pro", ModelPricing(1.25, 5.00, 2_000_000, 8192))
        register("gemini-1.5-flash", ModelPricing(0.075, 0.30, 1_000_000, 8192))

        // ── DeepSeek ────────────────────────────────────────────────────
        register("deepseek-v4-flash", ModelPricing(0.14, 0.28, 1_000_000, 8192))
        register("deepseek-v4-flash-free", ModelPricing(0.14, 0.28, 1_000_000, 8192))
        register("deepseek-ai/deepseek-v4-flash", ModelPricing(0.14, 0.28, 1_000_000, 8192))
        register("deepseek-v4-pro", ModelPricing(0.55, 2.19, 1_000_000, 8192))
        register("deepseek-v3", ModelPricing(0.14, 0.28, 64_000, 8192))
        register("deepseek-ai/deepseek-v3", ModelPricing(0.14, 0.28, 64_000, 8192))
        register("deepseek-r1", ModelPricing(0.55, 2.19, 64_000, 8192))
        register("deepseek-ai/deepseek-r1", ModelPricing(0.55, 2.19, 64_000, 8192))
        register("deepseek-coder", ModelPricing(0.14, 0.28, 128_000, 8192))
        register("deepseek-r1-distill-llama-70b", ModelPricing(0.55, 0.79, 128_000, 8192))

        // ── Groq / Meta Llama ───────────────────────────────────────────
        register("llama-3.3-70b", ModelPricing(0.59, 0.79, 128_000, 8192))
        register("llama-3.3-70b-versatile", ModelPricing(0.59, 0.79, 128_000, 8192))
        register("meta-llama/llama-3.3-70b-instruct", ModelPricing(0.59, 0.79, 128_000, 8192))
        register("llama-3.1-8b", ModelPricing(0.05, 0.08, 128_000, 8192))
        register("llama-3.1-8b-instant", ModelPricing(0.05, 0.08, 128_000, 8192))
        register("llama3.1-8b", ModelPricing(0.05, 0.08, 128_000, 8192))
        register("meta-llama/llama-3.1-8b-instruct", ModelPricing(0.05, 0.08, 128_000, 8192))
        register("nousresearch/hermes-3-llama-3.1-405b", ModelPricing(2.00, 3.00, 128_000, 8192))
        register("mixtral-8x7b", ModelPricing(0.24, 0.24, 32768, 8192))

        // ── Alibaba Qwen ────────────────────────────────────────────────
        register("qwen3.8-flash", ModelPricing(0.10, 0.30, 128_000, 8192))
        register("qwen/qwen3.8-flash", ModelPricing(0.10, 0.30, 128_000, 8192))
        register("qwen3.6-plus", ModelPricing(0.40, 1.20, 128_000, 8192))
        register("qwen/qwen3.6-27b", ModelPricing(0.20, 0.60, 128_000, 8192))
        register("qwen2.5-72b", ModelPricing(0.40, 1.20, 128_000, 8192))
        register("qwen/qwen2.5-72b-instruct", ModelPricing(0.40, 1.20, 128_000, 8192))
        register("qwen/qwen2.5-coder-32b-instruct", ModelPricing(0.20, 0.60, 128_000, 8192))

        // ── xAI Grok ────────────────────────────────────────────────────
        register("grok-4.6", ModelPricing(3.00, 15.00, 128_000, 8192))
        register("grok-2", ModelPricing(2.00, 10.00, 128_000, 8192))

        // ── Moonshot Kimi ───────────────────────────────────────────────
        register("kimi-k3", ModelPricing(0.50, 1.50, 128_000, 8192))
        register("moonshotai/kimi-k3", ModelPricing(0.50, 1.50, 128_000, 8192))

        // ── Zhipu GLM ───────────────────────────────────────────────────
        register("glm-5.2", ModelPricing(0.50, 1.00, 128_000, 8192))
        register("glm-5.3-flash", ModelPricing(0.20, 0.40, 128_000, 8192))
        register("zai-org/glm-5.3-flash", ModelPricing(0.20, 0.40, 128_000, 8192))
        register("zai-glm-4.7", ModelPricing(0.50, 1.00, 128_000, 8192))

        // ── NVIDIA Nemotron ─────────────────────────────────────────────
        register("nemotron-3-ultra-free", ModelPricing(0.20, 0.40, 128_000, 8192))
        register("nemotron-3.5-lightning-free", ModelPricing(0.20, 0.40, 128_000, 8192))
        register("nvidia/nvidia-nemotron-3.5-lightning-30b-a3b-bf16", ModelPricing(0.20, 0.40, 128_000, 8192))

        // ── Unique Zen / Contributor Models ─────────────────────────────
        register("mimo-v2.5-free", ModelPricing(0.15, 0.30, 128_000, 8192))
        register("mimo-v2.5", ModelPricing(0.15, 0.30, 128_000, 8192))
        register("muse-spark-1.2-contributor-free", ModelPricing(0.10, 0.20, 128_000, 8192))
        register("ling-3.0-flash-fin-free", ModelPricing(0.10, 0.20, 128_000, 8192))
        register("laguna-s-2.1-free", ModelPricing(0.10, 0.20, 128_000, 8192))

        // ── Google Gemma ────────────────────────────────────────────────
        register("gemma-4-31b", ModelPricing(0.10, 0.20, 128_000, 8192))
        register("gemma2-9b-it", ModelPricing(0.08, 0.16, 8192, 4096))
        register("google/gemma-2-9b-it", ModelPricing(0.08, 0.16, 8192, 4096))

        // ── Mistral ─────────────────────────────────────────────────────
        register("mistral-large", ModelPricing(2.00, 6.00, 128_000, 8192, true, 2.00, 1.00))
        register("mistral-small", ModelPricing(0.20, 0.60, 128_000, 8192, true, 0.20, 0.10))
        register("codestral", ModelPricing(1.00, 3.00, 256_000, 8192))
    }

    fun register(modelId: String, pricing: ModelPricing) {
        registry[modelId.lowercase().trim()] = pricing
    }

    fun getPricing(modelId: String): ModelPricing {
        val raw = modelId.trim()
        val lower = raw.lowercase()
        val stripped = lower.substringAfterLast("/").substringBefore(":").substringBefore("@")

        // 1. Direct registry lookups
        registry[lower]?.let { return it }
        registry[stripped]?.let { return it }

        // 2. Prefix / Contains registry keys
        registry.entries.firstOrNull { lower.startsWith(it.key) || lower.contains(it.key) }?.value?.let { return it }
        registry.entries.firstOrNull { stripped.startsWith(it.key) || stripped.contains(it.key) }?.value?.let { return it }

        // 3. Model family & capability tier matching
        return resolveFamilyPricing(lower, stripped)
    }

    private fun resolveFamilyPricing(lower: String, stripped: String): ModelPricing = when {
        lower.contains("minimax") || stripped.contains("minimax") -> {
            if (lower.contains("m2")) ModelPricing(0.15, 0.80, 128_000, 16384)
            else ModelPricing(0.20, 1.10, 1_000_000, 16384) // MiniMax M3 default
        }
        lower.contains("opus") || stripped.contains("opus") ->
            ModelPricing(15.00, 75.00, 200_000, 4096, true, 18.75, 1.50)
        lower.contains("haiku") || stripped.contains("haiku") ->
            ModelPricing(0.80, 4.00, 200_000, 8192, true, 1.00, 0.08)
        lower.contains("sonnet") || lower.contains("claude") || lower.contains("fable") ->
            ModelPricing(3.00, 15.00, 200_000, 8192, true, 3.75, 0.30)
        lower.contains("o1-mini") || lower.contains("o3-mini") ->
            ModelPricing(1.10, 4.40, 200_000, 100_000, true, 2.75, 0.55)
        lower.contains("o1") || lower.contains("o3") ->
            ModelPricing(15.00, 60.00, 200_000, 100_000)
        lower.contains("4o-mini") || lower.contains("oss-20b") ->
            ModelPricing(0.15, 0.60, 128_000, 16384, true, 0.25, 0.075)
        lower.contains("gpt-4") || lower.contains("gpt-5") || lower.contains("sol") ->
            ModelPricing(2.50, 10.00, 128_000, 16384, true, 3.75, 1.25)
        lower.contains("gemini") -> {
            if (lower.contains("pro")) ModelPricing(1.25, 5.00, 2_000_000, 8192)
            else ModelPricing(0.10, 0.40, 1_000_000, 8192)
        }
        lower.contains("deepseek") -> {
            if (lower.contains("r1") || lower.contains("pro")) ModelPricing(0.55, 2.19, 64_000, 8192)
            else ModelPricing(0.14, 0.28, 64_000, 8192)
        }
        lower.contains("llama") || lower.contains("hermes") -> {
            if (lower.contains("405b")) ModelPricing(2.00, 3.00, 128_000, 8192)
            else if (lower.contains("70b")) ModelPricing(0.59, 0.79, 128_000, 8192)
            else ModelPricing(0.05, 0.08, 128_000, 8192)
        }
        lower.contains("qwen") -> {
            if (lower.contains("72b") || lower.contains("plus") || lower.contains("max"))
                ModelPricing(0.40, 1.20, 128_000, 8192)
            else if (lower.contains("27b") || lower.contains("32b") || lower.contains("coder"))
                ModelPricing(0.20, 0.60, 128_000, 8192)
            else
                ModelPricing(0.10, 0.30, 128_000, 8192)
        }
        lower.contains("codestral") -> ModelPricing(1.00, 3.00, 256_000, 8192)
        lower.contains("mistral-large") -> ModelPricing(2.00, 6.00, 128_000, 8192, true, 2.00, 1.00)
        lower.contains("mistral") -> ModelPricing(0.20, 0.60, 128_000, 8192, true, 0.20, 0.10)
        lower.contains("grok") -> ModelPricing(3.00, 15.00, 128_000, 8192)
        lower.contains("kimi") -> ModelPricing(0.50, 1.50, 128_000, 8192)
        lower.contains("glm") -> ModelPricing(0.50, 1.00, 128_000, 8192)
        lower.contains("nemotron") -> ModelPricing(0.20, 0.40, 128_000, 8192)
        lower.contains("mimo") -> ModelPricing(0.15, 0.30, 128_000, 8192)
        lower.contains("muse") || lower.contains("ling") || lower.contains("laguna") ->
            ModelPricing(0.10, 0.20, 128_000, 8192)
        lower.contains("gemma") -> ModelPricing(0.10, 0.20, 128_000, 8192)
        lower.contains("mixtral") -> ModelPricing(0.24, 0.24, 32768, 8192)
        else -> defaultFallbackPricing
    }

    fun getContextLimit(modelId: String): Int {
        return getPricing(modelId).contextLimit
    }

    fun getOutputLimit(modelId: String): Int {
        return getPricing(modelId).outputLimit
    }

    fun supportsCaching(modelId: String): Boolean {
        return getPricing(modelId).supportsCaching
    }

    fun calculatePromptCost(modelId: String, tokens: Int): Double {
        val pricing = getPricing(modelId)
        return (tokens.toDouble() / 1_000_000.0) * pricing.promptPricePerMillion
    }

    fun calculateCompletionCost(modelId: String, tokens: Int): Double {
        val pricing = getPricing(modelId)
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
        val pricing = getPricing(modelId)

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

    fun resolveProvider(modelId: String): String {
        val lower = modelId.lowercase()
        return when {
            lower.contains("minimax") -> "MiniMax"
            lower.contains("claude") || lower.contains("anthropic") || lower.contains("fable") -> "Anthropic"
            lower.contains("gemini") || lower.contains("google") -> "Google Gemini"
            lower.contains("gpt") || lower.contains("o1") || lower.contains("o3") || lower.contains("openai") -> "OpenAI"
            lower.contains("deepseek") -> "DeepSeek"
            lower.contains("mistral") || lower.contains("codestral") -> "Mistral AI"
            lower.contains("llama") || lower.contains("mixtral") || lower.contains("hermes") -> "Meta Llama"
            lower.contains("qwen") -> "Alibaba Qwen"
            lower.contains("grok") || lower.contains("xai") -> "xAI Grok"
            lower.contains("kimi") || lower.contains("moonshot") -> "Moonshot AI"
            lower.contains("glm") || lower.contains("zai") -> "Zhipu AI"
            lower.contains("nemotron") || lower.contains("nvidia") -> "NVIDIA"
            lower.contains("openrouter") -> "OpenRouter"
            lower.contains("ollama") -> "Ollama (Local)"
            else -> "Zen AI"
        }
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
