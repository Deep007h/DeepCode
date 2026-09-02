package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale

class TextTransformPlugin : DeepCodePlugin {
    override val id = "text_transform"
    override val displayName = "Text Transform"
    override val description = "Text manipulation utilities"
    override val version = "1.0.0"
    override val category = PluginCategory.UTILITY
    override val iconName = "text_format"

    override fun getTools(): List<Tool> = listOf(
        Tool("text_transform", "Transform text", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "text" to mapOf("type" to "string"),
                "operation" to mapOf("type" to "string", "enum" to listOf(
                    "uppercase", "lowercase", "title_case", "camel_case", "snake_case",
                    "kebab_case", "pascal_case", "reverse", "word_count", "line_count",
                    "char_frequency", "remove_duplicates", "sort_lines", "trim_lines", "slug"
                ))
            ),
            "required" to listOf("text", "operation")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        val text = args.get("text").asString
        val op = args.get("operation").asString

        return when (op) {
            "uppercase" -> text.uppercase(Locale.ROOT)
            "lowercase" -> text.lowercase(Locale.ROOT)
            "title_case" -> text.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            "camel_case" -> text.split(Regex("[\\s_\\-]+")).mapIndexed { i, s -> if (i == 0) s.lowercase() else s.replaceFirstChar { c -> c.uppercase() } }.joinToString("")
            "snake_case" -> text.split(Regex("[\\s\\-]+")).joinToString("_") { it.lowercase() }
            "kebab_case" -> text.split(Regex("[\\s_]+")).joinToString("-") { it.lowercase() }
            "pascal_case" -> text.split(Regex("[\\s_\\-]+")).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            "reverse" -> text.reversed()
            "word_count" -> text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size.toString()
            "line_count" -> text.lines().size.toString()
            "char_frequency" -> text.groupingBy { it }.eachCount().toString()
            "remove_duplicates" -> text.lines().distinct().joinToString("\n")
            "sort_lines" -> text.lines().sorted().joinToString("\n")
            "trim_lines" -> text.lines().joinToString("\n") { it.trim() }
            "slug" -> text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            else -> "Unknown operation"
        }
    }
}
