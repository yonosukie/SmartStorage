package com.smartstorage.app.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Fixed HTTPS endpoint. Keys and server response bodies are never logged. */
class RemoveBgClient(private val connectionFactory: () -> HttpURLConnection = {
    URL("https://api.remove.bg/v1.0/removebg").openConnection() as HttpURLConnection
}) {
    suspend fun remove(photo: File, key: String): ByteArray = withContext(Dispatchers.IO) {
        require(key.isNotBlank() && key.none { it == '\r' || it == '\n' }) { "请先配置 remove.bg API Key" }
        require(photo.isFile && photo.length() in 1..10_000_000) { "图片不存在或超过 10 MB" }
        val boundary = "Storage${UUID.randomUUID()}"
        val connection = connectionFactory()
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("X-Api-Key", key)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setChunkedStreamingMode(8192)
            connection.outputStream.use { out ->
                fun text(value: String) { out.write(value.toByteArray(Charsets.UTF_8)) }
                text("--$boundary\r\nContent-Disposition: form-data; name=\"size\"\r\n\r\npreview\r\n")
                text("--$boundary\r\nContent-Disposition: form-data; name=\"format\"\r\n\r\npng\r\n")
                val png = photo.extension.equals("png", true)
                text("--$boundary\r\nContent-Disposition: form-data; name=\"image_file\"; filename=\"image.${if (png) "png" else "jpg"}\"\r\nContent-Type: image/${if (png) "png" else "jpeg"}\r\n\r\n")
                photo.inputStream().use { it.copyTo(out) }
                text("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            if (code != 200) error(when (code) {
                401, 403 -> "抠图密钥无效或权限不足，请检查配置"
                402 -> "remove.bg 额度不足，请检查账户"
                429 -> "抠图请求过于频繁，请稍后手动重试"
                400 -> "图片暂不支持抠图，或未识别到清晰主体"
                else -> "抠图服务暂不可用（HTTP $code），请稍后重试"
            })
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size() + count <= 8_000_000) { "抠图结果过大" }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            require(bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() }) { "抠图服务未返回有效 PNG" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000) { "抠图图片无效或尺寸过大" }
            ensureActive()
            bytes
        } catch (e: java.net.SocketTimeoutException) { throw IllegalStateException("抠图连接超时，请稍后重试") }
        catch (e: java.io.IOException) { throw IllegalStateException("无法连接抠图服务，请检查网络") }
        finally { connection.disconnect() }
    }
}
