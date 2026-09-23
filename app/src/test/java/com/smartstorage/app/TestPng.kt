package com.smartstorage.app

/** Two-by-two RGBA fixture: three transparent pixels and one opaque green pixel. */
object TestPng {
    val bytes: ByteArray get() = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAD0lEQVR4nGNgQAH/Gf4DAAQOAf+zZbiZAAAAAElFTkSuQmCC")
    fun rawPixels(png: ByteArray): ByteArray {
        val input = java.io.DataInputStream(png.inputStream())
        input.skipBytes(8)
        val compressed = java.io.ByteArrayOutputStream()
        while (input.available() > 0) {
            val length = input.readInt()
            val type = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
            val data = ByteArray(length).also(input::readFully)
            input.readInt()
            if (type == "IDAT") compressed.write(data)
        }
        return java.util.zip.InflaterInputStream(compressed.toByteArray().inputStream()).use { it.readBytes() }
    }
}
