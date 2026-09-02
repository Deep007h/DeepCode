package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request

class QrCodePlugin : DeepCodePlugin {
    override val id: String = "qr_code"
    override val displayName: String = "QR Code"
    override val description: String = "Generate and read QR codes"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.UTILITY
    override val iconName: String = "qr_code_2"

    private val client = OkHttpClient()
    
    override fun getTools(): List<Tool> {
        return listOf(
            Tool("qr_generate", "Generate a QR code image from data", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "data" to mapOf("type" to "string", "description" to "Data to encode in the QR code"),
                    "size" to mapOf("type" to "integer", "description" to "Size of the QR code in pixels (default 512)"),
                    "foreground" to mapOf("type" to "string", "description" to "Foreground color in hex (default 000000)"),
                    "background" to mapOf("type" to "string", "description" to "Background color in hex (default FFFFFF)")
                ),
                "required" to listOf("data")
            )),
            Tool("qr_read", "Read a QR code from an image", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "image_path" to mapOf("type" to "string", "description" to "Path to the QR code image")
                ),
                "required" to listOf("image_path")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "qr_generate" -> {
                val data = args.get("data")?.asString ?: throw IllegalArgumentException("data is required")
                val size = if (args.has("size")) args.get("size").asInt else 512
                val foreground = if (args.has("foreground")) args.get("foreground").asString.replace("#", "") else "000000"
                val background = if (args.has("background")) args.get("background").asString.replace("#", "") else "FFFFFF"

                val urlEncodedData = URLEncoder.encode(data, "UTF-8")
                val url = "https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=$urlEncodedData&color=$foreground&bgcolor=$background"

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to download QR code: ${response.code}")
                    val bytes = response.body?.bytes() ?: throw Exception("Empty response body")
                    
                    val outputDir = File(context.filesDir, "plugins/qr_code")
                    if (!outputDir.exists()) outputDir.mkdirs()
                    
                    val file = File(outputDir, "qr_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { it.write(bytes) }
                    
                    "[image:${file.absolutePath}]"
                }
            }
            "qr_read" -> {
                "QR reading requires the ZXing library to be integrated into the app. Please ensure it's available or use an external service."
            }
            else -> throw IllegalArgumentException("Unknown tool: $toolName")
        }
    }

    override fun getConfigFields(): List<PluginConfigField> {
        return listOf(
            PluginConfigField("default_size", "Default Size", ConfigFieldType.DROPDOWN, "512", listOf("256", "512", "1024"))
        )
    }
}
