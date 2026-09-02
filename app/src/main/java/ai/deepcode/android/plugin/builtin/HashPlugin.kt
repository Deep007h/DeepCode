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
import java.security.MessageDigest

class HashPlugin : DeepCodePlugin {
    override val id: String = "hash"
    override val displayName: String = "Cryptographic Hashes"
    override val description: String = "Generate hashes for text and files"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.UTILITY
    override val iconName: String = "fingerprint"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("hash_text", "Hash a text string", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string"),
                    "algorithms" to mapOf("type" to "array", "items" to mapOf("type" to "string"))
                ),
                "required" to listOf("text")
            )),
            Tool("hash_file", "Hash a file", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_path" to mapOf("type" to "string"),
                    "algorithm" to mapOf("type" to "string", "default" to "sha256")
                ),
                "required" to listOf("file_path")
            )),
            Tool("hash_verify", "Verify file hash", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_path" to mapOf("type" to "string"),
                    "expected_hash" to mapOf("type" to "string"),
                    "algorithm" to mapOf("type" to "string", "default" to "sha256")
                ),
                "required" to listOf("file_path", "expected_hash", "algorithm")
            ))
        )
    }

    private fun getDigestName(algo: String): String {
        return when (algo.lowercase()) {
            "md5" -> "MD5"
            "sha1", "sha-1" -> "SHA-1"
            "sha256", "sha-256" -> "SHA-256"
            "sha384", "sha-384" -> "SHA-384"
            "sha512", "sha-512" -> "SHA-512"
            else -> throw IllegalArgumentException("Unsupported algorithm: $algo")
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "hash_text" -> {
                val text = args.get("text").asString
                val algos = if (args.has("algorithms")) {
                    args.getAsJsonArray("algorithms").map { it.asString }
                } else {
                    listOf("sha256")
                }
                
                val result = JsonObject()
                for (algo in algos) {
                    val md = MessageDigest.getInstance(getDigestName(algo))
                    val hash = md.digest(text.toByteArray(Charsets.UTF_8)).toHex()
                    result.addProperty(algo, hash)
                }
                result.toString()
            }
            "hash_file" -> {
                val filePath = args.get("file_path").asString
                val algo = if (args.has("algorithm")) args.get("algorithm").asString else "sha256"
                
                val file = File(filePath)
                if (!file.exists()) throw Exception("File not found")
                
                val md = MessageDigest.getInstance(getDigestName(algo))
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        md.update(buffer, 0, bytesRead)
                    }
                }
                
                val result = JsonObject()
                result.addProperty("hash", md.digest().toHex())
                result.addProperty("file_size", file.length())
                result.addProperty("algorithm", algo)
                result.toString()
            }
            "hash_verify" -> {
                val filePath = args.get("file_path").asString
                val expectedHash = args.get("expected_hash").asString
                val algo = args.get("algorithm").asString
                
                val file = File(filePath)
                if (!file.exists()) throw Exception("File not found")
                
                val md = MessageDigest.getInstance(getDigestName(algo))
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        md.update(buffer, 0, bytesRead)
                    }
                }
                
                val actualHash = md.digest().toHex()
                val isMatch = actualHash.equals(expectedHash, ignoreCase = true)
                
                val result = JsonObject()
                result.addProperty("match", isMatch)
                result.addProperty("actual", actualHash)
                result.addProperty("expected", expectedHash)
                result.toString()
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }
}
