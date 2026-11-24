package com.example.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PdfMetadataInfo(
    val pageCount: Int,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val producer: String,
    val version: String,
    val isEncrypted: Boolean,
    val requiresPassword: Boolean,
)

object PdfEngine {

    suspend fun inspectPdf(file: File, password: String? = null): PdfMetadataInfo = withContext(Dispatchers.IO) {
        try {
            val doc = if (password.isNullOrEmpty()) {
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
            } else {
                PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
            }
            doc.use { pd ->
                val info = pd.documentInformation
                PdfMetadataInfo(
                    pageCount = pd.numberOfPages,
                    title = info?.title.orEmpty(),
                    author = info?.author.orEmpty(),
                    subject = info?.subject.orEmpty(),
                    creator = info?.creator.orEmpty(),
                    producer = info?.producer.orEmpty(),
                    version = pd.version.toString(),
                    isEncrypted = pd.isEncrypted,
                    requiresPassword = false,
                )
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("password") || msg.contains("decrypt") || msg.contains("encrypted")) {
                PdfMetadataInfo(0, "", "", "", "", "", "", isEncrypted = true, requiresPassword = true)
            } else {
                throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.", e)
            }
        }
    }

    suspend fun ensureUnlockedPdfFile(file: File, password: String?, workDir: File): File = withContext(Dispatchers.IO) {
        if (password.isNullOrEmpty()) {
            // Verify if it opens without password
            val meta = inspectPdf(file, null)
            if (!meta.requiresPassword) return@withContext file
            throw IllegalStateException("PDF is password-protected.")
        }
        val out = File(workDir, "unlocked_${System.currentTimeMillis()}.pdf")
        PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        out
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080,
        rotationDegrees: Int = 0,
    ): Bitmap = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val safeIndex = pageIndex.coerceIn(0, maxOf(0, renderer.pageCount - 1))
                renderer.openPage(safeIndex).use { page ->
                    val scale = (targetWidthPx.toFloat() / page.width.coerceAtLeast(1)).coerceIn(0.5f, 3.0f)
                    val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                    val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (rotationDegrees % 360 != 0) {
                        val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        if (rotated != bitmap) bitmap.recycle()
                        rotated
                    } else {
                        bitmap
                    }
                }
            }
        }
    }

    suspend fun extractTextPerPage(file: File, password: String? = null): List<String> = withContext(Dispatchers.IO) {
        val doc = if (password.isNullOrEmpty()) {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } else {
            PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
        }
        doc.use { pd ->
            val stripper = PDFTextStripper()
            val pages = mutableListOf<String>()
            for (p in 1..pd.numberOfPages) {
                stripper.startPage = p
                stripper.endPage = p
                pages += stripper.getText(pd).trim()
            }
            pages
        }
    }

    suspend fun searchPages(file: File, query: String, password: String? = null): List<Int> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pages = extractTextPerPage(file, password)
        pages.mapIndexedNotNull { index, text ->
            if (text.contains(query, ignoreCase = true)) index else null
        }
    }

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outFile: File,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        if (inputFiles.isEmpty()) throw IllegalArgumentException("No PDF files selected.")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath
        inputFiles.forEachIndexed { idx, f ->
            merger.addSource(f)
            onProgress((idx + 1).toFloat() / (inputFiles.size + 1))
        }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        onProgress(1f)
    }

    suspend fun extractOrSplitPdf(
        inputFile: File,
        pageIndicesZeroBased: List<Int>,
        outFile: File,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        val src = if (password.isNullOrEmpty()) {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly())
        } else {
            PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly())
        }
        src.use { sourceDoc ->
            PDDocument().use { targetDoc ->
                pageIndicesZeroBased
                    .filter { it in 0 until sourceDoc.numberOfPages }
                    .forEach { idx ->
                        val page = sourceDoc.getPage(idx)
                        targetDoc.importPage(page)
                    }
                if (targetDoc.numberOfPages == 0) {
                    throw IllegalArgumentException("No valid page numbers specified.")
                }
                targetDoc.save(outFile)
            }
        }
    }

    suspend fun rotatePdfPages(
        inputFile: File,
        deltaDegrees: Int,
        pageIndicesZeroBased: Set<Int>?,
        outFile: File,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        val src = if (password.isNullOrEmpty()) {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly())
        } else {
            PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly())
        }
        src.use { doc ->
            doc.isAllSecurityToBeRemoved = true
            for (i in 0 until doc.numberOfPages) {
                if (pageIndicesZeroBased == null || i in pageIndicesZeroBased) {
                    val page = doc.getPage(i)
                    page.rotation = ((page.rotation + deltaDegrees) % 360 + 360) % 360
                }
            }
            doc.save(outFile)
        }
    }

    suspend fun compressPdf(
        inputFile: File,
        jpegQuality: Int,
        scaleWidth: Int,
        workDir: File,
        outFile: File,
        password: String? = null,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val unlocked = ensureUnlockedPdfFile(inputFile, password, workDir)
        ParcelFileDescriptor.open(unlocked, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { outDoc ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        renderer.openPage(i).use { page ->
                            val ratio = (scaleWidth.toFloat() / page.width.coerceAtLeast(1)).coerceIn(0.4f, 1.5f)
                            val w = (page.width * ratio).toInt().coerceAtLeast(1)
                            val h = (page.height * ratio).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pdPage = PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            outDoc.addPage(pdPage)
                            val pdImage = JPEGFactory.createFromImage(outDoc, bmp, (jpegQuality / 100f).coerceIn(0.25f, 0.95f))
                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                            }
                            bmp.recycle()
                        }
                        onProgress((i + 1).toFloat() / total.coerceAtLeast(1))
                    }
                    outDoc.save(outFile)
                }
            }
        }
    }

    suspend fun textToPdf(
        title: String,
        paragraphs: List<String>,
        outFile: File,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44
        val contentWidth = pageWidth - margin * 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 34)
            textSize = 11.5f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 9f
        }

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin.toFloat()

        if (title.isNotBlank()) {
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth).build()
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleLayout.height + 14f
        }

        val safeParagraphs = if (paragraphs.isEmpty()) listOf(" ") else paragraphs
        safeParagraphs.forEachIndexed { idx, para ->
            val clean = para.ifEmpty { " " }
            val layout = StaticLayout.Builder.obtain(clean, 0, clean.length, bodyPaint, contentWidth).build()
            if (y + layout.height > pageHeight - margin - 24) {
                canvas.drawText("Page $pageNum", margin.toFloat(), (pageHeight - 20).toFloat(), footerPaint)
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin.toFloat()
            }
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 8f
            onProgress((idx + 1).toFloat() / safeParagraphs.size)
        }
        canvas.drawText("Page $pageNum", margin.toFloat(), (pageHeight - 20).toFloat(), footerPaint)
        pdf.finishPage(page)

        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f)
    }

    suspend fun imagesToPdf(
        imageFiles: List<File>,
        outFile: File,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        imageFiles.forEachIndexed { idx, file ->
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val pageW = 595
            val pageH = 842
            val margin = 24f
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val availW = pageW - margin * 2
            val availH = pageH - margin * 2
            val scale = minOf(availW / bmp.width, availH / bmp.height)
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val left = (pageW - drawW) / 2f
            val top = (pageH - drawH) / 2f
            val dest = android.graphics.RectF(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(bmp, null, dest, null)
            bmp.recycle()
            pdf.finishPage(page)
            onProgress((idx + 1).toFloat() / imageFiles.size)
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f)
    }

    suspend fun pdfToImages(
        file: File,
        format: Bitmap.CompressFormat,
        extension: String,
        quality: Int,
        outDir: File,
        baseName: String,
        password: String? = null,
        workDir: File,
        onProgress: (Float) -> Unit = {},
    ): List<File> = withContext(Dispatchers.IO) {
        val unlocked = ensureUnlockedPdfFile(file, password, workDir)
        val outputs = mutableListOf<File>()
        ParcelFileDescriptor.open(unlocked, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                for (i in 0 until total) {
                    val bmp = renderPageBitmap(unlocked, i, targetWidthPx = 1440)
                    val out = File(outDir, "${baseName}_page_${i + 1}.$extension")
                    FileOutputStream(out).use { fos ->
                        bmp.compress(format, quality.coerceIn(30, 100), fos)
                    }
                    bmp.recycle()
                    outputs += out
                    onProgress((i + 1).toFloat() / total.coerceAtLeast(1))
                }
            }
        }
        outputs
    }

    fun parsePageSelection(input: String, maxPages: Int): List<Int> {
        if (input.isBlank()) return (0 until maxPages).toList()
        val result = linkedSetOf<Int>()
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains("-")) {
                val parts = token.split("-")
                val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null) {
                    val s = minOf(start, end).coerceAtLeast(1)
                    val e = maxOf(start, end).coerceAtMost(maxPages)
                    for (p in s..e) result += (p - 1)
                }
            } else {
                token.toIntOrNull()?.let { p ->
                    if (p in 1..maxPages) result += (p - 1)
                }
            }
        }
        return result.toList()
    }
}
