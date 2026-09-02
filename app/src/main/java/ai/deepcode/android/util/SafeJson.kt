package ai.deepcode.android.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

object SafeJson {

    fun obj(json: JsonElement?, key: String): JsonObject? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonObject) null else el.asJsonObject
    }

    fun arr(json: JsonElement?, key: String): JsonArray? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonArray) null else el.asJsonArray
    }

    fun string(json: JsonElement?, key: String): String? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonPrimitive) null else el.asString
    }

    fun string(json: JsonElement?, key: String, default: String): String {
        return string(json, key) ?: default
    }

    fun long(json: JsonElement?, key: String): Long? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonPrimitive) null else el.asLong
    }

    fun int(json: JsonElement?, key: String): Int? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonPrimitive) null else el.asInt
    }

    fun bool(json: JsonElement?, key: String): Boolean? {
        if (json == null || !json.isJsonObject) return null
        val el = json.asJsonObject.get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonPrimitive) null else el.asBoolean
    }

    fun bool(json: JsonElement?, key: String, default: Boolean): Boolean {
        return bool(json, key) ?: default
    }

    fun get(obj: JsonObject, key: String): SafeElement {
        return SafeElement(obj.get(key))
    }

    fun parse(text: String): JsonObject? {
        return try {
            com.google.gson.Gson().fromJson(text, JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun parseArray(text: String): JsonArray? {
        return try {
            com.google.gson.Gson().fromJson(text, JsonArray::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

class SafeElement(private val element: JsonElement?) {

    fun asString(): String? {
        return if (element is JsonPrimitive) element.asString else null
    }

    fun asString(default: String): String = asString() ?: default

    fun asJsonObject(): JsonObject? {
        return if (element is JsonObject) element else null
    }

    fun asJsonArray(): JsonArray? {
        return if (element is JsonArray) element else null
    }

    fun asLong(): Long? {
        return if (element is JsonPrimitive) element.asLong else null
    }

    fun asInt(): Int? {
        return if (element is JsonPrimitive) element.asInt else null
    }

    fun asBoolean(): Boolean? {
        return if (element is JsonPrimitive) element.asBoolean else null
    }

    fun asBoolean(default: Boolean): Boolean = asBoolean() ?: default

    fun isNull(): Boolean = element == null || element.isJsonNull
}
