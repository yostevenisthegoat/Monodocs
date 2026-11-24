package com.example.core.engine

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import com.example.core.model.FileInspectionResult
import com.example.data.local.ConversionQuality
import com.example.data.local.FileStorageHelper
import com.example.data.local.MonoSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ConversionOutput(
    val outputFile: File,
    val outputFileName: String,
    val outputMimeType: String,
    val extraFiles: List<File> = emptyList(),
)

@UnstableApi
object UniversalConversionEngine {

    suspend fun convert(
        context: Context,
        sourceFile: File,
        inspection: FileInspectionResult,
        targetExtension: String,
        settings: MonoSettingsState,
        password: String? = null,
        onProgress: (Float) -> Unit = {},
    ): ConversionOutput = withContext(Dispatchers.IO) {
        val srcId = inspection.detectedFormatId.lowercase()
        val tgt = targetExtension.lowercase().trimStart('.')
        if (!FileStorageHelper.hasEnoughSpace(context, sourceFile.length() * 2)) {
            throw IllegalStateException("Not enough storage space to complete this operation.")
        }
        val baseName = inspection.fileName.substringBeforeLast('.').ifBlank { "converted" }
        val workDir = FileStorageHelper.workDir(context)
        val outFile = File(workDir, "${baseName}_${System.currentTimeMillis()}.$tgt")

        try {
            when {
                // PDF conversions
                srcId == "pdf" -> convertFromPdf(sourceFile, baseName, tgt, outFile, workDir, settings, password, onProgress)

                // Word / ODT / RTF / HTML / MD / Text / Code conversions
                srcId in setOf("docx", "odt", "rtf", "txt", "md", "html", "xml", "json", "yaml", "toml", "sql", "code") ->
                    convertTextDocument(sourceFile, srcId, baseName, tgt, outFile, onProgress)

                // Spreadsheets
                srcId in setOf("xlsx", "ods", "csv", "tsv") ->
                    convertSpreadsheet(sourceFile, srcId, baseName, tgt, outFile, onProgress)

                // Presentations
                srcId in setOf("pptx", "odp") ->
                    convertPresentation(sourceFile, srcId, baseName, tgt, outFile, onProgress)

                // E-Books
                srcId in setOf("epub", "fb2", "mobi", "cbz") ->
                    convertEbook(sourceFile, srcId, baseName, tgt, outFile, workDir, onProgress)

                // Images
                srcId in setOf("jpg", "png", "webp", "gif", "bmp", "ico", "svg", "heic", "avif") -> {
                    onProgress(0.25f)
                    val quality = when (settings.conversionQuality) {
                        ConversionQuality.HIGH -> maxOf(settings.imageCompressionPercent, 90)
                        ConversionQuality.BALANCED -> settings.imageCompressionPercent
                        ConversionQuality.COMPACT -> minOf(settings.imageCompressionPercent, 65)
                    }
                    ImageEngine.convertOrProcessImage(
                        inputFile = sourceFile,
                        sourceFormatId = srcId,
                        targetExtension = tgt,
                        outFile = outFile,
                        quality = quality,
                    )
                    onProgress(1f)
                }

                // Audio
                srcId in setOf("mp3", "wav", "flac", "aac", "ogg", "opus", "m4a") -> when (tgt) {
                    "wav" -> MediaEngine.decodeAudioToWav(sourceFile, outFile, onProgress)
                    "m4a" -> MediaEngine.runMedia3Transform(context, sourceFile, outFile, removeVideo = true, onProgress = onProgress)
                    else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
                }

                // Video
                srcId in setOf("mp4", "mkv", "mov", "webm", "3gp") -> when (tgt) {
                    "mp4" -> MediaEngine.runMedia3Transform(
                        context = context,
                        inputFile = sourceFile,
                        outputFile = outFile,
                        targetHeight = settings.videoQuality.maxHeight,
                        onProgress = onProgress,
                    )
                    "m4a" -> MediaEngine.runMedia3Transform(
                        context = context,
                        inputFile = sourceFile,
                        outputFile = outFile,
                        removeVideo = true,
                        onProgress = onProgress,
                    )
                    "wav" -> MediaEngine.decodeAudioToWav(sourceFile, outFile, onProgress)
                    "gif" -> MediaEngine.videoToAnimatedGif(sourceFile, outFile, onProgress = onProgress)
                    else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
                }

                // Archives -> ZIP
                srcId in setOf("rar", "7z", "tar", "gz", "bz2", "xz", "jar") && tgt == "zip" -> {
                    val tempExtract = File(workDir, "repack_${System.currentTimeMillis()}").apply { mkdirs() }
                    try {
                        val extracted = ArchiveEngine.extractEntries(sourceFile, srcId, null, tempExtract, password) { p ->
                            onProgress(p * 0.5f)
                        }
                        if (extracted.isEmpty()) throw IllegalStateException("Archive has no extractable files.")
                        val pairs = extracted.map { f ->
                            val rel = f.relativeTo(tempExtract).path.replace(File.separatorChar, '/')
                            f to rel
                        }
                        ArchiveEngine.createZip(pairs, outFile) { p -> onProgress(0.5f + p * 0.5f) }
                    } finally {
                        tempExtract.deleteRecursively()
                    }
                }

                // 3D Models -> OBJ
                srcId in setOf("stl", "glb") && tgt == "obj" -> {
                    onProgress(0.3f)
                    val mesh = Model3DAndFontEngine.parse3DModel(sourceFile, srcId)
                    Model3DAndFontEngine.exportMeshToObj(mesh, outFile)
                    onProgress(1f)
                }

                else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                throw IllegalStateException("Conversion failed. Your original file has not been modified.")
            }

            ConversionOutput(
                outputFile = outFile,
                outputFileName = "$baseName.$tgt",
                outputMimeType = mimeForExtension(tgt),
            )
        } catch (e: Exception) {
            outFile.delete()
            if (e.message?.contains("Mono Docs") == true || e.message?.contains("Not enough") == true) throw e
            throw IllegalStateException("Conversion failed. Your original file has not been modified.", e)
        }
    }

    private suspend fun convertFromPdf(
        sourceFile: File,
        baseName: String,
        tgt: String,
        outFile: File,
        workDir: File,
        settings: MonoSettingsState,
        password: String?,
        onProgress: (Float) -> Unit,
    ) {
        when (tgt) {
            "txt" -> {
                onProgress(0.2f)
                val pages = PdfEngine.extractTextPerPage(sourceFile, password)
                outFile.writeText(pages.mapIndexed { i, t -> "--- Page ${i + 1} ---\n$t" }.joinToString("\n\n"))
                onProgress(1f)
            }
            "docx" -> {
                onProgress(0.2f)
                val pages = PdfEngine.extractTextPerPage(sourceFile, password)
                val paras = pages.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
                onProgress(0.7f)
                OfficeAndEbookEngine.writeDocx(paras, baseName, outFile)
                onProgress(1f)
            }
            "html" -> {
                onProgress(0.2f)
                val pages = PdfEngine.extractTextPerPage(sourceFile, password)
                val body = pages.mapIndexed { i, t ->
                    "<h3>Page ${i + 1}</h3>" + t.lines().joinToString("") { "<p>${OfficeAndEbookEngine.escapeXml(it)}</p>" }
                }.joinToString("\n")
                outFile.writeText("<!DOCTYPE html><html><head><meta charset='utf-8'><title>${OfficeAndEbookEngine.escapeXml(baseName)}</title></head><body>$body</body></html>")
                onProgress(1f)
            }
            "png", "jpg" -> {
                val unlocked = PdfEngine.ensureUnlockedPdfFile(sourceFile, password, workDir)
                val bmp = PdfEngine.renderPageBitmap(unlocked, 0, targetWidthPx = 1600)
                try {
                    val fmt = if (tgt == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    outFile.outputStream().use { bmp.compress(fmt, settings.imageCompressionPercent, it) }
                } finally {
                    bmp.recycle()
                }
                onProgress(1f)
            }
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
    }

    private suspend fun convertTextDocument(
        sourceFile: File,
        srcId: String,
        baseName: String,
        tgt: String,
        outFile: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0.2f)
        val parsed = when (srcId) {
            "docx" -> OfficeAndEbookEngine.parseDocx(sourceFile)
            "odt" -> OfficeAndEbookEngine.parseOdt(sourceFile)
            "rtf" -> OfficeAndEbookEngine.parseRtf(sourceFile)
            "html" -> OfficeAndEbookEngine.parseHtmlToBlocks(sourceFile.readText(), baseName)
            "md" -> OfficeAndEbookEngine.parseMarkdownToBlocks(sourceFile.readText(), baseName)
            else -> {
                val lines = sourceFile.readLines()
                ParsedDocument(baseName, blocks = lines.map { DocumentBlock(it) })
            }
        }
        onProgress(0.55f)
        val paras = parsed.blocks.map { it.text }
        when (tgt) {
            "pdf" -> PdfEngine.textToPdf(parsed.title, paras, outFile) { p -> onProgress(0.55f + p * 0.45f) }
            "docx" -> {
                OfficeAndEbookEngine.writeDocx(paras, parsed.title, outFile)
                onProgress(1f)
            }
            "txt" -> {
                outFile.writeText(paras.joinToString("\n\n"))
                onProgress(1f)
            }
            "md" -> {
                val md = parsed.blocks.joinToString("\n\n") { b ->
                    if (b.isHeading) "${"#".repeat(b.headingLevel.coerceIn(1, 3))} ${b.text}" else b.text
                }
                outFile.writeText(md)
                onProgress(1f)
            }
            "html" -> {
                val body = parsed.blocks.joinToString("\n") { b ->
                    if (b.isHeading) {
                        val h = "h${b.headingLevel.coerceIn(1, 3)}"
                        "<$h>${OfficeAndEbookEngine.escapeXml(b.text)}</$h>"
                    } else "<p>${OfficeAndEbookEngine.escapeXml(b.text)}</p>"
                }
                outFile.writeText("<!DOCTYPE html><html><head><meta charset='utf-8'><title>${OfficeAndEbookEngine.escapeXml(parsed.title)}</title></head><body>$body</body></html>")
                onProgress(1f)
            }
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
    }

    private suspend fun convertSpreadsheet(
        sourceFile: File,
        srcId: String,
        baseName: String,
        tgt: String,
        outFile: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0.25f)
        val sheets = OfficeAndEbookEngine.parseSpreadsheet(sourceFile, srcId)
        val rows = sheets.firstOrNull()?.rows.orEmpty()
        onProgress(0.6f)
        when (tgt) {
            "csv" -> OfficeAndEbookEngine.writeCsv(rows, ',', outFile)
            "tsv" -> OfficeAndEbookEngine.writeCsv(rows, '\t', outFile)
            "xlsx" -> OfficeAndEbookEngine.writeXlsx(sheets, outFile)
            "pdf" -> {
                val paras = sheets.flatMap { sheet ->
                    listOf("=== ${sheet.name} ===") + sheet.rows.map { row -> row.joinToString("  |  ") }
                }
                PdfEngine.textToPdf(baseName, paras, outFile)
            }
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
        onProgress(1f)
    }

    private suspend fun convertPresentation(
        sourceFile: File,
        srcId: String,
        baseName: String,
        tgt: String,
        outFile: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0.3f)
        val slides = OfficeAndEbookEngine.parsePresentation(sourceFile, srcId)
        val lines = slides.flatMap { slide ->
            buildList {
                add("Slide ${slide.number}: ${slide.title}")
                slide.bodyLines.forEach { add("• $it") }
                if (slide.notes.isNotBlank()) add("Notes: ${slide.notes}")
                add("")
            }
        }
        onProgress(0.65f)
        when (tgt) {
            "pdf" -> PdfEngine.textToPdf(baseName, lines, outFile)
            "txt" -> outFile.writeText(lines.joinToString("\n"))
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
        onProgress(1f)
    }

    private suspend fun convertEbook(
        sourceFile: File,
        srcId: String,
        baseName: String,
        tgt: String,
        outFile: File,
        workDir: File,
        onProgress: (Float) -> Unit,
    ) {
        if (srcId == "cbz") {
            val tempDir = File(workDir, "cbz_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val extracted = ArchiveEngine.extractEntries(sourceFile, "zip", null, tempDir)
                val images = extracted.filter {
                    it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
                }.sortedBy { it.name }
                when (tgt) {
                    "pdf" -> PdfEngine.imagesToPdf(images, outFile, onProgress)
                    "zip" -> ArchiveEngine.createZip(images.map { it to it.name }, outFile, onProgress = onProgress)
                    else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
                }
            } finally {
                tempDir.deleteRecursively()
            }
            return
        }

        onProgress(0.25f)
        val ebook = OfficeAndEbookEngine.parseEbook(sourceFile, srcId)
        onProgress(0.6f)
        when (tgt) {
            "epub" -> OfficeAndEbookEngine.writeEpub(ebook, outFile)
            "txt" -> {
                val txt = ebook.chapters.joinToString("\n\n") { ch ->
                    "=== ${ch.title} ===\n" + ch.paragraphs.joinToString("\n\n")
                }
                outFile.writeText(txt)
            }
            "pdf" -> {
                val paras = ebook.chapters.flatMap { ch -> listOf(ch.title) + ch.paragraphs + "" }
                PdfEngine.textToPdf(ebook.title, paras, outFile) { p -> onProgress(0.6f + p * 0.4f) }
            }
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
        onProgress(1f)
    }

    fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        "txt", "md", "obj" -> "text/plain"
        "html" -> "text/html"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        "epub" -> "application/epub+zip"
        else -> "application/octet-stream"
    }
}
