package ai.deepcode.android.service.telegram

object TelegramFormatter {

    private val RE_THINK = Regex("""<think>.*?</think>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_EXISTING_TAGS = Regex("""</?(?:b|i|u|s|code|pre|blockquote|a|tg-spoiler)(?:\s+[^>]*?)?>""", RegexOption.IGNORE_CASE)
    private val RE_HTML_ENTITY = Regex("""^&(?:amp|lt|gt|quot|#\d+|#x[0-9a-fA-F]+);""")
    private val RE_CODE_BLOCK = Regex("""```([a-zA-Z0-9_-]*)\n?(.*?)```""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_INLINE_CODE = Regex("""`([^`\n]+)`""")
    private val RE_TABLE_ROW_SPLIT = Regex("""\|""")
    private val RE_TABLE_SEPARATOR = Regex("""^\|[-:| ]+\|$""")
    private val RE_DIVIDER = Regex("""^[ ]{0,3}(?:[-*_][ ]*){3,}$""", RegexOption.MULTILINE)
    private val RE_HEADING = Regex("""^(#{1,6})\s+(.+)$""", RegexOption.MULTILINE)
    private val RE_BOLD_STAR = Regex("""\*\*(.+?)\*\*""")
    private val RE_BOLD_UNDER = Regex("""__(.+?)__""")
    private val RE_ITALIC_STAR = Regex("""(?<!\w)\*([^*\n]+?)\*(?!\w)""")
    private val RE_ITALIC_UNDER = Regex("""(?<!\w)_([^_\n]+?)_(?!\w)""")
    private val RE_STRIKE = Regex("""~~(.+?)~~""")
    private val RE_BULLET = Regex("""^[ \t]*[-*+]\s+""", RegexOption.MULTILINE)
    private val RE_MD_LINK = Regex("""\[([^\]]+)\]\((https?://[^)]+)\)""")

    fun escapeHtml(text: String): String {
        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            when (val c = text[i]) {
                '&' -> {
                    val rest = text.substring(i)
                    val match = RE_HTML_ENTITY.find(rest)
                    if (match != null) {
                        out.append(match.value)
                        i += match.value.length
                    } else {
                        out.append("&amp;")
                        i++
                    }
                }
                '<' -> {
                    out.append("&lt;")
                    i++
                }
                '>' -> {
                    out.append("&gt;")
                    i++
                }
                '"' -> {
                    out.append("&quot;")
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("""<[^>]*>"""), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
    }

    fun formatMarkdownToTelegramHtml(raw: String): String {
        if (raw.isBlank()) return ""

        // 0. Remove think blocks and trim
        var text = RE_THINK.replace(raw, "").trim()

        val placeholders = ArrayList<String>()
        fun addPlaceholder(content: String): String {
            val idx = placeholders.size
            placeholders.add(content)
            return "@@TGBLOCK${idx}@@"
        }

        // 1. Preserve existing valid Telegram HTML tags
        val existingTags = ArrayList<String>()
        text = RE_EXISTING_TAGS.replace(text) { match ->
            val idx = existingTags.size
            existingTags.add(match.value)
            "@@EXISTINGTAG${idx}@@"
        }

        // 2. Extract code blocks: ```lang\ncode\n```
        text = RE_CODE_BLOCK.replace(text) { match ->
            val lang = match.groupValues[1].trim()
            val code = match.groupValues[2].trim('\n')
            val escaped = escapeHtml(code)
            val tag = if (lang.isNotEmpty()) {
                "<pre><code class=\"language-$lang\">$escaped</code></pre>"
            } else {
                "<pre>$escaped</pre>"
            }
            addPlaceholder(tag)
        }

        // 3. Extract inline code: `code`
        text = RE_INLINE_CODE.replace(text) { match ->
            val code = match.groupValues[1]
            addPlaceholder("<code>${escapeHtml(code)}</code>")
        }

        // 4. Extract tables: lines starting and ending with |
        val lines = text.lines()
        val outLines = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = ArrayList<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|") && lines[j].trim().endsWith("|")) {
                    val rawRow = lines[j].trim()
                    if (!RE_TABLE_SEPARATOR.matches(rawRow)) {
                        tableLines.add(rawRow)
                    }
                    j++
                }

                if (tableLines.isNotEmpty()) {
                    val rows = ArrayList<List<String>>()
                    for (tline in tableLines) {
                        val parts = tline.split(RE_TABLE_ROW_SPLIT)
                        if (parts.size >= 3) {
                            val cells = parts.subList(1, parts.size - 1).map { it.trim() }
                            if (cells.isNotEmpty()) {
                                rows.add(cells)
                            }
                        }
                    }

                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOf { it.size }
                        val colWidths = IntArray(maxCols)
                        for (row in rows) {
                            for (colIdx in 0 until minOf(row.size, maxCols)) {
                                colWidths[colIdx] = maxOf(colWidths[colIdx], row[colIdx].length)
                            }
                        }

                        val tableSb = StringBuilder()
                        for (row in rows) {
                            val rowCells = ArrayList<String>(maxCols)
                            for (colIdx in 0 until maxCols) {
                                val cellVal = if (colIdx < row.size) row[colIdx] else ""
                                val width = colWidths[colIdx]
                                rowCells.add(cellVal.padEnd(width))
                            }
                            tableSb.appendLine(rowCells.joinToString("   ").trimEnd())
                        }
                        val escapedTable = escapeHtml(tableSb.toString().trimEnd())
                        outLines.add(addPlaceholder("<pre>$escapedTable</pre>"))
                    }
                }
                i = j
            } else {
                outLines.add(line)
                i++
            }
        }
        text = outLines.joinToString("\n")

        // 5. Extract blockquotes: lines starting with >
        val bqLines = text.lines()
        val bqOut = ArrayList<String>(bqLines.size)
        var bqIdx = 0
        while (bqIdx < bqLines.size) {
            val line = bqLines[bqIdx]
            val trimmed = line.trim()
            if (trimmed.startsWith(">")) {
                val quoteLines = ArrayList<String>()
                var j = bqIdx
                while (j < bqLines.size && bqLines[j].trim().startsWith(">")) {
                    val qLine = bqLines[j].trim().replaceFirst(Regex("""^>\s?"""), "")
                    quoteLines.add(qLine)
                    j++
                }
                var quoteBody = quoteLines.joinToString("\n")
                quoteBody = escapeHtml(quoteBody)
                quoteBody = RE_BOLD_STAR.replace(quoteBody, "<b>$1</b>")
                quoteBody = RE_BOLD_UNDER.replace(quoteBody, "<b>$1</b>")
                quoteBody = RE_ITALIC_STAR.replace(quoteBody, "<i>$1</i>")
                quoteBody = RE_ITALIC_UNDER.replace(quoteBody, "<i>$1</i>")
                bqOut.add(addPlaceholder("<blockquote>$quoteBody</blockquote>"))
                bqIdx = j
            } else {
                bqOut.add(line)
                bqIdx++
            }
        }
        text = bqOut.joinToString("\n")

        // 6. Escape remaining HTML in normal text
        text = escapeHtml(text)

        // 7. Convert Markdown Headers
        text = RE_HEADING.replace(text) { match ->
            val content = match.groupValues[2].trim()
            "<b>$content</b>"
        }

        // 8. Convert Horizontal Rules
        text = RE_DIVIDER.replace(text, "────────── ✦ ──────────")

        // 9. Convert Bold: **text** or __text__
        text = RE_BOLD_STAR.replace(text, "<b>$1</b>")
        text = RE_BOLD_UNDER.replace(text, "<b>$1</b>")

        // 10. Convert Italic: *text* or _text_
        text = RE_ITALIC_STAR.replace(text, "<i>$1</i>")
        text = RE_ITALIC_UNDER.replace(text, "<i>$1</i>")

        // 11. Convert Strikethrough: ~~text~~
        text = RE_STRIKE.replace(text, "<s>$1</s>")

        // 12. Convert Bullet points: - item or * item -> • item
        text = RE_BULLET.replace(text, "• ")

        // 13. Convert Markdown Links: [text](url)
        text = RE_MD_LINK.replace(text, """<a href="$2">$1</a>""")

        // 14. Restore placeholders
        for (pIdx in placeholders.indices) {
            text = text.replace("@@TGBLOCK${pIdx}@@", placeholders[pIdx])
        }

        for (tIdx in existingTags.indices) {
            text = text.replace("@@EXISTINGTAG${tIdx}@@", existingTags[tIdx])
        }

        return text.trim()
    }

    fun chunkTelegramHtml(htmlText: String, maxLen: Int = 3900): List<String> {
        if (htmlText.length <= maxLen) {
            return listOf(htmlText)
        }

        val chunks = ArrayList<String>()
        val paragraphs = htmlText.split("\n\n")
        var current = StringBuilder()

        for (para in paragraphs) {
            val candidate = if (current.isEmpty()) para else current.toString() + "\n\n" + para
            if (candidate.length <= maxLen) {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(para)
            } else {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current = StringBuilder()
                }

                if (para.length <= maxLen) {
                    current.append(para)
                } else {
                    // Paragraph itself is huge, split by single lines
                    val subLines = para.split("\n")
                    for (line in subLines) {
                        val subCandidate = if (current.isEmpty()) line else current.toString() + "\n" + line
                        if (subCandidate.length <= maxLen) {
                            if (current.isNotEmpty()) current.append("\n")
                            current.append(line)
                        } else {
                            if (current.isNotEmpty()) {
                                chunks.add(current.toString())
                                current = StringBuilder()
                            }
                            if (line.length <= maxLen) {
                                current.append(line)
                            } else {
                                // Subline itself is longer than maxLen, chunk by character length
                                line.chunked(maxLen).forEach { c ->
                                    chunks.add(c)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }

        return if (chunks.isEmpty()) listOf(htmlText) else chunks
    }
}
