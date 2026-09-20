package ai.deepcode.android.util

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import ai.deepcode.android.data.local.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages display refresh rates and adaptive Variable Refresh Rate (VRR)
 * across 60Hz, 90Hz, 120Hz, and 144Hz display hardware.
 */
object RefreshRateManager {
    private const val TAG = "RefreshRateManager"
    const val SETTING_KEY = "refresh_rate_mode"

    const val MODE_DYNAMIC = "dynamic" // Adaptive 60 - 90 - 120 - 144 Hz
    const val MODE_144 = "144"         // Max 144Hz
    const val MODE_120 = "120"         // 120Hz
    const val MODE_90 = "90"           // 90Hz
    const val MODE_60 = "60"           // Standard 60Hz

    private var activityRef: WeakReference<Activity>? = null
    private var prefs: EncryptedPrefs? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentMode = MutableStateFlow(MODE_DYNAMIC)
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _appliedRefreshRate = MutableStateFlow(60f)
    val appliedRefreshRate: StateFlow<Float> = _appliedRefreshRate.asStateFlow()

    private val _supportedRates = MutableStateFlow<List<Float>>(listOf(60f, 90f, 120f, 144f))
    val supportedRates: StateFlow<List<Float>> = _supportedRates.asStateFlow()

    private var isStreamingActive = false
    private var isUserInteracting = false

    // Step-down runnables for variable refresh rate
    private val stepDownToIntermediateRunnable = Runnable {
        if (_currentMode.value == MODE_DYNAMIC && !isStreamingActive) {
            val supported = _supportedRates.value
            // Prefer 90Hz or 120Hz as intermediate step if supported
            val intermediate = when {
                supported.any { it in 88f..92f } -> 90f
                supported.any { it in 118f..122f } && _appliedRefreshRate.value > 122f -> 120f
                else -> 60f
            }
            if (intermediate < _appliedRefreshRate.value) {
                applyHz(intermediate, minHz = 60f, maxHz = 144f)
            }
        }
    }

    private val stepDownTo60Runnable = Runnable {
        if (_currentMode.value == MODE_DYNAMIC && !isStreamingActive) {
            applyHz(60f, minHz = 60f, maxHz = 144f)
            isUserInteracting = false
        }
    }

    fun init(activity: Activity, securePrefs: EncryptedPrefs) {
        activityRef = WeakReference(activity)
        prefs = securePrefs

        detectSupportedRates(activity)

        val savedMode = securePrefs.getSetting(SETTING_KEY, MODE_DYNAMIC)
        _currentMode.value = savedMode
        applyMode(savedMode)
    }

    private fun detectSupportedRates(activity: Activity) {
        try {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            }

            val modes = display?.supportedModes ?: emptyArray()
            val rates = modes.map { (it.refreshRate * 10f).roundToInt() / 10f }
                .distinct()
                .sorted()

            if (rates.isNotEmpty()) {
                _supportedRates.value = rates
            }
            AppLogger.i(TAG, "Hardware supported display rates: ${_supportedRates.value}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error querying display modes", e)
        }
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        prefs?.saveSetting(SETTING_KEY, mode)
        applyMode(mode)
    }

    fun applyMode(mode: String) {
        mainHandler.removeCallbacks(stepDownToIntermediateRunnable)
        mainHandler.removeCallbacks(stepDownTo60Runnable)

        when (mode) {
            MODE_DYNAMIC -> {
                val maxHz = getMaxSupportedHz(144f)
                applyHz(maxHz, minHz = 60f, maxHz = maxHz)
                scheduleStepDown()
            }
            MODE_144 -> {
                val targetHz = getMaxSupportedHz(144f)
                applyHz(targetHz, minHz = targetHz, maxHz = targetHz)
            }
            MODE_120 -> {
                val targetHz = getClosestSupportedHz(120f)
                applyHz(targetHz, minHz = targetHz, maxHz = targetHz)
            }
            MODE_90 -> {
                val targetHz = getClosestSupportedHz(90f)
                applyHz(targetHz, minHz = targetHz, maxHz = targetHz)
            }
            MODE_60 -> {
                applyHz(60f, minHz = 60f, maxHz = 60f)
            }
            else -> {
                val maxHz = getMaxSupportedHz(144f)
                applyHz(maxHz, minHz = 60f, maxHz = maxHz)
            }
        }
    }

    /**
     * Boosts display refresh rate immediately upon touch/gesture interaction in DYNAMIC mode.
     */
    fun onUserInteraction() {
        if (_currentMode.value != MODE_DYNAMIC) return

        isUserInteracting = true
        val maxHz = getMaxSupportedHz(144f)
        if (_appliedRefreshRate.value < maxHz) {
            applyHz(maxHz, minHz = 60f, maxHz = maxHz)
        }
        scheduleStepDown()
    }

    /**
     * Locks refresh rate to high while chat streaming/animations are running.
     */
    fun setStreamingActive(active: Boolean) {
        isStreamingActive = active
        if (_currentMode.value == MODE_DYNAMIC) {
            if (active) {
                mainHandler.removeCallbacks(stepDownToIntermediateRunnable)
                mainHandler.removeCallbacks(stepDownTo60Runnable)
                val maxHz = getMaxSupportedHz(144f)
                applyHz(maxHz, minHz = 60f, maxHz = maxHz)
            } else {
                scheduleStepDown()
            }
        }
    }

    private fun scheduleStepDown() {
        mainHandler.removeCallbacks(stepDownToIntermediateRunnable)
        mainHandler.removeCallbacks(stepDownTo60Runnable)

        // Step down to intermediate (90/120Hz) after 1.5s idle
        mainHandler.postDelayed(stepDownToIntermediateRunnable, 1500L)
        // Step down to 60Hz after 3.0s idle
        mainHandler.postDelayed(stepDownTo60Runnable, 3000L)
    }

    private fun applyHz(targetHz: Float, minHz: Float, maxHz: Float) {
        val activity = activityRef?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val window = activity.window ?: return@runOnUiThread
                val lp = window.attributes
                var changed = false

                // 1. Min/Max Display Refresh Rate hints via reflection (supported on Android 11+ / 12+)
                try {
                    val minField = lp.javaClass.getField("preferredMinDisplayRefreshRate")
                    minField.setFloat(lp, minHz)
                    changed = true
                } catch (_: Exception) {}

                try {
                    val maxField = lp.javaClass.getField("preferredMaxDisplayRefreshRate")
                    maxField.setFloat(lp, maxHz)
                    changed = true
                } catch (_: Exception) {}

                // Android 14+ touch boost flag
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val method = lp.javaClass.getMethod("setFrameRateBoostOnTouchEnabled", Boolean::class.javaPrimitiveType)
                        method.invoke(lp, true)
                        changed = true
                    }
                } catch (_: Exception) {}

                // 2. Window LayoutParams preferredRefreshRate
                if (lp.preferredRefreshRate != targetHz) {
                    lp.preferredRefreshRate = targetHz
                    changed = true
                }

                // 3. Find matching display mode ID if supported
                val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay
                }

                val modes = display?.supportedModes ?: emptyArray()
                val bestMode = modes
                    .filter { abs(it.refreshRate - targetHz) < 2.0f }
                    .maxByOrNull { it.physicalWidth * it.physicalHeight }

                if (bestMode != null && lp.preferredDisplayModeId != bestMode.modeId) {
                    lp.preferredDisplayModeId = bestMode.modeId
                    changed = true
                }

                if (changed) {
                    window.attributes = lp
                }

                // 4. View requested frame rate hint on Android 14+ / 34+
                try {
                    val decorView = window.peekDecorView()
                    if (decorView != null) {
                        val method = decorView.javaClass.getMethod("setRequestedFrameRate", Float::class.javaPrimitiveType)
                        method.invoke(decorView, targetHz)
                    }
                } catch (_: Exception) {}

                _appliedRefreshRate.value = targetHz
                AppLogger.d(TAG, "Applied refresh rate: $targetHz Hz (min: $minHz, max: $maxHz)")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to apply refresh rate $targetHz", e)
            }
        }
    }

    fun getMaxSupportedHz(cap: Float = 144f): Float {
        val supported = _supportedRates.value
        val valid = supported.filter { it <= cap + 2.0f }
        return valid.maxOrNull() ?: cap
    }

    fun getClosestSupportedHz(target: Float): Float {
        val supported = _supportedRates.value
        if (supported.isEmpty()) return target
        return supported.minByOrNull { abs(it - target) } ?: target
    }
}
