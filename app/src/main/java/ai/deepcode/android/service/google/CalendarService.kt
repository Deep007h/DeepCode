package ai.deepcode.android.service.google

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val description: String,
    val isAllDay: Boolean
)

class CalendarService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val authService = GoogleAuthService(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    suspend fun getTodayEvents(): Result<List<CalendarEvent>> {
        val cal = Calendar.getInstance()
        val startOfDay = dateFormat.format(Date(cal.timeInMillis)).substringBefore("T") + "T00:00:00" + getTimezoneOffset()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = dateFormat.format(Date(cal.timeInMillis)).substringBefore("T") + "T00:00:00" + getTimezoneOffset()
        return listEvents(startOfDay, endOfDay, "50", false)
    }

    suspend fun getUpcomingEvents(maxResults: Int = 10): Result<List<CalendarEvent>> {
        val now = dateFormat.format(Date(System.currentTimeMillis()))
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 14)
        val twoWeeks = dateFormat.format(Date(cal.timeInMillis))
        return listEvents(now, twoWeeks, maxResults.toString(), true)
    }

    private suspend fun listEvents(timeMin: String, timeMax: String, maxResults: String, useMax: Boolean): Result<List<CalendarEvent>> {
        val tokenResult = authService.getValidAccessToken("google_calendar")
        if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
        val token = tokenResult.getOrThrow()
        return withContext(Dispatchers.IO) {
            try {
                val params = "timeMin=${java.net.URLEncoder.encode(timeMin, "UTF-8")}&timeMax=${java.net.URLEncoder.encode(timeMax, "UTF-8")}&orderBy=startTime&singleEvents=true" +
                    if (useMax) "&maxResults=$maxResults" else ""
                val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?$params"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Calendar API error: HTTP ${response.code} - $body"))
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = json.getAsJsonArray("items") ?: return@withContext Result.success(emptyList())
                val events = items.mapNotNull { item ->
                    val obj = item.asJsonObject
                    val start = obj.getAsJsonObject("start") ?: return@mapNotNull null
                    val end = obj.getAsJsonObject("end") ?: return@mapNotNull null
                    CalendarEvent(
                        id = obj.get("id")?.asString ?: "",
                        title = obj.get("summary")?.asString ?: "(No title)",
                        startTime = start.get("dateTime")?.asString ?: start.get("date")?.asString ?: "",
                        endTime = end.get("dateTime")?.asString ?: end.get("date")?.asString ?: "",
                        location = obj.get("location")?.asString ?: "",
                        description = obj.get("description")?.asString ?: "",
                        isAllDay = start.get("date")?.asString != null
                    )
                }
                Result.success(events)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getTimezoneOffset(): String {
        val tz = TimeZone.getDefault()
        val offsetMs = tz.getOffset(System.currentTimeMillis())
        val hours = offsetMs / 3600000
        val minutes = (offsetMs % 3600000) / 60000
        return String.format(Locale.US, "%+03d:%02d", hours, Math.abs(minutes))
    }
}
