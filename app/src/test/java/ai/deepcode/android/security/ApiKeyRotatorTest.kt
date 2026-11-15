package ai.deepcode.android.security

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.ApiKeyRotator
import ai.deepcode.android.data.remote.RateLimitException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiKeyRotatorTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var encryptedPrefs: EncryptedPrefs

    @Before
    fun setUp() {
        ApiKeyRotator.clearAllCooldowns()
        fakePrefs = FakeSharedPreferences()
        encryptedPrefs = EncryptedPrefs.forTesting(fakePrefs)
    }

    @After
    fun tearDown() {
        ApiKeyRotator.clearAllCooldowns()
    }

    @Test
    fun testMultiKeyRotationUpTo6Keys() {
        val provider = "anthropic"
        val keys = listOf(
            "sk-ant-key-1-test-aaaa",
            "sk-ant-key-2-test-bbbb",
            "sk-ant-key-3-test-cccc",
            "sk-ant-key-4-test-dddd",
            "sk-ant-key-5-test-eeee",
            "sk-ant-key-6-test-ffff"
        )

        for (i in 1..6) {
            encryptedPrefs.saveApiKeySlot(provider, i, keys[i - 1])
        }

        assertEquals(6, ApiKeyRotator.getConfiguredKeyCount(encryptedPrefs, provider))

        for (i in 1..6) {
            assertEquals(i, ApiKeyRotator.findSlotForKey(encryptedPrefs, provider, keys[i - 1]))
        }

        // Initially returns slot 1
        val first = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(first)
        assertEquals(keys[0], first!!.first)
        assertEquals(1, first.second)

        // Mark slot 1 exhausted -> returns slot 2
        ApiKeyRotator.markKeyRateLimited(provider, 1, keys[0])
        val second = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(second)
        assertEquals(keys[1], second!!.first)
        assertEquals(2, second.second)

        // Rotate via getAvailableKeyAfter from slot 2 -> returns slot 3
        val nextAfter2 = ApiKeyRotator.getAvailableKeyAfter(encryptedPrefs, provider, 2)
        assertNotNull(nextAfter2)
        assertEquals(keys[2], nextAfter2!!.first)
        assertEquals(3, nextAfter2.second)

        // Mark slots 2 through 5 exhausted -> returns slot 6
        for (i in 2..5) {
            ApiKeyRotator.markKeyRateLimited(provider, i, keys[i - 1])
        }
        val sixth = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(sixth)
        assertEquals(keys[5], sixth!!.first)
        assertEquals(6, sixth.second)
    }

    @Test
    fun testHttp400AndBadModelNameDoNotRotate() {
        val provider = "openai"
        val key = "sk-openai-key-usable-12345678"
        encryptedPrefs.saveApiKeySlot(provider, 1, key)

        // 1. HTTP 400 Bad Request
        assertFalse(
            "HTTP 400 must NOT be rotatable",
            ApiKeyRotator.isRotatableError(null, 400, "{\"error\":{\"message\":\"Invalid parameter 'max_tokens'\"}}")
        )
        assertTrue(
            "HTTP 400 must be identified as bad model or request error",
            ApiKeyRotator.isBadModelOrRequestError(null, 400, "{\"error\":{\"message\":\"Bad request syntax\"}}")
        )

        // 2. Bad model names across providers
        val badModelBodies = listOf(
            "The model 'gpt-5-turbo-nonexistent' does not exist",
            "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"model: claude-bad not found\"}}",
            "models/gemini-unknown-123 is not found for API version v1beta",
            "Model 'deepseek-bad' not found",
            "No such model: mistral-ultra-fake",
            "Invalid model name specified",
            "model is disabled for your organization",
            "model access is disabled",
            "model_not_found"
        )

        for (body in badModelBodies) {
            assertTrue("Should detect bad model in '$body'", ApiKeyRotator.isBadModelOrRequestError(null, null, body))
            assertFalse("Must NOT rotate for bad model in '$body'", ApiKeyRotator.isRotatableError(null, null, body))
        }

        // 3. Verify markKeyFailure does NOT mark key exhausted when encountering HTTP 400 or bad model
        ApiKeyRotator.markKeyFailure(
            provider, 1, key,
            Exception("HTTP 400 Bad Request: model not found"), 400, "{\"error\":\"model_not_found\"}"
        )
        assertFalse("Key must NOT be exhausted after HTTP 400 / bad model error", ApiKeyRotator.isKeyExhausted(provider, 1, key))
        val readyKey = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(readyKey)
        assertEquals(key, readyKey!!.first)
    }

    @Test
    fun testRateLimitVsInvalidAuthDistinction() {
        val provider = "groq"
        val key1 = "gsk_key1_ratelimited_111111"
        val key2 = "gsk_key2_authinvalid_222222"
        val key3 = "gsk_key3_valid_clean_333333"

        encryptedPrefs.saveApiKeySlot(provider, 1, key1)
        encryptedPrefs.saveApiKeySlot(provider, 2, key2)
        encryptedPrefs.saveApiKeySlot(provider, 3, key3)

        // 1. Rate limit classification
        val rateLimitMsg = "Rate limit reached: 30 requests per minute"
        assertTrue(ApiKeyRotator.isRateLimitError(null, 429, rateLimitMsg))
        assertFalse(ApiKeyRotator.isAuthError(null, 429, rateLimitMsg))
        assertTrue(ApiKeyRotator.isRotatableError(null, 429, rateLimitMsg))

        // 2. Invalid auth classification
        val authMsg = "Incorrect API key provided: invalid_api_key"
        assertTrue(ApiKeyRotator.isAuthError(null, 401, authMsg))
        assertFalse(ApiKeyRotator.isRateLimitError(null, 401, authMsg))
        assertTrue(ApiKeyRotator.isRotatableError(null, 401, authMsg))

        // 3. Mark key1 as rate limited (short cooldown, e.g. 500ms for test)
        ApiKeyRotator.markKeyRateLimited(provider, 1, key1, cooldownMs = 500L)
        assertTrue(ApiKeyRotator.isKeyExhausted(provider, 1, key1))
        assertFalse(ApiKeyRotator.isKeyAuthInvalid(provider, 1, key1))

        // 4. Mark key2 as auth invalid
        ApiKeyRotator.markKeyAuthInvalid(provider, 2, key2)
        assertTrue(ApiKeyRotator.isKeyExhausted(provider, 2, key2))
        assertTrue(ApiKeyRotator.isKeyAuthInvalid(provider, 2, key2))

        // Current available key should be key3
        val current = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(current)
        assertEquals(key3, current!!.first)
        assertEquals(3, current.second)

        // Mark key3 in rate limit cooldown as well
        ApiKeyRotator.markKeyRateLimited(provider, 3, key3, cooldownMs = 60_000L)

        // NOW ALL 3 KEYS ARE EXHAUSTED:
        // key1 expires in ~500ms (rate limit)
        // key2 is auth invalid (24h)
        // key3 expires in 60s (rate limit)
        // Fallback should pick key1 (the earliest cooling down non-auth-invalid key)!
        val fallback = ApiKeyRotator.getNextAvailableKey(encryptedPrefs, provider)
        assertNotNull(fallback)
        assertEquals("Fallback must pick key1 over permanently invalid key2", key1, fallback!!.first)
        assertEquals(1, fallback.second)
    }

    @Test
    fun testCooldownExpirationAndClearing() {
        val provider = "cerebras"
        val key = "csk_test_cooldown_12345"
        encryptedPrefs.saveApiKeySlot(provider, 1, key)

        // Cooldown of 50ms
        ApiKeyRotator.markKeyRateLimited(provider, 1, key, cooldownMs = 50L)
        assertTrue(ApiKeyRotator.isKeyExhausted(provider, 1, key))

        Thread.sleep(80L)

        assertFalse("Key should no longer be exhausted after cooldown expires", ApiKeyRotator.isKeyExhausted(provider, 1, key))

        // Test clearCooldowns
        ApiKeyRotator.markKeyRateLimited(provider, 1, key, cooldownMs = 60_000L)
        assertTrue(ApiKeyRotator.isKeyExhausted(provider, 1, key))

        ApiKeyRotator.clearCooldowns(provider, encryptedPrefs)
        assertFalse("Key should be cleared immediately", ApiKeyRotator.isKeyExhausted(provider, 1, key))
    }

    @Test
    fun testStorageAliases() {
        val key = "zen-custom-key-12345678"
        encryptedPrefs.saveApiKeySlot("zen", 1, key)

        // Marking slot 1 exhausted under 'opencode' should be seen under 'zen'
        ApiKeyRotator.markKeyRateLimited("opencode", 1, key, cooldownMs = 10_000L)
        assertTrue("Cooldown must be recognized across canonical alias 'zen'", ApiKeyRotator.isKeyExhausted("zen", 1, key))
        assertTrue("Cooldown must be recognized across alias 'opencode-zen'", ApiKeyRotator.isKeyExhausted("opencode-zen", 1, key))
    }
}
