package com.mink.app.net

import com.mink.app.core.Cancelable
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection

class SseReader(
    private val conn: HttpURLConnection,
    private val inputStream: InputStream,
    private val onChunk: (String) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (Throwable) -> Unit
) : Runnable, Cancelable {

    @Volatile
    private var isCancelled = false

    override fun run() {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var line: String? = null
            while (!isCancelled && reader?.readLine().also { line = it } != null) {
                val current = line ?: break
                if (current.startsWith("data:")) {
                    val data = current.substring(5).trim()
                    if (data == "[DONE]") {
                        break
                    }
                    if (data.isNotEmpty()) {
                        onChunk(data)
                    }
                }
            }
            if (!isCancelled) {
                onDone()
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                onError(e)
            }
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    override fun cancel() {
        isCancelled = true
        try { conn.disconnect() } catch (_: Exception) {}
    }
}
