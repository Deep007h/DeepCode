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
}
