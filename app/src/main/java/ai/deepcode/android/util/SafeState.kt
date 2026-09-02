package ai.deepcode.android.util

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

object SafeState {

    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    suspend fun <T> updateStateFlow(flow: MutableStateFlow<T>, value: T) {
        flow.value = value
    }

    suspend fun <T> updateStateFlow(flow: MutableStateFlow<T>, updater: (T) -> T) {
        flow.value = updater(flow.value)
    }

    fun <T> updateStateFlowBlocking(flow: MutableStateFlow<T>, value: T) {
        flow.value = value
    }

    fun <T> tryUpdateStateFlow(flow: MutableStateFlow<T>, value: T) {
        try {
            flow.value = value
        } catch (_: Exception) {}
    }

    fun <T> tryUpdateStateFlow(flow: MutableStateFlow<T>, updater: (T) -> T) {
        try {
            flow.value = updater(flow.value)
        } catch (_: Exception) {}
    }
}
