package ai.deepcode.android.service.notion

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class NotionUser(
    val id: String,
    val name: String,
    val email: String
)

data class NotionPage(
    val id: String,
    val title: String,
    val url: String,
    val lastEditedTime: String,
    val objectType: String
)

data class NotionDatabase(
    val id: String,
    val title: String,
    val url: String
)

class NotionService(private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    private val baseUrl = "https://api.notion.com/v1/"
    private val notionVersion = "2022-06-28"

    private fun buildRequest(endpoint: String, body: String? = null): Request {
        val builder = Request.Builder()
            .url("$baseUrl$endpoint")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", notionVersion)
            .header("Content-Type", "application/json")
        if (body != null) {
            builder.post(body.toRequestBody(jsonMediaType))
        }
        return builder.build()
    }

    fun validateToken(): Result<NotionUser> {
        return try {
            val request = buildRequest("users/me")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Notion API error: HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val person = json.getAsJsonObject("person")
            Result.success(NotionUser(
                id = json.get("id")?.asString ?: "",
                name = json.get("name")?.asString ?: "",
                email = person?.get("email")?.asString ?: ""
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun search(query: String = "", pageSize: Int = 20): Result<List<NotionPage>> {
        return try {
            val bodyJson = JsonObject().apply {
                addProperty("page_size", pageSize)
                if (query.isNotBlank()) {
                    addProperty("query", query)
                }
            }
            val request = buildRequest("search", bodyJson.toString())
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Notion search failed: HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results") ?: JsonArray()
            val pages = results.mapNotNull { item ->
                val obj = item.asJsonObject
                val objType = obj.get("object")?.asString ?: return@mapNotNull null
                val title = extractTitle(obj, objType)
                NotionPage(
                    id = obj.get("id")?.asString ?: "",
                    title = title,
                    url = obj.get("url")?.asString ?: "",
                    lastEditedTime = obj.get("last_edited_time")?.asString ?: "",
                    objectType = objType
                )
            }
            Result.success(pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPageContent(pageId: String): Result<String> {
        return try {
            val request = buildRequest("blocks/${pageId.replace("-", "")}/children?page_size=100")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Notion page fetch failed: HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results") ?: JsonArray()
            val text = results.mapNotNull { blockToText(it.asJsonObject) }.joinToString("\n")
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listDatabases(): Result<List<NotionDatabase>> {
        return try {
            val bodyJson = JsonObject().apply {
                addProperty("page_size", 50)
                add("filter", JsonObject().apply {
                    addProperty("value", "database")
                    addProperty("property", "object")
                })
            }
            val request = buildRequest("search", bodyJson.toString())
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Notion search failed: HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results") ?: JsonArray()
            val databases = results.mapNotNull { item ->
                val obj = item.asJsonObject
                if (obj.get("object")?.asString != "database") return@mapNotNull null
                val title = extractTitle(obj, "database")
                NotionDatabase(
                    id = obj.get("id")?.asString ?: "",
                    title = title,
                    url = obj.get("url")?.asString ?: ""
                )
            }
            Result.success(databases)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun queryDatabase(databaseId: String, pageSize: Int = 20): Result<List<NotionPage>> {
        return try {
            val bodyJson = JsonObject().apply {
                addProperty("page_size", pageSize)
            }
            val request = buildRequest("databases/${databaseId.replace("-", "")}/query", bodyJson.toString())
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Notion database query failed: HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results") ?: JsonArray()
            val pages = results.mapNotNull { item ->
                val obj = item.asJsonObject
                NotionPage(
                    id = obj.get("id")?.asString ?: "",
                    title = extractTitle(obj, "page"),
                    url = obj.get("url")?.asString ?: "",
                    lastEditedTime = obj.get("last_edited_time")?.asString ?: "",
                    objectType = "page"
                )
            }
            Result.success(pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTitle(obj: JsonObject, objType: String): String {
        return try {
            if (objType == "database") {
                val titles = obj.getAsJsonArray("title") ?: return "Untitled"
                if (titles.size() > 0) {
                    titles[0].asJsonObject.get("plain_text")?.asString ?: "Untitled"
                } else "Untitled"
            } else {
                val properties = obj.getAsJsonObject("properties") ?: return "Untitled"
                val titleProp = properties.entrySet().firstOrNull { (_, v) ->
                    val type = v.asJsonObject.get("type")?.asString
                    type == "title"
                }?.value?.asJsonObject ?: return "Untitled"
                val titles = titleProp.getAsJsonArray("title") ?: return "Untitled"
                if (titles.size() > 0) {
                    titles[0].asJsonObject.get("plain_text")?.asString ?: "Untitled"
                } else "Untitled"
            }
        } catch (_: Exception) { "Untitled" }
    }

    private fun blockToText(block: JsonObject): String? {
        return try {
            val type = block.get("type")?.asString ?: return null
            val content = block.getAsJsonObject(type) ?: return null
            when (type) {
                "paragraph" -> richTextContent(content)
                "heading_1" -> "# ${richTextContent(content)}"
                "heading_2" -> "## ${richTextContent(content)}"
                "heading_3" -> "### ${richTextContent(content)}"
                "bulleted_list_item" -> "- ${richTextContent(content)}"
                "numbered_list_item" -> "1. ${richTextContent(content)}"
                "to_do" -> {
                    val checked = content.get("checked")?.asBoolean ?: false
                    "${if (checked) "[x]" else "[ ]"} ${richTextContent(content)}"
                }
                "code" -> {
                    val lang = content.get("language")?.asString ?: ""
                    "```$lang\n${richTextContent(content)}\n```"
                }
                "quote" -> "> ${richTextContent(content)}"
                "callout" -> richTextContent(content)
                "divider" -> "---"
                "image" -> "[Image: ${content.get("caption")?.let { richTextContent(it.asJsonObject) } ?: ""}]"
                "bookmark" -> "[Link: ${content.get("url")?.asString ?: ""}]"
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun richTextContent(obj: JsonObject): String {
        val richText = obj.getAsJsonArray("rich_text") ?: return ""
        return richText.mapNotNull { rt ->
            val textObj = rt.asJsonObject.getAsJsonObject("text") ?: return@mapNotNull null
            textObj.get("content")?.asString
        }.joinToString("")
    }

    companion object {
        fun createPage(parentDatabaseId: String, properties: Map<String, Any>, token: String): Result<String> {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val gson = Gson()

            val propsJson = JsonObject()
            properties.forEach { (key, value) ->
                when (value) {
                    is String -> {
                        propsJson.add(key, JsonObject().apply {
                            addProperty("type", "title")
                            add("title", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("type", "text")
                                    add("text", JsonObject().apply {
                                        addProperty("content", value)
                                    })
                                })
                            })
                        })
                    }
                    is Number -> {
                        propsJson.add(key, JsonObject().apply {
                            addProperty("type", "number")
                            addProperty("number", value)
                        })
                    }
                    is Boolean -> {
                        propsJson.add(key, JsonObject().apply {
                            addProperty("type", "checkbox")
                            addProperty("checkbox", value)
                        })
                    }
                }
            }

            val bodyJson = JsonObject().apply {
                add("parent", JsonObject().apply {
                    addProperty("database_id", parentDatabaseId.replace("-", ""))
                })
                add("properties", propsJson)
            }

            val request = Request.Builder()
                .url("https://api.notion.com/v1/pages")
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            return try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return Result.failure(Exception("Notion create page failed: HTTP ${response.code} - $errBody"))
                }
                val respBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val json = gson.fromJson(respBody, JsonObject::class.java)
                Result.success(json.get("id")?.asString ?: "")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
