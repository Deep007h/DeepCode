package ai.deepcode.android.service.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.gson.Gson
import java.io.File

// ════════════════════════════════════════════════
// Layout Specification Data Classes
// ════════════════════════════════════════════════

enum class PageSize(val width: Int, val height: Int) {
    LETTER(612, 792), A4(595, 842), LEGAL(612, 1008)
}

data class Margins(
    val top: Float = 54f,
    val bottom: Float = 54f,
    val left: Float = 54f,
    val right: Float = 54f
)

data class ColorScheme(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val textColor: Int,
    val mutedColor: Int,
    val backgroundColor: Int
)

data class Typography(
    val titleSize: Float = 28f,
    val headingSize: Float = 18f,
    val bodySize: Float = 11f,
    val captionSize: Float = 9f,
    val titleBold: Boolean = true,
    val headingBold: Boolean = true
)

enum class HeaderStyle { NONE, SIMPLE, ACCENT_LINE, FULL_BANNER }
enum class FooterStyle { NONE, PAGE_NUMBER, PAGE_NUMBER_WITH_LINE, FULL_FOOTER }
enum class BodyStyle { PROSE, POEM, TWO_COLUMN, BULLET_LIST, REPORT, TABLE_HEAVY }

data class Decorations(
    val accentLine: Boolean = true,
    val pullQuotes: Boolean = true,
    val sectionDividers: Boolean = true,
    val watermark: String? = null
)

data class PdfLayout(
    val id: String,
    val name: String,
    val description: String,
    val pageSize: PageSize = PageSize.LETTER,
    val margins: Margins = Margins(),
    val colorScheme: ColorScheme,
    val typography: Typography = Typography(),
    val headerStyle: HeaderStyle = HeaderStyle.ACCENT_LINE,
    val footerStyle: FooterStyle = FooterStyle.PAGE_NUMBER_WITH_LINE,
    val bodyStyle: BodyStyle = BodyStyle.PROSE,
    val decorations: Decorations = Decorations(),
    val isBuiltin: Boolean = false
)

// ════════════════════════════════════════════════
// Document Content Model
// ════════════════════════════════════════════════

enum class SectionType { PARAGRAPH, BULLET_LIST, NUMBERED_LIST, BLOCKQUOTE, TABLE, CODE, MATH }

data class DocumentSection(
    val heading: String? = null,
    val body: String,
    val type: SectionType = SectionType.PARAGRAPH
)

data class DocumentContent(
    val title: String,
    val subtitle: String? = null,
    val author: String? = null,
    val date: String? = null,
    val sections: List<DocumentSection> = emptyList(),
    val rawContent: String? = null
)

// ════════════════════════════════════════════════
// PDF Layout Engine
// ════════════════════════════════════════════════

class PdfLayoutEngine {

    companion object {
        // ── Built-in Layouts ──

        val CLASSIC = PdfLayout(
            id = "classic", name = "Classic",
            description = "Warm editorial style with accent lines — the original DeepCode look",
            colorScheme = ColorScheme(
                primary = Color.parseColor("#1A1A1A"), secondary = Color.parseColor("#555555"),
                accent = Color.parseColor("#C1622D"), textColor = Color.parseColor("#1A1A1A"),
                mutedColor = Color.parseColor("#888888"), backgroundColor = Color.parseColor("#FFFFFF")
            ), isBuiltin = true
        )

        val MODERN_MINIMAL = PdfLayout(
            id = "modern-minimal", name = "Modern Minimal",
            description = "Clean design with generous whitespace and subtle grey tones",
            margins = Margins(72f, 72f, 72f, 72f),
            colorScheme = ColorScheme(
                primary = Color.parseColor("#2D2D2D"), secondary = Color.parseColor("#757575"),
                accent = Color.parseColor("#9E9E9E"), textColor = Color.parseColor("#2D2D2D"),
                mutedColor = Color.parseColor("#BDBDBD"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            typography = Typography(titleSize = 32f, bodySize = 11f, headingSize = 16f),
            headerStyle = HeaderStyle.NONE,
            decorations = Decorations(accentLine = false, pullQuotes = false),
            isBuiltin = true
        )

        val CORPORATE_REPORT = PdfLayout(
            id = "corporate-report", name = "Corporate Report",
            description = "Professional blue/navy theme with header bar and section dividers",
            colorScheme = ColorScheme(
                primary = Color.parseColor("#1B365D"), secondary = Color.parseColor("#4A6FA5"),
                accent = Color.parseColor("#2E86C1"), textColor = Color.parseColor("#1A1A1A"),
                mutedColor = Color.parseColor("#7F8C8D"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            headerStyle = HeaderStyle.FULL_BANNER,
            footerStyle = FooterStyle.FULL_FOOTER,
            bodyStyle = BodyStyle.REPORT,
            isBuiltin = true
        )

        val ACADEMIC_PAPER = PdfLayout(
            id = "academic-paper", name = "Academic Paper",
            description = "Two-column scholarly format with numbered sections",
            colorScheme = ColorScheme(
                primary = Color.parseColor("#000000"), secondary = Color.parseColor("#333333"),
                accent = Color.parseColor("#000000"), textColor = Color.parseColor("#000000"),
                mutedColor = Color.parseColor("#666666"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            typography = Typography(titleSize = 22f, bodySize = 10f, headingSize = 13f),
            bodyStyle = BodyStyle.TWO_COLUMN,
            headerStyle = HeaderStyle.SIMPLE,
            decorations = Decorations(accentLine = false, pullQuotes = false, sectionDividers = false),
            isBuiltin = true
        )

        val CREATIVE_PORTFOLIO = PdfLayout(
            id = "creative-portfolio", name = "Creative Portfolio",
            description = "Bold and vibrant design for creative work",
            margins = Margins(60f, 60f, 48f, 48f),
            colorScheme = ColorScheme(
                primary = Color.parseColor("#1A1A2E"), secondary = Color.parseColor("#16213E"),
                accent = Color.parseColor("#E94560"), textColor = Color.parseColor("#1A1A2E"),
                mutedColor = Color.parseColor("#A0A0A0"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            typography = Typography(titleSize = 36f, bodySize = 11f, headingSize = 20f, headingBold = true),
            headerStyle = HeaderStyle.ACCENT_LINE,
            isBuiltin = true
        )

        val INVOICE = PdfLayout(
            id = "invoice-receipt", name = "Invoice / Receipt",
            description = "Table-heavy layout for invoices, receipts, and financial documents",
            colorScheme = ColorScheme(
                primary = Color.parseColor("#2C3E50"), secondary = Color.parseColor("#34495E"),
                accent = Color.parseColor("#27AE60"), textColor = Color.parseColor("#2C3E50"),
                mutedColor = Color.parseColor("#95A5A6"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            bodyStyle = BodyStyle.TABLE_HEAVY,
            headerStyle = HeaderStyle.SIMPLE,
            footerStyle = FooterStyle.FULL_FOOTER,
            isBuiltin = true
        )

        val NEWSLETTER = PdfLayout(
            id = "newsletter", name = "Newsletter",
            description = "Two-column body with masthead header and pull quotes",
            colorScheme = ColorScheme(
                primary = Color.parseColor("#2C3E50"), secondary = Color.parseColor("#E74C3C"),
                accent = Color.parseColor("#E74C3C"), textColor = Color.parseColor("#2C3E50"),
                mutedColor = Color.parseColor("#7F8C8D"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            typography = Typography(titleSize = 30f, headingSize = 16f, bodySize = 10f),
            bodyStyle = BodyStyle.TWO_COLUMN,
            headerStyle = HeaderStyle.FULL_BANNER,
            decorations = Decorations(pullQuotes = true),
            isBuiltin = true
        )

        val RESUME = PdfLayout(
            id = "resume-cv", name = "Resume / CV",
            description = "Clean professional resume format with section dividers",
            margins = Margins(48f, 48f, 48f, 48f),
            colorScheme = ColorScheme(
                primary = Color.parseColor("#2C3E50"), secondary = Color.parseColor("#34495E"),
                accent = Color.parseColor("#3498DB"), textColor = Color.parseColor("#2C3E50"),
                mutedColor = Color.parseColor("#7F8C8D"), backgroundColor = Color.parseColor("#FFFFFF")
            ),
            typography = Typography(titleSize = 24f, headingSize = 14f, bodySize = 10f, captionSize = 8.5f),
            bodyStyle = BodyStyle.BULLET_LIST,
            headerStyle = HeaderStyle.SIMPLE,
            footerStyle = FooterStyle.PAGE_NUMBER,
            decorations = Decorations(accentLine = true, sectionDividers = true, pullQuotes = false),
            isBuiltin = true
        )

        val builtinLayouts = listOf(
            CLASSIC, MODERN_MINIMAL, CORPORATE_REPORT, ACADEMIC_PAPER,
            CREATIVE_PORTFOLIO, INVOICE, NEWSLETTER, RESUME
        )

        fun getLayoutById(id: String): PdfLayout? =
            builtinLayouts.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun getLayoutByName(name: String): PdfLayout? =
            builtinLayouts.firstOrNull { it.name.equals(name, ignoreCase = true) }

        fun findLayout(idOrName: String): PdfLayout? =
            getLayoutById(idOrName) ?: getLayoutByName(idOrName)

        fun getAllLayoutDescriptions(): String = builtinLayouts.joinToString("\n") { layout ->
            "• ${layout.name} (id: ${layout.id}) — ${layout.description}"
        }
    }

    // ════════════════════════════════════════════════
    // LaTeX preprocessing
    // ════════════════════════════════════════════════

    private fun cleanHeaderTitle(title: String, maxLen: Int = 45): String {
        val trimmed = title.trim()
        if (trimmed.length <= maxLen) return trimmed.removeSuffix("-").trim()
        val sub = trimmed.take(maxLen).substringBeforeLast(" ")
        return sub.removeSuffix("-").trim() + "..."
    }

    private fun preprocessLatex(latex: String): String {
        var result = latex
        result = result.replace(Regex("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}")) { match ->
            "${match.groupValues[1]}/${match.groupValues[2]}"
        }
        val latexMap = mapOf(
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
            "\\epsilon" to "ε", "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ",
            "\\pi" to "π", "\\sigma" to "σ", "\\tau" to "τ", "\\omega" to "ω",
            "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω",
            "\\le" to "≤", "\\ge" to "≥", "\\neq" to "≠", "\\approx" to "≈",
            "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\cdot" to "·",
            "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\union" to "∪",
            "\\cap" to "∩", "\\forall" to "∀", "\\exists" to "∃",
            "\\to" to "→", "\\Rightarrow" to "⇒", "\\iff" to "⇔",
            "\\infty" to "∞", "\\partial" to "∂",
        )
        for ((cmd, unicode) in latexMap) {
            result = result.replace(cmd, unicode)
        }
        result = result.replace(Regex("\\\\(?:text|mathrm|mathbf|mathit|mathsf|texttt)\\{(.*?)\\}")) { it.groupValues[1] }
        result = result.replace(Regex("\\\\left|\\\\right|\\\\big[lr]?|\\\\Big[lr]?|\\\\bigg[lr]?|\\\\Bigg[lr]?"), "")
        result = result.replace(Regex("[{}]"), "")
        return result.trim()
    }

    private fun stripMarkdownFormatting(text: String): String {
        var clean = text
        clean = clean.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        clean = clean.replace(Regex("(?<=\\s|^)\\*([^*]+)\\*(?=\\s|$|[,\\.:;!\\?])"), "$1")
        clean = clean.replace(Regex("`([^`]+)`"), "$1")
        return clean
    }

    private fun stripLatexDelimiters(text: String): String {
        if (!text.contains("$")) return stripMarkdownFormatting(text)
        val cleanMath = text.replace(Regex("\\$\\$[^$]*\\$\\$"), "")
            .replace(Regex("\\$([^$]*)\\$")) { match ->
                preprocessLatex(match.groupValues[1])
            }
        return stripMarkdownFormatting(cleanMath)
    }

    private fun drawHeader(
        canvas: Canvas, layout: PdfLayout, pageNum: Int, pw: Int, m: Margins,
        bannerPaint: Paint, bannerTextPaint: Paint, captionPaint: Paint, accentPaint: Paint, title: String
    ) {
        val headerTitle = cleanHeaderTitle(title, 45)
        when (layout.headerStyle) {
            HeaderStyle.NONE -> { /* no header */ }
            HeaderStyle.SIMPLE -> {
                canvas.drawText(headerTitle, m.left, m.top + 14f, captionPaint)
                canvas.drawLine(m.left, m.top + 20f, pw - m.right, m.top + 20f, accentPaint.apply { strokeWidth = 0.8f })
            }
            HeaderStyle.ACCENT_LINE -> {
                canvas.drawText(headerTitle, m.left, m.top + 14f, captionPaint)
                canvas.drawLine(m.left, m.top + 20f, pw - m.right, m.top + 20f, accentPaint)
            }
            HeaderStyle.FULL_BANNER -> {
                canvas.drawRect(0f, 0f, pw.toFloat(), 40f, bannerPaint)
                canvas.drawText(headerTitle, m.left, 26f, bannerTextPaint)
                val pageStr = "Page $pageNum"
                canvas.drawText(pageStr, pw - m.right - bannerTextPaint.measureText(pageStr), 26f, bannerTextPaint)
            }
        }
    }

    private fun drawFooter(
        canvas: Canvas, layout: PdfLayout, pageNum: Int, totalPages: Int,
        pw: Int, ph: Float, m: Margins,
        hairlinePaint: Paint, pageNumPaint: Paint, captionPaint: Paint, title: String
    ) {
        val footerTitle = cleanHeaderTitle(title, 40)
        when (layout.footerStyle) {
            FooterStyle.NONE -> { /* no footer */ }
            FooterStyle.PAGE_NUMBER -> {
                canvas.drawText("$pageNum", pw / 2f, ph - 22f, pageNumPaint)
            }
            FooterStyle.PAGE_NUMBER_WITH_LINE -> {
                canvas.drawLine(m.left, ph - 36f, pw - m.right, ph - 36f, hairlinePaint)
                canvas.drawText("$pageNum", pw / 2f, ph - 22f, pageNumPaint)
            }
            FooterStyle.FULL_FOOTER -> {
                canvas.drawLine(m.left, ph - 40f, pw - m.right, ph - 40f, hairlinePaint)
                canvas.drawText(footerTitle, m.left, ph - 26f, captionPaint)
                canvas.drawText("$pageNum", pw / 2f, ph - 26f, pageNumPaint)
                val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date())
                val datePaint = Paint(captionPaint)
                canvas.drawText(dateStr, pw - m.right - datePaint.measureText(dateStr), ph - 26f, datePaint)
            }
        }
    }

    // ════════════════════════════════════════════════
    // Raw Content Parser
    // ════════════════════════════════════════════════

    fun parseRawContentToSections(rawContent: String): List<DocumentSection> {
        val sections = mutableListOf<DocumentSection>()
        val lines = rawContent.split("\n")
        var currentHeading: String? = null
        val buffer = StringBuilder()
        var currentType = SectionType.PARAGRAPH
        var inCodeBlock = false

        fun flushBuffer() {
            val text = if (currentType == SectionType.CODE) buffer.toString().trimEnd() else buffer.toString().trim()
            if (text.isNotBlank()) {
                sections.add(DocumentSection(heading = currentHeading, body = text, type = currentType))
                currentHeading = null
            }
            buffer.clear()
            currentType = SectionType.PARAGRAPH
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (inCodeBlock) {
                if (trimmed.startsWith("```")) {
                    inCodeBlock = false
                    flushBuffer()
                } else {
                    buffer.appendLine(line)
                }
                i++
                continue
            }

            when {
                trimmed.startsWith("```") -> {
                    flushBuffer()
                    currentType = SectionType.CODE
                    inCodeBlock = true
                }
                trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
                    flushBuffer()
                    currentHeading = trimmed.removePrefix("### ").removePrefix("## ").trim()
                }
                trimmed.startsWith("# ") -> {
                    flushBuffer()
                    currentHeading = trimmed.removePrefix("# ").trim()
                }
                trimmed.length > 5 && trimmed == trimmed.uppercase() && trimmed.split(" ").size >= 2 && !trimmed.startsWith("|") -> {
                    flushBuffer()
                    currentHeading = trimmed
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    if (currentType != SectionType.TABLE) {
                        flushBuffer()
                        currentType = SectionType.TABLE
                    }
                    buffer.appendLine(trimmed)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    if (currentType != SectionType.BULLET_LIST) {
                        flushBuffer()
                        currentType = SectionType.BULLET_LIST
                    }
                    buffer.appendLine(trimmed)
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    if (currentType != SectionType.NUMBERED_LIST) {
                        flushBuffer()
                        currentType = SectionType.NUMBERED_LIST
                    }
                    buffer.appendLine(trimmed)
                }
                trimmed.startsWith("> ") -> {
                    if (currentType != SectionType.BLOCKQUOTE) {
                        flushBuffer()
                        currentType = SectionType.BLOCKQUOTE
                    }
                    buffer.appendLine(trimmed)
                }
                trimmed.startsWith("\$\$") -> {
                    if (currentType == SectionType.MATH) {
                        flushBuffer()
                    } else {
                        flushBuffer()
                        currentType = SectionType.MATH
                        val rest = trimmed.removePrefix("\$\$").trim()
                        if (rest.isNotBlank()) {
                            buffer.appendLine(rest)
                            flushBuffer()
                        }
                    }
                }
                trimmed.isEmpty() -> {
                    if (buffer.isNotEmpty()) {
                        flushBuffer()
                    }
                }
                else -> {
                    buffer.appendLine(trimmed)
                }
            }
            i++
        }
        flushBuffer()

        return if (sections.isEmpty()) listOf(DocumentSection(body = rawContent)) else sections
    }

    // ════════════════════════════════════════════════
    // Main Render Method
    // ════════════════════════════════════════════════

    fun renderDocument(context: Context, layout: PdfLayout, content: DocumentContent, filename: String?): String {
        val document = PdfDocument()
        val pw = layout.pageSize.width
        val ph = layout.pageSize.height.toFloat()
        val m = layout.margins

        fun bottomLimit(): Float = ph - m.bottom - 40f

        val pages = mutableListOf<PdfDocument.Page>()

        val primaryColorInt = layout.colorScheme.primary
        val secondaryColorInt = layout.colorScheme.secondary
        val textColorInt = layout.colorScheme.textColor

        val titlePaint = Paint().apply {
            color = primaryColorInt
            textSize = layout.typography.titleSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = primaryColorInt
            textSize = layout.typography.headingSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subheadingPaint = Paint().apply {
            color = secondaryColorInt
            textSize = layout.typography.headingSize - 2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = textColorInt
            textSize = layout.typography.bodySize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.argb(180, Color.red(textColorInt), Color.green(textColorInt), Color.blue(textColorInt))
            textSize = layout.typography.headingSize - 2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }
        val captionPaint = Paint().apply {
            color = Color.argb(140, Color.red(textColorInt), Color.green(textColorInt), Color.blue(textColorInt))
            textSize = layout.typography.captionSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val accentPaint = Paint().apply {
            color = primaryColorInt
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val hairlinePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val bannerPaint = Paint().apply {
            color = primaryColorInt
            style = Paint.Style.FILL
        }
        val bannerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = layout.typography.captionSize + 1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val pageNumPaint = Paint().apply {
            color = textColorInt
            textSize = layout.typography.captionSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val quotePaint = Paint().apply {
            color = secondaryColorInt
            textSize = layout.typography.bodySize
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }

        val contentW = pw - m.left - m.right
        val bodyLineHeight = layout.typography.bodySize + 6f
        val headingLineHeight = layout.typography.headingSize + 8f
        val unit = 6f

        var y = m.top
        val headerHeight = if (layout.headerStyle == HeaderStyle.FULL_BANNER) 40f else 28f

        fun completePageWithFooter() {
            val pIndex = pages.size
            if (pIndex > 0) {
                drawFooter(pages[pIndex - 1].canvas, layout, pIndex, 0, pw, ph, m, hairlinePaint, pageNumPaint, captionPaint, content.title)
            }
        }

        fun newPage(): Canvas {
            if (pages.isNotEmpty()) {
                completePageWithFooter()
                document.finishPage(pages.last())
            }
            val info = PdfDocument.PageInfo.Builder(pw, ph.toInt(), pages.size + 1).create()
            val page = document.startPage(info)
            pages.add(page)
            y = m.top
            if (pages.size > 1) {
                drawHeader(page.canvas, layout, pages.size, pw, m, bannerPaint, bannerTextPaint, captionPaint, accentPaint, content.title)
                y += headerHeight + 16f
            }
            return page.canvas
        }

        var canvas = newPage()

        fun drawWrappedText(paint: Paint, text: String, x: Float, maxWidth: Float, lineHeight: Float) {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            val cur = StringBuilder()
            for (word in words) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (paint.measureText(test) < maxWidth) {
                    if (cur.isNotEmpty()) cur.append(" ")
                    cur.append(word)
                } else {
                    if (cur.isNotEmpty()) lines.add(cur.toString())
                    cur.clear()
                    cur.append(word)
                }
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            for (line in lines) {
                if (y > bottomLimit()) {
                    canvas = newPage()
                }
                canvas.drawText(line, x, y, paint)
                y += lineHeight
            }
        }

        // ── Title block ──
        val eyebrow = content.author?.takeIf { it.isNotBlank() }?.let { "BY ${it.uppercase()}" }
            ?: "STUDY & EXAMINATION GUIDE"
        canvas.drawText(eyebrow, m.left, y, captionPaint)
        y += unit * 2.5f

        drawWrappedText(titlePaint, content.title, m.left, contentW, layout.typography.titleSize + 6f)
        y += unit * 1.5f

        if (!content.subtitle.isNullOrBlank()) {
            drawWrappedText(subtitlePaint, content.subtitle, m.left, contentW, layout.typography.headingSize)
            y += unit * 1.5f
        }

        if (!content.date.isNullOrBlank()) {
            canvas.drawText(content.date.uppercase(), m.left, y, captionPaint)
            y += unit * 2f
        }

        if (layout.decorations.accentLine) {
            val lineLen = minOf(titlePaint.measureText(content.title) + 40f, 200f)
            canvas.drawLine(m.left, y, m.left + lineLen, y, accentPaint)
            y += unit * 3.5f
        } else {
            y += unit * 2f
        }

        // ── Body content ──
        val sections = if (content.sections.isNotEmpty()) {
            content.sections
        } else if (!content.rawContent.isNullOrBlank()) {
            parseRawContentToSections(content.rawContent)
        } else {
            listOf(DocumentSection(body = ""))
        }

        var sectionIndex = 0
        for (section in sections) {
            if (!section.heading.isNullOrBlank()) {
                if (y > bottomLimit() - headingLineHeight * 3.5f) { canvas = newPage() }
                if (layout.decorations.sectionDividers && sectionIndex > 0) {
                    canvas.drawLine(m.left, y, m.left + contentW, y, hairlinePaint)
                    y += unit * 2f
                }
                val hPaint = when {
                    section.heading.startsWith("Question", ignoreCase = true) -> headingPaint
                    section.heading.startsWith("Solution", ignoreCase = true) -> subheadingPaint
                    section.heading.startsWith("Answer", ignoreCase = true) -> subheadingPaint
                    else -> headingPaint
                }
                if (section.heading.startsWith("Solution", ignoreCase = true) || section.heading.startsWith("Answer", ignoreCase = true)) {
                    val boxBgPaint = Paint().apply {
                        color = primaryColorInt
                        style = Paint.Style.FILL
                        alpha = 10
                    }
                    val boxH = headingLineHeight + 8f
                    canvas.drawRect(m.left, y - 4f, m.left + contentW, y + boxH, boxBgPaint)
                }
                drawWrappedText(hPaint, section.heading, m.left, contentW, headingLineHeight)
                y += unit * 1.5f
            }

            when (section.type) {
                SectionType.PARAGRAPH -> {
                    val rawLines = section.body.split("\n").map { it.trim() }
                    val paragraphs = mutableListOf<String>()
                    val curPara = StringBuilder()
                    for (line in rawLines) {
                        if (line.isEmpty()) {
                            if (curPara.isNotEmpty()) {
                                paragraphs.add(curPara.toString())
                                curPara.clear()
                            }
                            continue
                        }
                        if (curPara.isNotEmpty()) {
                            curPara.append(" ")
                        }
                        curPara.append(line)
                    }
                    if (curPara.isNotEmpty()) paragraphs.add(curPara.toString())
                    if (paragraphs.isEmpty()) paragraphs.add("")

                    for (t in paragraphs) {
                        if (t.isEmpty()) { y += unit * 2f; continue }
                        val isKeyLabel = t.startsWith("Output:", ignoreCase = true) ||
                                         t.startsWith("Input:", ignoreCase = true) ||
                                         t.startsWith("Answer:", ignoreCase = true) ||
                                         t.startsWith("Solution:", ignoreCase = true) ||
                                         t.startsWith("Best Practice", ignoreCase = true) ||
                                         t.startsWith("Note:", ignoreCase = true)

                        if (isKeyLabel && y > bottomLimit() - bodyLineHeight * 4f) {
                            canvas = newPage()
                        } else if (y > bottomLimit()) {
                            canvas = newPage()
                        }

                        if (t.startsWith(">")) {
                            val quoteText = t.removePrefix(">").trim()
                            if (y > bottomLimit() - bodyLineHeight * 2f) canvas = newPage()
                            val qLeft = m.left + 16f
                            canvas.drawLine(m.left + 4f, y - bodyLineHeight + 4f, m.left + 4f, y + 4f, accentPaint)
                            drawWrappedText(quotePaint.apply { textAlign = Paint.Align.LEFT }, quoteText, qLeft, contentW - 20f, bodyLineHeight + 2f)
                            y += unit
                        } else {
                            drawWrappedText(bodyPaint, stripLatexDelimiters(t), m.left, contentW, bodyLineHeight)
                        }
                        y += unit / 2f
                    }
                }
                SectionType.BULLET_LIST -> {
                    val items = section.body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    for (item in items) {
                        if (y > bottomLimit() - bodyLineHeight * 2f) canvas = newPage()
                        val cleanItem = item.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                        val bulletX = m.left + 12f
                        val textX = m.left + 28f
                        canvas.drawCircle(bulletX, y - bodyLineHeight / 3f, 3f, Paint().apply { color = primaryColorInt; style = Paint.Style.FILL })
                        drawWrappedText(bodyPaint, stripLatexDelimiters(cleanItem), textX, contentW - 28f, bodyLineHeight)
                        y += unit / 2f
                    }
                }
                SectionType.NUMBERED_LIST -> {
                    val items = section.body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    var num = 1
                    for (item in items) {
                        if (y > bottomLimit() - bodyLineHeight * 2f) canvas = newPage()
                        val cleanItem = item.replace(Regex("^\\d+\\.\\s*"), "")
                        val numStr = "$num."
                        val textX = m.left + 28f
                        canvas.drawText(numStr, m.left + 8f, y, captionPaint)
                        drawWrappedText(bodyPaint, stripLatexDelimiters(cleanItem), textX, contentW - 28f, bodyLineHeight)
                        y += unit / 2f
                        num++
                    }
                }

                SectionType.BLOCKQUOTE -> {
                    if (y > bottomLimit() - bodyLineHeight * 3f) canvas = newPage()
                    val qLeft = m.left + 16f
                    val quoteLines = section.body.split("\n")
                    val startQY = y
                    for (line in quoteLines) {
                        val t = line.removePrefix(">").trim()
                        if (t.isEmpty()) { y += unit; continue }
                        drawWrappedText(quotePaint.apply { textAlign = Paint.Align.LEFT }, stripLatexDelimiters(t), qLeft, contentW - 20f, bodyLineHeight + 2f)
                    }
                    canvas.drawLine(m.left + 6f, startQY - 4f, m.left + 6f, y + 2f, accentPaint)
                    y += unit
                }

                SectionType.TABLE -> {
                    val rows = section.body.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.matches(Regex("^[\\|\\-\\s:]+$")) }
                    if (rows.isNotEmpty()) {
                        val parsedRows = rows.map { row ->
                            row.trim('|').split("|").map { it.trim() }
                        }
                        val colCount = parsedRows.maxOf { it.size }
                        val measurePaint = Paint(bodyPaint)
                        val colMaxChars = IntArray(colCount) { 0 }
                        for (row in parsedRows) {
                            for ((idx, cell) in row.withIndex()) {
                                if (idx < colCount) {
                                    val clean = stripLatexDelimiters(cell)
                                    if (clean.length > colMaxChars[idx]) colMaxChars[idx] = clean.length
                                }
                            }
                        }
                        val minCol = 8
                        val charWidth = bodyPaint.measureText("W")
                        val colWidths = FloatArray(colCount) { idx ->
                            (colMaxChars[idx].coerceAtLeast(minCol) * charWidth)
                        }
                        val totalW = colWidths.sum()
                        if (totalW > 0f) {
                            val scale = contentW / totalW
                            for (i in 0 until colCount) colWidths[i] = (colWidths[i] * scale).coerceAtLeast(charWidth * minCol)
                        } else {
                            for (i in 0 until colCount) colWidths[i] = contentW / colCount
                        }
                        val widthSum = colWidths.sum()
                        if (widthSum > contentW) {
                            val s = contentW / widthSum
                            for (i in 0 until colCount) colWidths[i] *= s
                        }

                        val firstRowY = y - bodyLineHeight

                        val tableHeaderPaint = Paint(headingPaint).apply {
                            textSize = layout.typography.bodySize
                        }
                        val tableBorderPaint = Paint().apply {
                            color = Color.parseColor("#D0D0D0")
                            strokeWidth = 0.8f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }

                        for ((rowIdx, row) in parsedRows.withIndex()) {
                            if (y > bottomLimit()) canvas = newPage()
                            val paint = if (rowIdx == 0) tableHeaderPaint else bodyPaint
                            if (rowIdx == 0) {
                                val bgPaint = Paint().apply {
                                    color = primaryColorInt
                                    style = Paint.Style.FILL
                                    alpha = 25
                                }
                                canvas.drawRect(m.left, y - bodyLineHeight, m.left + contentW, y + 8f, bgPaint)
                            }
                            var cellX = m.left + 4f
                            var maxRowLines = 1
                            val cellLines = mutableListOf<List<String>>()
                            for ((colIdx, cell) in row.withIndex()) {
                                if (colIdx >= colCount) break
                                val clean = stripLatexDelimiters(cell)
                                val cw = colWidths[colIdx] - 8f
                                val words = clean.split(" ")
                                val lines = mutableListOf<String>()
                                val cur = StringBuilder()
                                for (word in words) {
                                    var w = word
                                    while (measurePaint.measureText(w) > cw && cw > 10f) {
                                        var cut = w.length - 1
                                        while (cut > 1 && measurePaint.measureText(w.substring(0, cut)) > cw) {
                                            cut--
                                        }
                                        if (cur.isNotEmpty()) {
                                            lines.add(cur.toString())
                                            cur.clear()
                                        }
                                        lines.add(w.substring(0, cut))
                                        w = w.substring(cut)
                                    }
                                    val test = if (cur.isEmpty()) w else "$cur $w"
                                    if (measurePaint.measureText(test) < cw) {
                                        if (cur.isNotEmpty()) cur.append(" ")
                                        cur.append(w)
                                    } else {
                                        if (cur.isNotEmpty()) lines.add(cur.toString())
                                        cur.clear(); cur.append(w)
                                    }
                                }
                                if (cur.isNotEmpty()) lines.add(cur.toString())
                                cellLines.add(lines)
                                if (lines.size > maxRowLines) maxRowLines = lines.size
                            }
                            val cellRowHeight = bodyLineHeight * maxRowLines + 8f
                            if (y + cellRowHeight > bottomLimit()) {
                                canvas = newPage()
                                if (rowIdx > 0 && parsedRows.isNotEmpty()) {
                                    val bgPaint = Paint().apply {
                                        color = primaryColorInt
                                        style = Paint.Style.FILL
                                        alpha = 25
                                    }
                                    canvas.drawRect(m.left, y - bodyLineHeight, m.left + contentW, y + 8f, bgPaint)
                                    var hCellX = m.left + 4f
                                    for ((hColIdx, hCell) in parsedRows[0].withIndex()) {
                                        if (hColIdx >= colCount) break
                                        val hCw = colWidths[hColIdx]
                                        canvas.drawText(stripLatexDelimiters(hCell), hCellX, y, tableHeaderPaint)
                                        hCellX += hCw
                                    }
                                    canvas.drawLine(m.left, y + 8f, m.left + contentW, y + 8f, tableBorderPaint)
                                    y += bodyLineHeight + 12f
                                }
                            }
                            for ((colIdx, cell) in row.withIndex()) {
                                if (colIdx >= colCount) break
                                val lines = if (colIdx < cellLines.size) cellLines[colIdx] else listOf(cell)
                                val cw = colWidths[colIdx]
                                var lineY = y
                                for (lineText in lines) {
                                    canvas.drawText(lineText, cellX, lineY, paint)
                                    lineY += bodyLineHeight
                                }
                                cellX += cw
                            }
                            canvas.drawLine(m.left, y + cellRowHeight - 2f, m.left + contentW, y + cellRowHeight - 2f, tableBorderPaint)
                            y += cellRowHeight
                        }
                        val tableBottom = y
                        var vx = m.left
                        for (i in 0..colCount) {
                            canvas.drawLine(vx, firstRowY, vx, tableBottom - 6f, tableBorderPaint)
                            if (i < colCount) vx += colWidths[i]
                        }
                        y += unit
                    }
                }

                SectionType.MATH -> {
                    val mathPaint = Paint().apply {
                        color = textColorInt
                        textSize = layout.typography.bodySize + 2f
                        typeface = Typeface.SERIF
                        isAntiAlias = true
                    }
                    val mathLines = section.body.split("\n").filter { it.isNotBlank() }
                    if (mathLines.isNotEmpty()) {
                        if (y > bottomLimit() - bodyLineHeight * 3f) { canvas = newPage() }
                        val startY = y
                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F8F8F8")
                            style = Paint.Style.FILL
                        }
                        for (line in mathLines) {
                            val processed = stripLatexDelimiters(line)
                            val tw = mathPaint.measureText(processed)
                            val mathX = if (tw < contentW - 16f) m.left + (contentW - tw) / 2f else m.left + 16f
                            canvas.drawRect(m.left + 4f, y - bodyLineHeight + 2f, m.left + contentW - 4f, y + 4f, bgPaint)
                            canvas.drawText(processed, mathX, y, mathPaint)
                            y += bodyLineHeight + 2f
                        }
                        y += unit
                    }
                }

                SectionType.CODE -> {
                    val codePaint = Paint().apply {
                        color = textColorInt
                        textSize = layout.typography.bodySize - 1.5f
                        typeface = Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    val codeBgPaint = Paint().apply {
                        color = Color.parseColor("#F8F9FA")
                        style = Paint.Style.FILL
                    }
                    val codeBorderPaint = Paint().apply {
                        color = Color.parseColor("#E5E7EB")
                        strokeWidth = 0.8f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }

                    val rawCodeLines = section.body.split("\n")
                    if (rawCodeLines.isNotEmpty()) {
                        val charW = codePaint.measureText("A")
                        val maxCharsPerLine = if (charW > 0f) ((contentW - 16f) / charW).toInt().coerceAtLeast(10) else 60

                        val expandedLines = mutableListOf<String>()
                        for (codeLine in rawCodeLines) {
                            if (codeLine.length > maxCharsPerLine) {
                                expandedLines.addAll(codeLine.chunked(maxCharsPerLine))
                            } else {
                                expandedLines.add(codeLine)
                            }
                        }

                        val blockHeight = expandedLines.size * bodyLineHeight + 8f
                        if (blockHeight < (bottomLimit() - m.top) && y + blockHeight > bottomLimit()) {
                            canvas = newPage()
                        }

                        val startBlockY = y
                        for (lineChunk in expandedLines) {
                            if (y > bottomLimit()) {
                                canvas = newPage()
                            }
                            canvas.drawRect(m.left, y - bodyLineHeight + 4f, m.left + contentW, y + 4f, codeBgPaint)
                            canvas.drawText(lineChunk, m.left + 8f, y, codePaint)
                            y += bodyLineHeight
                        }
                        canvas.drawRect(m.left, startBlockY - bodyLineHeight + 4f, m.left + contentW, y - bodyLineHeight + 4f, codeBorderPaint)
                        y += unit
                    }
                }
            }
            y += unit
            sectionIndex++
        }

        // ── Watermark ──
        if (!layout.decorations.watermark.isNullOrBlank()) {
            for (page in pages) {
                val wmPaint = Paint().apply {
                    color = layout.colorScheme.mutedColor
                    alpha = 30
                    textSize = 60f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                page.canvas.save()
                page.canvas.rotate(-45f, pw / 2f, ph / 2f)
                page.canvas.drawText(layout.decorations.watermark, pw / 2f, ph / 2f, wmPaint)
                page.canvas.restore()
            }
        }

        // ── Finalize ──
        if (pages.isNotEmpty()) {
            completePageWithFooter()
            document.finishPage(pages.last())
        }

        // ── Write to file ──
        val baseName = filename?.takeIf { it.isNotBlank() }
            ?: content.title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(50).replace(" ", "_").ifEmpty { "document" }
        val pdfName = if (baseName.lowercase().endsWith(".pdf")) baseName else "$baseName.pdf"
        val outputDir = File(context.filesDir, "pdf")
        outputDir.mkdirs()
        val outputFile = File(outputDir, pdfName)

        return try {
            java.io.FileOutputStream(outputFile).use { out -> document.writeTo(out) }
            document.close()
            // Also copy to public Downloads folder for user convenience
            try {
                val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (dlDir != null && dlDir.exists()) {
                    outputFile.copyTo(File(dlDir, pdfName), overwrite = true)
                }
            } catch (_: Exception) {}
            "[file:${outputFile.absolutePath}] PDF created successfully: '${content.title}' (layout: ${layout.name})"
        } catch (e: Exception) {
            document.close()
            "Error creating PDF: ${e.message}"
        }
    }

    // ════════════════════════════════════════════════
    // Layout Serialization
    // ════════════════════════════════════════════════

    fun layoutToJson(layout: PdfLayout): String = Gson().toJson(layout)

    fun layoutFromJson(json: String): PdfLayout? = try {
        Gson().fromJson(json, PdfLayout::class.java)
    } catch (e: Exception) { null }
}
