package com.smartstorage.app

import android.app.Application
import com.smartstorage.app.data.OpenRouterClient
import com.smartstorage.app.data.RecognitionSettings
import com.smartstorage.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OpenRouterClientTest {
    private class Response(val status: Int, val body: String) : HttpURLConnection(URL("https://openrouter.ai/api/v1/chat/completions")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = body.byteInputStream()
        override fun getResponseCode(): Int {
            if (status == -1) throw SocketTimeoutException()
            return status
        }
    }
    private fun envelope(content: String, reason: String = "stop") = buildJsonObject {
        putJsonArray("choices") { addJsonObject {
            put("finish_reason", reason)
            putJsonObject("message") { put("content", content) }
        } }
    }.toString()
    private val item = """{"items":[{"name":"纯牛奶","category":"食品","unit":"盒","notes":"250mL","suggestedTags":["乳制品"]}]}"""
    private fun source() = File.createTempFile("recognition", ".png").apply { writeBytes(TestPng.bytes) }

    @Test fun sendsImageToFreeModelAndKeepsOriginal() = runBlocking {
        val photo = source()
        val response = Response(200, envelope(item))
        try {
            val result = OpenRouterClient { response }.recognize(photo, "test-key", "openrouter/free", categories)
            assertEquals("纯牛奶", result.single().name)
            assertEquals("Bearer test-key", response.getRequestProperty("Authorization"))
            val body = Json.parseToJsonElement(response.sent.toString("UTF-8")).jsonObject
            assertEquals("openrouter/free", body.getValue("model").jsonPrimitive.content)
            val parts = body.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonArray
            assertEquals("text", parts[0].jsonObject.getValue("type").jsonPrimitive.content)
            assertTrue(parts[1].jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
            assertFalse(response.instanceFollowRedirects)
            assertTrue(response.closed)
            assertArrayEquals(TestPng.bytes, photo.readBytes())
        } finally { photo.delete() }
    }

    @Test fun rejectsPaidModelsBeforeOpeningConnection() = runBlocking {
        val photo = source()
        try {
            var calls = 0
            val client = OpenRouterClient { calls++; Response(200, envelope(item)) }
            assertTrue(runCatching { client.recognize(photo, "test-key", "openai/gpt-4o", categories) }.isFailure)
            assertEquals(0, calls)
            RecognitionSettings.requireFreeModel("vendor/vision:free")
            for (model in listOf("openrouter/auto", "vendor/model:free:paid", "https://example.com", " vendor/model:free")) {
                assertTrue(runCatching { RecognitionSettings.requireFreeModel(model) }.isFailure)
            }
        } finally { photo.delete() }
    }

    @Test fun errorsAreSafeAndDoNotRetryOrChangePhoto() = runBlocking {
        val photo = source()
        try {
            for (code in listOf(400, 401, 402, 403, 404, 429, 500, 302, -1)) {
                var calls = 0
                val response = Response(code, "secret server response")
                val error = runCatching { OpenRouterClient { calls++; response }.recognize(photo, "test-key", "openrouter/free", categories) }.exceptionOrNull()
                assertNotNull(error)
                assertFalse(error!!.message.orEmpty().contains("test-key"))
                assertFalse(error.message.orEmpty().contains("secret server"))
                assertEquals(1, calls)
                assertTrue(response.closed)
                assertArrayEquals(TestPng.bytes, photo.readBytes())
            }
        } finally { photo.delete() }
    }

    @Test fun rejectsTruncatedMalformedAndOversizeResponses() = runBlocking {
        for (body in listOf(envelope(item, "length"), envelope("not json"), """{"error":{"message":"secret"}}""", envelope("""{"items":[{"name":123}]}"""))) {
            assertTrue(runCatching { OpenRouterClient.parseResponse(body, categories) }.isFailure)
        }
        val photo = source()
        val response = Response(200, "x".repeat(256_001))
        try {
            assertTrue(runCatching { OpenRouterClient { response }.recognize(photo, "test-key", "openrouter/free", categories) }.isFailure)
            assertTrue(response.closed)
        } finally { photo.delete() }
    }

    @Test fun filtersUntrustedFieldsAndSupportsMultipleItems() {
        val content = """{"items":[{"name":"杯子","category":"编造分类","unit":"美元","notes":null,"suggestedTags":["已有标签","已有标签"],"price":100},{"name":null},{"name":"书","category":"书籍文具","unit":"本"}]}"""
        val parsed = OpenRouterClient.parseResponse(envelope(content), categories)
        assertEquals(2, parsed.size)
        assertEquals("未分类", parsed[0].category)
        assertNull(parsed[0].unit)
        assertEquals(listOf("已有标签"), parsed[0].suggestedTags)
        assertEquals("书", parsed[1].name)
        assertTrue(OpenRouterClient.parseResponse(envelope("""{"items":[]}"""), categories).isEmpty())
    }

    @Test fun acceptsJsonFenceAndRejectsTooManyItems() {
        assertEquals("纯牛奶", OpenRouterClient.parseResponse(envelope("```json\n$item\n```"), categories).single().name)
        val many = """{"items":[""" + List(9) { """{"name":"物品$it"}""" }.joinToString(",") + "]}"
        assertTrue(runCatching { OpenRouterClient.parseResponse(envelope(many), categories) }.isFailure)
    }

    @Test fun fillsOnlyEligibleFieldsAndMatchesExistingTagIds() {
        val labels = listOf(Label(id = "tag-1", name = "乳制品"))
        val candidate = RecognizedItem("牛奶", "食品", "盒", "250mL", listOf("乳制品", "新标签"))
        val draft = RecognitionDraft(name = "用户名称", notes = "用户备注")
        val filled = draft.fill(candidate, labels, emptySet())
        assertEquals("用户名称", filled.name)
        assertEquals("用户备注", filled.notes)
        assertEquals("食品", filled.category)
        assertEquals("盒", filled.unit)
        assertEquals(listOf("tag-1"), filled.tags)
        val manuallyCleared = RecognitionDraft().fill(candidate, labels, setOf("name", "unit", "tags"))
        assertEquals("", manuallyCleared.name)
        assertEquals("件", manuallyCleared.unit)
        assertTrue(manuallyCleared.tags.isEmpty())
    }

    @Test fun undoPreservesUserChangesIncludingSameValueAndClearedFields() {
        val before = RecognitionDraft()
        val applied = before.fill(RecognizedItem("牛奶", "食品", "盒", "250mL"), emptyList(), emptySet())
        val edited = applied.copy(name = "手动名称", notes = "")
        val undone = edited.undo(before, applied, setOf("name", "notes", "unit"))
        assertEquals("手动名称", undone.name)
        assertEquals("", undone.notes)
        assertEquals("盒", undone.unit)
        assertEquals("未分类", undone.category)
        assertEquals(before, applied.undo(before, applied, emptySet()))
    }
}
