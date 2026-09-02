package ai.deepcode.android.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request / Response models ──

data class WAHealthResponse(
    val status: String,
    val connected: Boolean,
    val phone: String?,
    val uptime: Double
)

data class WAQrResponse(
    val connected: Boolean,
    val phone: String?,
    val qr: String?,
    val error: String?
)

data class WAStatusResponse(
    val connected: Boolean,
    val phone: String?
)

data class WALogoutResponse(
    val success: Boolean,
    val error: String?
)

data class WASendRequest(
    val jid: String,
    val text: String
)

data class WASendResponse(
    val success: Boolean,
    val id: String?,
    val error: String?
)

data class WAMessage(
    val id: String,
    val jid: String,
    val fromMe: Boolean? = null,
    val from: String? = null,
    val text: String,
    val timestamp: Long? = null,
    val isGroup: Boolean? = null
)

data class WAMessagesResponse(
    val messages: List<WAMessage>,
    val error: String?
)

data class WAIncomingMessagesResponse(
    val messages: List<WAMessage>
)

data class WAContact(
    val jid: String,
    val name: String,
    val verifiedName: String? = null
)

data class WAContactsResponse(
    val contacts: List<WAContact>,
    val error: String?
)

data class WAPresenceRequest(
    val type: String
)

data class WAPresenceResponse(
    val success: Boolean,
    val error: String?
)

// ── Retrofit interface ──

interface WhatsAppBridgeAPI {
    @GET("health")
    suspend fun health(): Response<WAHealthResponse>

    @GET("auth/qr")
    suspend fun getQR(@Query("timeout") timeout: Long = 120_000): Response<WAQrResponse>

    @GET("auth/status")
    suspend fun getAuthStatus(): Response<WAStatusResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<WALogoutResponse>

    @POST("message/send")
    suspend fun sendMessage(@Body request: WASendRequest): Response<WASendResponse>

    @GET("messages")
    suspend fun getMessages(
        @Query("jid") jid: String,
        @Query("limit") limit: Int = 20
    ): Response<WAMessagesResponse>

    @GET("messages/incoming")
    suspend fun getIncomingMessages(
        @Query("since") since: Long = 0,
        @Query("jid") jid: String? = null
    ): Response<WAIncomingMessagesResponse>

    @GET("contacts")
    suspend fun getContacts(): Response<WAContactsResponse>

    @POST("presence")
    suspend fun setPresence(@Body request: WAPresenceRequest): Response<WAPresenceResponse>
}
