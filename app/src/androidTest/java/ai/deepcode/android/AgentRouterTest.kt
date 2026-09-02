package ai.deepcode.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.domain.model.Message
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AgentRouterTest {
    @Test
    fun testAgentRouterSendHi() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = EncryptedPrefs(appContext)
        
        val key = prefs.getApiKey("agentrouter")
        assertTrue("API key saved", key.isNotEmpty())
        assertTrue("Key starts with sk-", key.startsWith("sk-"))
        
        val provider = AIProviderFactory.providers.find { it.name == "Agent Router" }
        assertNotNull("Agent Router provider exists", provider)
        assertEquals("5 models", 5, provider!!.models.size)

        val msg = Message("1", "test", "user", "Say just OK and nothing else", System.currentTimeMillis())
        val result = StringBuilder()
        var error: Throwable? = null

        runBlocking {
            withTimeout(60000) {
                provider.streamCompletion(
                    messages = listOf(msg),
                    model = "gpt-5.5",
                    tools = null,
                    apiKey = key,
                    customBaseUrl = null,
                    onToken = { result.append(it) },
                    onToolCall = {},
                    onComplete = {},
                    onError = { error = it }
                )
            }
        }

        if (error != null) fail("Error: ${error.message}")
        assertTrue("Got response: ${result.take(100)}", result.isNotEmpty())
        println("RESPONSE: $result")
    }
}
