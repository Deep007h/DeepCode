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
            "this message",
            "that message",
            "the message",
            "your message",
            "this poem",
            "that poem",
            "the poem",
            "your poem",
            "this reply",
            "that reply",
            "this response",
            "that response",
            "of this",
            "for this",
            "audio of this",
            "create audio of this message",
            "create audio of that message",
            "make audio of this message",
            "create a audio file of this",
            "create an audio file of this",
            "make an audio of this",
            "make audio of this",
            "read this",
            "read this message",
            "speak this",
            "speak this message",
            "audio of the poem",
            "read the poem",
            "convert that to audio",
            "convert this message to audio",
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

        // Test audio creation request detection
        val audioRequests = listOf(
            "create audio of this message",
            "make audio file of this",
            "read this aloud",
            "speak this",
            "convert this message to audio",
            "create an audio file of this"
        )
        for (req in audioRequests) {
            assertTrue("Expected '$req' to be identified as audio creation request", ToolExecutor.isAudioCreationRequest(req))
            assertTrue("Expected '$req' to be pure audio creation request", ToolExecutor.isPureAudioCreationRequest(req))
        }

        // Generative + audio requests should NOT be pure audio shortcuts
        val generativeRequests = listOf(
            "write a poem about rain and create audio of it",
            "compose a song and make audio",
            "explain quantum physics and read aloud"
        )
        for (gen in generativeRequests) {
            assertFalse("Expected '$gen' NOT to be pure audio creation request", ToolExecutor.isPureAudioCreationRequest(gen))
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

    @Test
    fun testCleanTextForSpeechWithReplyQuote() {
        val executor = ToolExecutor()
        val poemWithReply = """
            [reply author="DeepCode" id="101"]
            Two roads diverged in a yellow wood,
            And sorry I could not travel both
            And be one traveler, long I stood
            And looked down one as far as I could
            To where it bent in the undergrowth;
            [/reply]

            create audio of this message
        """.trimIndent()

        val cleaned = executor.cleanTextForSpeech(poemWithReply)
        println("=== CLEANED REPLIED POEM FOR SPEECH ===")
        println(cleaned)

        // The poem content must be preserved for speech!
        assertTrue(cleaned.contains("Two roads diverged in a yellow wood"))
        assertTrue(cleaned.contains("And sorry I could not travel both"))

        // The meta-instruction itself must NOT be spoken!
        assertFalse(cleaned.contains("create audio of this message"))
        assertFalse(cleaned.contains("[reply"))
        assertFalse(cleaned.contains("[/reply]"))
    }

    @Test
    fun testReplyWithLongPoemIsMetaReference() {
        val longPoemWithReply = """
            [reply author="DeepCode" id="102"]
            In the quiet twilight of a winter eve,
            When ancient pines in velvet shadows grieve,
            The starlight dances on the frosted lake,
            And silent dreams in slumbering forests wake.
            Through winding paths where silver whispers blow,
            Beneath the tapestry of fallen snow,
            A gentle stillness settles on the land,
            Held softly in the evening's peaceful hand.
            [/reply]

            create audio of this message
        """.trimIndent()

        assertTrue(ToolExecutor.isMetaReferenceText(longPoemWithReply))
        assertTrue(ToolExecutor.isAudioCreationRequest(longPoemWithReply))
        assertTrue(ToolExecutor.isPureAudioCreationRequest(longPoemWithReply))
    }

    @Test
    fun testEdgeWsSynthesizeLive() {
        val executor = ToolExecutor()
        val ssml = executor.buildEdgeSsml("Hello, this is a test of DeepCode neural audio synthesis.", "en-US-AriaNeural", "en-US", "+0", "+0", "")
        println("Generated SSML: $ssml")
        val audioBytes = executor.edgeWsSynthesize(ssml)
        println("Synthesized audio bytes: ${audioBytes?.size}")
        assertNotNull("Audio bytes must not be null from Edge TTS WebSocket", audioBytes)
        assertTrue("Audio bytes size must be > 1000 bytes", (audioBytes?.size ?: 0) > 1000)
    }
}


