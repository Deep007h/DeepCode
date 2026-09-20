package ai.deepcode.android.util

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class RootFlavor(val displayName: String) {
    KERNEL_SU("KernelSU"),
    MAGISK("Magisk"),
    APATCH("APatch"),
    GENERIC_SU("Generic Root (su)"),
    NONE("Not Rooted")
}

data class RootCheckResult(
    val isAvailable: Boolean,
    val isGranted: Boolean,
    val flavor: RootFlavor,
    val uidInfo: String,
    val message: String
)

object RootSystem {
    private const val TAG = "RootSystem"

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    private val _rootFlavor = MutableStateFlow(RootFlavor.NONE)
    val rootFlavor: StateFlow<RootFlavor> = _rootFlavor.asStateFlow()

    private val _lastOutput = MutableStateFlow("")
    val lastOutput: StateFlow<String> = _lastOutput.asStateFlow()

    private val KNOWN_SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    )

    private var cachedSuBinary: String? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            detectInitialState()
        }
    }

    private fun detectInitialState() {
        val suPath = findSuBinary()
        if (suPath != null) {
            _isRootAvailable.value = true
            _rootFlavor.value = detectFlavor()
        } else {
            _isRootAvailable.value = false
            _rootFlavor.value = RootFlavor.NONE
        }
    }

    fun findSuBinary(): String? {
        cachedSuBinary?.let { if (File(it).exists() || it == "su") return it }

        for (path in KNOWN_SU_PATHS) {
            try {
                val f = File(path)
                if (f.exists() && f.canExecute()) {
                    cachedSuBinary = path
                    return path
                }
            } catch (_: Exception) {}
        }

        // Check if su is in system PATH via `which su`
        try {
            val proc = ProcessBuilder("which", "su").start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor(2, TimeUnit.SECONDS) && proc.exitValue() == 0 && out.isNotEmpty()) {
                cachedSuBinary = out
                return out
            }
        } catch (_: Exception) {}

        // Even if which fails, su might be callable directly
        if (checkSuCallable()) {
            cachedSuBinary = "su"
            return "su"
        }

        return null
    }

    fun getSuBinaryPath(): String {
        return findSuBinary() ?: "su"
    }

    private fun checkSuCallable(): Boolean {
        return try {
            val proc = ProcessBuilder("su", "-v").start()
            proc.waitFor(2, TimeUnit.SECONDS)
            proc.exitValue() == 0 || proc.inputStream.bufferedReader().readText().isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun detectFlavor(): RootFlavor {
        // 1. Check KernelSU
        try {
            if (File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists()) {
                return RootFlavor.KERNEL_SU
            }
            val procVersion = try { File("/proc/version").readText() } catch (_: Exception) { "" }
            if (procVersion.contains("KernelSU", ignoreCase = true)) {
                return RootFlavor.KERNEL_SU
            }
        } catch (_: Exception) {}

        // 2. Check APatch
        try {
            if (File("/data/adb/ap").exists() || File("/data/adb/apd").exists()) {
                return RootFlavor.APATCH
            }
        } catch (_: Exception) {}

        // 3. Check Magisk
        try {
            if (File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists()) {
                return RootFlavor.MAGISK
            }
        } catch (_: Exception) {}

        // 4. Query su version
        try {
            val suBin = getSuBinaryPath()
            val proc = ProcessBuilder(suBin, "-v").start()
            val vOut = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(2, TimeUnit.SECONDS)
            when {
                vOut.contains("ksu", ignoreCase = true) || vOut.contains("KernelSU", ignoreCase = true) -> return RootFlavor.KERNEL_SU
                vOut.contains("magisk", ignoreCase = true) -> return RootFlavor.MAGISK
                vOut.contains("apatch", ignoreCase = true) -> return RootFlavor.APATCH
                vOut.isNotBlank() -> return RootFlavor.GENERIC_SU
            }
        } catch (_: Exception) {}

        return if (_isRootAvailable.value) RootFlavor.GENERIC_SU else RootFlavor.NONE
    }

    /**
     * Actively requests Root/Superuser access from KernelSU, Magisk, or APatch.
     * This will trigger the system Superuser dialogue if not yet granted.
     * Timeout allows user sufficient time (up to 30 seconds) to tap "Grant" on screen.
     */
    suspend fun requestRootAccess(context: Context, timeoutSeconds: Long = 30L): RootCheckResult = withContext(Dispatchers.IO) {
        val suBin = findSuBinary() ?: "su"
        AppLogger.i(TAG, "Requesting Superuser access using binary: $suBin")

        val prefs = EncryptedPrefs.getInstance(context)
        return@withContext try {
            val pb = ProcessBuilder(suBin, "-c", "id")
                .redirectErrorStream(true)
            val env = pb.environment()
            env["PATH"] = (env["PATH"] ?: "") + ":/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/data/adb/ksu/bin:/data/adb/ap/bin"

            val proc = pb.start()
            val output = StringBuilder()
            val readerThread = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            output.appendLine(line)
                            line = reader.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.isDaemon = true
            readerThread.start()

            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext RootCheckResult(
                    isAvailable = true,
                    isGranted = false,
                    flavor = detectFlavor(),
                    uidInfo = "",
                    message = "Root request timed out ($timeoutSeconds s). If KernelSU or Magisk displayed a prompt, please approve and try again."
                )
            }
            readerThread.join(1000)

            val outText = output.toString().trim()
            val exitCode = proc.exitValue()
            AppLogger.i(TAG, "Root request finished: exitCode=$exitCode, output=$outText")

            val isGranted = exitCode == 0 && (outText.contains("uid=0") || outText.contains("root"))
            val flavor = detectFlavor()

            _isRootAvailable.value = true
            _isRootGranted.value = isGranted
            _rootFlavor.value = flavor
            _lastOutput.value = outText

            if (isGranted) {
                prefs.saveBooleanSetting("root_mode", true)
                prefs.saveSetting("root_flavor", flavor.displayName)
                RootCheckResult(
                    isAvailable = true,
                    isGranted = true,
                    flavor = flavor,
                    uidInfo = outText,
                    message = "Superuser access granted successfully via ${flavor.displayName} (uid=0)!"
                )
            } else {
                prefs.saveBooleanSetting("root_mode", false)
                RootCheckResult(
                    isAvailable = true,
                    isGranted = false,
                    flavor = flavor,
                    uidInfo = outText,
                    message = if (outText.contains("denied", ignoreCase = true)) {
                        "Superuser permission was denied in ${flavor.displayName}."
                    } else {
                        "Root execution failed (exitCode=$exitCode): $outText"
                    }
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to request root: ${e.message}", e)
            RootCheckResult(
                isAvailable = false,
                isGranted = false,
                flavor = RootFlavor.NONE,
                uidInfo = "",
                message = "Failed to invoke superuser: ${e.message}"
            )
        }
    }

    /**
     * Translates ADB commands or runs native terminal commands as Root.
     */
    fun translateAdbOrShellCommand(rawCommand: String): String {
        val trimmed = rawCommand.trim()
        return when {
            // Strip "adb shell " prefix
            trimmed.startsWith("adb shell ", ignoreCase = true) -> {
                trimmed.substring(10).trim()
            }
            // Strip "adb -s <serial> shell " prefix
            trimmed.matches(Regex("(?i)^adb\\s+-s\\s+\\S+\\s+shell\\s+.*")) -> {
                trimmed.replaceFirst(Regex("(?i)^adb\\s+-s\\s+\\S+\\s+shell\\s+"), "").trim()
            }
            // Emulate "adb devices"
            trimmed.equals("adb devices", ignoreCase = true) || trimmed.equals("adb devices -l", ignoreCase = true) -> {
                "echo \"List of devices attached\nlocalhost:5555          device product:deepcode model:rooted_device device:android (native root)\""
            }
            // "adb logcat" -> "logcat"
            trimmed.startsWith("adb logcat", ignoreCase = true) -> {
                "logcat" + trimmed.substring(10)
            }
            // "adb install [-r] <path>" -> "pm install [-r] <path>"
            trimmed.startsWith("adb install", ignoreCase = true) -> {
                "pm install" + trimmed.substring(11)
            }
            // "adb uninstall <pkg>" -> "pm uninstall <pkg>"
            trimmed.startsWith("adb uninstall", ignoreCase = true) -> {
                "pm uninstall" + trimmed.substring(13)
            }
            // "adb push <src> <dst>" -> "cp -rf <src> <dst>"
            trimmed.startsWith("adb push", ignoreCase = true) -> {
                "cp -rf" + trimmed.substring(8)
            }
            // "adb pull <src> <dst>" -> "cp -rf <src> <dst>"
            trimmed.startsWith("adb pull", ignoreCase = true) -> {
                "cp -rf" + trimmed.substring(8)
            }
            else -> trimmed
        }
    }

    /**
     * Executes any terminal command directly as root via `su -c`.
     */
    fun executeAsRoot(command: String, workingDir: String = "/storage/emulated/0"): String {
        val suBin = getSuBinaryPath()
        val finalCmd = translateAdbOrShellCommand(command)

        return try {
            val workingDirFile = File(workingDir).takeIf { it.exists() } ?: File("/")
            val pb = ProcessBuilder(suBin, "-c", finalCmd)
                .directory(workingDirFile)
                .redirectErrorStream(true)

            val env = pb.environment()
            env["PATH"] = (env["PATH"] ?: "") + ":/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/data/adb/ksu/bin:/data/adb/ap/bin"

            val proc = pb.start()
            val output = StringBuilder()
            val buffer = CharArray(4096)
            proc.inputStream.bufferedReader().use { reader ->
                var count = reader.read(buffer)
                while (count != -1 && output.length < 256 * 1024) {
                    output.append(buffer, 0, count)
                    count = reader.read(buffer)
                }
            }

            val finished = proc.waitFor(45, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                output.append("\n[Command timed out after 45s and was terminated]")
            }

            val res = output.toString()
            _lastOutput.value = res.take(1000)
            res.ifEmpty { "[Command completed with exit code ${proc.exitValue()}]" }
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeAsRoot failed for: $command", e)
            "Error executing root command: ${e.message}"
        }
    }
}
