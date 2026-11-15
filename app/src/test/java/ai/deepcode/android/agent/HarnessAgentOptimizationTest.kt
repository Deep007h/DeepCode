package ai.deepcode.android.agent

import com.jarves.mh.model.AgentKind
import com.jarves.mh.runtime.AgentCapability
import com.jarves.mh.runtime.AgentRegistry
import com.jarves.mh.runtime.RuntimeBridge
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class HarnessAgentOptimizationTest {

    @Test
    fun testAllHarnessAgentsRegistered() {
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

        for (kind in AgentKind.entries) {
            val driver = registry.require(kind)
            assertNotNull("Driver for $kind must be registered", driver)
            assertEquals(kind, driver.kind)
            assertTrue("Driver must support at least one capability", driver.capabilities.isNotEmpty())
            assertTrue("Driver must support resume", driver.capabilities.contains(AgentCapability.RESUME))
        }
    }

    @Test
    fun testDeltaAndThoughtFormatting() {
        val deltaLine = "[Delta] Streaming token slice"
        assertTrue(deltaLine.startsWith("[Delta] "))
        assertEquals("Streaming token slice", deltaLine.removePrefix("[Delta] "))

        val thoughtLine = "[Thought] Planning autonomous execution"
        assertTrue(thoughtLine.startsWith("[Thought]"))
        assertEquals("Planning autonomous execution", thoughtLine.removePrefix("[Thought]").trim())

        val toolLine = "[Tool: bash] ls -la"
        assertTrue(toolLine.startsWith("[Tool:"))
        assertEquals("bash", toolLine.substringAfter("Tool:").substringBefore("]").trim())
        assertEquals("ls -la", toolLine.substringAfter("]").trim())
    }

    @Test
    fun testOpencodeDriverExplicitRegistrationAndCapabilities() {
        val dummyBridge = Proxy.newProxyInstance(
            RuntimeBridge::class.java.classLoader,
            arrayOf(RuntimeBridge::class.java)
        ) { _, _, _ -> null } as RuntimeBridge

        val customOpencodeBridge = Proxy.newProxyInstance(
            RuntimeBridge::class.java.classLoader,
            arrayOf(RuntimeBridge::class.java)
        ) { _, _, _ -> null } as RuntimeBridge

        val registry = AgentRegistry.builtIns(
            claude = dummyBridge,
            deepSeek = dummyBridge,
            antigravity = dummyBridge,
            hermes = dummyBridge,
            opencode = customOpencodeBridge,
        )

        val opencodeDriver = registry.require(AgentKind.OPENCODE)
        assertNotNull(opencodeDriver)
        assertEquals(AgentKind.OPENCODE, opencodeDriver.kind)
        assertSame(customOpencodeBridge, opencodeDriver.runtime)
        assertTrue("OpenCode must support API_KEY", opencodeDriver.capabilities.contains(AgentCapability.API_KEY))
        assertTrue("OpenCode must support PROVIDER_PICKER", opencodeDriver.capabilities.contains(AgentCapability.PROVIDER_PICKER))
        assertTrue("OpenCode must support MODEL_PICKER", opencodeDriver.capabilities.contains(AgentCapability.MODEL_PICKER))
        assertTrue("OpenCode must support RESUME", opencodeDriver.capabilities.contains(AgentCapability.RESUME))
        assertTrue("OpenCode must support INTERACTIVE_APPROVALS", opencodeDriver.capabilities.contains(AgentCapability.INTERACTIVE_APPROVALS))
    }

    @Test
    fun testArchitectureCompatibilityCheck() {
        // Valid ARM64 platforms
        assertTrue(com.jarves.mh.runtime.supportsArm64Runtime(arrayOf("arm64-v8a", "armeabi-v7a"), "aarch64"))
        assertTrue(com.jarves.mh.runtime.supportsArm64Runtime(arrayOf("arm64-v8a"), "arm64"))

        // Incompatible architectures
        assertFalse(com.jarves.mh.runtime.supportsArm64Runtime(arrayOf("x86_64"), "x86_64"))
        assertFalse(com.jarves.mh.runtime.supportsArm64Runtime(arrayOf("armeabi-v7a"), "armv7l"))
        assertFalse(com.jarves.mh.runtime.supportsArm64Runtime(arrayOf("x86"), "i686"))
        assertFalse(com.jarves.mh.runtime.supportsArm64Runtime(emptyArray(), "aarch64"))
    }

    @Test
    fun testOpencodeRunnerScriptGenerationAndSslResilience() {
        val tempDir = java.nio.file.Files.createTempDirectory("opencode_test").toFile()
        try {
            val runnerFile = java.io.File(tempDir, "opencode_runner.py")
            val installerClass = com.jarves.mh.runtime.RuntimeInstaller::class.java
            val method = installerClass.getDeclaredMethod("writeOpencodeRunnerScript", java.io.File::class.java)
            method.isAccessible = true

            val dummyContext = object : android.content.ContextWrapper(null) {
                override fun getFilesDir(): java.io.File = tempDir
                override fun getCacheDir(): java.io.File = tempDir
            }
            val installer = com.jarves.mh.runtime.RuntimeInstaller(dummyContext)
            method.invoke(installer, runnerFile)

            assertTrue("OpenCode runner file must be generated", runnerFile.exists())
            val content = runnerFile.readText()

            // SSL resilience
            assertTrue("Must import ssl", content.contains("import ssl"))
            assertTrue("Must define get_resilient_ssl_context", content.contains("def get_resilient_ssl_context():"))
            assertTrue("Must define execute_http_request", content.contains("def execute_http_request("))
            assertTrue("Must include _create_unverified_context fallback", content.contains("_create_unverified_context"))

            // Tools and execution
            assertTrue("Must define OPENCODE_TOOLS", content.contains("OPENCODE_TOOLS = ["))
            assertTrue("Must support bash tool", content.contains("\"name\": \"bash\""))
            assertTrue("Must support read tool", content.contains("\"name\": \"read\""))
            assertTrue("Must support patch_file tool", content.contains("\"name\": \"patch_file\""))
            assertTrue("Must support write_file tool", content.contains("\"name\": \"write_file\""))

            // Streaming and events
            assertTrue("Must handle stream response", content.contains("delta") && content.contains("thought"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
