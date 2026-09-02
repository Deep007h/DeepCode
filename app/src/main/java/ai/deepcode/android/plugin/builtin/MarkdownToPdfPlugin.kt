package ai.deepcode.android.plugin.builtin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream

class MarkdownToPdfPlugin : DeepCodePlugin {
    override val id = "md_to_pdf"
    override val displayName = "Markdown to PDF"
    override val description = "Convert Markdown to PDF document"
    override val version = "1.0.0"
    override val category = PluginCategory.DOCUMENT
    override val iconName = "picture_as_pdf"

    override fun getConfigFields() = listOf(
        PluginConfigField("default_page_size", "Default Page Size", ConfigFieldType.DROPDOWN, "A4", listOf("A4", "Letter", "Legal"))
    )

    override fun getTools(): List<Tool> = listOf(
        Tool("md_to_pdf_convert", "Convert markdown to pdf", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "markdown" to mapOf("type" to "string"),
                "filename" to mapOf("type" to "string"),
                "page_size" to mapOf("type" to "string", "enum" to listOf("A4", "Letter", "Legal"))
            ),
            "required" to listOf("markdown")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        var md = args.get("markdown").asString
        if (md.startsWith("/")) {
            val f = File(md)
            if (f.exists()) md = f.readText()
        }
        val filename = if (args.has("filename")) args.get("filename").asString else "document.pdf"
        val pageSize = if (args.has("page_size")) args.get("page_size").asString else "A4"

        val (pageW, pageH) = when (pageSize) {
            "Letter" -> 612 to 792
            "Legal" -> 612 to 1008
            else -> 595 to 842 // A4
        }
        val margin = 50f
        
        val doc = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = margin

        val paint = Paint()
        paint.color = Color.BLACK

        fun checkNewPage(needed: Float) {
            if (currentY + needed > pageH - margin) {
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                currentY = margin
            }
        }

        fun drawWrappedText(text: String, startX: Float, p: Paint, spacing: Float) {
            val words = text.split(" ")
            var line = ""
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(testLine) > pageW - margin - startX) {
                    checkNewPage(spacing)
                    canvas.drawText(line, startX, currentY, p)
                    currentY += spacing
                    line = word
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                checkNewPage(spacing)
                canvas.drawText(line, startX, currentY, p)
                currentY += spacing
            }
        }

        md.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    paint.textSize = 24f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentY += 10f
                    drawWrappedText(line.substring(2), margin, paint, 30f)
                }
                line.startsWith("## ") -> {
                    paint.textSize = 20f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentY += 10f
                    drawWrappedText(line.substring(3), margin, paint, 25f)
                }
                line.startsWith("### ") -> {
                    paint.textSize = 17f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentY += 10f
                    drawWrappedText(line.substring(4), margin, paint, 22f)
                }
                line.startsWith("---") -> {
                    checkNewPage(20f)
                    currentY += 10f
                    paint.strokeWidth = 1f
                    canvas.drawLine(margin, currentY, pageW - margin, currentY, paint)
                    currentY += 10f
                }
                line.startsWith("- ") -> {
                    paint.textSize = 12f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    drawWrappedText("• " + line.substring(2), margin + 15f, paint, 16f)
                }
                line.startsWith("> ") -> {
                    paint.textSize = 12f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    drawWrappedText(line.substring(2), margin + 20f, paint, 16f)
                }
                else -> {
                    paint.textSize = 12f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    if (line.isNotBlank()) {
                        drawWrappedText(line, margin, paint, 16f)
                    } else {
                        currentY += 8f
                    }
                }
            }
        }
        
        doc.finishPage(page)
        val outDir = File(context.filesDir, "plugins/md_to_pdf")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, if (filename.endsWith(".pdf")) filename else "$filename.pdf")
        doc.writeTo(FileOutputStream(outFile))
        doc.close()
        
        return "PDF saved to ${outFile.absolutePath}"
    }
}
