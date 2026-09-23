package com.smartstorage.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

val storageJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }
@Serializable private data class Manifest(val version: Int = 1, val createdAt: Long = System.currentTimeMillis(), val checksums: Map<String, String>)
data class RestoredArchive(val state: Inventory, val photos: Map<String, ByteArray>)

object Archive {
    private const val MAX_BYTES = 128 * 1024 * 1024
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)); key.fill(0) }
    }
    fun write(state: Inventory, photos: (String) -> ByteArray, output: OutputStream, password: CharArray = charArrayOf()) {
        InventoryRules.validate(state)
        val entries = linkedMapOf("inventory.json" to storageJson.encodeToString(state).toByteArray(Charsets.UTF_8))
        var size = entries.values.sumOf { it.size.toLong() }
        state.items.mapNotNull { it.photo }.distinct().forEach { name ->
            require(name.matches(Regex("[a-zA-Z0-9_-]+\\.(jpg|png)"))) { "图片路径无效" }
            val bytes = photos(name); size += bytes.size
            require(size <= MAX_BYTES) { "备份超过当前版本 128 MB 限制" }
            entries["photos/$name"] = bytes
        }
        val data = DataOutputStream(output)
        data.writeBytes(if (password.isEmpty()) "SSB1P" else "SSB1E")
        val payload: OutputStream = if (password.isEmpty()) data else {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes); val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            data.write(salt); data.write(iv); CipherOutputStream(data, cipher(Cipher.ENCRYPT_MODE, password, salt, iv))
        }
        ZipOutputStream(payload).use { zip ->
            entries["manifest.json"] = storageJson.encodeToString(Manifest(checksums = entries.mapValues { sha(it.value) })).toByteArray(Charsets.UTF_8)
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    fun read(input: InputStream, password: CharArray = charArrayOf()): RestoredArchive {
        val data = DataInputStream(input)
        val header = ByteArray(5); data.readFully(header)
        val mode = header.toString(Charsets.US_ASCII)
        require(mode == "SSB1P" || mode == "SSB1E") { "不是有效的 SmartStorage 备份" }
        val stream = if (mode == "SSB1E") {
            require(password.isNotEmpty()) { "此备份需要密码" }
            val salt = ByteArray(16); val iv = ByteArray(12); data.readFully(salt); data.readFully(iv)
            CipherInputStream(data, cipher(Cipher.DECRYPT_MODE, password, salt, iv))
        } else data
        // Read through the authentication tag before parsing any encrypted content.
        val zipBytes = stream.use { it.readLimited(MAX_BYTES + 4 * 1024 * 1024) }
        val entries = linkedMapOf<String, ByteArray>(); var size = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entries.size < 20_000 && !entry.isDirectory && entry.name !in entries) { "备份结构无效" }
                require(entry.name == "inventory.json" || entry.name == "manifest.json" || entry.name.matches(Regex("photos/[a-zA-Z0-9_-]+\\.(jpg|png)"))) { "备份包含非法路径" }
                val bytes = zip.readLimited((MAX_BYTES - size).toInt()); size += bytes.size; entries[entry.name] = bytes
            }
        }
        val manifest = storageJson.decodeFromString<Manifest>(entries.remove("manifest.json")?.toString(Charsets.UTF_8) ?: error("缺少校验清单"))
        require(manifest.version == 1 && entries.keys == manifest.checksums.keys) { "备份清单不完整" }
        require(entries.all { sha(it.value) == manifest.checksums[it.key] }) { "备份校验失败" }
        val state = storageJson.decodeFromString<Inventory>(entries.getValue("inventory.json").toString(Charsets.UTF_8))
        InventoryRules.validate(state)
        val photos = entries.filterKeys { it.startsWith("photos/") }.mapKeys { it.key.removePrefix("photos/") }
        require(state.items.mapNotNull { it.photo }.toSet() == photos.keys) { "图片引用不完整" }
        return RestoredArchive(state, photos)
    }
    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
        while (true) { val n = read(buffer); if (n < 0) break; total += n; require(total <= limit) { "备份文件过大" }; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
}
