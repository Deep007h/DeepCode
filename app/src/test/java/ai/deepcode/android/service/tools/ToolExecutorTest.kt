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

    @Test
    fun testAudioMetaReferenceDetection() {
        val executor = ToolExecutor()
        val metaInputs = listOf(
            "this",
            "of this",
            "for this",
            "audio of this",
            "create a audio file of this",
            "create an audio file of this",
            "make an audio of this",
            "make audio of this",
            "read this",
            "speak this",
            "audio of the poem",
            "read the poem",
            "convert that to audio",
            "last response",
            "previous message",
            "audio of what you wrote"
        )
        for (input in metaInputs) {
            assertTrue("Expected '$input' to be identified as a meta-reference", executor.isMetaReferenceText(input))
        }

        val nonMetaInputs = listOf(
            "The quick brown fox jumps over the lazy dog.",
            "Once upon a time in a faraway kingdom, there lived a wise king.",
            "Two roads diverged in a yellow wood,\nAnd sorry I could not travel both"
        )
        for (input in nonMetaInputs) {
            assertFalse("Expected '$input' NOT to be identified as a meta-reference", executor.isMetaReferenceText(input))
        }
    }

    @Test
    fun testCleanTextForSpeechPoemPreservation() {
        val executor = ToolExecutor()
        val poemInCodeFence = """
            Here is a poem for you:
            ```
            The sun sets low beyond the hill,
            The evening breeze is calm and still.
            A lonely star begins to glow,
            Upon the quiet earth below.
            ```
            Hope you enjoyed it!
        """.trimIndent()

        val cleaned = executor.cleanTextForSpeech(poemInCodeFence)
        println("=== CLEANED POEM FOR SPEECH ===")
        println(cleaned)
        // Ensure the poem stanzas are preserved in the text to be spoken
        assertTrue(cleaned.contains("The sun sets low beyond the hill"))
        assertTrue(cleaned.contains("The evening breeze is calm and still"))
        assertFalse(cleaned.contains("[code snippet]"))

        // Ensure actual programming code is safely replaced with code snippet
        val pythonCode = """
            Here is the python script:
            ```python
            def hello_world():
                print("Hello, world!")
            ```
        """.trimIndent()
        val cleanedCode = executor.cleanTextForSpeech(pythonCode)
        println("=== CLEANED CODE FOR SPEECH ===")
        println(cleanedCode)
        assertTrue(cleanedCode.contains("[code snippet]"))
        assertFalse(cleanedCode.contains("def hello_world"))
    }

    @Test
    fun testPlayMusicToolDeclared() {
        val executor = ToolExecutor()
        val tools = executor.getDeclaredTools()
        val playMusicTool = tools.find { it.name == "play_music" }
        assertNotNull("play_music tool must be declared in getDeclaredTools()", playMusicTool)
        assertTrue(playMusicTool!!.description.contains("YouTube Music"))
    }

    @Test
    fun testMusicDetectionHandlerRequiresReasoning() {
        val handler = ai.deepcode.android.service.music.MusicDetectionHandler()
        // Requests requiring reasoning (should NOT be bypassed)
        assertTrue(handler.requiresReasoning("play new karan aujla song in youtube music"))
        assertTrue(handler.requiresReasoning("play latest drake song"))
        assertTrue(handler.requiresReasoning("play trending punjabi songs"))
        assertTrue(handler.requiresReasoning("play recent hits by taylor swift"))
        assertTrue(handler.requiresReasoning("play best songs of weekend"))

        // Exact song titles with no descriptor keywords (can be direct)
        assertFalse(handler.requiresReasoning("play Tauba Tauba by Karan Aujla"))
        assertFalse(handler.requiresReasoning("play Shape of You"))
        assertFalse(handler.requiresReasoning("play Bohemian Rhapsody by Queen"))
    }
}


