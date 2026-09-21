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

    @Synchronized
    fun getBots(): List<BotConfig> {
        val json = prefs.getString("bots", "[]") ?: "[]"
        val type = object : TypeToken<List<BotConfig>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveBots(bots: List<BotConfig>) {
        prefs.edit().putString("bots", gson.toJson(bots)).apply()
    }

    @Synchronized
    fun addBot(token: String, label: String = "") {
        val clean = token.trim()
        if (clean.isEmpty()) return
        val bots = getBots().toMutableList()
        if (bots.any { it.token == clean }) return
        bots.add(BotConfig(token = clean, label = label))
        saveBots(bots)
    }

    @Synchronized
    fun removeBot(index: Int) {
        val bots = getBots().toMutableList()
        if (index in bots.indices) {
            bots.removeAt(index)
            saveBots(bots)
        }
    }

    @Synchronized
    fun removeBotByToken(token: String) {
        val clean = token.trim()
        val bots = getBots().filterNot { it.token == clean }
        saveBots(bots)
    }

    @Synchronized
    fun getTokens(): List<String> = getBots().map { it.token }
}
