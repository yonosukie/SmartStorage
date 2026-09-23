package com.smartstorage.app

import android.app.Application
import com.smartstorage.app.data.RemoveBgClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RemoveBgClientTest {
    private class Response(val status: Int, val body: ByteArray) : HttpURLConnection(URL("https://api.remove.bg/v1.0/removebg")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = body.inputStream()
        override fun getResponseCode() = status
    }
    private fun source() = File.createTempFile("source", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    @Test fun sendsMultipartAndPreservesTransparentPng() = runBlocking {
        val png = TestPng.bytes
        val response = Response(200, png); val photo = source()
        try {
            val result = RemoveBgClient { response }.remove(photo, "test-key")
            assertArrayEquals(png, result)
            assertEquals("test-key", response.getRequestProperty("X-Api-Key"))
            assertTrue(response.sent.toString("UTF-8").contains("name=\"image_file\""))
            assertTrue(response.sent.toString("UTF-8").contains("preview"))
            assertEquals(6, result[25].toInt()) // PNG RGBA color type
            val pixels = TestPng.rawPixels(result)
            assertEquals(0, pixels[4].toInt() and 255)
            assertEquals(255, pixels[17].toInt() and 255)
            assertTrue(response.closed)
            assertFalse(response.instanceFollowRedirects)
        } finally { photo.delete() }
    }
    @Test fun authenticationAndQuotaErrorsKeepSource() = runBlocking {
        val photo = source()
        try {
            for (code in listOf(401, 402, 429, 500)) {
                val response = Response(code, byteArrayOf())
                val error = runCatching { RemoveBgClient { response }.remove(photo, "test-key") }.exceptionOrNull()
                assertNotNull(error); assertFalse(error!!.message.orEmpty().contains("test-key"))
                assertTrue(photo.exists()); assertTrue(response.closed)
            }
        } finally { photo.delete() }
    }
    @Test fun invalidPayloadIsRejectedWithoutChangingSource() = runBlocking {
        val photo = source(); val response = Response(200, "not an image".toByteArray())
        try {
            assertTrue(runCatching { RemoveBgClient { response }.remove(photo, "test-key") }.isFailure)
            assertArrayEquals(byteArrayOf(1, 2, 3), photo.readBytes())
        } finally { photo.delete() }
    }
}
