package com.example

import com.example.core.detector.FileSignatureInspector
import com.example.core.engine.ArchiveEngine
import com.example.core.engine.Model3DAndFontEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MonoDocsJvmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file inspector detects PDF magic bytes despite wrong extension`() = runTest {
        val disguisedPdf = File(tempFolder.root, "document.xyz")
        disguisedPdf.writeBytes("%PDF-1.7\n%MonoDocsTest".toByteArray(Charsets.US_ASCII))

        val result = FileSignatureInspector.inspectFile(disguisedPdf, computeHash = true)
        assertEquals("pdf", result.detectedFormatId)
        assertEquals("application/pdf", result.mimeType)
        assertTrue(result.isSupported)
        assertTrue(result.hasExtensionMismatch)
        assertEquals(64, result.sha256.length)
    }

    @Test
    fun `file inspector detects DOCX inside misnamed zip container`() = runTest {
        val disguisedDocx = File(tempFolder.root, "mystery.bin")
        ZipOutputStream(disguisedDocx.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write("<w:document/>".toByteArray())
            zos.closeEntry()
        }

        val result = FileSignatureInspector.inspectFile(disguisedDocx)
        assertEquals("docx", result.detectedFormatId)
        assertTrue(result.hasExtensionMismatch)
        assertTrue(result.supportedOutputs.contains("pdf"))
    }

    @Test
    fun `archive create zip with AES password and extract works offline`() = runTest {
        val srcFile = File(tempFolder.root, "notes.txt").apply { writeText("Secret Mono Docs Payload") }
        val zipFile = File(tempFolder.root, "bundle.zip")

        ArchiveEngine.createZip(listOf(srcFile to "notes.txt"), zipFile, password = "mono123")
        val listing = ArchiveEngine.listEntries(zipFile, "zip")
        assertTrue(listing.isPasswordProtected)
        assertEquals(1, listing.entries.size)

        val extractDir = File(tempFolder.root, "out")
        val extracted = ArchiveEngine.extractEntries(zipFile, "zip", null, extractDir, password = "mono123")
        assertEquals(1, extracted.size)
        assertEquals("Secret Mono Docs Payload", extracted[0].readText())
    }

    @Test
    fun `obj and binary stl 3d models parse vertices and triangles`() = runTest {
        val objFile = File(tempFolder.root, "cube_tri.obj").apply {
            writeText(
                """
                v 0.0 0.0 0.0
                v 1.0 0.0 0.0
                v 0.0 1.0 0.0
                v 0.0 0.0 1.0
                f 1 2 3
                f 1 2 4
                """.trimIndent()
            )
        }
        val inspObj = FileSignatureInspector.inspectFile(objFile)
        assertEquals("obj", inspObj.detectedFormatId)
        val meshObj = Model3DAndFontEngine.parse3DModel(objFile, "obj")
        assertEquals(4, meshObj.vertexCount)
        assertEquals(2, meshObj.triangleCount)

        // Binary STL (1 triangle -> 84 + 50 = 134 bytes)
        val stlFile = File(tempFolder.root, "mesh.stl")
        val buf = ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        buf.putInt(1) // 1 triangle
        buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(1f) // normal
        buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f) // v1
        buf.putFloat(1f); buf.putFloat(0f); buf.putFloat(0f) // v2
        buf.putFloat(0f); buf.putFloat(1f); buf.putFloat(0f) // v3
        buf.putShort(0)
        stlFile.writeBytes(buf.array())

        val inspStl = FileSignatureInspector.inspectFile(stlFile)
        assertEquals("stl", inspStl.detectedFormatId)
        val meshStl = Model3DAndFontEngine.parse3DModel(stlFile, "stl")
        assertEquals(3, meshStl.vertexCount)
        assertEquals(1, meshStl.triangleCount)
        assertFalse(meshStl.vertices.isEmpty())
    }
}
