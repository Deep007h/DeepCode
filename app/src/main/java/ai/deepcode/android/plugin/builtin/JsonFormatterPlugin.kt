package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonElement

class JsonFormatterPlugin : DeepCodePlugin {
    override val id: String = "json_formatter"
    override val displayName: String = "JSON Formatter"
    override val description: String = "Format, minify, and query JSON strings"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.DATA
    override val iconName: String = "data_object"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("json_format", "Format a JSON string with indentation", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "json" to mapOf("type" to "string"),
                    "indent" to mapOf("type" to "integer", "default" to 2)
                ),
                "required" to listOf("json")
            )),
            Tool("json_validate", "Validate a JSON string", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "json" to mapOf("type" to "string")
                ),
                "required" to listOf("json")
            )),
            Tool("json_minify", "Minify a JSON string", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "json" to mapOf("type" to "string")
                ),
                "required" to listOf("json")
            )),
            Tool("json_query", "Query a value inside a JSON string", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "json" to mapOf("type" to "string"),
                    "path" to mapOf("type" to "string", "description" to "e.g. users[0].name")
                ),
                "required" to listOf("json", "path")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "json_format" -> {
                val jsonStr = args.get("json").asString
                val indent = if (args.has("indent")) args.get("indent").asInt else 2
                try {
                    val element = JsonParser.parseString(jsonStr)
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    gson.toJson(element)
                } catch (e: Exception) {
                    throw Exception("Invalid JSON: ${e.message}")
                }
            }
            "json_validate" -> {
                val jsonStr = args.get("json").asString
                val result = JsonObject()
                try {
                    val element = JsonParser.parseString(jsonStr)
                    result.addProperty("valid", true)
                    if (element.isJsonObject) {
                        result.addProperty("type", "object")
                        result.addProperty("key_count", element.asJsonObject.keySet().size)
                    } else if (element.isJsonArray) {
                        result.addProperty("type", "array")
                        result.addProperty("item_count", element.asJsonArray.size())
                    } else {
                        result.addProperty("type", "primitive")
                    }
                } catch (e: Exception) {
                    result.addProperty("valid", false)
                    result.addProperty("details", e.message)
                }
                result.toString()
            }
            "json_minify" -> {
                val jsonStr = args.get("json").asString
                try {
                    val element = JsonParser.parseString(jsonStr)
                    Gson().toJson(element)
                } catch (e: Exception) {
                    throw Exception("Invalid JSON: ${e.message}")
                }
            }
            "json_query" -> {
                val jsonStr = args.get("json").asString
                val path = args.get("path").asString
                try {
                    var element: JsonElement = JsonParser.parseString(jsonStr)
                    
                    val parts = path.split(Regex("\\.|(?=\\[)")).filter { it.isNotEmpty() }
                    
                    for (part in parts) {
                        if (part.startsWith("[")) {
                            val idxStr = part.removeSurrounding("[", "]")
                            val idx = idxStr.toIntOrNull() ?: throw Exception("Invalid array index: $part")
                            if (!element.isJsonArray) throw Exception("Expected array at $part")
                            val arr = element.asJsonArray
                            if (idx < 0 || idx >= arr.size()) throw Exception("Index out of bounds: $idx")
                            element = arr.get(idx)
                        } else {
                            if (!element.isJsonObject) throw Exception("Expected object at $part")
                            element = element.asJsonObject.get(part) ?: throw Exception("Key not found: $part")
                        }
                    }
                    element.toString()
                } catch (e: Exception) {
                    throw Exception("Query failed: ${e.message}")
                }
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
}
