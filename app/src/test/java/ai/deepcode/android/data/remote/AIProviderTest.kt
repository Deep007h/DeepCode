package ai.deepcode.android.data.remote

import ai.deepcode.android.data.local.ModelPriceProvider
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.Tool
import com.google.gson.JsonParser
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProtocol
import com.jarves.mh.network.ProviderApiClient
import org.junit.Assert.*
import org.junit.Test

class AIProviderTest {

    @Test
    fun testModelPriceProviderRegistrations() {
        // Claude 3.7
        val sonnet37 = ModelPriceProvider.getPricing("claude-3-7-sonnet")
        assertEquals(200_000, sonnet37.contextLimit)
        assertEquals(64_000, sonnet37.outputLimit)
        assertEquals(3.00, sonnet37.promptPricePerMillion, 0.001)
        assertEquals(15.00, sonnet37.completionPricePerMillion, 0.001)
        assertTrue(sonnet37.supportsCaching)
        assertEquals(0.30, sonnet37.cacheReadPricePerMillion ?: 0.0, 0.001)

        // Gemini 2.5 Pro (2M context limit)
        val gemini25 = ModelPriceProvider.getPricing("gemini-2.5-pro")
        assertEquals(2_000_000, gemini25.contextLimit)
        assertEquals(8192, gemini25.outputLimit)

        // DeepSeek official IDs
        val deepSeekChat = ModelPriceProvider.getPricing("deepseek-chat")
        assertEquals(64_000, deepSeekChat.contextLimit)
        assertEquals(0.14, deepSeekChat.promptPricePerMillion, 0.001)
        assertEquals(0.28, deepSeekChat.completionPricePerMillion, 0.001)
        assertEquals(0.014, deepSeekChat.cacheReadPricePerMillion ?: 0.0, 0.0001)
        assertTrue(deepSeekChat.supportsCaching)

        val deepSeekReasoner = ModelPriceProvider.getPricing("deepseek-reasoner")
        assertEquals(64_000, deepSeekReasoner.contextLimit)
        assertEquals(0.55, deepSeekReasoner.promptPricePerMillion, 0.001)
        assertEquals(2.19, deepSeekReasoner.completionPricePerMillion, 0.001)
        assertEquals(0.14, deepSeekReasoner.cacheReadPricePerMillion ?: 0.0, 0.001)

        // Atria Dawn
        val atriaDawn = ModelPriceProvider.getPricing("atria-dawn-preview")
        assertEquals(256_000, atriaDawn.contextLimit)
        assertEquals(0.50, atriaDawn.promptPricePerMillion, 0.001)
        assertEquals(1.50, atriaDawn.completionPricePerMillion, 0.001)

        // Cerebras Qwen 2.5 Coder
        val qwenCoder = ModelPriceProvider.getPricing("qwen2.5-coder-32b")
        assertEquals(128_000, qwenCoder.contextLimit)
        assertEquals(0.20, qwenCoder.promptPricePerMillion, 0.001)
    }

    @Test
    fun testModelPriceCalculationWithCache() {
        val deepSeekCost = ModelPriceProvider.calculateTurnCost(
            modelId = "deepseek-chat",
            inputTokens = 100_000,
            outputTokens = 10_000,
            cacheReadTokens = 80_000
        )
        // input: 100K * $0.14 / 1M = $0.014
        // output: 10K * $0.28 / 1M = $0.0028
        // cache read discount: 80K * ($0.14 - $0.014) / 1M = 80K * $0.126 / 1M = $0.01008
        // net cost: 0.014 + 0.0028 - 0.01008 = 0.00672
        assertEquals(0.00672, deepSeekCost, 0.0001)
    }

    @Test
    fun testModelProviderResolution() {
        assertEquals("Atria", ModelPriceProvider.resolveProvider("atria-dawn-preview"))
        assertEquals("TokenHarbor", ModelPriceProvider.resolveProvider("tokenharbor-chat"))
        assertEquals("Cerebras", ModelPriceProvider.resolveProvider("cerebras/qwen2.5-coder-32b"))
        assertEquals("Groq", ModelPriceProvider.resolveProvider("groq/llama-3.3-70b-versatile"))
        assertEquals("Ollama Cloud", ModelPriceProvider.resolveProvider("ollama-cloud/qwen-2.5-coder"))
        assertEquals("Anthropic", ModelPriceProvider.resolveProvider("claude-3-7-sonnet"))
        assertEquals("Google Gemini", ModelPriceProvider.resolveProvider("gemini-2.5-pro"))
        assertEquals("DeepSeek", ModelPriceProvider.resolveProvider("deepseek-chat"))
    }

    @Test
    fun testProviderKindDefaults() {
        assertEquals("anthropic/claude-3.5-sonnet", ProviderKind.LLM_ROUTER.defaultModel)
        assertEquals("deepseek-chat", ProviderKind.DEEPSEEK.defaultModel)
        assertEquals("kimi-k3", ProviderKind.KIMI.defaultModel)
        assertEquals("mimo-v2.6-flash-free", ProviderKind.OPENCODE_ZEN.defaultModel)
    }

    @Test
    fun testProviderApiClientValidationBody() {
        val client = ProviderApiClient()

        // Strip ~ prefix
        val anthropicBody = client.validationBody("~anthropic/claude-3.5-sonnet", ProviderProtocol.ANTHROPIC)
        val anthropicJson = JsonParser.parseString(anthropicBody).asJsonObject
        assertEquals("anthropic/claude-3.5-sonnet", anthropicJson.get("model").asString)
        assertEquals(1, anthropicJson.get("max_tokens").asInt)

        // OpenAI o1/o3 reasoning models use max_completion_tokens
        val o1Body = client.validationBody("o1-mini", ProviderProtocol.OPENAI_CHAT)
        val o1Json = JsonParser.parseString(o1Body).asJsonObject
        assertEquals("o1-mini", o1Json.get("model").asString)
        assertTrue(o1Json.has("max_completion_tokens"))
        assertEquals(1, o1Json.get("max_completion_tokens").asInt)
        assertFalse(o1Json.has("max_tokens"))

        val o3Body = client.validationBody("o3-mini", ProviderProtocol.OPENAI_CHAT)
        val o3Json = JsonParser.parseString(o3Body).asJsonObject
        assertEquals("o3-mini", o3Json.get("model").asString)
        assertTrue(o3Json.has("max_completion_tokens"))
        assertEquals(1, o3Json.get("max_completion_tokens").asInt)
        assertFalse(o3Json.has("max_tokens"))

        // Standard OpenAI chat uses max_tokens
        val gpt4oBody = client.validationBody("gpt-4o", ProviderProtocol.OPENAI_CHAT)
        val gpt4oJson = JsonParser.parseString(gpt4oBody).asJsonObject
        assertEquals("gpt-4o", gpt4oJson.get("model").asString)
        assertTrue(gpt4oJson.has("max_tokens"))
        assertEquals(1, gpt4oJson.get("max_tokens").asInt)
    }

    @Test
    fun testAIProviderFactoryRegistry() {
        val providers = AIProviderFactory.providers
        assertTrue(providers.any { it.name.equals("Cerebras", ignoreCase = true) })
        assertTrue(providers.any { it.name.equals("OpenRouter", ignoreCase = true) })
        assertTrue(providers.any { it.name.equals("Zen AI", ignoreCase = true) })
        assertTrue(providers.any { it.name.equals("Atria", ignoreCase = true) })
        assertTrue(providers.any { it.name.equals("Mistral", ignoreCase = true) || it.name.equals("Mistral AI", ignoreCase = true) })
        assertTrue(providers.any { it.name.equals("Ollama Cloud", ignoreCase = true) })
    }

    @Test
    fun testShouldIncludeTools() {
        // Simple greetings should not include tools
        val greetingMessages = listOf(
            Message(id = "1", sessionId = "s1", role = "user", content = "hello", timestamp = System.currentTimeMillis())
        )
        val dummyTools = listOf(
            Tool(name = "read_file", description = "reads file", inputSchema = emptyMap())
        )
        assertFalse(shouldIncludeTools(greetingMessages, dummyTools))

        // Coding requests should include tools
        val codingMessages = listOf(
            Message(id = "2", sessionId = "s1", role = "user", content = "inspect build.gradle and fix dependencies", timestamp = System.currentTimeMillis())
        )
        assertTrue(shouldIncludeTools(codingMessages, dummyTools))
    }
}
