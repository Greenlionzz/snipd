package com.mink.app.net

import com.mink.app.core.ErrorCode
import com.mink.app.core.Logger
import com.mink.app.core.Result
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String
) {
    fun header(name: String): String? = headers.entries.find { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

object HttpClient {

    private const val DEFAULT_TIMEOUT_MS = 15000
    private const val USER_AGENT = "Tuned/1.0 (Android; CodeAssist)"

    fun get(
        urlStr: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Result<HttpResponse> {
        return request("GET", urlStr, headers, null, timeoutMs)
    }

    fun post(
        urlStr: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Result<HttpResponse> {
        return request("POST", urlStr, headers, body, timeoutMs)
    }

    fun request(
        method: String,
        urlStr: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Result<HttpResponse> {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "gzip")
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
            }

            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                }
            }

            val statusCode = conn.responseCode
            val isSuccess = statusCode in 200..299 || statusCode == 304

            val rawStream: InputStream? = if (isSuccess) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }

            val contentEncoding = conn.contentEncoding
            val stream = if (rawStream != null && contentEncoding != null && contentEncoding.contains("gzip", ignoreCase = true)) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }

            val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val responseHeaders = conn.headerFields ?: emptyMap()
            conn.disconnect()

            val resp = HttpResponse(statusCode, responseHeaders, responseBody)
            if (statusCode in 200..299 || statusCode == 304) {
                Result.Ok(resp)
            } else {
                val errCode = when (statusCode) {
                    401, 403 -> ErrorCode.BAD_KEY
                    429 -> ErrorCode.RATE_LIMIT
                    404 -> ErrorCode.NOT_FOUND
                    else -> ErrorCode.NETWORK
                }
                Result.Err("HTTP error $statusCode: $responseBody", code = errCode)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Logger.e("HttpClient", "Timeout connecting to $urlStr", e)
            Result.Err("Request timed out", code = ErrorCode.TIMEOUT, cause = e)
        } catch (e: Exception) {
            Logger.e("HttpClient", "Error connecting to $urlStr", e)
            Result.Err(e.message ?: "Network error", code = ErrorCode.NETWORK, cause = e)
        }
    }

    fun openStream(
        urlStr: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 30000
    ): Result<Pair<HttpURLConnection, InputStream>> {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (body != null) "POST" else "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/event-stream")
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
            }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val stream = conn.inputStream
                Result.Ok(Pair(conn, stream))
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Result.Err("HTTP $code: $errText", code = ErrorCode.NETWORK)
            }
        } catch (e: Exception) {
            Result.Err(e.message ?: "Stream failed", code = ErrorCode.NETWORK, cause = e)
        }
    }
}
