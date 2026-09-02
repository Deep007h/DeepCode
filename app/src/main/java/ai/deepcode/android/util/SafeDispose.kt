package ai.deepcode.android.util

import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

object SafeDispose {

    fun dispose(client: OkHttpClient?) {
        if (client == null) return
        try {
            client.dispatcher.executorService.shutdown()
            try {
                client.dispatcher.executorService.awaitTermination(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                client.dispatcher.executorService.shutdownNow()
            }
            client.connectionPool.evictAll()
        } catch (_: Exception) {}
    }

    fun disposeQuietly(closeable: Closeable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (_: Exception) {}
    }

    fun <T : Closeable> T.useSafely(block: (T) -> Unit) {
        try {
            block(this)
        } catch (e: Exception) {
            AppLogger.e("SafeDispose", "Error in useSafely", e)
        } finally {
            disposeQuietly(this)
        }
    }
}
