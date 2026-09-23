package com.smartstorage.app.data

import android.content.Context

class RecognitionSettings(context: Context) {
    private val secret = CutoutSettings(context, "recognition-private", "smartstorage-openrouter")
    private val prefs = context.getSharedPreferences("recognition-private", Context.MODE_PRIVATE)
    fun readKey() = secret.readKey()
    val model: String get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    var automatic: Boolean
        get() = secret.automatic
        set(value) { secret.automatic = value }
    @android.annotation.SuppressLint("UseKtx")
    fun save(key: String, model: String) {
        val selected = model.trim()
        requireFreeModel(selected)
        secret.saveKey(key)
        check(prefs.edit().putString("model", selected).commit()) { "模型配置保存失败" }
    }
    fun clear() = secret.clear()
    companion object {
        const val DEFAULT_MODEL = "openrouter/free"
        fun requireFreeModel(model: String) {
            require(model == DEFAULT_MODEL || model.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.:-]+:free"))) {
                "仅支持 openrouter/free 或以 :free 结尾的免费视觉模型"
            }
        }
    }
}
