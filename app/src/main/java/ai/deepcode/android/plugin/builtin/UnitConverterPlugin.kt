package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale

class UnitConverterPlugin : DeepCodePlugin {
    override val id = "unit_converter"
    override val displayName = "Unit Converter"
    override val description = "Convert between different units of measurement"
    override val version = "1.0.0"
    override val category = PluginCategory.UTILITY
    override val iconName = "straighten"

    private val lengthFactors = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "inch" to 0.0254, "foot" to 0.3048, "yard" to 0.9144, "mile" to 1609.344
    )
    private val weightFactors = mapOf(
        "mg" to 0.001, "g" to 1.0, "kg" to 1000.0,
        "lb" to 453.59237, "oz" to 28.34952, "ton" to 1000000.0
    )
    private val volumeFactors = mapOf(
        "ml" to 1.0, "l" to 1000.0, "cup" to 240.0,
        "pint" to 473.176, "gallon" to 3785.41, "fl_oz" to 29.5735
    )
    private val areaFactors = mapOf(
        "sqm" to 1.0, "sqft" to 0.092903, "acre" to 4046.86, "hectare" to 10000.0
    )
    private val speedFactors = mapOf(
        "m/s" to 1.0, "km/h" to 0.277778, "mph" to 0.44704, "knots" to 0.514444
    )
    private val dataFactors = mapOf(
        "bytes" to 1.0, "kb" to 1024.0, "mb" to 1048576.0,
        "gb" to 1073741824.0, "tb" to 1099511627776.0, "pb" to 1125899906842624.0
    )
    private val timeFactors = mapOf(
        "seconds" to 1.0, "minutes" to 60.0, "hours" to 3600.0,
        "days" to 86400.0, "weeks" to 604800.0
    )

    private val categories = listOf(
        lengthFactors, weightFactors, volumeFactors, areaFactors,
        speedFactors, dataFactors, timeFactors
    )

    override fun getTools(): List<Tool> = listOf(
        Tool("convert_unit", "Convert a value from one unit to another", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "value" to mapOf("type" to "number"),
                "from" to mapOf("type" to "string"),
                "to" to mapOf("type" to "string")
            ),
            "required" to listOf("value", "from", "to")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        if (toolName != "convert_unit") return "Unknown tool"
        val value = args.get("value").asDouble
        val from = args.get("from").asString.lowercase(Locale.ROOT)
        val to = args.get("to").asString.lowercase(Locale.ROOT)

        if (isTemperature(from) || isTemperature(to)) {
            if (!isTemperature(from) || !isTemperature(to)) return "Cannot mix temperature with other units"
            return convertTemperature(value, from, to).toString()
        }

        for (cat in categories) {
            if (cat.containsKey(from) && cat.containsKey(to)) {
                val fromBase = cat[from]!!
                val toBase = cat[to]!!
                val inBase = value * fromBase
                val result = inBase / toBase
                return result.toString()
            }
        }
        return "Invalid or incompatible units"
    }

    private fun isTemperature(u: String) = u in listOf("celsius", "fahrenheit", "kelvin", "c", "f", "k")

    private fun convertTemperature(v: Double, from: String, to: String): Double {
        val c = when (from) {
            "fahrenheit", "f" -> (v - 32) * 5 / 9
            "kelvin", "k" -> v - 273.15
            else -> v
        }
        return when (to) {
            "fahrenheit", "f" -> c * 9 / 5 + 32
            "kelvin", "k" -> c + 273.15
            else -> c
        }
    }
}
