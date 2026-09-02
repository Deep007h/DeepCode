package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import java.io.File
import java.io.FileWriter
import java.io.FileReader
import java.io.BufferedReader

class CsvPlugin : DeepCodePlugin {
    override val id: String = "csv"
    override val displayName: String = "CSV Utilities"
    override val description: String = "Create, parse, and convert CSV files"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.DOCUMENT
    override val iconName: String = "table_chart"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("csv_create", "Create a CSV file from data", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "headers" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "rows" to mapOf("type" to "array", "items" to mapOf("type" to "array", "items" to mapOf("type" to "string"))),
                    "filename" to mapOf("type" to "string"),
                    "delimiter" to mapOf("type" to "string", "default" to ",")
                ),
                "required" to listOf("headers", "rows")
            )),
            Tool("csv_parse", "Parse a CSV file and get a summary", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_path" to mapOf("type" to "string"),
                    "delimiter" to mapOf("type" to "string", "default" to ",")
                ),
                "required" to listOf("file_path")
            )),
            Tool("csv_to_json", "Convert a CSV file to JSON format", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_path" to mapOf("type" to "string")
                ),
                "required" to listOf("file_path")
            ))
        )
    }

    private fun escapeCsvField(field: String, delimiter: String): String {
        val needsQuotes = field.contains(delimiter) || field.contains("\"") || field.contains("\n") || field.contains("\r")
        if (!needsQuotes) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        val outputDir = File(context.filesDir, "plugins/csv")
        if (!outputDir.exists()) outputDir.mkdirs()

        return when (toolName) {
            "csv_create" -> {
                val headers = args.getAsJsonArray("headers")
                val rows = args.getAsJsonArray("rows")
                val filename = if (args.has("filename")) args.get("filename").asString else "data_${System.currentTimeMillis()}.csv"
                val delimiter = if (args.has("delimiter")) args.get("delimiter").asString else ","
                
                val file = File(outputDir, filename)
                FileWriter(file).use { writer ->
                    val headerLine = headers.map { escapeCsvField(it.asString, delimiter) }.joinToString(delimiter)
                    writer.write(headerLine + "\n")
                    
                    rows.forEach { rowEl ->
                        val row = rowEl.asJsonArray
                        val rowLine = row.map { escapeCsvField(it.asString, delimiter) }.joinToString(delimiter)
                        writer.write(rowLine + "\n")
                    }
                }
                "[file:${file.absolutePath}]"
            }
            "csv_parse" -> {
                val filePath = args.get("file_path").asString
                val delimiter = if (args.has("delimiter")) args.get("delimiter").asString else ","
                val file = File(filePath)
                if (!file.exists()) throw Exception("File not found: $filePath")
                
                var rowCount = 0
                var headers: List<String> = emptyList()
                val firstRows = mutableListOf<List<String>>()
                
                BufferedReader(FileReader(file)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine != null) {
                        headers = headerLine.split(delimiter)
                        var line = reader.readLine()
                        while (line != null) {
                            rowCount++
                            if (firstRows.size < 5) {
                                firstRows.add(line.split(delimiter))
                            }
                            line = reader.readLine()
                        }
                    }
                }
                
                val summary = JsonObject()
                summary.addProperty("row_count", rowCount)
                summary.addProperty("column_count", headers.size)
                
                val headersArray = JsonArray()
                headers.forEach { headersArray.add(it) }
                summary.add("headers", headersArray)
                
                val rowsArray = JsonArray()
                firstRows.forEach { row ->
                    val rowJson = JsonArray()
                    row.forEach { rowJson.add(it) }
                    rowsArray.add(rowJson)
                }
                summary.add("first_5_rows", rowsArray)
                
                summary.toString()
            }
            "csv_to_json" -> {
                val filePath = args.get("file_path").asString
                val delimiter = "," // Assuming comma for simplicity
                val file = File(filePath)
                if (!file.exists()) throw Exception("File not found: $filePath")
                
                val jsonArray = JsonArray()
                BufferedReader(FileReader(file)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine != null) {
                        val headers = headerLine.split(delimiter)
                        var line = reader.readLine()
                        while (line != null) {
                            val values = line.split(delimiter)
                            val obj = JsonObject()
                            for (i in headers.indices) {
                                val key = headers[i].trim().removeSurrounding("\"")
                                val value = if (i < values.size) values[i].trim().removeSurrounding("\"") else ""
                                obj.addProperty(key, value)
                            }
                            jsonArray.add(obj)
                            line = reader.readLine()
                        }
                    }
                }
                
                val outFile = File(outputDir, file.nameWithoutExtension + ".json")
                FileWriter(outFile).use { it.write(jsonArray.toString()) }
                "[file:${outFile.absolutePath}]"
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
}
