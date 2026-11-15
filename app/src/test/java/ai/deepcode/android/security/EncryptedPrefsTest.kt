package ai.deepcode.android.security

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.ApiKeyRotator
import ai.deepcode.android.util.AppLogger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EncryptedPrefsTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var encryptedPrefs: EncryptedPrefs

    @Before
    fun setUp() {
        ApiKeyRotator.clearAllCooldowns()
        fakePrefs = FakeSharedPreferences()
        encryptedPrefs = EncryptedPrefs.forTesting(fakePrefs)
    }

    @Test
    fun testSixSlotsPerProvider() {
        val provider = "openai"
        val keys = (1..6).map { "sk-openai-slot-$it-secret" }

        for (slot in 1..6) {
            encryptedPrefs.saveApiKeySlot(provider, slot, keys[slot - 1])
        }

        for (slot in 1..6) {
            assertEquals(keys[slot - 1], encryptedPrefs.getApiKeySlot(provider, slot))
        }

        val allKeys = encryptedPrefs.getApiKeys(provider)
        assertEquals(6, allKeys.size)
        assertEquals(keys, allKeys)

        // Slot 0 or > 6
        assertEquals(keys[0], encryptedPrefs.getApiKeySlot(provider, 1))
        assertEquals("", encryptedPrefs.getApiKeySlot(provider, 7))
    }

    @Test
    fun testSavingApiKeyClearsCooldowns() {
        val provider = "together"
        val oldKey = "together-old-12345"
        val newKey = "together-new-67890"

        encryptedPrefs.saveApiKeySlot(provider, 1, oldKey)
        ApiKeyRotator.markKeyRateLimited(provider, 1, oldKey, cooldownMs = 60_000L)
        assertTrue(ApiKeyRotator.isKeyExhausted(provider, 1, oldKey))

        // Saving new key slot 1 must clear cooldowns
        encryptedPrefs.saveApiKeySlot(provider, 1, newKey)
        assertFalse(ApiKeyRotator.isKeyExhausted(provider, 1, newKey))
    }

    @Test
    fun testSettingsPersistence() {
        encryptedPrefs.saveSetting("theme", "dark")
        assertEquals("dark", encryptedPrefs.getSetting("theme", "system"))

        encryptedPrefs.saveBooleanSetting("root_mode", true)
        assertTrue(encryptedPrefs.getBooleanSetting("root_mode", false))

        encryptedPrefs.saveChatGPTAccessToken("sess-test-token-123")
        assertEquals("sess-test-token-123", encryptedPrefs.getChatGPTAccessToken())
    }

    @Test
    fun testAppLoggerSecretSanitization() {
        val rawMessage = "Request failed for Bearer sk-ant-api03-abcdef123456789012345678 with api_key=ghp_secrettokenvalue12345678"
        val sanitized = AppLogger.sanitizeLog(rawMessage)

        assertFalse("Bearer token must be redacted", sanitized.contains("Bearer sk-ant-api03-abcdef123456789012345678"))
        assertFalse("Raw GitHub token must be redacted", sanitized.contains("ghp_secrettokenvalue12345678"))
        assertTrue("Must contain [REDACTED]", sanitized.contains("[REDACTED]"))
    }
}
