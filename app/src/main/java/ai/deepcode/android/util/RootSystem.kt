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
    const val ROOT_PATH_EXTENSIONS = ":/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/data/data/com.termux/files/usr/bin:/data/local/tmp:/data/adb/modules"

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
            val flavor = detectFlavor()
            _rootFlavor.value = flavor
            checkSuAlreadyGranted()
        } else {
            _isRootAvailable.value = false
            _rootFlavor.value = RootFlavor.NONE
            _isRootGranted.value = false
        }
    }

    private fun checkSuAlreadyGranted() {
        try {
            val suBin = getSuBinaryPath()
            val pb = ProcessBuilder(suBin, "-c", "id").redirectErrorStream(true)
            val env = pb.environment()
            env["PATH"] = (env["PATH"] ?: "") + ROOT_PATH_EXTENSIONS
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            val finished = proc.waitFor(1500, TimeUnit.MILLISECONDS)
            if (finished && proc.exitValue() == 0 && (out.contains("uid=0") || out.contains("root"))) {
                _isRootGranted.value = true
                _lastOutput.value = out
                grantAllFilesAccessViaRoot("ai.deepcode.android")
            }
        } catch (_: Exception) {}
    }

    fun grantAllFilesAccessViaRoot(packageName: String = "ai.deepcode.android") {
        try {
            val cleanPkg = packageName.trim()
            if (cleanPkg.isNotBlank()) {
                executeAsRoot(
                    "appops set $cleanPkg MANAGE_EXTERNAL_STORAGE allow 2>/dev/null; " +
                    "appops set $cleanPkg NO_ISOLATED_STORAGE allow 2>/dev/null; " +
                    "pm grant $cleanPkg android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; " +
                    "pm grant $cleanPkg android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null; " +
                    "pm grant $cleanPkg android.permission.MANAGE_EXTERNAL_STORAGE 2>/dev/null"
                )
            }
        } catch (_: Exception) {}
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
            env["PATH"] = (env["PATH"] ?: "") + ROOT_PATH_EXTENSIONS

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
                grantAllFilesAccessViaRoot(context.packageName)
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

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9._]+$")

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
            env["PATH"] = (env["PATH"] ?: "") + ROOT_PATH_EXTENSIONS

            val proc = pb.start()
            val output = StringBuilder()
            val readerThread = Thread {
                try {
                    val buffer = CharArray(4096)
                    proc.inputStream.bufferedReader().use { reader ->
                        var count = reader.read(buffer)
                        while (count != -1 && output.length < 256 * 1024) {
                            output.append(buffer, 0, count)
                            count = reader.read(buffer)
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.isDaemon = true
            readerThread.start()

            val finished = proc.waitFor(45, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                output.append("\n[Command timed out after 45s and was terminated]")
            }
            readerThread.join(1000)

            val res = output.toString()
            _lastOutput.value = res.take(1000)
            res.ifEmpty { "[Command completed with exit code ${proc.exitValue()}]" }
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeAsRoot failed for: $command", e)
            "Error executing root command: ${e.message}"
        }
    }

    /**
     * Retrieves battery telemetry including percentage, health, temperature, and charging status.
     */
    fun getBatteryInfo(): String {
        return executeAsRoot("dumpsys battery")
    }

    /**
     * Retrieves memory stats (/proc/meminfo) and filesystem storage usage (df -h).
     */
    fun getMemoryInfo(): String {
        return executeAsRoot("cat /proc/meminfo | head -n 12 && echo '\n--- STORAGE USAGE ---' && df -h /data /storage/emulated/0")
    }

    /**
     * Retrieves hardware model, manufacturer, Android SDK, kernel release, uptime, and SELinux status.
     */
    fun getDeviceInfo(): String {
        return executeAsRoot("echo 'Model:' \$(getprop ro.product.model) && echo 'Manufacturer:' \$(getprop ro.product.manufacturer) && echo 'Android:' \$(getprop ro.build.version.release) '(SDK ' \$(getprop ro.build.version.sdk)')' && echo 'Kernel:' \$(uname -a) && echo 'SELinux:' \$(getenforce) && echo 'Uptime:' \$(uptime)")
    }

    /**
     * Lists third-party installed packages, optionally filtered.
     */
    fun listInstalledPackages(filter: String = ""): String {
        val cleanFilter = filter.trim().filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        val filterCmd = if (cleanFilter.isNotBlank()) "| grep -i '$cleanFilter'" else "| head -n 40"
        return executeAsRoot("pm list packages -3 $filterCmd")
    }

    /**
     * Manages app lifecycle: freeze (disable-user), unfreeze (enable), force-stop, clear-cache, or launch.
     */
    fun appControl(action: String, packageName: String): String {
        val pkg = packageName.trim()
        if (pkg.isEmpty() && !action.equals("clear_cache", ignoreCase = true)) {
            return "Error: packageName is required for appControl action: $action"
        }
        if (pkg.isNotEmpty() && !PACKAGE_NAME_REGEX.matches(pkg)) {
            return "Error: Invalid package name: $pkg"
        }
        return when (action.lowercase().trim()) {
            "freeze", "disable" -> executeAsRoot("pm disable-user --user 0 $pkg")
            "unfreeze", "enable" -> executeAsRoot("pm enable $pkg")
            "force_stop", "stop" -> executeAsRoot("am force-stop $pkg")
            "clear_cache" -> executeAsRoot("pm trim-caches 1000M")
            "launch", "open" -> executeAsRoot("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            else -> "Unknown action: '$action'. Supported: freeze, unfreeze, force_stop, clear_cache, launch"
        }
    }

    /**
     * Captures an instant device screenshot via `screencap -p` and returns the file/image markdown marker.
     */
    fun takeScreenshot(outputDir: String = "/storage/emulated/0/Download"): String {
        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()
        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val fullPath = File(dir, filename).absolutePath
        val out = executeAsRoot("screencap -p '$fullPath'")
        return if (File(fullPath).exists() && File(fullPath).length() > 0) {
            "[image:$fullPath]\nScreenshot captured successfully: $fullPath"
        } else {
            "Failed to capture screenshot. Output: $out"
        }
    }
}
