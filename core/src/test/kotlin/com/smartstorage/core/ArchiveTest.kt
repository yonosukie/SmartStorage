package com.smartstorage.core

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.ZipInputStream

class ArchiveTest {
    private val photo = "photo.jpg"
    private fun sample() = InventoryRules.add(Inventory(), Thing("item", "=SUM(A1:A2) & 茶杯", photo = photo),
        Batch("batch", "item", price = 12345), 2, UNPLACED, "add")
    private fun archive(password: String = ""): ByteArray = ByteArrayOutputStream().also { Archive.write(sample(), { byteArrayOf(1, 2, 3) }, it, password.toCharArray()) }.toByteArray()
    @Test fun plainBackupRoundTripIncludesPhotos() {
        val original = sample(); val out = ByteArrayOutputStream(); Archive.write(original, { byteArrayOf(1,2,3) }, out)
        val restored = Archive.read(out.toByteArray().inputStream())
        assertEquals(original, restored.state); assertArrayEquals(byteArrayOf(1,2,3), restored.photos[photo])
    }
    @Test fun pngCutoutSurvivesBackupWithoutReencoding() {
        val s = sample().let { it.copy(items = it.items.map { item -> item.copy(photo = "transparent.png") }) }
        val pixels = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val out = ByteArrayOutputStream(); Archive.write(s, { pixels }, out)
        val restored = Archive.read(out.toByteArray().inputStream())
        assertEquals("transparent.png", restored.state.items.single().photo)
        assertArrayEquals(pixels, restored.photos["transparent.png"])
    }
    @Test fun excelOmitsLegacyUnitColumnsAndValues() {
        val state = sample().let { it.copy(items = it.items.map { item -> item.copy(unit = "旧计量单位") }) }
        val bytes = ByteArrayOutputStream().also { ExcelExport.write(state, it) }.toByteArray()
        val sheets = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name.startsWith("xl/worksheets/")) sheets += zip.readBytes().toString(Charsets.UTF_8)
        } }
        assertFalse(sheets.any { it.contains("旧计量单位") || it.contains(">单位<") })
        assertTrue(sheets.any { it.contains("当前数量") })
        val archive = ByteArrayOutputStream(); Archive.write(state, { byteArrayOf(1) }, archive)
        assertEquals("旧计量单位", Archive.read(archive.toByteArray().inputStream()).state.items.single().unit)
    }
    @Test fun encryptedBackupRequiresCorrectPassword() {
        val bytes = archive("correct")
        assertEquals(1, Archive.read(bytes.inputStream(), "correct".toCharArray()).state.items.size)
        assertThrows(Exception::class.java) { Archive.read(bytes.inputStream(), "wrong".toCharArray()) }
    }
    @Test fun truncatedEncryptedArchiveRejected() {
        val bytes = archive("secret"); assertThrows(Exception::class.java) { Archive.read(bytes.copyOf(bytes.size - 10).inputStream(), "secret".toCharArray()) }
    }
    @Test fun invalidPhotoPathRejected() {
        val s = sample().copy(items = listOf(Thing("item", "坏路径", photo = "../escape.jpg")))
        assertThrows(IllegalArgumentException::class.java) { Archive.write(s, { byteArrayOf() }, ByteArrayOutputStream()) }
    }
    @Test fun invalidReferenceRejected() {
        val s = sample().copy(balances = listOf(Balance(batchId = "missing", quantity = 1)))
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.validate(s) }
    }
    @Test fun futureVersionRejected() {
        assertThrows(IllegalArgumentException::class.java) { InventoryRules.validate(sample().copy(version = 3)) }
    }
    @Test fun excelContainsFourSheetsAndLiteralUserText() {
        val bytes = ByteArrayOutputStream().also { ExcelExport.write(sample(), it) }.toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) } }
        assertEquals(4, entries.keys.count { it.startsWith("xl/worksheets/") })
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("=SUM(A1:A2) &amp; 茶杯"))
        assertFalse(entries.values.any { it.contains("<f>") }); assertTrue(entries.values.any { it.contains("<v>246.90</v>") })
    }
}
