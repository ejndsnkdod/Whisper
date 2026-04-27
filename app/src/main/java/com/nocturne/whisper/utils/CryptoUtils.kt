package com.nocturne.whisper.utils

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val KEY_LENGTH = 16

    private fun getSecretKey(context: Context): SecretKeySpec {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "default_key_1234"

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))
        val keyBytes = hashBytes.copyOf(KEY_LENGTH)

        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun generateIV(): ByteArray {
        val iv = ByteArray(16)
        java.security.SecureRandom().nextBytes(iv)
        return iv
    }

    fun encrypt(context: Context, value: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getSecretKey(context)
            val iv = generateIV()

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()

            value
        }
    }

    fun decrypt(context: Context, value: String): String {
        return try {
            val combined = Base64.decode(value, Base64.NO_WRAP)

            val iv = combined.copyOfRange(0, 16)
            val encryptedBytes = combined.copyOfRange(16, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getSecretKey(context)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()

            value
        }
    }

    fun isEncrypted(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        return try {
            val decoded = Base64.decode(value, Base64.NO_WRAP)

            decoded.size > 16
        } catch (e: Exception) {
            false
        }
    }
}
