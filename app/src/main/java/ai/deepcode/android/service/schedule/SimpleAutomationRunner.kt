package ai.deepcode.android.service.schedule

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProvider
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.domain.model.ToolCall
import ai.deepcode.android.service.gmail.GmailHandler
import ai.deepcode.android.service.github.GitHubHandler
import ai.deepcode.android.service.music.MusicDetectionHandler
import ai.deepcode.android.util.AppLogger
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

class SimpleAutomationRunner(private val context: Context) {

    private val securePrefs = EncryptedPrefs.getInstance(context)
    private val gson = Gson()

    private val webSearchTool = Tool("web_search", "Search the web for current information. Use this for news, weather, research, or any real-time data query. Supports multiple search providers with automatic fallback.", mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "The search query (e.g. 'weather in London', 'latest AI news', 'Python tutorial')"),
            "numResults" to mapOf("type" to "number", "description" to "Number of search results to return (default: 8)"),
            "livecrawl" to mapOf("type" to "string", "enum" to listOf("fallback", "preferred"), "description" to "Whether to attempt live crawling of search results: 'fallback' (use cached if available, crawl otherwise) or 'preferred' (always crawl live)"),
            "type" to mapOf("type" to "string", "enum" to listOf("auto", "fast", "deep"), "description" to "Search type: 'auto' (balanced), 'fast' (quick results), 'deep' (comprehensive search)"),
            "contextMaxCharacters" to mapOf("type" to "number", "description" to "Maximum characters for each result's context string (default: 10000)")
        ),
        "required" to listOf("query")
    ))

    private val cryptoPriceTool = Tool("get_crypto_price", "Get the current price of any cryptocurrency. Use this when the user asks for BTC, Bitcoin, Ethereum, crypto price, or any cryptocurrency price.", mapOf(
        "type" to "object",
        "properties" to mapOf(
            "coin" to mapOf("type" to "string", "description" to "Cryptocurrency name or ticker (e.g. bitcoin, btc, ethereum, eth, solana, sol, dogecoin, doge)")
        ),
        "required" to listOf("coin")
    ))

    private data class ProviderEntry(
        val provider: AIProvider,
        val modelId: String,
        val apiKey: String,
        val customUrl: String?
    )

    private data class ApiResult(
        val text: String,
        val toolCalls: List<ToolCall>
    )

    private suspend fun executeOfflineHermesAgent(actionPrompt: String): String {
        val lower = actionPrompt.lowercase()

        // 1. Gmail handler
        val gmailResponse = try {
            GmailHandler(context).fetch(actionPrompt)
        } catch (e: Exception) { "" }
        if (gmailResponse.isNotEmpty()) {
            return gmailResponse
        }

        // 2. GitHub handler
        val githubResponse = try {
            GitHubHandler(context).fetch(actionPrompt)
        } catch (e: Exception) { "" }
        if (githubResponse.isNotEmpty()) {
            return githubResponse
        }

        // 3. YouTube Music handler
        val musicMsg = try {
            MusicDetectionHandler(context).play(actionPrompt)
        } catch (e: Exception) { "" }
        if (musicMsg.isNotEmpty()) {
            return musicMsg
        }

        // 4. Crypto price queries
        val cryptoMatch = Regex("""\b(btc|bitcoin|eth|ethereum|sol|solana|crypto|price)\b""", RegexOption.IGNORE_CASE)
        if (cryptoMatch.containsMatchIn(lower)) {
            val coin = when {
                lower.contains("btc") || lower.contains("bitcoin") -> "bitcoin"
                lower.contains("eth") || lower.contains("ethereum") -> "ethereum"
                lower.contains("sol") || lower.contains("solana") -> "solana"
                lower.contains("doge") || lower.contains("dogecoin") -> "dogecoin"
                else -> {
                    val words = lower.split("\\s+".toRegex())
                    val idx = words.indexOfFirst { it in listOf("price", "crypto") }
                    if (idx + 1 < words.size) words[idx + 1] else "bitcoin"
                }
            }
            return fetchCryptoPrice(coin)
        }

        // 5. News / Weather / Web Search queries
        if (lower.contains("news") || lower.contains("headline") || lower.contains("search") || lower.contains("weather")) {
            val query = if (lower.contains("weather")) actionPrompt else "latest ${actionPrompt.take(50)}"
            return fetchWebSearchResults(query)
        }

        // 6. Default fallback
        return executeDirectFallback(actionPrompt)
    }

    private suspend fun prefetchCryptoPrice(prompt: String): String? {
        val lower = prompt.lowercase()
        val coin = when {
            Regex("""\b(btc|bitcoin)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "bitcoin"
            Regex("""\b(eth|ethereum)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "ethereum"
            Regex("""\b(sol|solana)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "solana"
            Regex("""\b(doge|dogecoin)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "dogecoin"
            lower.contains("crypto") || lower.contains("price") -> {
                val words = lower.split("\\s+".toRegex())
                val idx = words.indexOfFirst { it in listOf("price", "crypto") }
                if (idx + 1 < words.size && words[idx + 1].length <= 10) words[idx + 1] else null
            }
            else -> null
        }
        return if (coin != null) fetchCryptoPrice(coin) else null
    }

    suspend fun run(actionPrompt: String, telegramChatId: String?): String {
        val providers = buildFallbackChain()
        if (providers.isNotEmpty()) {
            val cryptoContext = prefetchCryptoPrice(actionPrompt)
            val systemContent = buildString {
                append("You are the morning briefing and automation agent. You review the user's upcoming day, read the retrieved search results or task updates, and deliver a concise, formatted summary covering the information. Deliver a beautiful summary of the news, weather, or updates retrieved.")
                if (cryptoContext != null) {
                    append("\n\nIMPORTANT: Live cryptocurrency price data has already been fetched below. DO NOT call web_search or any price tool — just analyze and present this data nicely:\n$cryptoContext")
                }
            }
            val systemMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = "automation",
                role = "system",
                content = systemContent,
                timestamp = System.currentTimeMillis()
            )
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = "automation",
                role = "user",
                content = actionPrompt,
                timestamp = System.currentTimeMillis()
            )
            val messages = mutableListOf(systemMessage, userMessage)

            var iterations = 0
            var finalResponse: String? = null

            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++
                var apiResult: ApiResult? = null
                for (provider in providers) {
                    apiResult = tryCallProvider(provider, messages)
                    if (apiResult != null) break
                }

                if (apiResult == null) {
                    AppLogger.w("SimpleAutomationRunner", "All providers failed. Falling back to offline agent.")
                    break
                }

                if (apiResult.toolCalls.isEmpty()) {
                    finalResponse = apiResult.text
                    break
                }

                // Append assistant message
                messages.add(Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = "automation",
                    role = "assistant",
                    content = apiResult.text,
                    timestamp = System.currentTimeMillis(),
                    isToolCall = true,
                    toolCallsJson = gson.toJson(apiResult.toolCalls)
                ))

                // Execute tool calls
                for (tc in apiResult.toolCalls) {
                    val toolOutput = executeSimpleTool(tc)
                    messages.add(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = "automation",
                        role = "tool",
                        content = toolOutput,
                        timestamp = System.currentTimeMillis(),
                        toolCallsJson = tc.id,
                        toolResultsJson = toolOutput
                    ))
                }
            }

            if (!finalResponse.isNullOrBlank()) {
                return sendAndReturn(finalResponse, telegramChatId)
            }
        }

        val response = executeOfflineHermesAgent(actionPrompt)
        return sendAndReturn(response, telegramChatId)
    }

    private suspend fun tryCallProvider(entry: ProviderEntry, messages: List<Message>): ApiResult? {
        var lastError: String? = null

        // Hermes-agent pattern: inner retry loop with jittered backoff
        for (retry in 0 until MAX_RETRIES) {
            val toolCalls = mutableListOf<ToolCall>()
            val sb = StringBuilder()

            try {
                withContext(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        entry.provider.streamCompletion(
                            messages = messages,
                            model = entry.modelId,
                            tools = listOf(webSearchTool, cryptoPriceTool),
                            apiKey = entry.apiKey,
                            customBaseUrl = entry.customUrl,
                            onToken = { sb.append(it) },
                            onToolCall = { toolCalls.add(it) },
                            onComplete = {},
                            onError = {}
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
                if (retry < MAX_RETRIES - 1) {
                    delay(jitteredBackoffMs(retry))
                }
                continue
            }

            // Even if onError was called (e.g., Zen mock fallback), check for output
            if (sb.isNotEmpty() || toolCalls.isNotEmpty()) {
                return ApiResult(sb.toString(), toolCalls)
            }

            lastError = "No output from provider"
            if (retry < MAX_RETRIES - 1) {
                delay(jitteredBackoffMs(retry))
            }
        }

        AppLogger.e("SimpleAutomationRunner", "Provider ${entry.provider.name} failed after retries: ${lastError ?: "unknown"}")
        return null
    }

    private fun buildFallbackChain(): List<ProviderEntry> {
        val primary = resolveProvider()
        val seen = mutableSetOf<String>()
        val entries = mutableListOf<ProviderEntry>()

        val primaryKey = getApiKey(primary.provider)
        entries.add(ProviderEntry(primary.provider, primary.modelId, primaryKey, getCustomUrl(primary.provider)))
        seen.add(primary.provider.name)

        // Add other providers that have API keys configured
        for (p in AIProviderFactory.providers) {
            if (seen.contains(p.name)) continue
            val key = getApiKey(p)
            if (p.isFree || key.isNotEmpty()) {
                val model = p.models.firstOrNull()?.id ?: ""
                if (model.isNotEmpty()) {
                    entries.add(ProviderEntry(p, model, key, getCustomUrl(p)))
                    seen.add(p.name)
                }
            }
        }

        return entries
    }

    private suspend fun executeDirectFallback(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            Regex("""\b(btc|bitcoin|crypto)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> {
                fetchCryptoPrice("bitcoin")
            }
            Regex("""\b(eth|ethereum)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> {
                fetchCryptoPrice("ethereum")
            }
            lower.contains("news") || lower.contains("headline") || lower.contains("search") -> {
                fetchWebSearchResults("latest ${prompt.take(50)}")
            }
            lower.contains("weather") -> {
                fetchWebSearchResults("current weather")
            }
            else -> "I couldn't process that request. All AI providers are unavailable."
        }
    }

    private suspend fun executeSimpleTool(tc: ToolCall): String {
        return try {
            val args = gson.fromJson(tc.arguments, JsonObject::class.java)
            when (tc.name) {
                "web_search" -> {
                    val query = args.get("query")?.asString ?: return "Error: Missing query"
                    val numResults = args.get("numResults")?.asInt ?: 8
                    val livecrawl = args.get("livecrawl")?.asString ?: "fallback"
                    val type = args.get("type")?.asString ?: "auto"
                    val contextMaxCharacters = args.get("contextMaxCharacters")?.asInt ?: 10000
                    fetchWebSearchResults(query, numResults, livecrawl, type, contextMaxCharacters)
                }
                "get_crypto_price" -> {
                    val coin = args.get("coin")?.asString ?: return "Error: Missing coin name"
                    fetchCryptoPrice(coin)
                }
                else -> "Error: Unknown tool ${tc.name}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    companion object {
        private const val MAX_TOOL_ITERATIONS = 6
        private const val MAX_RETRIES = 2
        private const val TIMEOUT_MS = 25_000L
        private const val BASE_DELAY_MS = 2_000L
        private const val MAX_DELAY_MS = 30_000L

        private val COIN_ALIASES = mapOf(
            "btc" to "bitcoin", "bitcoin" to "bitcoin",
            "eth" to "ethereum", "ethereum" to "ethereum",
            "sol" to "solana", "solana" to "solana",
            "doge" to "dogecoin", "dogecoin" to "dogecoin",
            "xrp" to "ripple", "ripple" to "ripple",
            "ada" to "cardano", "cardano" to "cardano",
            "dot" to "polkadot", "polkadot" to "polkadot",
            "matic" to "polygon", "polygon" to "polygon",
            "avax" to "avalanche-2", "avalanche" to "avalanche-2",
            "ltc" to "litecoin", "litecoin" to "litecoin",
            "link" to "chainlink", "chainlink" to "chainlink",
            "uni" to "uniswap", "uniswap" to "uniswap",
            "atom" to "cosmos", "cosmos" to "cosmos",
            "bnb" to "binancecoin", "binance" to "binancecoin",
            "sui" to "sui", "apt" to "aptos", "aptos" to "aptos",
            "ton" to "the-open-network", "near" to "near",
            "op" to "optimism", "optimism" to "optimism",
            "arb" to "arbitrum", "arbitrum" to "arbitrum",
            "pepe" to "pepe", "shib" to "shiba-inu", "shiba" to "shiba-inu"
        )
    }

    private suspend fun fetchCryptoPrice(coin: String): String = withContext(Dispatchers.IO) {
        try {
            val key = coin.trim().lowercase()
            val coinId = COIN_ALIASES[key] ?: key
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=${URLEncoder.encode(coinId, "UTF-8")}&vs_currencies=usd")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val json = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()
            val obj = gson.fromJson(json, JsonObject::class.java)
            val priceData = obj?.getAsJsonObject(coinId)
            val usdPrice = priceData?.get("usd")?.asDouble
            if (usdPrice != null) {
                val name = COIN_ALIASES.entries.firstOrNull { it.value == coinId }?.key ?: coinId
                val formatted = when {
                    usdPrice >= 1 -> "$%,.2f".format(usdPrice)
                    usdPrice >= 0.01 -> "$%,.4f".format(usdPrice)
                    else -> "$%,.8f".format(usdPrice)
                }
                "Current price of ${name.uppercase()} / $coinId: $formatted USD"
            } else {
                "Could not find price for '$coin'. Try a different name or ticker (e.g. bitcoin, btc, ethereum, eth)."
            }
        } catch (e: Exception) {
            "Crypto price fetch error: ${e.message}"
        }
    }

    private data class GeoResult(val lat: Double, val lon: Double, val name: String)

    private suspend fun geocodeCity(city: String): GeoResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1&language=en&format=json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val json = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()
            val obj = gson.fromJson(json, JsonObject::class.java)
            val results = obj?.getAsJsonArray("results")
            if (results != null && results.size() > 0) {
                val first = results.get(0).asJsonObject
                GeoResult(
                    first.get("latitude").asDouble,
                    first.get("longitude").asDouble,
                    first.get("name").asString
                )
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun fetchWeather(lat: Double, lon: Double, cityName: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,uv_index&timezone=auto")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()
            val obj = gson.fromJson(json, JsonObject::class.java)
            val current = obj?.getAsJsonObject("current") ?: return@withContext "Weather data unavailable for $cityName"

            val temp = current.get("temperature_2m")?.asDouble
            val feelsLike = current.get("apparent_temperature")?.asDouble
            val humidity = current.get("relative_humidity_2m")?.asInt
            val precip = current.get("precipitation")?.asDouble
            val wind = current.get("wind_speed_10m")?.asDouble
            val uv = current.get("uv_index")?.asDouble
            val wmoCode = current.get("weather_code")?.asInt

            val weatherDesc = when (wmoCode) {
                0 -> "Clear sky"
                1 -> "Mainly clear"
                2 -> "Partly cloudy"
                3 -> "Overcast"
                45, 48 -> "Foggy"
                51, 53, 55 -> "Drizzle"
                56, 57 -> "Freezing drizzle"
                61, 63, 65 -> "Rain"
                66, 67 -> "Freezing rain"
                71, 73, 75 -> "Snowfall"
                77 -> "Snow grains"
                80, 81, 82 -> "Rain showers"
                85, 86 -> "Snow showers"
                95 -> "Thunderstorm"
                96, 99 -> "Thunderstorm with hail"
                else -> "Unknown"
            }

            buildString {
                append("🌤 Current Weather in $cityName\n")
                append("Condition: $weatherDesc\n")
                if (temp != null) append("Temperature: ${temp}°C")
                if (feelsLike != null) append(" (feels like ${feelsLike}°C)")
                append("\n")
                if (humidity != null) append("Humidity: $humidity%\n")
                if (wind != null) append("Wind: $wind km/h\n")
                if (precip != null && precip > 0) append("Precipitation: ${precip}mm\n")
                if (uv != null) append("UV Index: $uv")
            }
        } catch (e: Exception) { "Weather data unavailable: ${e.message}" }
    }

    private suspend fun fetchWikipediaSearch(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${URLEncoder.encode(query, "UTF-8")}&format=json&srlimit=3&srprop=snippet")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()
            val obj = gson.fromJson(json, JsonObject::class.java)
            val search = obj?.getAsJsonObject("query")?.getAsJsonArray("search")
            if (search != null && search.size() > 0) {
                val sb = StringBuilder()
                for (i in 0 until minOf(search.size(), 3)) {
                    val item = search.get(i).asJsonObject
                    val title = item.get("title")?.asString ?: continue
                    val snippet = item.get("snippet")?.asString?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: ""
                    if (i == 0) sb.append("Wikipedia: $title")
                    else sb.append("\n• $title")
                    if (snippet.isNotEmpty()) sb.append(" — $snippet")
                }
                sb.toString()
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun fetchDDGApi(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.duckduckgo.com/?q=${URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1&skip_disambig=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            val json = if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes().let { String(it) } }
            } else null
            conn.disconnect()
            if (json == null) return@withContext null
            val obj = gson.fromJson(json, JsonObject::class.java)
            val results = mutableListOf<String>()
            val abstractText = obj?.get("AbstractText")?.asString?.takeIf { it.isNotBlank() }
            val heading = obj?.get("Heading")?.asString?.takeIf { it.isNotBlank() }
            val answer = obj?.get("Answer")?.asString?.takeIf { it.isNotBlank() }
            val definition = obj?.get("Definition")?.asString?.takeIf { it.isNotBlank() }
            if (answer != null) results.add("Answer: $answer")
            if (definition != null) results.add("Definition: $definition")
            if (heading != null && abstractText != null) results.add("$heading: $abstractText")
            val relatedTopics = obj?.getAsJsonArray("RelatedTopics")
            if (relatedTopics != null) {
                var count = 0
                for (i in 0 until relatedTopics.size()) {
                    if (count >= 5) break
                    try {
                        val topic = relatedTopics.get(i).asJsonObject
                        if (topic.has("Text")) { results.add("• ${topic.get("Text").asString.take(200)}"); count++ }
                        else if (topic.has("Topics")) {
                            val subs = topic.getAsJsonArray("Topics")
                            for (j in 0 until (subs?.size() ?: 0)) {
                                if (count >= 5) break
                                val sub = subs?.get(j)?.asJsonObject
                                if (sub?.has("Text") == true) { results.add("• ${sub.get("Text").asString.take(200)}"); count++ }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            results.joinToString("\n").takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    private suspend fun fetchDDGHtml(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://html.duckduckgo.com/html/?q=${URLEncoder.encode(query, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            val html = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()
            if (html.contains("challenge") || html.contains("anomaly")) return@withContext null
            val titles = Regex("""<a[^>]*class=["'][^"']*result[^"']*["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).map { it.groupValues[1].replace(Regex("""<[^>]+>"""), "").trim() }
                .filter { it.isNotEmpty() && it.length > 5 }.toList()
            if (titles.isEmpty()) return@withContext null
            val snippets = Regex("""<td[^>]*class=["'][^"']*snippet[^"']*["'][^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).map { it.groupValues[1].replace(Regex("""<[^>]+>"""), "").trim() }
                .filter { it.isNotEmpty() }.toList()
            val results = mutableListOf<String>()
            for (i in 0 until minOf(titles.size, 8)) {
                results.add("${i + 1}. ${titles[i]}")
                val snippet = snippets.getOrElse(i) { "" }
                if (snippet.isNotEmpty()) results.add("   $snippet")
            }
            results.joinToString("\n").takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    private suspend fun fetchWebSearchResults(query: String, numResults: Int = 8, livecrawl: String = "fallback", type: String = "auto", contextMaxCharacters: Int = 10000): String = withContext(Dispatchers.IO) {
        val lower = query.lowercase()

        // Try MCP-based providers first (Exa, Parallel)
        val mcpResult = mcpWebSearch(query, numResults, livecrawl, type, contextMaxCharacters)
        if (mcpResult != null) return@withContext mcpResult

        // 1. Weather: use Open-Meteo (free, no key, works everywhere)
        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast")) {
            var city = query
                .replace(Regex("(?i)\\b(whats|what's|what is|tell me|give me|show me|current|latest|the|today's|todays)\\b"), "")
                .replace(Regex("(?i)\\b(weather|temperature|forecast|conditions|report)\\b"), "")
                .replace(Regex("(?i)\\b(in|at|for|of)\\b"), "")
                .replace("?", "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (city.length in 2..50) {
                var geo = geocodeCity(city)
                if (geo == null && city.contains(",")) {
                    val firstPart = city.substringBefore(",").trim()
                    if (firstPart.length >= 2) {
                        geo = geocodeCity(firstPart)
                    }
                }
                if (geo != null) {
                    val weather = fetchWeather(geo.lat, geo.lon, geo.name)
                    if (weather.isNotEmpty()) return@withContext weather
                }
            }
        }

        // 2. DuckDuckGo Instant Answer API
        val ddgResult = fetchDDGApi(query)
        if (ddgResult != null) return@withContext ddgResult

        // 3. Wikipedia search
        val wikiResult = fetchWikipediaSearch(query)
        if (wikiResult != null) return@withContext wikiResult

        // 4. DuckDuckGo HTML scrape (may work on mobile networks)
        val htmlResult = fetchDDGHtml(query)
        if (htmlResult != null) return@withContext htmlResult

        "No results found for: $query"
    }

    private fun mcpWebSearch(query: String, numResults: Int, livecrawl: String, type: String, contextMaxCharacters: Int): String? {
        // Try Exa MCP
        var conn: HttpURLConnection? = null
        try {
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/call",
                "params" to mapOf(
                    "name" to "web_search_exa",
                    "arguments" to mapOf(
                        "query" to query,
                        "type" to type,
                        "numResults" to numResults,
                        "livecrawl" to livecrawl,
                        "contextMaxCharacters" to contextMaxCharacters
                    )
                )
            ))
            val url = URL("https://mcp.exa.ai/mcp")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "DeepCode-Android/1.0")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return null
                val sb = StringBuilder()
                for (i in 0 until minOf(content.size(), 8)) {
                    val item = content.get(i).asJsonObject
                    val text = item.get("text")?.asString ?: continue
                    val source = item.get("source")?.asString ?: ""
                    if (source.isNotEmpty()) sb.appendLine("Source: $source")
                    sb.appendLine(text.take(contextMaxCharacters))
                    sb.appendLine()
                }
                val out = sb.toString()
                if (out.isNotEmpty()) return "Search results for '$query':\n$out"
            }
        } catch (e: Exception) { /* fall through */ }
        finally { conn?.disconnect() }

        // Try Parallel MCP
        conn = null
        try {
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/call",
                "params" to mapOf(
                    "name" to "web_search",
                    "arguments" to mapOf(
                        "objective" to query,
                        "search_queries" to listOf(query),
                        "session_id" to UUID.randomUUID().toString()
                    )
                )
            ))
            val url = URL("https://search.parallel.ai/mcp")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "DeepCode-Android/1.0")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return null
                val sb = StringBuilder()
                for (i in 0 until minOf(content.size(), 8)) {
                    val item = content.get(i).asJsonObject
                    val text = item.get("text")?.asString ?: continue
                    sb.appendLine(text)
                    sb.appendLine()
                }
                val out = sb.toString()
                if (out.isNotEmpty()) return "Search results for '$query':\n$out"
            }
        } catch (e: Exception) { /* fall through */ }
        finally { conn?.disconnect() }

        return null
    }

    private fun jitteredBackoffMs(attempt: Int): Long {
        val delay = minOf(BASE_DELAY_MS * (1L shl attempt), MAX_DELAY_MS)
        val jitter = (Random.nextDouble() * 0.5 * delay).toLong()
        return delay + jitter
    }

    private suspend fun sendAndReturn(text: String, chatId: String?): String {
        val targetChatId = chatId
            ?: securePrefs.getSetting("telegram_default_chat_id", "").takeIf { it.isNotBlank() }
        if (targetChatId != null) sendTelegram(text, targetChatId)
        return text
    }

    private fun stripThoughts(text: String): String {
        var cleaned = text.replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<thought>[\\s\\S]*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</thought>", RegexOption.IGNORE_CASE), "")
        val trimmed = cleaned.trim()
        return if (trimmed.isEmpty() && text.isNotEmpty()) {
            "Thinking completed."
        } else {
            trimmed
        }
    }

    private fun formatMarkdownTablesForTelegram(text: String): String {
        val lines = text.lines()
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("|")) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|")) {
                    val raw = lines[j].trim()
                    val isSeparator = raw.matches(Regex("^\\|[-:| ]+\\|$"))
                    if (!isSeparator) {
                        tableLines.add(raw)
                    }
                    j++
                }
                if (tableLines.isNotEmpty()) {
                    val rows = tableLines.map { row ->
                        row.split("|").map { it.trim() }.drop(1).dropLastWhile { it.isEmpty() }
                    }.filter { it.isNotEmpty() }
                    
                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOfOrNull { it.size } ?: 0
                        val colWidths = IntArray(maxCols)
                        for (row in rows) {
                            for (colIndex in 0 until minOf(row.size, maxCols)) {
                                colWidths[colIndex] = maxOf(colWidths[colIndex], row[colIndex].length)
                            }
                        }
                        val formattedTable = StringBuilder()
                        formattedTable.appendLine("```")
                        for (row in rows) {
                            val rowStr = StringBuilder()
                            for (colIndex in 0 until maxCols) {
                                val cell = row.getOrElse(colIndex) { "" }
                                val width = colWidths[colIndex]
                                val paddedCell = cell.padEnd(width)
                                rowStr.append(paddedCell).append("   ")
                            }
                            formattedTable.appendLine(rowStr.toString().trimEnd())
                        }
                        formattedTable.append("```")
                        result.append(formattedTable.toString())
                    } else {
                        for (k in i until j) {
                            result.append(lines[k])
                            if (k < j - 1) result.append("\n")
                        }
                    }
                }
                i = j
            } else {
                result.append(line)
                i++
            }
            if (i < lines.size) {
                result.append("\n")
            }
        }
        return result.toString()
    }

    private suspend fun sendTelegram(text: String, chatId: String) = withContext(Dispatchers.IO) {
        try {
            val botToken = securePrefs.getSetting("telegram_bot_token", "")
            if (botToken.isBlank()) return@withContext

            val url = URL("https://api.telegram.org/bot${botToken}/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val cleanText = stripThoughts(text)
            val processed = formatMarkdownTablesForTelegram(cleanText)
            val body = """{"chat_id":"$chatId","text":${gson.toJson(processed)},"parse_mode":"Markdown"}"""
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.use { it.readBytes()?.let { String(it) } } ?: "no body"
                AppLogger.e("SimpleAutomationRunner", "Telegram error $code: $err")
            } else {
                conn.inputStream.use { it.readBytes() }
                AppLogger.i("SimpleAutomationRunner", "Sent to chat $chatId")
            }
            conn.disconnect()
        } catch (e: Exception) {
            AppLogger.e("SimpleAutomationRunner", "Telegram send failed", e)
        }
    }

    private data class Resolved(val provider: AIProvider, val modelId: String)

    private fun resolveProvider(): Resolved {
        val modelSetting = securePrefs.getSetting("agent_model", "big-pickle")
        val providerSetting = securePrefs.getSetting("agent_provider", "Zen AI")
        var provider = AIProviderFactory.providers.firstOrNull { it.name == providerSetting }
            ?: AIProviderFactory.providers.firstOrNull { it.name == "Zen AI" }
            ?: AIProviderFactory.providers.firstOrNull()
            ?: return Resolved(ai.deepcode.android.data.remote.ZenProvider(), "big-pickle")

        val key = getApiKey(provider)
        if (!provider.isFree && key.isEmpty()) {
            provider = AIProviderFactory.providers.firstOrNull { p -> p.isFree || getApiKey(p).isNotEmpty() }
                ?: provider
        }
        val hasModel = provider.models.any { it.id == modelSetting }
        val finalModel = if (hasModel) modelSetting else (provider.models.firstOrNull()?.id ?: "")
        return Resolved(provider, finalModel)
    }

    private fun getApiKey(provider: AIProvider): String {
        return when (provider.name) {
            "Zen AI", "Zen", "Zen (Free)" -> securePrefs.getApiKey("zen")
            "Google Gemini" -> securePrefs.getApiKey("gemini")
            "Groq" -> securePrefs.getApiKey("groq")
            "Cerebrus" -> securePrefs.getApiKey("cerebrus")
            "OpenRouter" -> securePrefs.getApiKey("openrouter")
            "OpenAI" -> securePrefs.getApiKey("openai")
            "Anthropic" -> securePrefs.getApiKey("anthropic")
            "Mistral AI" -> securePrefs.getApiKey("mistral")
            "Agent Router" -> securePrefs.getApiKey("agentrouter")
            else -> ""
        }
    }

    private fun getCustomUrl(provider: AIProvider): String? {
        val key = when (provider.name) {
            "Zen AI", "Zen", "Zen (Free)" -> "url_zen"
            "Google Gemini" -> "url_gemini"
            "Groq" -> "url_groq"
            "Cerebrus" -> "url_cerebrus"
            "OpenRouter" -> "url_openrouter"
            "OpenAI" -> "url_openai"
            "Anthropic" -> "url_anthropic"
            "Mistral AI" -> "url_mistral"
            "Agent Router" -> "url_agentrouter"
            else -> ""
        }
        val url = securePrefs.getSetting(key, "")
        return url.ifEmpty { null }
    }
}
