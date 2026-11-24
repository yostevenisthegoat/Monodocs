package com.example.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ImageMetadataInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val exifItems: Map<String, String>,
)

object ImageEngine {

    suspend fun decodeBitmap(file: File, formatId: String, maxDim: Int = 2048): Bitmap = withContext(Dispatchers.IO) {
        if (formatId == "svg") {
            val svg = FileInputStream(file).use { SVG.getFromInputStream(it) }
            val w = svg.documentWidth.takeIf { it > 0 }?.toInt()?.coerceIn(64, maxDim) ?: 1024
            val h = svg.documentHeight.takeIf { it > 0 }?.toInt()?.coerceIn(64, maxDim) ?: 1024
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            svg.setDocumentWidth(w.toFloat())
            svg.setDocumentHeight(h.toFloat())
            svg.renderToCanvas(canvas)
            return@withContext bmp
        }

        if (Build.VERSION.SDK_INT >= 28 && (formatId == "heic" || formatId == "avif")) {
            val src = ImageDecoder.createSource(file)
            return@withContext ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                if (w > maxDim || h > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(w, h)
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")

        applyExifOrientation(file, raw)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    suspend fun inspectMetadata(file: File, formatId: String): ImageMetadataInfo = withContext(Dispatchers.IO) {
        if (formatId == "svg") {
            val svg = FileInputStream(file).use { SVG.getFromInputStream(it) }
            val w = svg.documentWidth.takeIf { it > 0 }?.toInt() ?: 0
            val h = svg.documentHeight.takeIf { it > 0 }?.toInt() ?: 0
            return@withContext ImageMetadataInfo(
                width = w,
                height = h,
                mimeType = "image/svg+xml",
                exifItems = mapOf("Type" to "Scalable Vector Graphics (SVG)"),
            )
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val exifMap = linkedMapOf<String, String>()
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            val tags = listOf(
                "Camera Make" to ExifInterface.TAG_MAKE,
                "Camera Model" to ExifInterface.TAG_MODEL,
                "Date Taken" to ExifInterface.TAG_DATETIME_ORIGINAL,
                "Exposure" to ExifInterface.TAG_EXPOSURE_TIME,
                "Aperture" to ExifInterface.TAG_F_NUMBER,
                "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                "Focal Length" to ExifInterface.TAG_FOCAL_LENGTH,
                "Orientation" to ExifInterface.TAG_ORIENTATION,
                "Software" to ExifInterface.TAG_SOFTWARE,
            )
            tags.forEach { (label, tag) ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { exifMap[label] = it }
            }
        }
        ImageMetadataInfo(
            width = opts.outWidth,
            height = opts.outHeight,
            mimeType = opts.outMimeType ?: "image/*",
            exifItems = exifMap,
        )
    }

    suspend fun convertOrProcessImage(
        inputFile: File,
        sourceFormatId: String,
        targetExtension: String,
        outFile: File,
        quality: Int = 85,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        rotateDegrees: Int = 0,
        cropNormalized: CropRect? = null,
    ) = withContext(Dispatchers.IO) {
        if (targetExtension.lowercase() == "pdf") {
            val bmp = decodeBitmap(inputFile, sourceFormatId, maxDim = 2400)
            val processed = transformBitmap(bmp, targetWidth, targetHeight, rotateDegrees, cropNormalized)
            val tmpPng = File.createTempFile("img2pdf_", ".png", outFile.parentFile)
            try {
                FileOutputStream(tmpPng).use { processed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                PdfEngine.imagesToPdf(listOf(tmpPng), outFile)
            } finally {
                if (processed != bmp) processed.recycle()
                bmp.recycle()
                tmpPng.delete()
            }
            return@withContext
        }

        val bmp = decodeBitmap(inputFile, sourceFormatId, maxDim = 4096)
        val processed = transformBitmap(bmp, targetWidth, targetHeight, rotateDegrees, cropNormalized)
        try {
            when (targetExtension.lowercase()) {
                "bmp" -> writeBmp(processed, outFile)
                "jpg", "jpeg" -> {
                    val flattened = Bitmap.createBitmap(processed.width, processed.height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(flattened)
                    c.drawColor(Color.WHITE)
                    c.drawBitmap(processed, 0f, 0f, null)
                    FileOutputStream(outFile).use { flattened.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), it) }
                    flattened.recycle()
                }
                "webp" -> {
                    val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    FileOutputStream(outFile).use { processed.compress(fmt, quality.coerceIn(10, 100), it) }
                }
                else -> {
                    FileOutputStream(outFile).use { processed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        } finally {
            if (processed != bmp) processed.recycle()
            bmp.recycle()
        }
    }

    data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun transformBitmap(
        source: Bitmap,
        targetWidth: Int?,
        targetHeight: Int?,
        rotateDegrees: Int,
        crop: CropRect?,
    ): Bitmap {
        var current = source
        if (crop != null) {
            val l = (crop.left.coerceIn(0f, 0.95f) * current.width).toInt()
            val t = (crop.top.coerceIn(0f, 0.95f) * current.height).toInt()
            val r = (crop.right.coerceIn(crop.left + 0.05f, 1f) * current.width).toInt()
            val b = (crop.bottom.coerceIn(crop.top + 0.05f, 1f) * current.height).toInt()
            val w = (r - l).coerceAtLeast(1)
            val h = (b - t).coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(current, l, t, w, h)
            if (current != source) current.recycle()
            current = cropped
        }
        if (rotateDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate((rotateDegrees % 360).toFloat()) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
            if (current != source) current.recycle()
            current = rotated
        }
        if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0 &&
            (targetWidth != current.width || targetHeight != current.height)
        ) {
            val scaled = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
            if (current != source) current.recycle()
            current = scaled
        }
        return current
    }

    private fun writeBmp(bitmap: Bitmap, outFile: File) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rowBytes = (w * 3 + 3) and 3.inv()
        val imageSize = rowBytes * h
        val fileSize = 54 + imageSize
        val buf = java.nio.ByteBuffer.allocate(fileSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put('B'.code.toByte())
        buf.put('M'.code.toByte())
        buf.putInt(fileSize)
        buf.putInt(0)
        buf.putInt(54)
        buf.putInt(40)
        buf.putInt(w)
        buf.putInt(h)
        buf.putShort(1)
        buf.putShort(24)
        buf.putInt(0)
        buf.putInt(imageSize)
        buf.putInt(2835)
        buf.putInt(2835)
        buf.putInt(0)
        buf.putInt(0)

        val pad = ByteArray(rowBytes - w * 3)
        for (y in h - 1 downTo 0) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val c = pixels[rowOffset + x]
                buf.put((c and 0xFF).toByte())         // B
                buf.put(((c shr 8) and 0xFF).toByte()) // G
                buf.put(((c shr 16) and 0xFF).toByte())// R
            }
            if (pad.isNotEmpty()) buf.put(pad)
        }
        FileOutputStream(outFile).use { it.write(buf.array()) }
    }
}
