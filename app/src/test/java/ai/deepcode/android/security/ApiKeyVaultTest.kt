package ai.deepcode.android.security

import com.jarves.mh.data.ApiKeyCredential
import com.jarves.mh.data.ApiKeyVault
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiKeyVaultTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var vault: ApiKeyVault

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        vault = ApiKeyVault(fakePrefs)
    }

    @Test
    fun testPutAndGetSecret() {
        val provider = "openai"
        val secret = "sk-test-secret-value-abcdef-123456"

        vault.put(provider, secret)
        assertTrue(vault.contains(provider))

        val retrieved = vault.get(provider)
        assertEquals(secret, retrieved)
    }

    @Test
    fun testCredentialsMaskingInToString() {
        val cred = ApiKeyCredential(
            id = "key-id-1",
            name = "Production Key",
            secret = "sk-live-super-secret-must-not-leak",
            isActive = true
        )

        val str = cred.toString()
        assertFalse("toString must NOT leak raw secret!", str.contains("sk-live-super-secret-must-not-leak"))
        assertTrue("toString must contain secret=***", str.contains("secret=***"))
    }

    @Test
    fun testSanitizeKeyNameDoesNotEchoSecret() {
        val provider = "deepseek"
        val secretKey = "sk-deepseek-sensitive-key-99999"

        // If a caller accidentally passes the secret as the name
        val info = vault.add(provider, secretKey, secretKey)
        assertFalse("Key name must NOT be set to the raw secret", info.name.contains("sk-deepseek-sensitive-key-99999"))
        assertTrue("Key name should be safely sanitized", info.name.startsWith("API key"))
    }

    @Test
    fun testMultipleKeysPoolAndActivation() {
        val provider = "anthropic"
        val key1 = "sk-ant-1"
        val key2 = "sk-ant-2"

        val info1 = vault.add(provider, "Key 1", key1)
        val info2 = vault.add(provider, "Key 2", key2)

        assertTrue("First key should be initially active", info1.isActive)
        assertEquals(key1, vault.get(provider))

        // Activate key 2
        vault.activate(provider, info2.id)
        assertEquals(key2, vault.get(provider))

        val creds = vault.credentials(provider)
        assertEquals(2, creds.size)
        assertTrue("Active key should be first in sorted credentials", creds[0].isActive)
        assertEquals(info2.id, creds[0].id)
    }

    @Test
    fun testCorruptedCiphertextSafeRecovery() {
        val provider = "groq"
        vault.put(provider, "gsk-good-secret-123")
        assertEquals("gsk-good-secret-123", vault.get(provider))

        // Corrupt preferences data
        val pool = vault.list(provider)
        assertTrue(pool.isNotEmpty())
        val keyId = pool.first().id
        fakePrefs.edit()
            .putString("$provider.pool.$keyId.value", "INVALID_BASE64_CORRUPT_CIPHERTEXT!!")
            .apply()

        // Decryption of corrupted entry should return null without crashing
        val retrieved = vault.get(provider)
        assertNull("Corrupted entry should safely return null", retrieved)

        // Vault should be able to recover and accept new secrets
        vault.recoverCorruptedStorage()
        vault.put(provider, "gsk-new-secret-456")
        assertEquals("gsk-new-secret-456", vault.get(provider))
    }
}
