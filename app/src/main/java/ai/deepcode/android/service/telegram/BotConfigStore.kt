package ai.deepcode.android.service.telegram

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class BotConfig(
    val token: String,
    val label: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

class BotConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("telegram_bots", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getBots(): List<BotConfig> {
        val json = prefs.getString("bots", "[]") ?: "[]"
        val type = object : TypeToken<List<BotConfig>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveBots(bots: List<BotConfig>) {
        prefs.edit().putString("bots", gson.toJson(bots)).apply()
    }

    fun addBot(token: String) {
        val bots = getBots().toMutableList()
        bots.add(BotConfig(token = token))
        saveBots(bots)
    }

    fun removeBot(index: Int) {
        val bots = getBots().toMutableList()
        if (index in bots.indices) {
            bots.removeAt(index)
            saveBots(bots)
        }
    }

    fun getTokens(): List<String> = getBots().map { it.token }
}
