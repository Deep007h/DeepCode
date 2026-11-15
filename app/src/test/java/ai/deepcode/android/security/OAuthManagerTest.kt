package ai.deepcode.android.security

import ai.deepcode.android.ui.connections.OAuthManager
import org.junit.Assert.*
import org.junit.Test

class OAuthManagerTest {

    @Test
    fun testOAuthLogSanitization() {
        val sensitiveLog = "Token response received: code=4/0AbCdEf123456789 and access_token=ya29.a0AfH6SMDI_sensitive and refresh_token=1//04_refresh_secret with client_secret=GOCSPX-supersecret"
        val sanitized = OAuthManager.sanitizeOAuthLog(sensitiveLog)

        assertFalse("Auth code must be redacted", sanitized.contains("4/0AbCdEf123456789"))
        assertFalse("Access token must be redacted", sanitized.contains("ya29.a0AfH6SMDI_sensitive"))
        assertFalse("Refresh token must be redacted", sanitized.contains("1//04_refresh_secret"))
        assertFalse("Client secret must be redacted", sanitized.contains("GOCSPX-supersecret"))

        assertTrue("Must contain redacted code parameter", sanitized.contains("code=[REDACTED]"))
        assertTrue("Must contain redacted access_token", sanitized.contains("access_token=[REDACTED]"))
        assertTrue("Must contain redacted refresh_token", sanitized.contains("refresh_token=[REDACTED]"))
        assertTrue("Must contain redacted client_secret", sanitized.contains("client_secret=[REDACTED]"))
    }

    @Test
    fun testBearerTokenSanitization() {
        val authHeaderLog = "Sending request to Google API with Authorization: Bearer ya29.a0AfH6SMDI99887766554433"
        val sanitized = OAuthManager.sanitizeOAuthLog(authHeaderLog)

        assertFalse("Bearer token value must be redacted", sanitized.contains("ya29.a0AfH6SMDI99887766554433"))
        assertTrue("Must contain Bearer [REDACTED]", sanitized.contains("Bearer [REDACTED]"))
    }
}
