package com.example.core.detector

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.model.FileCategory
import com.example.core.model.FileInspectionResult
import com.example.core.model.SupportedFormatsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object FileSignatureInspector {

    suspend fun inspectUri(
        context: Context,
        uri: Uri,
        computeHash: Boolean = false,
    ): FileInspectionResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = "unknown_file"
        var sizeBytes = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        if (fileName == "unknown_file") {
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown_file"
        }

        val header = ByteArray(4096)
        var bytesRead = 0
        val sha256 = if (computeHash) {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16384)
                var total = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    if (bytesRead < header.size) {
                        val copyLen = minOf(r, header.size - bytesRead)
                        System.arraycopy(buf, 0, header, bytesRead, copyLen)
                        bytesRead += copyLen
                    }
                    digest.update(buf, 0, r)
                    total += r
                }
                if (sizeBytes < 0) sizeBytes = total
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } else {
            resolver.openInputStream(uri)?.use { input ->
                bytesRead = readUpTo(input, header)
            }
            if (sizeBytes < 0) {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length >= 0) sizeBytes = afd.length
                }
            }
            ""
        }

        val slice = header.copyOf(bytesRead)
        val declaredExt = fileName.substringAfterLast('.', "").lowercase()
        val hexPreview = slice.take(16).joinToString(" ") { "%02X".format(it) }

        val detected = detectFromBytes(
            header = slice,
            declaredExt = declaredExt,
            sizeBytes = maxOf(0L, sizeBytes),
            zipProbe = { resolver.openInputStream(uri)?.use { probeZipContainer(it) } },
            ebmlProbe = { probeEbmlDoctype(slice) },
        )

        buildInspectionResult(
            uriString = uri.toString(),
            fileName = fileName,
            declaredExt = declaredExt,
            detectedId = detected.first,
            customNote = detected.second,
            sizeBytes = maxOf(0L, sizeBytes),
            hexPreview = hexPreview,
            sha256 = sha256,
        )
    }

    suspend fun inspectFile(
        file: File,
        computeHash: Boolean = false,
    ): FileInspectionResult = withContext(Dispatchers.IO) {
        val header = ByteArray(4096)
        var bytesRead = 0
        FileInputStream(file).use { input ->
            bytesRead = readUpTo(input, header)
        }
        val sha256 = if (computeHash) {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(16384)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    digest.update(buf, 0, r)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } else ""

        val slice = header.copyOf(bytesRead)
        val declaredExt = file.extension.lowercase()
        val hexPreview = slice.take(16).joinToString(" ") { "%02X".format(it) }

        val detected = detectFromBytes(
            header = slice,
            declaredExt = declaredExt,
            sizeBytes = file.length(),
            zipProbe = { FileInputStream(file).use { probeZipContainer(it) } },
            ebmlProbe = { probeEbmlDoctype(slice) },
        )

        buildInspectionResult(
            uriString = file.toURI().toString(),
            fileName = file.name,
            declaredExt = declaredExt,
            detectedId = detected.first,
            customNote = detected.second,
            sizeBytes = file.length(),
            hexPreview = hexPreview,
            sha256 = sha256,
        )
    }

    private fun buildInspectionResult(
        uriString: String,
        fileName: String,
        declaredExt: String,
        detectedId: String,
        customNote: String?,
        sizeBytes: Long,
        hexPreview: String,
        sha256: String,
    ): FileInspectionResult {
        val spec = SupportedFormatsRegistry.findById(detectedId)
        val isMismatch = spec != null &&
            declaredExt.isNotBlank() &&
            declaredExt !in spec.extensions &&
            !(detectedId == "code" && declaredExt in spec.extensions)

        return if (spec != null) {
            FileInspectionResult(
                uriString = uriString,
                fileName = fileName,
                declaredExtension = declaredExt,
                detectedFormatId = spec.id,
                detectedFormatName = spec.displayName,
                detectedExtension = spec.primaryExtension,
                mimeType = spec.mimeTypes.first(),
                category = spec.category,
                sizeBytes = sizeBytes,
                isSupported = spec.isViewable,
                hasExtensionMismatch = isMismatch,
                magicHexPreview = hexPreview,
                sha256 = sha256,
                supportedOutputs = spec.supportedOutputs,
                statusNote = customNote ?: spec.note,
            )
        } else {
            val extLabel = declaredExt.uppercase().ifBlank { "UNKNOWN" }
            FileInspectionResult(
                uriString = uriString,
                fileName = fileName,
                declaredExtension = declaredExt,
                detectedFormatId = "unknown",
                detectedFormatName = "Unrecognized Binary ($extLabel)",
                detectedExtension = declaredExt,
                mimeType = "application/octet-stream",
                category = FileCategory.UNKNOWN,
                sizeBytes = sizeBytes,
                isSupported = false,
                hasExtensionMismatch = false,
                magicHexPreview = hexPreview,
                sha256 = sha256,
                supportedOutputs = emptyList(),
                statusNote = customNote ?: "This file format is not currently supported by Mono Docs.",
            )
        }
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun detectFromBytes(
        header: ByteArray,
        declaredExt: String,
        sizeBytes: Long,
        zipProbe: () -> String?,
        ebmlProbe: () -> String,
    ): Pair<String, String?> {
        if (header.isEmpty()) {
            val fallback = SupportedFormatsRegistry.findByExtension(declaredExt)?.id ?: "txt"
            return fallback to "Empty file (0 bytes)."
        }

        // PDF: %PDF-
        if (startsWithAscii(header, "%PDF-")) return "pdf" to null

        // PNG
        if (matchesHex(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "png" to null

        // JPEG
        if (matchesHex(header, 0, 0xFF, 0xD8, 0xFF)) return "jpg" to null

        // GIF
        if (startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a")) return "gif" to null

        // BMP
        if (startsWithAscii(header, "BM")) return "bmp" to null

        // ICO
        if (matchesHex(header, 0, 0x00, 0x00, 0x01, 0x00)) return "ico" to null

        // RIFF (WEBP / WAV / AVI)
        if (header.size >= 12 && startsWithAscii(header, "RIFF")) {
            val tag = String(header, 8, 4, Charsets.US_ASCII)
            when (tag) {
                "WEBP" -> return "webp" to null
                "WAVE" -> return "wav" to null
                "AVI " -> return "unknown" to "AVI container detected; convert or open with MP4/MKV tools."
            }
        }

        // 7Z
        if (matchesHex(header, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) return "7z" to null

        // RAR (RAR4 vs RAR5)
        if (matchesHex(header, 0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)) {
            val isRar5 = header.size >= 8 && header[6] == 0x01.toByte() && header[7] == 0x00.toByte()
            return if (isRar5) {
                "rar" to "RAR5 signature detected. RAR4 archives extract natively; RAR5 requires conversion on desktop."
            } else {
                "rar" to null
            }
        }

        // GZIP
        if (matchesHex(header, 0, 0x1F, 0x8B)) return "gz" to null

        // BZ2
        if (startsWithAscii(header, "BZh")) return "bz2" to null

        // XZ
        if (matchesHex(header, 0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)) return "xz" to null

        // TAR (ustar at offset 257)
        if (header.size >= 263 && String(header, 257, 5, Charsets.US_ASCII) == "ustar") return "tar" to null

        // ZIP & ZIP-based containers (DOCX, XLSX, PPTX, ODT, ODS, ODP, EPUB, CBZ, JAR)
        if (matchesHex(header, 0, 0x50, 0x4B, 0x03, 0x04) || matchesHex(header, 0, 0x50, 0x4B, 0x05, 0x06)) {
            val probed = runCatching { zipProbe() }.getOrNull()
            if (probed != null) return probed to null
            return when (declaredExt) {
                "cbz" -> "cbz"
                "jar" -> "jar"
                "epub" -> "epub"
                else -> "zip"
            } to null
        }

        // FLAC
        if (startsWithAscii(header, "fLaC")) return "flac" to null

        // OGG / OPUS
        if (startsWithAscii(header, "OggS")) {
            val snippet = String(header, 0, minOf(header.size, 128), Charsets.ISO_8859_1)
            return if (snippet.contains("OpusHead")) "opus" to null else "ogg" to null
        }

        // MP3 (ID3 or MPEG frame sync)
        if (startsWithAscii(header, "ID3") ||
            (header.size >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        ) {
            if (declaredExt == "aac") return "aac" to null
            return "mp3" to null
        }

        // EBML (MKV / WEBM)
        if (matchesHex(header, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return ebmlProbe() to null
        }

        // ISO Base Media File Format (ftyp at offset 4)
        if (header.size >= 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
            val brand = String(header, 8, 4, Charsets.US_ASCII).lowercase()
            return when {
                brand.startsWith("heic") || brand.startsWith("heix") || brand == "mif1" -> "heic" to null
                brand.startsWith("avif") || brand.startsWith("avis") -> "avif" to null
                brand.startsWith("m4a") -> "m4a" to null
                brand.startsWith("qt") -> "mov" to null
                brand.startsWith("3gp") -> "3gp" to null
                else -> if (declaredExt == "m4a") "m4a" to null else "mp4" to null
            }
        }

        // GLB (Binary glTF)
        if (startsWithAscii(header, "glTF")) return "glb" to null

        // Fonts: TTF / OTF
        if (matchesHex(header, 0, 0x00, 0x01, 0x00, 0x00, 0x00)) return "ttf" to null
        if (startsWithAscii(header, "OTTO")) return "otf" to null

        // MOBI / PalmDOC (BOOKMOBI or TEXtREAd at offset 60)
        if (header.size >= 68) {
            val mobiTag = String(header, 60, 8, Charsets.US_ASCII)
            if (mobiTag == "BOOKMOBI" || mobiTag == "TEXtREAd") return "mobi" to null
        }

        // Binary STL check (80 byte header + 4 byte triangle count -> expected size == 84 + count * 50)
        if (sizeBytes >= 84 && header.size >= 84) {
            val triCount = ((header[80].toInt() and 0xFF)) or
                ((header[81].toInt() and 0xFF) shl 8) or
                ((header[82].toInt() and 0xFF) shl 16) or
                ((header[83].toInt() and 0xFF) shl 24)
            if (triCount > 0 && 84L + triCount.toLong() * 50L == sizeBytes) {
                return "stl" to null
            }
        }

        // Text-based signature detection
        if (isMostlyText(header)) {
            val text = String(header, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            val lower = text.lowercase()
            return when {
                lower.startsWith("{\\rtf") -> "rtf" to null
                lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg")) -> "svg" to null
                lower.contains("<fictionbook") -> "fb2" to null
                lower.startsWith("<!doctype html") || lower.startsWith("<html") -> "html" to null
                lower.startsWith("<?xml") -> "xml" to null
                lower.startsWith("solid ") && lower.contains("facet") -> "stl" to null
                (text.startsWith("{") || text.startsWith("[")) && declaredExt != "log" -> "json" to null
                declaredExt == "obj" || (text.lines().take(15).any { it.startsWith("v ") } && text.lines().any { it.startsWith("f ") }) -> "obj" to null
                declaredExt == "md" || declaredExt == "markdown" -> "md" to null
                declaredExt == "csv" -> "csv" to null
                declaredExt == "tsv" -> "tsv" to null
                declaredExt == "yaml" || declaredExt == "yml" -> "yaml" to null
                declaredExt == "toml" -> "toml" to null
                declaredExt == "sql" -> "sql" to null
                declaredExt in setOf("js", "ts", "css", "py", "lua", "java", "kt", "kts", "c", "cpp", "h", "hpp", "log", "ini", "cfg", "sh", "gradle", "properties") -> "code" to null
                else -> "txt" to null
            }
        }

        // Fallback to declared extension if recognized
        val byExt = SupportedFormatsRegistry.findByExtension(declaredExt)
        return (byExt?.id ?: "unknown") to null
    }

    private fun probeZipContainer(input: InputStream): String? {
        var hasWord = false
        var hasExcel = false
        var hasPpt = false
        var hasContainerXml = false
        var mimeText: String? = null
        var imageCount = 0
        var hasManifest = false

        ZipInputStream(input).use { zis ->
            var count = 0
            while (count < 80) {
                val entry = zis.nextEntry ?: break
                count++
                val name = entry.name.lowercase()
                when {
                    name == "mimetype" -> {
                        val bytes = ByteArray(128)
                        val r = zis.read(bytes)
                        if (r > 0) mimeText = String(bytes, 0, r, Charsets.UTF_8).trim()
                    }
                    name.startsWith("word/") -> hasWord = true
                    name.startsWith("xl/") -> hasExcel = true
                    name.startsWith("ppt/") -> hasPpt = true
                    name == "meta-inf/container.xml" -> hasContainerXml = true
                    name == "meta-inf/manifest.mf" -> hasManifest = true
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> imageCount++
                }
                zis.closeEntry()
            }
        }

        return when {
            mimeText == "application/epub+zip" || hasContainerXml -> "epub"
            mimeText == "application/vnd.oasis.opendocument.text" || mimeText == "application/vnd.oasis.opendocument.text-template" -> "odt"
            mimeText == "application/vnd.oasis.opendocument.spreadsheet" || mimeText == "application/vnd.oasis.opendocument.spreadsheet-template" -> "ods"
            mimeText == "application/vnd.oasis.opendocument.presentation" || mimeText == "application/vnd.oasis.opendocument.presentation-template" -> "odp"
            hasWord -> "docx"
            hasExcel -> "xlsx"
            hasPpt -> "pptx"
            hasManifest -> "jar"
            imageCount >= 3 -> "cbz"
            else -> "zip"
        }
    }

    private fun probeEbmlDoctype(header: ByteArray): String {
        val ascii = String(header, Charsets.ISO_8859_1).lowercase()
        return if (ascii.contains("webm")) "webm" else "mkv"
    }

    private fun startsWithAscii(bytes: ByteArray, text: String): Boolean {
        if (bytes.size < text.length) return false
        for (i in text.indices) {
            if (bytes[i] != text[i].code.toByte()) return false
        }
        return true
    }

    private fun matchesHex(bytes: ByteArray, offset: Int, vararg expected: Int): Boolean {
        if (bytes.size < offset + expected.size) return false
        for (i in expected.indices) {
            if ((bytes[offset + i].toInt() and 0xFF) != expected[i]) return false
        }
        return true
    }

    private fun isMostlyText(bytes: ByteArray): Boolean {
        val sampleLen = minOf(bytes.size, 512)
        if (sampleLen == 0) return true
        var controlBytes = 0
        for (i in 0 until sampleLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) controlBytes++
        }
        return controlBytes <= sampleLen / 20
    }
}
