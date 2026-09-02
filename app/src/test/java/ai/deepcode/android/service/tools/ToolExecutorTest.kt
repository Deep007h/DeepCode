package ai.deepcode.android.service.tools

import org.junit.Test
import org.junit.Assert.*

class ToolExecutorTest {

    @Test
    fun testWebSearch() {
        val executor = ToolExecutor()
        val result = executor.executeTool(
            "web_search",
            "{\"query\":\"bitcoin price today\"}",
            "",
            false
        )
        println("=== SEARCH RESULT START ===")
        println(result)
        println("=== SEARCH RESULT END ===")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testDDGLiteSearch() {
        val executor = ToolExecutor()
        val result = executor.fetchDDGLite("bitcoin price today")
        println("=== DDG LITE SEARCH RESULT START ===")
        println(result)
        println("=== DDG LITE SEARCH RESULT END ===")
        if (result == null) {
            println("Warning: DDG Lite returned null. Skipping assert.")
        } else {
            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun testYahooSearch() {
        val executor = ToolExecutor()
        val result = executor.fetchYahoo("bitcoin price today")
        println("=== YAHOO SEARCH RESULT START ===")
        println(result)
        println("=== YAHOO SEARCH RESULT END ===")
        if (result == null) {
            println("Warning: Yahoo Search returned null (likely rate-limited or transient failure). Skipping assert.")
        } else {
            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun testVideoGeneration() {
        val apiKey = System.getenv("VEO_API_KEY") ?: ""
        if (apiKey.isEmpty()) {
            println("VEO_API_KEY not set. Skipping test.")
            return
        }
        System.setProperty("VEO_API_KEY", apiKey)
        val executor = ToolExecutor()
        val result = executor.executeTool(
            "generate_video",
            "{\"prompt\":\"A cinematic shot of a cute golden retriever puppy running in a field of sunflowers, 720p, high quality\"}",
            "",
            false
        )
        println("=== VIDEO GENERATION RESULT START ===")
        println(result)
        println("=== VIDEO GENERATION RESULT END ===")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // Should contain video marker or local path
        assertTrue(result.contains("video:"))
    }
}
