package com.smartstorage.app.data

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CutoutSettings(context: Context) {
    private val prefs = context.getSharedPreferences("cutout-private", Context.MODE_PRIVATE)
    private val alias = "smartstorage-removebg"
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun readKey(): String {
        val data = prefs.getString("key", null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            require(bytes.size > 12)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }
        }.getOrDefault("")
    }
    // Keep commit() result so a failed key write cannot be reported as success.
    @android.annotation.SuppressLint("UseKtx")
    fun saveKey(value: String) {
        val key = value.trim()
        require(key.length in 8..512 && key.none { it.isWhitespace() }) { "请输入有效的 API Key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val data = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("key", Base64.encodeToString(data, Base64.NO_WRAP)).putBoolean("automatic", true).commit()) { "密钥保存失败" }
    }
    var automatic: Boolean
        get() = prefs.getBoolean("automatic", false)
        set(value) { prefs.edit { putBoolean("automatic", value) } }
    @android.annotation.SuppressLint("UseKtx")
    fun clear() { check(prefs.edit().clear().commit()) { "清除配置失败" } }
}
