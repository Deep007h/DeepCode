package ai.deepcode.android.agent

import ai.deepcode.android.data.local.WORKFLOW_HERMES
import ai.deepcode.android.ui.chat.getDynamicThinkingSteps
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.providersForAgent
import com.jarves.mh.runtime.AgentRegistry
import com.jarves.mh.runtime.RuntimeBridge
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class HermesAgentIntegrationTest {

    @Test
    fun testHermesAgentKindDefinition() {
        val hermes = AgentKind.fromStored("hermes")
        assertEquals(AgentKind.HERMES, hermes)
        assertEquals("Hermes Agent", hermes.title)
        assertEquals("hermes", hermes.stableId)
        assertEquals("hermes", WORKFLOW_HERMES)
    }

    @Test
    fun testHermesProviders() {
        val providers = providersForAgent(AgentKind.HERMES)
        assertTrue("Hermes must support OpenRouter", providers.contains(ProviderKind.LLM_ROUTER))
        assertTrue("Hermes must support OpenCode Zen", providers.contains(ProviderKind.OPENCODE_ZEN))
        assertTrue("Hermes must support DeepSeek", providers.contains(ProviderKind.DEEPSEEK))
    }

    @Test
    fun testHermesDynamicThinkingSteps() {
        val steps = getDynamicThinkingSteps("🪽 Hermes Agent · PRoot")
        assertNotNull(steps)
        assertTrue("Should have multiple progressive steps", steps.size >= 5)
        assertTrue("Should mention Hermes or memory/skills", steps.any { it.contains("Hermes", ignoreCase = true) || it.contains("skills", ignoreCase = true) })
    }

    @Test
    fun testHermesAgentRegistry() {
        // Create mock bridge
        val dummyBridge = Proxy.newProxyInstance(
            RuntimeBridge::class.java.classLoader,
            arrayOf(RuntimeBridge::class.java)
        ) { _, _, _ -> null } as RuntimeBridge

        val registry = AgentRegistry.builtIns(
            claude = dummyBridge,
            deepSeek = dummyBridge,
            antigravity = dummyBridge,
            hermes = dummyBridge
        )

        val hermesDriver = registry.require(AgentKind.HERMES)
        assertNotNull(hermesDriver)
        assertEquals(AgentKind.HERMES, hermesDriver.kind)
    }

    @Test
    fun testHermesRunnerScriptIncludesResilientSslHandling() {
        val tempDir = java.nio.file.Files.createTempDirectory("hermes_test").toFile()
        try {
            val runnerFile = java.io.File(tempDir, "hermes_runner.py")
            assertEquals("0.5.1", com.jarves.mh.runtime.RuntimeInstaller.HERMES_VERSION)
            val installerClass = com.jarves.mh.runtime.RuntimeInstaller::class.java
            val method = installerClass.getDeclaredMethod("writeHermesRunnerScript", java.io.File::class.java)
            method.isAccessible = true

            val dummyContext = object : android.content.ContextWrapper(null) {
                override fun getFilesDir(): java.io.File = tempDir
                override fun getCacheDir(): java.io.File = tempDir
            }
            val installer = com.jarves.mh.runtime.RuntimeInstaller(dummyContext)
            method.invoke(installer, runnerFile)

            assertTrue("Runner file must be generated", runnerFile.exists())
            val content = runnerFile.readText()

            // SSL resilience (carried from v0.4.2)
            assertTrue("Must import ssl", content.contains("import ssl"))
            assertTrue("Must define get_resilient_ssl_context", content.contains("def get_resilient_ssl_context():"))
            assertTrue("Must define execute_http_request", content.contains("def execute_http_request("))
            assertTrue("Must include _create_unverified_context fallback", content.contains("_create_unverified_context"))
            assertTrue("Must handle certificate verification errors", content.contains("certificate") && content.contains("verify"))

            // Provider-aware routing (new in v0.5.0)
            assertTrue("Must read HERMES_PROVIDER env var", content.contains("HERMES_PROVIDER"))
            assertTrue("Must read HERMES_BASE_URL env var", content.contains("HERMES_BASE_URL"))
            assertTrue("Must define resolve_provider", content.contains("def resolve_provider():"))
            assertTrue("Must define build_url", content.contains("def build_url("))
            assertTrue("Must define build_headers", content.contains("def build_headers("))
            assertTrue("Must define build_payload", content.contains("def build_payload("))

            // OpenCode Zen wire protocol
            assertTrue("Must generate Zen session IDs", content.contains("def generate_session_id():"))
            assertTrue("Must generate Zen request IDs", content.contains("def generate_request_id():"))
            assertTrue("Must set x-opencode-client header", content.contains("x-opencode-client"))
            assertTrue("Must set x-opencode-project header", content.contains("x-opencode-project"))
            assertTrue("Must set x-opencode-session header", content.contains("x-opencode-session"))
            assertTrue("Must set x-session-affinity header", content.contains("x-session-affinity"))
            assertTrue("Must inject decoy bash tool", content.contains("\"name\": \"bash\""))
            assertTrue("Must inject decoy read tool", content.contains("\"name\": \"read\""))
            assertTrue("Must set tool_choice none", content.contains("tool_choice"))

            // Provider-specific handling
            assertTrue("Must support opencode_zen provider", content.contains("opencode_zen"))
            assertTrue("Must support openrouter provider", content.contains("openrouter"))
            assertTrue("Must support deepseek provider", content.contains("deepseek"))
            assertTrue("Must support anthropic provider", content.contains("anthropic"))
            assertTrue("Must define Anthropic stream parser", content.contains("def parse_anthropic_stream("))
            assertTrue("Must define OpenAI stream parser", content.contains("def parse_openai_stream("))

            // Default free model for Zen
            assertTrue("Must default to mimo-v2.5-free", content.contains("mimo-v2.5-free"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
