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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile

class ZipPlugin : DeepCodePlugin {
    override val id: String = "zip"
    override val displayName: String = "Zip Tools"
    override val description: String = "Create, extract, and list zip files"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.UTILITY
    override val iconName: String = "folder_zip"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("zip_create", "Create a zip archive from files", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "files" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "output_name" to mapOf("type" to "string"),
                    "compression_level" to mapOf("type" to "integer", "default" to 6)
                ),
                "required" to listOf("files")
            )),
            Tool("zip_extract", "Extract a zip archive", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "zip_path" to mapOf("type" to "string"),
                    "output_dir" to mapOf("type" to "string")
                ),
                "required" to listOf("zip_path")
            )),
            Tool("zip_list", "List contents of a zip archive", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "zip_path" to mapOf("type" to "string")
                ),
                "required" to listOf("zip_path")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        val defaultOutputDir = File(context.filesDir, "plugins/zip")
        if (!defaultOutputDir.exists()) defaultOutputDir.mkdirs()

        return when (toolName) {
            "zip_create" -> {
                val files = args.getAsJsonArray("files").map { File(it.asString) }
                val outputName = if (args.has("output_name")) args.get("output_name").asString else "archive_${System.currentTimeMillis()}.zip"
                val compressionLevel = if (args.has("compression_level")) args.get("compression_level").asInt else 6
                
                val outputFile = File(defaultOutputDir, outputName)
                
                ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                    zos.setLevel(compressionLevel)
                    files.forEach { file ->
                        if (file.exists() && file.isFile) {
                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
                "[file:${outputFile.absolutePath}]"
            }
            "zip_extract" -> {
                val zipPath = args.get("zip_path").asString
                val zipFile = File(zipPath)
                if (!zipFile.exists()) throw Exception("Zip file not found")
                
                val outputDir = if (args.has("output_dir")) File(args.get("output_dir").asString) else File(defaultOutputDir, zipFile.nameWithoutExtension)
                if (!outputDir.exists()) outputDir.mkdirs()
                
                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.contains("../") || entry.name.contains("..\\")) {
                            throw SecurityException("Zip Slip vulnerability detected: invalid entry name ${entry.name}")
                        }
                        
                        val outFile = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                "[directory:${outputDir.absolutePath}]"
            }
            "zip_list" -> {
                val zipPath = args.get("zip_path").asString
                val file = File(zipPath)
                if (!file.exists()) throw Exception("Zip file not found")
                
                val jsonArray = JsonArray()
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val obj = JsonObject()
                        obj.addProperty("name", entry.name)
                        obj.addProperty("size", entry.size)
                        obj.addProperty("compressed_size", entry.compressedSize)
                        obj.addProperty("is_directory", entry.isDirectory)
                        jsonArray.add(obj)
                    }
                }
                jsonArray.toString()
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
}
