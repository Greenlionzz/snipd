package com.mink.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mink.app.core.Logger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tuned_secure_keys", Context.MODE_PRIVATE)
    private val keyAlias = "tuned_ai_keystore_key"

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(keyAlias)) {
                val kgen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                kgen.init(
                    KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                kgen.generateKey()
            }
        } catch (e: Exception) {
            Logger.e("SecureKeyStore", "Failed to init KeyStore key", e)
        }
    }

    fun put(key: String, value: String) {
        if (value.isBlank()) {
            remove(key)
            return
        }
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = ks.getKey(keyAlias, null) as? SecretKey
            if (secretKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.getIV()
                val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + cipherText.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
                val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
                prefs.edit().putString(key, encoded).apply()
                return
            }
        } catch (e: Exception) {
            Logger.e("SecureKeyStore", "Cipher encrypt failed, fallback to base64", e)
        }
        val fallback = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs.edit().putString(key, "raw:$fallback").apply()
    }

    fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        if (encoded.startsWith("raw:")) {
            val rawB64 = encoded.removePrefix("raw:")
            return String(Base64.decode(rawB64, Base64.NO_WRAP), Charsets.UTF_8)
        }
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = ks.getKey(keyAlias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(cipherText)
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.e("SecureKeyStore", "Failed to decrypt key: $key", e)
            null
        }
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
