package ai.deepcode.android.util

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, exception)
    }

    fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> Error(message, exception)
    }

    companion object {
        fun <T> of(value: T?): AppResult<T> {
            return if (value != null) Success(value) else Error("Null value")
        }

        suspend fun <T> tryOf(block: suspend () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e.message ?: "Unknown error", e)
            }
        }
    }
}

object CrashGuard {

    fun <T> run(label: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Caught exception in [$label]", e)
            null
        }
    }

    fun <T> run(label: String, default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Caught exception in [$label]", e)
            default
        }
    }

    suspend fun <T> runSuspend(label: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Caught exception in [$label]", e)
            null
        }
    }

    suspend fun <T> runSuspend(label: String, default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Caught exception in [$label]", e)
            default
        }
    }

    fun ignore(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Ignored exception in [$label]", e)
        }
    }

    suspend fun ignoreSuspend(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            AppLogger.e("CrashGuard", "Ignored exception in [$label]", e)
        }
    }
}
