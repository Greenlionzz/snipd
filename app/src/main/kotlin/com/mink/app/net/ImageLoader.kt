package com.mink.app.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.mink.app.core.AppExecutors
import com.mink.app.core.Cancelable
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class ImageLoader(private val cacheDir: File) {

    private val memCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        memCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun load(url: String, imageView: ImageView, placeholderRes: Int? = null): Cancelable {
        if (url.isBlank()) {
            if (placeholderRes != null) imageView.setImageResource(placeholderRes)
            return Cancelable.NOOP
        }

        val cached = memCache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return Cancelable.NOOP
        }

        if (placeholderRes != null) {
            imageView.setImageResource(placeholderRes)
        }

        imageView.tag = url

        return AppExecutors.executeIo {
            val bitmap = fetchBitmap(url)
            if (bitmap != null) {
                memCache.put(url, bitmap)
                AppExecutors.main {
                    if (imageView.tag == url) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun fetchBitmap(urlStr: String): Bitmap? {
        val diskFile = File(cacheDir, md5(urlStr) + ".img")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                return BitmapFactory.decodeFile(diskFile.absolutePath)
            } catch (_: Exception) {}
        }

        return try {
            val url = URL(urlStr)
            val conn = url.openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.getInputStream().use { input ->
                val bytes = input.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    try {
                        FileOutputStream(diskFile).use { it.write(bytes) }
                    } catch (_: Exception) {}
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun md5(s: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            s.hashCode().toString()
        }
    }
}
