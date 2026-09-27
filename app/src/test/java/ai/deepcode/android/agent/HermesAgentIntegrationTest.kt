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
            assertEquals("0.4.2", com.jarves.mh.runtime.RuntimeInstaller.HERMES_VERSION)
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
            assertTrue("Must import ssl", content.contains("import ssl"))
            assertTrue("Must define get_resilient_ssl_context", content.contains("def get_resilient_ssl_context():"))
            assertTrue("Must define execute_http_request", content.contains("def execute_http_request("))
            assertTrue("Must include _create_unverified_context fallback", content.contains("_create_unverified_context"))
            assertTrue("Must handle certificate verification errors", content.contains("certificate") && content.contains("verify"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
