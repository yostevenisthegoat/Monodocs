package com.example.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MediaMetadataInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Int,
    val title: String,
    val artist: String,
    val album: String,
    val mimeType: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
)

@UnstableApi
object MediaEngine {

    suspend fun inspectMedia(file: File): MediaMetadataInfo = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            val hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" || width > 0
            val hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" || !hasVideo
            MediaMetadataInfo(duration, width, height, bitrate, title, artist, album, mime, hasVideo, hasAudio)
        } finally {
            mmr.release()
        }
    }

    suspend fun extractThumbnail(file: File, timeMs: Long): Bitmap = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            mmr.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.frameAtTime
                ?: throw IllegalStateException("Could not extract video frame.")
        } finally {
            mmr.release()
        }
    }

    suspend fun runMedia3Transform(
        context: Context,
        inputFile: File,
        outputFile: File,
        removeVideo: Boolean = false,
        removeAudio: Boolean = false,
        startMs: Long? = null,
        endMs: Long? = null,
        targetHeight: Int = 0,
        onProgress: (Float) -> Unit = {},
    ): Unit = withContext(Dispatchers.Main) {
        if (outputFile.exists()) outputFile.delete()
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.fromFile(inputFile))
        if (startMs != null && endMs != null && endMs > startMs) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
        }
        val effects = mutableListOf<Effect>()
        if (targetHeight > 0 && !removeVideo) {
            effects += Presentation.createForHeight(targetHeight)
        }
        val edited = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveVideo(removeVideo)
            .setRemoveAudio(removeAudio)
            .apply {
                if (effects.isNotEmpty()) {
                    setEffects(androidx.media3.transformer.Effects(emptyList(), effects))
                }
            }
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(1f)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Conversion failed. Your original file has not been modified.", exportException)
                            )
                        }
                    }
                })
                .build()

            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val poller = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    handler.postDelayed(this, 250L)
                }
            }

            transformer.start(edited, outputFile.absolutePath)
            handler.post(poller)

            cont.invokeOnCancellation {
                handler.removeCallbacks(poller)
                transformer.cancel()
            }
        }
    }

    suspend fun decodeAudioToWav(
        inputFile: File,
        outFile: File,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) {
                throw IllegalStateException("No audio track found in file.")
            }
            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                // Placeholder 44-byte WAV header
                bos.write(ByteArray(44))
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var pcmBytes = 0L

                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                                if (durationUs > 0) {
                                    onProgress((pts.toFloat() / durationUs).coerceIn(0f, 0.95f))
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)!!
                            if (info.size > 0) {
                                val chunk = ByteArray(info.size)
                                outBuf.position(info.offset)
                                outBuf.get(chunk)
                                bos.write(chunk)
                                pcmBytes += chunk.size
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
                bos.flush()
                writeWavHeader(outFile, pcmBytes, sampleRate, channelCount)
                onProgress(1f)
            }
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun writeWavHeader(file: File, pcmBytes: Long, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + pcmBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.toInt())
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }

    suspend fun videoToAnimatedGif(
        inputFile: File,
        outFile: File,
        startMs: Long = 0L,
        endMs: Long = 5000L,
        fps: Int = 8,
        maxWidth: Int = 360,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            mmr.setDataSource(inputFile.absolutePath)
            val totalDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
            val safeStart = startMs.coerceIn(0L, totalDuration)
            val safeEnd = endMs.coerceIn(safeStart + 250L, minOf(totalDuration, safeStart + 10_000L))
            val stepMs = (1000L / fps.coerceIn(4, 15))
            val count = ((safeEnd - safeStart) / stepMs).toInt().coerceIn(2, 60)

            for (i in 0 until count) {
                val tMs = safeStart + i * stepMs
                val raw = mmr.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) {
                    val scale = (maxWidth.toFloat() / raw.width.coerceAtLeast(1)).coerceAtMost(1f)
                    val w = (raw.width * scale).toInt().coerceAtLeast(16)
                    val h = (raw.height * scale).toInt().coerceAtLeast(16)
                    val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                    if (scaled != raw) raw.recycle()
                    frames += scaled
                }
                onProgress(0.5f * (i + 1) / count)
            }
            if (frames.isEmpty()) throw IllegalStateException("Could not extract frames for GIF.")

            FileOutputStream(outFile).use { fos ->
                Gif89aEncoder.encode(frames, delayCs = (100 / fps.coerceIn(4, 15)), output = fos) { p ->
                    onProgress(0.5f + 0.5f * p)
                }
            }
        } finally {
            frames.forEach { it.recycle() }
            mmr.release()
        }
    }
}

/**
 * Pure Kotlin Animated GIF89a Encoder with 6x6x6 RGB uniform palette + LZW compression.
 */
private object Gif89aEncoder {

    fun encode(
        frames: List<Bitmap>,
        delayCs: Int,
        output: FileOutputStream,
        onProgress: (Float) -> Unit,
    ) {
        val width = frames.first().width
        val height = frames.first().height

        // Build 256-color palette (216 web-safe RGB cube + 40 grayscale levels)
        val palette = ByteArray(256 * 3)
        var pIdx = 0
        for (r in 0..5) for (g in 0..5) for (b in 0..5) {
            palette[pIdx++] = (r * 51).toByte()
            palette[pIdx++] = (g * 51).toByte()
            palette[pIdx++] = (b * 51).toByte()
        }
        for (i in 0 until 40) {
            val v = (i * 255 / 39).toByte()
            palette[pIdx++] = v
            palette[pIdx++] = v
            palette[pIdx++] = v
        }

        // GIF89a Header
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(output, width)
        writeShort(output, height)
        output.write(0xF7) // Global Color Table flag (1), color res (7), sort (0), size (7 -> 256 colors)
        output.write(0)    // Background color index
        output.write(0)    // Pixel aspect ratio
        output.write(palette)

        // Netscape 2.0 Looping Application Extension
        output.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))

        val pixels = IntArray(width * height)
        val indexed = ByteArray(width * height)

        frames.forEachIndexed { idx, bmp ->
            val resized = if (bmp.width != width || bmp.height != height) Bitmap.createScaledBitmap(bmp, width, height, true) else bmp
            resized.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val ri = (r * 5 + 127) / 255
                val gi = (g * 5 + 127) / 255
                val bi = (b * 5 + 127) / 255
                indexed[i] = (ri * 36 + gi * 6 + bi).toByte()
            }
            if (resized != bmp) resized.recycle()

            // Graphic Control Extension
            output.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
            writeShort(output, delayCs)
            output.write(0)
            output.write(0)

            // Image Descriptor
            output.write(0x2C)
            writeShort(output, 0)
            writeShort(output, 0)
            writeShort(output, width)
            writeShort(output, height)
            output.write(0)

            // LZW Minimum Code Size = 8
            output.write(8)
            writeLzwData(output, indexed, minCodeSize = 8)
            output.write(0) // Block terminator
            onProgress((idx + 1).toFloat() / frames.size)
        }
        output.write(0x3B) // Trailer
    }

    private fun writeLzwData(output: FileOutputStream, data: ByteArray, minCodeSize: Int) {
        val clearCode = 1 shl minCodeSize
        val eofCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eofCode + 1
        val table = HashMap<Int, Int>(4096)

        val bitBuf = BitSubBlockWriter(output)
        bitBuf.writeBits(clearCode, codeSize)

        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = table[key]
            if (found != null) {
                prefix = found
            } else {
                bitBuf.writeBits(prefix, codeSize)
                if (nextCode < 4096) {
                    table[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bitBuf.writeBits(clearCode, codeSize)
                    table.clear()
                    codeSize = minCodeSize + 1
                    nextCode = eofCode + 1
                }
                prefix = k
            }
        }
        bitBuf.writeBits(prefix, codeSize)
        bitBuf.writeBits(eofCode, codeSize)
        bitBuf.flush()
    }

    private class BitSubBlockWriter(private val out: FileOutputStream) {
        private var acc = 0
        private var bits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        fun writeBits(code: Int, size: Int) {
            acc = acc or (code shl bits)
            bits += size
            while (bits >= 8) {
                emitByte((acc and 0xFF).toByte())
                acc = acc ushr 8
                bits -= 8
            }
        }

        private fun emitByte(b: Byte) {
            block[blockLen++] = b
            if (blockLen == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLen = 0
            }
        }

        fun flush() {
            if (bits > 0) {
                emitByte((acc and 0xFF).toByte())
            }
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }
    }

    private fun writeShort(out: FileOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
