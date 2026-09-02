package ai.deepcode.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.deepcode.android.service.tools.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@RunWith(AndroidJUnit4::class)
class PdfEngineTest {

    @Test
    fun testPdfGenerationAndLayouts() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PdfLayoutEngine()

        val sampleContent = """
            This is a paragraph introducing the test document. It will be rendered under the specified layout styles to verify that all margins, borders, typography, headers, and footers render correctly.
            
            ## SECTION 1: DETAILED SPECS
            
            Here is a bullet list showing the features of our upgraded layout engine:
            - Beautiful, curated ColorSchemes including Dark Blue, Red/Orange, Classic Warm, and Minimal Gray.
            - Curated PageSizes including Letter, A4, and Legal.
            - Built-in margin styles and custom user margins.
            - Intelligent header styles: FULL_BANNER, ACCENT_LINE, SIMPLE, or NONE.
            - Intelligent footer styles: FULL_FOOTER, PAGE_NUMBER_WITH_LINE, PAGE_NUMBER, or NONE.
            - Automatic word-wrapping, multi-page layout rendering, and page-breaking logic.
            
            ## SECTION 2: COMPARATIVE MATRIX
            
            Below is a structured comparative layout table:
            | Layout Name | ID | Primary Color | Alignment | Style |
            | --- | --- | --- | --- | --- |
            | Classic | classic | #1A1A1A | Left | Editorial |
            | Corporate Report | corporate-report | #1B365D | Left | Formal Banner |
            | Academic Paper | academic-paper | #000000 | Two-Column | Academic |
            | Creative Portfolio | creative-portfolio | #1A1A2E | Left | Bold Vibrant |
            | Invoice / Receipt | invoice-receipt | #2C3E50 | Left | Table-Heavy |
            | Newsletter | newsletter | #2C3E50 | Two-Column | Masthead Banner |
            | Resume / CV | resume-cv | #2C3E50 | Left | Bullet Timeline |
            
            ## SECTION 3: CODE BLOCK TEST
            
            Here is a code block containing Kotlin sample code:
            ```
            fun main() {
                val layout = PdfLayoutEngine.findLayout("corporate-report")
                val path = PdfLayoutEngine().renderDocument(context, layout, docContent, "report")
                println("PDF generated at: ${'$'}path")
            }
            ```
            
            ## SECTION 4: BLOCKQUOTE TEST
            
            Let's verify that blockquotes are styled with an accent bar on the left:
            > "The detail is not the detail. The detail is the product."
            > — Charles Eames
            
            This concludes our layout engine verification test.
        """.trimIndent()

        // Create PDF files for multiple layouts to test
        val testLayouts = listOf("classic", "corporate-report", "academic-paper", "invoice-receipt", "newsletter", "resume-cv")

        for (layoutId in testLayouts) {
            val layout = PdfLayoutEngine.findLayout(layoutId) ?: PdfLayoutEngine.CLASSIC
            val filename = "test_pdf_${layoutId}"
            val result = engine.renderDocument(appContext, layout, DocumentContent(
                title = "Upgraded PDF Engine Verification - ${layout.name}",
                subtitle = "Verifying margins, colors, lists, tables, headers and footers for layout ID: ${layout.id}",
                author = "Antigravity Agent",
                date = "July 7, 2026",
                rawContent = sampleContent
            ), filename)

            println("Result for $layoutId: $result")

            // Copy to /sdcard/Download/ for easy ADB pull
            if (result.startsWith("[file:")) {
                val path = result.substringAfter("[file:").substringBefore("]")
                val sourceFile = File(path)
                if (sourceFile.exists()) {
                    val destDir = File("/sdcard/Download")
                    try {
                        if (destDir.exists() || destDir.mkdirs()) {
                            val destFile = File(destDir, "${filename}.pdf")
                            if (destFile.exists()) {
                                destFile.delete()
                            }
                            sourceFile.copyTo(destFile, overwrite = true)
                            println("Copied test PDF to /sdcard/Download/${filename}.pdf")
                        }
                    } catch (e: Exception) {
                        println("Failed to copy to Download directory (expected on modern Android without storage permissions): ${e.message}")
                    }
                }
            }
        }
    }

    @Test
    fun testAgentEngineExecution() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(appContext)
        val engine = ai.deepcode.android.agent.AgentEngine(appContext)

        val sessionId = "test_run_" + System.currentTimeMillis()
        val userPrompt = "Create a pdf of pyq of Java for ptu with answers solved in detail"

        // Setup a dummy user message first (simulating the chat launch)
        kotlinx.coroutines.runBlocking {
            db.messageDao().insertMessage(ai.deepcode.android.data.local.MessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = userPrompt,
                timestamp = System.currentTimeMillis(),
                isToolCall = false,
                toolCallsJson = null,
                toolResultsJson = null
            ))
        }

        println("Starting AgentEngine run for prompt: $userPrompt")
        val flow = engine.run(sessionId, userPrompt)
        
        val output = StringBuilder()
        kotlinx.coroutines.runBlocking {
            try {
                flow.collect { token ->
                    output.setLength(0)
                    output.append(token)
                }
            } catch (e: Exception) {
                println("\nAgent run failed with exception: ${e.message}")
                e.printStackTrace()
            }

            println("\n\n=== VERIFYING DATABASE CONVERSATION HISTORY ===")
            val messages = db.messageDao().getMessagesListForSession(sessionId)
            for (m in messages) {
                println("[${m.role}] content: ${m.content}")
                if (m.isToolCall || !m.toolCallsJson.isNullOrBlank()) {
                    println("  toolCallsJson: ${m.toolCallsJson}")
                }
                if (!m.toolResultsJson.isNullOrBlank()) {
                    println("  toolResultsJson: ${m.toolResultsJson}")
                }
            }
            println("==================================================")
        }
        
        // Write the run log to /sdcard/Download/agent_test_log.txt for pulling
        try {
            val logFile = File("/sdcard/Download/agent_test_log.txt")
            kotlinx.coroutines.runBlocking {
                logFile.writeText("=== PROMPT: $userPrompt ===\n\n=== RUN OUTPUT ===\n$output\n\n=== DB HISTORY ===\n" + 
                    db.messageDao().getMessagesListForSession(sessionId).joinToString("\n") { m ->
                        "[${m.role}] content: ${m.content}\n" + 
                        (m.toolCallsJson?.let { "  toolCallsJson: $it\n" } ?: "") + 
                        (m.toolResultsJson?.let { "  toolResultsJson: $it\n" } ?: "")
                    }
                )
            }
            println("Successfully wrote log to /sdcard/Download/agent_test_log.txt")
        } catch (e: Exception) {
            println("Failed to write log: ${e.message}")
        }
    }

    @Test
    fun pullAppLogs() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val logFile = File(appContext.filesDir, "logs/deepcode.jsonl")
        val destFile = File("/sdcard/Download/deepcode_logs.txt")
        if (logFile.exists()) {
            logFile.copyTo(destFile, overwrite = true)
            println("Successfully copied logs to ${destFile.absolutePath}")
        } else {
            println("Log file does not exist at ${logFile.absolutePath}")
        }
    }    @Test
    fun dumpDatabaseMessages() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(appContext)
        val outFile = File("/sdcard/Download/db_dump.txt")
        kotlinx.coroutines.runBlocking {
            val sessions = db.sessionDao().getAllSessions().first()
            val sb = java.lang.StringBuilder()
            sb.append("=== CHAT SESSIONS (Count: ${sessions.size}) ===\n")
            sessions.forEach { s ->
                sb.append("Session ID: ${s.id}, Title: ${s.title}, CreatedAt: ${s.createdAt}\n")
                val messages = db.messageDao().getMessagesListForSession(s.id)
                messages.forEach { m ->
                    sb.append("  [${m.role}] content: ${m.content.take(200)}\n")
                    if (m.toolCallsJson != null) sb.append("    toolCallsJson: ${m.toolCallsJson}\n")
                    if (m.toolResultsJson != null) sb.append("    toolResultsJson: ${m.toolResultsJson?.take(200)}\n")
                }
            }
            outFile.writeText(sb.toString())
            println("Dumped database to ${outFile.absolutePath}")
        }
    }
}
