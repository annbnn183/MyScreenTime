package com.atharok.screentime.common.utils

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 可选的口令加密（P1）。屏幕使用记录可能暴露个人生活习惯，因此允许用户用密码保护备份文件。
 *
 * 结构：AES-256/GCM + PBKDF2 派生密钥（随机盐、随机 IV）。加密后的文件仍是一个 JSON 信封：
 * `{ "encrypted": true, "kdf": ..., "rounds": ..., "salt": ..., "iv": ..., "data": ... }`
 *
 * 注意：密码一旦忘记，数据无法找回（GCM 认证失败即视为密码错误），程序中不会、也无法提供找回。
 * 明文 / 密码 / 派生密钥都不会写入日志。
 */
object BackupCipher {

    private const val KDF = "PBKDF2WithHmacSHA1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ROUNDS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** 快速判断一段文本是不是加密信封。 */
    fun isEncrypted(raw: String): Boolean = runCatching {
        JSONObject(raw).optBoolean("encrypted", false)
    }.getOrDefault(false)

    /** 用口令把明文加密成信封字符串。 */
    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ROUNDS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("encrypted", true)
            put("kdf", KDF)
            put("rounds", ROUNDS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("cipher", TRANSFORMATION)
            put("data", Base64.encodeToString(cipherText, Base64.NO_WRAP))
        }.toString()
    }

    /** 解密信封；口令错误或数据被篡改时返回 null（GCM 认证失败）。 */
    fun decrypt(envelope: JSONObject, password: String): String? = runCatching {
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val rounds = envelope.optInt("rounds", ROUNDS)
        val key = deriveKey(password, salt, rounds)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun deriveKey(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS)
        return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
    }
}
