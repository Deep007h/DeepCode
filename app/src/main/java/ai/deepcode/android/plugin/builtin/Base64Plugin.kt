package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import com.google.gson.JsonObject
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class Base64Plugin : DeepCodePlugin {
    override val id: String = "base64"
    override val displayName: String = "Base64 Encoder"
    override val description: String = "Encode and decode base64 strings and files"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.UTILITY
    override val iconName: String = "swap_horiz"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("base64_encode", "Encode text to Base64", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string")
                ),
                "required" to listOf("text")
            )),
            Tool("base64_decode", "Decode Base64 to text", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "encoded" to mapOf("type" to "string")
                ),
                "required" to listOf("encoded")
            )),
            Tool("base64_file_encode", "Encode file to Base64", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_path" to mapOf("type" to "string")
                ),
                "required" to listOf("file_path")
            )),
            Tool("base64_file_decode", "Decode Base64 to file", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "encoded" to mapOf("type" to "string"),
                    "output_path" to mapOf("type" to "string")
                ),
                "required" to listOf("encoded", "output_path")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "base64_encode" -> {
                val text = args.get("text").asString
                Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            "base64_decode" -> {
                val encoded = args.get("encoded").asString
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            }
            "base64_file_encode" -> {
                val filePath = args.get("file_path").asString
                val file = File(filePath)
                if (!file.exists() || !file.isFile) throw Exception("File not found: $filePath")
                
                if (file.length() <= 512 * 1024L) {
                    val bytes = file.readBytes()
                    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    if (encoded.length > 1000) {
                        val outputDir = File(context.filesDir, "plugins/base64")
                        if (!outputDir.exists()) outputDir.mkdirs()
                        val outFile = File(outputDir, "${file.nameWithoutExtension}_base64.txt")
                        outFile.writeText(encoded)
                        "[file:${outFile.absolutePath}]"
                    } else {
                        encoded
                    }
                } else {
                    val outputDir = File(context.filesDir, "plugins/base64")
                    if (!outputDir.exists()) outputDir.mkdirs()
                    val outFile = File(outputDir, "${file.nameWithoutExtension}_base64.txt")
                    FileInputStream(file).use { fis ->
                        FileOutputStream(outFile).use { fos ->
                            android.util.Base64OutputStream(fos, Base64.NO_WRAP).use { b64os ->
                                fis.copyTo(b64os)
                            }
                        }
                    }
                    "[file:${outFile.absolutePath}]"
                }
            }
            "base64_file_decode" -> {
                var encoded = args.get("encoded").asString.trim()
                val rawOutputPath = args.get("output_path").asString.trim()
                
                if (encoded.contains(",")) {
                    val parts = encoded.split(",", limit = 2)
                    if (parts[0].startsWith("data:") && parts[0].endsWith(";base64")) {
                        encoded = parts[1].trim()
                    }
                }
                
                val outputDir = File(context.filesDir, "plugins/base64")
                if (!outputDir.exists()) outputDir.mkdirs()
                val outFile = if (File(rawOutputPath).isAbsolute) {
                    File(rawOutputPath)
                } else {
                    val sanitizedName = rawOutputPath.substringAfterLast("/").substringAfterLast("\\")
                    File(outputDir, sanitizedName.ifEmpty { "decoded_${System.currentTimeMillis()}" })
                }
                outFile.parentFile?.mkdirs()
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                FileOutputStream(outFile).use { it.write(bytes) }
                
                "[file:${outFile.absolutePath}]"
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
}
