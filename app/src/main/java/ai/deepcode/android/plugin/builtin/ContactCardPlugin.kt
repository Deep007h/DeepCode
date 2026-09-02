package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File

class ContactCardPlugin : DeepCodePlugin {
    override val id = "contact_card"
    override val displayName = "Contact Card"
    override val description = "Create vCard files"
    override val version = "1.0.0"
    override val category = PluginCategory.PRODUCTIVITY
    override val iconName = "contact_page"

    override fun getTools(): List<Tool> = listOf(
        Tool("contact_create_vcard", "Create vCard 3.0", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "phone" to mapOf("type" to "string"),
                "email" to mapOf("type" to "string"),
                "organization" to mapOf("type" to "string"),
                "title" to mapOf("type" to "string"),
                "website" to mapOf("type" to "string"),
                "address" to mapOf("type" to "string"),
                "birthday" to mapOf("type" to "string"),
                "notes" to mapOf("type" to "string")
            ),
            "required" to listOf("name")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        val name = args.get("name").asString
        val parts = name.split(" ")
        val fn = name
        val n = if (parts.size > 1) "${parts.last()};${parts.dropLast(1).joinToString(" ")};;;" else "$name;;;;"

        val sb = java.lang.StringBuilder()
        sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        sb.append("FN:$fn\r\nN:$n\r\n")

        if (args.has("phone")) sb.append("TEL;TYPE=CELL:${args.get("phone").asString}\r\n")
        if (args.has("email")) sb.append("EMAIL;TYPE=INTERNET:${args.get("email").asString}\r\n")
        if (args.has("organization")) sb.append("ORG:${args.get("organization").asString}\r\n")
        if (args.has("title")) sb.append("TITLE:${args.get("title").asString}\r\n")
        if (args.has("website")) sb.append("URL:${args.get("website").asString}\r\n")
        if (args.has("address")) sb.append("ADR;TYPE=HOME:;;${args.get("address").asString};;;;\r\n")
        if (args.has("birthday")) sb.append("BDAY:${args.get("birthday").asString}\r\n")
        if (args.has("notes")) sb.append("NOTE:${args.get("notes").asString}\r\n")
        
        sb.append("END:VCARD\r\n")

        val outDir = File(context.filesDir, "plugins/contact_card")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "${name.replace(" ", "_")}.vcf")
        outFile.writeText(sb.toString())
        
        return "vCard saved to ${outFile.absolutePath}"
    }
}
