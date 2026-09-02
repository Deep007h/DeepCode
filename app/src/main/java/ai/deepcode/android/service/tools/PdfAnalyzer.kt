package ai.deepcode.android.service.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.gson.Gson
import java.io.File

// ════════════════════════════════════════════════
// Analysis Result Models
// ════════════════════════════════════════════════

data class DetectedMargins(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float
)

data class PdfAnalysisResult(
    val pageCount: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val detectedMargins: DetectedMargins,
    val detectedColors: List<String>,
    val estimatedColumnCount: Int,
    val hasHeader: Boolean,
    val hasFooter: Boolean,
    val textDensity: String,
    val suggestedLayoutId: String?,
    val summary: String
)

private data class PageAnalysis(
    val margins: DetectedMargins,
    val dominantColors: List<Int>,
    val columnCount: Int,
    val hasHeader: Boolean,
    val hasFooter: Boolean,
    val contentRatio: Float
)

// ════════════════════════════════════════════════
// PDF Analyzer
// ════════════════════════════════════════════════

class PdfAnalyzer(private val context: Context) {

    private val gson = Gson()

    /**
     * Main entry point: analyze a PDF file and return structured results.
     */
    fun analyzePdf(pdfPath: String): PdfAnalysisResult {
        val file = File(pdfPath)
        if (!file.exists() || !file.canRead()) {
            return errorResult("File not found or not readable: $pdfPath")
        }

        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)

        try {
            val pageCount = renderer.pageCount
            if (pageCount == 0) return errorResult("PDF has no pages")

            val analyses = mutableListOf<PageAnalysis>()
            var pdfWidth = 0
            var pdfHeight = 0

            // Analyze first 2 pages (or all if fewer)
            val pagesToAnalyze = minOf(pageCount, 2)
            for (i in 0 until pagesToAnalyze) {
                val page = renderer.openPage(i)
                pdfWidth = page.width
                pdfHeight = page.height

                // Render at ~150 DPI equivalent (scale up from 72 DPI base)
                val scale = 2.0f
                val bmpWidth = (page.width * scale).toInt()
                val bmpHeight = (page.height * scale).toInt()
                val bitmap = try {
                    Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    ai.deepcode.android.util.AppLogger.e("PdfAnalyzer", "OOM creating bitmap for page $i", e)
                    page.close()
                    continue
                }
                bitmap.eraseColor(Color.WHITE)

                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val analysis = analyzePageBitmap(bitmap, pdfWidth, pdfHeight)
                analyses.add(analysis)

                bitmap.recycle()
                page.close()
            }

            // Aggregate results across analyzed pages
            val avgMargins = DetectedMargins(
                top = analyses.map { it.margins.top }.average().toFloat(),
                bottom = analyses.map { it.margins.bottom }.average().toFloat(),
                left = analyses.map { it.margins.left }.average().toFloat(),
                right = analyses.map { it.margins.right }.average().toFloat()
            )
            val allColors = analyses.flatMap { it.dominantColors }.distinct()
            val hexColors = allColors.take(5).map { String.format("#%06X", 0xFFFFFF and it) }
            val avgColumnCount = analyses.map { it.columnCount }.maxOrNull() ?: 1
            val hasHeader = analyses.any { it.hasHeader }
            val hasFooter = analyses.any { it.hasFooter }
            val avgContentRatio = analyses.map { it.contentRatio }.average().toFloat()

            val textDensity = when {
                avgContentRatio < 0.15f -> "sparse"
                avgContentRatio < 0.45f -> "normal"
                else -> "dense"
            }

            val suggestedId = suggestLayout(avgColumnCount, hasHeader, hasFooter, textDensity, avgMargins)

            val summary = buildString {
                appendLine("PDF Analysis Results:")
                appendLine("• Pages: $pageCount (${pdfWidth}×${pdfHeight} pts)")
                appendLine("• Margins: top=${avgMargins.top.toInt()}pt, bottom=${avgMargins.bottom.toInt()}pt, left=${avgMargins.left.toInt()}pt, right=${avgMargins.right.toInt()}pt")
                appendLine("• Columns: $avgColumnCount")
                appendLine("• Header detected: $hasHeader")
                appendLine("• Footer detected: $hasFooter")
                appendLine("• Text density: $textDensity")
                appendLine("• Dominant colors: ${hexColors.joinToString(", ")}")
                appendLine("• Suggested layout: $suggestedId")
            }

            return PdfAnalysisResult(
                pageCount = pageCount,
                pageWidth = pdfWidth,
                pageHeight = pdfHeight,
                detectedMargins = avgMargins,
                detectedColors = hexColors,
                estimatedColumnCount = avgColumnCount,
                hasHeader = hasHeader,
                hasFooter = hasFooter,
                textDensity = textDensity,
                suggestedLayoutId = suggestedId,
                summary = summary
            )
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /**
     * Analyze a single rendered page bitmap to extract layout properties.
     */
    private fun analyzePageBitmap(bitmap: Bitmap, pdfWidth: Int, pdfHeight: Int): PageAnalysis {
        val w = bitmap.width
        val h = bitmap.height
        val scaleX = pdfWidth.toFloat() / w
        val scaleY = pdfHeight.toFloat() / h
        val threshold = 0.05f  // 5% non-white pixels to count as content

        // ── Margin Detection ──
        val topMarginPx = scanFromTop(bitmap, w, h, threshold)
        val bottomMarginPx = scanFromBottom(bitmap, w, h, threshold)
        val leftMarginPx = scanFromLeft(bitmap, w, h, threshold)
        val rightMarginPx = scanFromRight(bitmap, w, h, threshold)

        val margins = DetectedMargins(
            top = topMarginPx * scaleY,
            bottom = bottomMarginPx * scaleY,
            left = leftMarginPx * scaleX,
            right = rightMarginPx * scaleX
        )

        // ── Color Extraction ──
        val colors = extractDominantColors(bitmap, topMarginPx, bottomMarginPx, leftMarginPx, rightMarginPx)

        // ── Column Detection ──
        val columnCount = detectColumns(bitmap, w, h, topMarginPx, bottomMarginPx, leftMarginPx, rightMarginPx)

        // ── Header/Footer Detection ──
        val headerZone = h * 0.10
        val footerZone = h * 0.90
        val hasHeader = hasContentInZone(bitmap, w, 0, headerZone.toInt())
        val hasFooter = hasContentInZone(bitmap, w, footerZone.toInt(), h)

        // ── Content Density ──
        val contentRatio = calculateContentRatio(bitmap, topMarginPx, h - bottomMarginPx, leftMarginPx, w - rightMarginPx)

        return PageAnalysis(margins, colors, columnCount, hasHeader, hasFooter, contentRatio)
    }

    // ── Margin scanning helpers ──

    private fun scanFromTop(bitmap: Bitmap, w: Int, h: Int, threshold: Float): Int {
        val sampleStep = maxOf(1, w / 100)
        for (row in 0 until h) {
            var nonWhite = 0
            for (col in 0 until w step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(col, row))) nonWhite++
            }
            if (nonWhite.toFloat() / (w / sampleStep) > threshold) return row
        }
        return 0
    }

    private fun scanFromBottom(bitmap: Bitmap, w: Int, h: Int, threshold: Float): Int {
        val sampleStep = maxOf(1, w / 100)
        for (row in h - 1 downTo 0) {
            var nonWhite = 0
            for (col in 0 until w step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(col, row))) nonWhite++
            }
            if (nonWhite.toFloat() / (w / sampleStep) > threshold) return h - 1 - row
        }
        return 0
    }

    private fun scanFromLeft(bitmap: Bitmap, w: Int, h: Int, threshold: Float): Int {
        val sampleStep = maxOf(1, h / 100)
        for (col in 0 until w) {
            var nonWhite = 0
            for (row in 0 until h step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(col, row))) nonWhite++
            }
            if (nonWhite.toFloat() / (h / sampleStep) > threshold) return col
        }
        return 0
    }

    private fun scanFromRight(bitmap: Bitmap, w: Int, h: Int, threshold: Float): Int {
        val sampleStep = maxOf(1, h / 100)
        for (col in w - 1 downTo 0) {
            var nonWhite = 0
            for (row in 0 until h step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(col, row))) nonWhite++
            }
            if (nonWhite.toFloat() / (h / sampleStep) > threshold) return w - 1 - col
        }
        return 0
    }

    private fun isWhitePixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 240 && g > 240 && b > 240
    }

    // ── Color Extraction ──

    private fun extractDominantColors(bitmap: Bitmap, top: Int, bottom: Int, left: Int, right: Int): List<Int> {
        val colorCounts = mutableMapOf<Int, Int>()
        val sampleStep = maxOf(3, bitmap.width / 80)
        val contentTop = top.coerceAtLeast(0)
        val contentBottom = (bitmap.height - bottom).coerceAtMost(bitmap.height)
        val contentLeft = left.coerceAtLeast(0)
        val contentRight = (bitmap.width - right).coerceAtMost(bitmap.width)

        for (y in contentTop until contentBottom step sampleStep) {
            for (x in contentLeft until contentRight step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Skip white and near-white pixels
                if (r > 240 && g > 240 && b > 240) continue
                // Quantize to 32-bin per channel for grouping
                val qr = (r / 32) * 32
                val qg = (g / 32) * 32
                val qb = (b / 32) * 32
                val quantized = Color.rgb(qr, qg, qb)
                colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
            }
        }

        return colorCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    // ── Column Detection ──

    private fun detectColumns(bitmap: Bitmap, w: Int, h: Int, top: Int, bottom: Int, left: Int, right: Int): Int {
        val contentLeft = left.coerceAtLeast(0)
        val contentRight = (w - right).coerceAtMost(w)
        val contentTop = (top + h * 0.2).toInt()  // Skip header area
        val contentBottom = (h - bottom - h * 0.1).toInt()  // Skip footer area
        if (contentBottom <= contentTop || contentRight <= contentLeft) return 1

        val contentWidth = contentRight - contentLeft
        val minGapWidth = (contentWidth * 0.05).toInt()  // 5% of content width = gap
        val stripWidth = maxOf(2, contentWidth / 100)

        // Count content density per vertical strip
        val stripDensities = mutableListOf<Float>()
        var x = contentLeft
        while (x < contentRight) {
            var nonWhite = 0
            var total = 0
            for (y in contentTop until contentBottom step 3) {
                for (sx in x until minOf(x + stripWidth, contentRight)) {
                    if (!isWhitePixel(bitmap.getPixel(sx, y))) nonWhite++
                    total++
                }
            }
            stripDensities.add(if (total > 0) nonWhite.toFloat() / total else 0f)
            x += stripWidth
        }

        // Find gaps (strips with very low density surrounded by higher density)
        var gapCount = 0
        var inGap = false
        var gapWidth = 0
        for (i in stripDensities.indices) {
            if (stripDensities[i] < 0.02f) {
                gapWidth++
                if (!inGap && gapWidth * stripWidth > minGapWidth) {
                    inGap = true
                    gapCount++
                }
            } else {
                inGap = false
                gapWidth = 0
            }
        }

        return (gapCount + 1).coerceIn(1, 3)
    }

    // ── Zone Content Detection ──

    private fun hasContentInZone(bitmap: Bitmap, w: Int, startRow: Int, endRow: Int): Boolean {
        val sampleStep = maxOf(2, w / 50)
        var nonWhite = 0
        var total = 0
        for (y in startRow until endRow step 3) {
            for (x in 0 until w step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) nonWhite++
                total++
            }
        }
        return total > 0 && (nonWhite.toFloat() / total) > 0.02f
    }

    // ── Content Ratio ──

    private fun calculateContentRatio(bitmap: Bitmap, top: Int, bottom: Int, left: Int, right: Int): Float {
        val sampleStep = maxOf(3, bitmap.width / 60)
        val cTop = top.coerceAtLeast(0)
        val cBottom = bottom.coerceAtMost(bitmap.height)
        val cLeft = left.coerceAtLeast(0)
        val cRight = right.coerceAtMost(bitmap.width)
        if (cBottom <= cTop || cRight <= cLeft) return 0f

        var nonWhite = 0
        var total = 0
        for (y in cTop until cBottom step sampleStep) {
            for (x in cLeft until cRight step sampleStep) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) nonWhite++
                total++
            }
        }
        return if (total > 0) nonWhite.toFloat() / total else 0f
    }

    // ── Layout Suggestion ──

    private fun suggestLayout(
        columnCount: Int, hasHeader: Boolean, hasFooter: Boolean,
        textDensity: String, margins: DetectedMargins
    ): String {
        return when {
            columnCount >= 2 && hasHeader -> "newsletter"
            columnCount >= 2 -> "academic-paper"
            textDensity == "dense" && hasHeader && hasFooter -> "corporate-report"
            textDensity == "sparse" && margins.top > 65f -> "modern-minimal"
            textDensity == "dense" -> "invoice-receipt"
            hasHeader && hasFooter -> "corporate-report"
            margins.left < 50f && margins.right < 50f -> "resume-cv"
            else -> "classic"
        }
    }

    /**
     * Render a single page of a PDF as a bitmap for preview.
     */
    fun renderPagePreview(pdfPath: String, pageIndex: Int, maxWidth: Int): Bitmap? {
        val file = File(pdfPath)
        if (!file.exists()) return null

        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        try {
            if (pageIndex >= renderer.pageCount) return null
            val page = renderer.openPage(pageIndex)
            val scale = maxWidth.toFloat() / page.width
            val bmpWidth = maxWidth
            val bmpHeight = (page.height * scale).toInt()
            val bitmap = try {
                Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                ai.deepcode.android.util.AppLogger.e("PdfAnalyzer", "OOM creating page bitmap", e)
                page.close()
                return null
            }
            bitmap.eraseColor(Color.WHITE)
            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /**
     * Generate a PdfLayout that tries to match the analyzed PDF's style.
     */
    fun generateMatchingLayout(result: PdfAnalysisResult): PdfLayout {
        val colors = result.detectedColors.map { Color.parseColor(it) }
        val primary = colors.getOrElse(0) { Color.parseColor("#1A1A1A") }
        val secondary = colors.getOrElse(1) { Color.parseColor("#555555") }
        val accent = colors.getOrElse(2) { Color.parseColor("#C1622D") }

        val bodyStyle = when {
            result.estimatedColumnCount >= 2 -> BodyStyle.TWO_COLUMN
            result.textDensity == "dense" -> BodyStyle.REPORT
            result.textDensity == "sparse" -> BodyStyle.PROSE
            else -> BodyStyle.PROSE
        }
        val headerStyle = if (result.hasHeader) HeaderStyle.ACCENT_LINE else HeaderStyle.NONE
        val footerStyle = if (result.hasFooter) FooterStyle.PAGE_NUMBER_WITH_LINE else FooterStyle.NONE

        val pageSize = when {
            result.pageWidth in 590..600 && result.pageHeight in 838..846 -> PageSize.A4
            result.pageWidth in 608..616 && result.pageHeight in 1004..1012 -> PageSize.LEGAL
            else -> PageSize.LETTER
        }

        return PdfLayout(
            id = "analyzed-${System.currentTimeMillis()}",
            name = "Analyzed Layout",
            description = "Layout extracted from PDF analysis",
            pageSize = pageSize,
            margins = Margins(
                top = result.detectedMargins.top,
                bottom = result.detectedMargins.bottom,
                left = result.detectedMargins.left,
                right = result.detectedMargins.right
            ),
            colorScheme = ColorScheme(
                primary = primary,
                secondary = secondary,
                accent = accent,
                textColor = primary,
                mutedColor = Color.parseColor("#888888"),
                backgroundColor = Color.parseColor("#FFFFFF")
            ),
            bodyStyle = bodyStyle,
            headerStyle = headerStyle,
            footerStyle = footerStyle,
            isBuiltin = false
        )
    }

    /**
     * Serialize analysis result to JSON for tool output.
     */
    fun getAnalysisAsJson(result: PdfAnalysisResult): String = gson.toJson(result)

    private fun errorResult(message: String) = PdfAnalysisResult(
        pageCount = 0, pageWidth = 0, pageHeight = 0,
        detectedMargins = DetectedMargins(0f, 0f, 0f, 0f),
        detectedColors = emptyList(), estimatedColumnCount = 1,
        hasHeader = false, hasFooter = false, textDensity = "unknown",
        suggestedLayoutId = null, summary = "Error: $message"
    )
}
