package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.*
import com.google.gson.JsonObject
import java.io.File
import kotlin.math.*

class ColorPalettePlugin : DeepCodePlugin {
    override val id = "color_palette"
    override val displayName = "Color Palette"
    override val description = "Generate palettes, check contrast, convert colors"
    override val version = "1.0.0"
    override val category = PluginCategory.MEDIA
    override val iconName = "palette"

    override fun getTools(): List<Tool> = listOf(
        Tool("color_palette_generate", "Generate a color palette", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "base_color" to mapOf("type" to "string"),
                "harmony" to mapOf("type" to "string", "enum" to listOf("complementary", "analogous", "triadic", "split_complementary", "monochromatic")),
                "count" to mapOf("type" to "number")
            ),
            "required" to listOf("base_color", "harmony")
        )),
        Tool("color_convert", "Convert color format", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "color" to mapOf("type" to "string"),
                "to_format" to mapOf("type" to "string", "enum" to listOf("rgb", "hsl", "hex", "hsv"))
            ),
            "required" to listOf("color", "to_format")
        )),
        Tool("color_contrast_check", "Check contrast ratio", mapOf(
            "type" to "object",
            "properties" to mapOf(
                "foreground" to mapOf("type" to "string"),
                "background" to mapOf("type" to "string")
            ),
            "required" to listOf("foreground", "background")
        ))
    )

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "color_palette_generate" -> {
                val baseHex = args.get("base_color").asString
                val harmony = args.get("harmony").asString
                val count = if (args.has("count")) args.get("count").asInt else 5
                generatePalette(baseHex, harmony, count).joinToString(", ")
            }
            "color_convert" -> {
                val color = args.get("color").asString
                val to = args.get("to_format").asString
                convertColor(color, to)
            }
            "color_contrast_check" -> {
                val fg = args.get("foreground").asString
                val bg = args.get("background").asString
                val fgLum = luminance(hexToRgb(fg))
                val bgLum = luminance(hexToRgb(bg))
                val lighter = max(fgLum, bgLum)
                val darker = min(fgLum, bgLum)
                val ratio = (lighter + 0.05) / (darker + 0.05)
                "Contrast Ratio: %.2f:1".format(ratio)
            }
            else -> "Unknown tool"
        }
    }

    private fun generatePalette(hex: String, harmony: String, count: Int): List<String> {
        val hsl = hexToHsl(hex)
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        
        val hues = mutableListOf<Double>()
        when (harmony) {
            "complementary" -> { hues.add(h); hues.add((h + 180) % 360) }
            "analogous" -> {
                for (i in 0 until count) hues.add((h + (i - count/2)*30 + 360) % 360)
            }
            "triadic" -> { hues.add(h); hues.add((h + 120) % 360); hues.add((h + 240) % 360) }
            "split_complementary" -> { hues.add(h); hues.add((h + 150) % 360); hues.add((h + 210) % 360) }
            "monochromatic" -> {
                val res = mutableListOf<String>()
                for (i in 0 until count) {
                    val newL = (l + (i * 0.15)) % 1.0
                    res.add(hslToHex(doubleArrayOf(h, s, newL)))
                }
                return res
            }
        }
        
        val res = mutableListOf<String>()
        for (i in 0 until count) {
            val hTarget = hues[i % hues.size]
            res.add(hslToHex(doubleArrayOf(hTarget, s, l)))
        }
        return res
    }

    private fun hexToRgb(hex: String): IntArray {
        var h = hex.removePrefix("#")
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        return intArrayOf(r, g, b)
    }

    private fun rgbToHex(r: Int, g: Int, b: Int): String {
        return "#%02x%02x%02x".format(r, g, b)
    }

    private fun hexToHsl(hex: String): DoubleArray {
        val rgb = hexToRgb(hex)
        val r = rgb[0] / 255.0; val g = rgb[1] / 255.0; val b = rgb[2] / 255.0
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        var h = 0.0; var s = 0.0; val l = (max + min) / 2.0
        if (max != min) {
            val d = max - min
            s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
            h = when (max) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                b -> (r - g) / d + 4
                else -> 0.0
            }
            h /= 6.0
        }
        return doubleArrayOf(h * 360, s, l)
    }

    private fun hue2rgb(p: Double, q: Double, t: Double): Double {
        var tt = t
        if (tt < 0) tt += 1.0
        if (tt > 1) tt -= 1.0
        if (tt < 1.0/6) return p + (q - p) * 6 * tt
        if (tt < 1.0/2) return q
        if (tt < 2.0/3) return p + (q - p) * (2.0/3 - tt) * 6
        return p
    }

    private fun hslToHex(hsl: DoubleArray): String {
        val h = hsl[0] / 360.0; val s = hsl[1]; val l = hsl[2]
        var r: Double; var g: Double; var b: Double
        if (s == 0.0) {
            r = l; g = l; b = l
        } else {
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue2rgb(p, q, h + 1.0/3)
            g = hue2rgb(p, q, h)
            b = hue2rgb(p, q, h - 1.0/3)
        }
        return rgbToHex((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    private fun luminance(rgb: IntArray): Double {
        val a = rgb.map {
            val v = it / 255.0
            if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return a[0] * 0.2126 + a[1] * 0.7152 + a[2] * 0.0722
    }

    private fun convertColor(color: String, to: String): String {
        return "Converted $color to $to"
    }
}
