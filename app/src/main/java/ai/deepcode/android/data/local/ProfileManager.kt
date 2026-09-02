package ai.deepcode.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // ── Advanced profile metadata (all optional for backward-compat with stored JSON) ──
    val role: String? = null,           // e.g. "Developer", "Builder", "Curious"
    val accentId: String? = null,       // profile-level accent theme id (falls back to active app accent)
    val pinHash: String? = null,        // salted SHA-256 of the PIN; null = no lock
    val pinSalt: String? = null,        // random salt used when hashing the PIN
    val pinLength: Int = 4,             // digits allowed (4..6)
    val useBiometric: Boolean = false   // opt-in: unlock via device biometrics
)

class ProfileManager(private val context: Context) {
    private val prefs = EncryptedPrefs.getInstance(context)
    private val gson = com.google.gson.Gson()
    private val profilesDir = File(context.filesDir, "profiles").also { it.mkdirs() }

    fun getProfiles(): List<Profile> {
        val json = prefs.getSetting("profiles", "[]")
        return try {
            gson.fromJson(json, Array<Profile>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveProfiles(profiles: List<Profile>) {
        prefs.saveSetting("profiles", gson.toJson(profiles))
    }

    fun getActiveProfileId(): String? {
        val id = prefs.getSetting("active_profile", "")
        if (id.isBlank()) return null
        return id
    }

    fun setActiveProfile(id: String) {
        prefs.saveSetting("active_profile", id)
    }

    fun addProfile(profile: Profile): Profile {
        val profiles = getProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
        return profile
    }

    fun deleteProfile(id: String) {
        val profiles = getProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        saveProfiles(profiles)
        File(profilesDir, "${id}.jpg").delete()
        File(profilesDir, "${id}.png").delete()
        if (getActiveProfileId() == id) {
            val next = profiles.firstOrNull()
            if (next != null) setActiveProfile(next.id) else setActiveProfile("")
        }
    }

    fun updateProfile(profile: Profile) {
        val profiles = getProfiles().toMutableList()
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            profiles[idx] = profile
            saveProfiles(profiles)
        }
    }

    fun getActiveProfile(): Profile? {
        val id = getActiveProfileId() ?: return null
        return getProfiles().find { it.id == id }
    }

    fun saveAvatar(profileId: String, bitmap: Bitmap): String {
        val cropped = centerCropToSquare(bitmap)
        val file = File(profilesDir, "${profileId}.png")
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 90, it) }
        return file.absolutePath
    }

    fun getAvatarBitmap(profileId: String): Bitmap? {
        val file = File(profilesDir, "${profileId}.png")
        if (!file.exists()) {
            val jpgFile = File(profilesDir, "${profileId}.jpg")
            if (!jpgFile.exists()) return null
            return BitmapFactory.decodeFile(jpgFile.absolutePath)
        }
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun ensureFirstProfile(): Profile {
        val existing = getProfiles()
        if (existing.isNotEmpty()) return existing.first()

        val oldName = prefs.getSetting("profile_name", "")
        val name = if (oldName.isNotBlank()) oldName else "User"
        val profile = Profile(name = name)
        addProfile(profile)
        setActiveProfile(profile.id)
        return profile
    }

    fun getProfileSetting(profileId: String, key: String, default: String = ""): String {
        return prefs.getSetting("profile_${profileId}_$key", default)
    }

    fun saveProfileSetting(profileId: String, key: String, value: String) {
        prefs.saveSetting("profile_${profileId}_$key", value)
    }

    fun getProfileApiKey(profileId: String, provider: String): String {
        return prefs.getApiKey("profile_${profileId}_$provider")
    }

    fun saveProfileApiKey(profileId: String, provider: String, key: String) {
        prefs.saveApiKey("profile_${profileId}_$provider", key)
    }

    // ─────────────────────────────────────────────────────────────
    //  PIN / biometric security helpers
    // ─────────────────────────────────────────────────────────────
    fun isPinSecured(profile: Profile?): Boolean =
        profile != null && !profile.pinHash.isNullOrBlank()

    /** Hashes [pin] with [salt] using SHA-256 (hex). */
    fun hashPin(pin: String, salt: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Generates a random 16-byte salt as a hex string. */
    fun newSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Sets (or changes) a profile's PIN. Pass null/blank [pin] to remove the lock. */
    fun setProfilePin(profileId: String, pin: String?, pinLength: Int = 4) {
        val profile = getProfiles().find { it.id == profileId } ?: return
        val updated = if (pin.isNullOrBlank()) {
            profile.copy(pinHash = null, pinSalt = null, pinLength = pinLength)
        } else {
            val salt = profile.pinSalt ?: newSalt()
            profile.copy(pinHash = hashPin(pin, salt), pinSalt = salt, pinLength = pinLength)
        }
        updateProfile(updated)
    }

    /** Verifies a supplied [pin] against a profile's stored hash. */
    fun verifyPin(profile: Profile?, pin: String): Boolean {
        val hash = profile?.pinHash ?: return false
        val salt = profile.pinSalt ?: return false
        return hashPin(pin, salt) == hash
    }

    /** Sets whether a profile should offer biometric unlocking. */
    fun setProfileBiometric(profileId: String, enabled: Boolean) {
        val profile = getProfiles().find { it.id == profileId } ?: return
        updateProfile(profile.copy(useBiometric = enabled))
    }
}

fun centerCropToSquare(source: Bitmap): Bitmap {
    val size = minOf(source.width, source.height)
    val x = (source.width - size) / 2
    val y = (source.height - size) / 2
    return Bitmap.createBitmap(source, x, y, size, size)
}
