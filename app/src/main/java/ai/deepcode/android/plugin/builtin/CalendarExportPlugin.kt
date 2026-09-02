package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CalendarExportPlugin : DeepCodePlugin {
    override val id = "calendar_export"
    override val displayName = "Calendar Export"
    override val description = "Export events to ICS format"
    override val version = "1.0.0"
    override val category = PluginCategory.PRODUCTIVITY
    override val iconName = "event"

    override fun getTools(): List<Tool> = listOf(
        Tool("calendar_create_event", "Create ICS calendar event", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "summary" to mapOf("type" to "string"),
                "dtstart" to mapOf("type" to "string"),
                "dtend" to mapOf("type" to "string"),
                "location" to mapOf("type" to "string"),
                "description" to mapOf("type" to "string")
            ),
            "required" to listOf("summary", "dtstart", "dtend")
        )),
        Tool("calendar_create_recurring", "Create recurring event", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "summary" to mapOf("type" to "string"),
                "dtstart" to mapOf("type" to "string"),
                "dtend" to mapOf("type" to "string"),
                "rrule" to mapOf("type" to "string")
            ),
            "required" to listOf("summary", "dtstart", "dtend", "rrule")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        val summary = args.get("summary").asString
        val dtstart = formatToIcalDate(args.get("dtstart").asString)
        val dtend = formatToIcalDate(args.get("dtend").asString)
        val location = if (args.has("location")) args.get("location").asString else ""
        val desc = if (args.has("description")) args.get("description").asString else ""
        val rrule = if (args.has("rrule")) "RRULE:${args.get("rrule").asString}\n" else ""

        val uid = UUID.randomUUID().toString()
        val dtstamp = formatToIcalDate(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))

        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//OpenCode//Android//EN
            BEGIN:VEVENT
            UID:$uid
            DTSTAMP:$dtstamp
            DTSTART:$dtstart
            DTEND:$dtend
            SUMMARY:$summary
            LOCATION:$location
            DESCRIPTION:$desc
            $rrule
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Reminder
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\r\n")

        val outDir = File(context.filesDir, "plugins/calendar_export")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "${summary.replace(" ", "_")}.ics")
        outFile.writeText(ics)
        return "Event saved to ${outFile.absolutePath}"
    }

    private fun formatToIcalDate(isoDate: String): String {
        return isoDate.replace("-", "").replace(":", "").replace("T", "T")
    }
}
