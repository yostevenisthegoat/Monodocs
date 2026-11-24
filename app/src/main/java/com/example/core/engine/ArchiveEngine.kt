package com.example.core.engine

import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class ArchiveEntryItem(
    val name: String,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
)

data class ArchiveListingResult(
    val formatLabel: String,
    val entries: List<ArchiveEntryItem>,
    val isPasswordProtected: Boolean,
    val warningNote: String? = null,
)

object ArchiveEngine {

    suspend fun listEntries(
        file: File,
        formatId: String,
        password: String? = null,
    ): ArchiveListingResult = withContext(Dispatchers.IO) {
        when (formatId) {
            "zip", "jar", "cbz" -> listZip(file)
            "7z" -> list7z(file, password)
            "rar" -> listRar(file)
            "tar", "gz", "bz2", "xz" -> listTarOrCompressed(file, formatId)
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
    }

    private fun listZip(file: File): ArchiveListingResult {
        val zf = Zip4jFile(file)
        if (!zf.isValidZipFile) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        val headers = zf.fileHeaders
        val entries = headers.map { h ->
            ArchiveEntryItem(
                name = h.fileName,
                sizeBytes = h.uncompressedSize,
                compressedBytes = h.compressedSize,
                isDirectory = h.isDirectory,
                isEncrypted = h.isEncrypted,
            )
        }
        return ArchiveListingResult(
            formatLabel = "ZIP Archive",
            entries = entries,
            isPasswordProtected = entries.any { it.isEncrypted },
        )
    }

    private fun list7z(file: File, password: String?): ArchiveListingResult {
        return try {
            val builder = SevenZFile.builder().setFile(file)
            if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
            builder.get().use { sz ->
                val list = mutableListOf<ArchiveEntryItem>()
                while (true) {
                    val entry = sz.nextEntry ?: break
                    list += ArchiveEntryItem(
                        name = entry.name,
                        sizeBytes = entry.size,
                        compressedBytes = entry.size,
                        isDirectory = entry.isDirectory,
                        isEncrypted = false,
                    )
                }
                ArchiveListingResult("7-Zip Archive", list, isPasswordProtected = !password.isNullOrEmpty())
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("password") || msg.contains("encrypted")) {
                ArchiveListingResult("7-Zip Archive", emptyList(), isPasswordProtected = true)
            } else {
                throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.", e)
            }
        }
    }

    private fun listRar(file: File): ArchiveListingResult {
        return try {
            Archive(file).use { rar ->
                val list = rar.fileHeaders.map { fh ->
                    ArchiveEntryItem(
                        name = fh.fileName,
                        sizeBytes = fh.fullUnpackSize,
                        compressedBytes = fh.fullPackSize,
                        isDirectory = fh.isDirectory,
                        isEncrypted = fh.isEncrypted,
                    )
                }
                ArchiveListingResult(
                    formatLabel = "RAR Archive",
                    entries = list,
                    isPasswordProtected = rar.isEncrypted,
                )
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("rar5") || msg.contains("unsupported")) {
                ArchiveListingResult(
                    formatLabel = "RAR5 Archive",
                    entries = emptyList(),
                    isPasswordProtected = false,
                    warningNote = "RAR5 format detected. Native RAR4 archives extract directly; RAR5 requires desktop WinRAR/7-Zip conversion.",
                )
            } else {
                throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.", e)
            }
        }
    }

    private fun listTarOrCompressed(file: File, formatId: String): ArchiveListingResult {
        val list = mutableListOf<ArchiveEntryItem>()
        openCompressedStream(file, formatId).use { stream ->
            if (formatId == "tar" || isTarStream(file, formatId)) {
                TarArchiveInputStream(stream).use { tis ->
                    while (true) {
                        val entry = tis.nextEntry ?: break
                        list += ArchiveEntryItem(
                            name = entry.name,
                            sizeBytes = entry.size,
                            compressedBytes = entry.size,
                            isDirectory = entry.isDirectory,
                            isEncrypted = false,
                        )
                    }
                }
            } else {
                val base = file.nameWithoutExtension.ifBlank { "extracted_payload" }
                list += ArchiveEntryItem(
                    name = base,
                    sizeBytes = file.length(),
                    compressedBytes = file.length(),
                    isDirectory = false,
                    isEncrypted = false,
                )
            }
        }
        return ArchiveListingResult(formatId.uppercase() + " Archive", list, isPasswordProtected = false)
    }

    suspend fun extractEntries(
        file: File,
        formatId: String,
        selectedNames: Set<String>?,
        destDir: File,
        password: String? = null,
        onProgress: (Float) -> Unit = {},
    ): List<File> = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val extracted = mutableListOf<File>()
        when (formatId) {
            "zip", "jar", "cbz" -> {
                val zf = Zip4jFile(file)
                if (zf.isEncrypted && !password.isNullOrEmpty()) {
                    zf.setPassword(password.toCharArray())
                }
                val headers = zf.fileHeaders.filter { h ->
                    !h.isDirectory && (selectedNames == null || h.fileName in selectedNames)
                }
                headers.forEachIndexed { idx, h ->
                    val target = safeResolve(destDir, h.fileName)
                    target.parentFile?.mkdirs()
                    zf.extractFile(h, destDir.absolutePath)
                    if (target.exists()) extracted += target
                    onProgress((idx + 1).toFloat() / headers.size.coerceAtLeast(1))
                }
            }
            "7z" -> {
                val builder = SevenZFile.builder().setFile(file)
                if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
                builder.get().use { sz ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val entry = sz.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (selectedNames != null && entry.name !in selectedNames) continue
                        val target = safeResolve(destDir, entry.name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { fos ->
                            while (true) {
                                val r = sz.read(buf)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                            }
                        }
                        extracted += target
                    }
                    onProgress(1f)
                }
            }
            "rar" -> {
                Archive(file).use { rar ->
                    val headers = rar.fileHeaders.filter { !it.isDirectory && (selectedNames == null || it.fileName in selectedNames) }
                    headers.forEachIndexed { idx, fh ->
                        val target = safeResolve(destDir, fh.fileName)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { fos -> rar.extractFile(fh, fos) }
                        extracted += target
                        onProgress((idx + 1).toFloat() / headers.size.coerceAtLeast(1))
                    }
                }
            }
            "tar", "gz", "bz2", "xz" -> {
                val isTar = formatId == "tar" || isTarStream(file, formatId)
                openCompressedStream(file, formatId).use { stream ->
                    if (isTar) {
                        TarArchiveInputStream(stream).use { tis ->
                            val buf = ByteArray(16384)
                            while (true) {
                                val entry = tis.nextEntry ?: break
                                if (entry.isDirectory) continue
                                if (selectedNames != null && entry.name !in selectedNames) continue
                                val target = safeResolve(destDir, entry.name)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos ->
                                    while (true) {
                                        val r = tis.read(buf)
                                        if (r <= 0) break
                                        fos.write(buf, 0, r)
                                    }
                                }
                                extracted += target
                            }
                        }
                    } else {
                        val target = safeResolve(destDir, file.nameWithoutExtension.ifBlank { "extracted_file" })
                        FileOutputStream(target).use { fos -> stream.copyTo(fos, 16384) }
                        extracted += target
                    }
                    onProgress(1f)
                }
            }
        }
        extracted
    }

    suspend fun createZip(
        files: List<Pair<File, String>>,
        outFile: File,
        password: String? = null,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        if (outFile.exists()) outFile.delete()
        val zf = Zip4jFile(outFile)
        val params = ZipParameters().apply {
            compressionLevel = CompressionLevel.NORMAL
            if (!password.isNullOrEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
        }
        if (!password.isNullOrEmpty()) {
            zf.setPassword(password.toCharArray())
        }
        files.forEachIndexed { idx, (file, entryName) ->
            val p = ZipParameters(params).apply { fileNameInZip = entryName }
            zf.addFile(file, p)
            onProgress((idx + 1).toFloat() / files.size.coerceAtLeast(1))
        }
    }

    private fun openCompressedStream(file: File, formatId: String): InputStream {
        val bis = BufferedInputStream(FileInputStream(file))
        return when (formatId) {
            "gz" -> GzipCompressorInputStream(bis)
            "bz2" -> BZip2CompressorInputStream(bis)
            "xz" -> XZCompressorInputStream(bis)
            else -> bis
        }
    }

    private fun isTarStream(file: File, formatId: String): Boolean {
        if (formatId == "tar") return true
        return runCatching {
            openCompressedStream(file, formatId).use { s ->
                val header = ByteArray(265)
                var total = 0
                while (total < 265) {
                    val r = s.read(header, total, 265 - total)
                    if (r <= 0) break
                    total += r
                }
                total >= 263 && String(header, 257, 5, Charsets.US_ASCII) == "ustar"
            }
        }.getOrDefault(false)
    }

    private fun safeResolve(destDir: File, entryName: String): File {
        val clean = entryName.replace("\\", "/").trimStart('/')
        val resolved = File(destDir, clean)
        val destCanonical = destDir.canonicalPath
        val resolvedCanonical = resolved.canonicalPath
        if (!resolvedCanonical.startsWith(destCanonical + File.separator) && resolvedCanonical != destCanonical) {
            throw SecurityException("Blocked unsafe archive path: $entryName")
        }
        return resolved
    }
}
