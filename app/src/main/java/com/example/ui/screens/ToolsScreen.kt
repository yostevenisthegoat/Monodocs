package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.core.detector.FileSignatureInspector
import com.example.core.engine.ArchiveEngine
import com.example.core.engine.ImageEngine
import com.example.core.engine.ImageMetadataInfo
import com.example.core.engine.MediaEngine
import com.example.core.engine.MediaMetadataInfo
import com.example.core.engine.PdfEngine
import com.example.core.engine.PdfMetadataInfo
import com.example.core.model.FileInspectionResult
import com.example.data.local.FileStorageHelper
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoSuccess
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class ToolCategoryTab(val label: String) {
    PDF("PDF"),
    IMAGE("Image"),
    AUDIO_VIDEO("Audio & Video"),
    ARCHIVE("Archive"),
    GENERAL("Inspector & General"),
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ToolsScreen(
    initialTab: ToolCategoryTab,
    onOpenInspector: (FileInspectionResult) -> Unit,
    onOpenBatchConvert: () -> Unit,
    onOpenFileInViewer: (File, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var lastOutputFile by remember { mutableStateOf<Pair<File, String>?>(null) }

    val context = LocalContext.current
    val saveLastOutputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        val out = lastOutputFile ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { os ->
                    out.first.inputStream().use { it.copyTo(os) }
                }
            }.onSuccess { statusBanner = "Exported ${out.second} to selected folder." }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("File Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Offline PDF, Image, Media, Archive & Signature utilities", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolCategoryTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab; statusBanner = null },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("tool_tab_${tab.name.lowercase()}"),
                        )
                    }
                }
            }

            statusBanner?.let { msg ->
                item {
                    MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true, cornerRadius = 14.dp) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MonoSuccess)
                            lastOutputFile?.let { (file, name) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onOpenFileInViewer(file, name) }, modifier = Modifier.weight(1f)) {
                                        Text("Open Result")
                                    }
                                    OutlinedButton(onClick = { saveLastOutputLauncher.launch(name) }, modifier = Modifier.weight(1f)) {
                                        Text("Save As")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                when (activeTab) {
                    ToolCategoryTab.PDF -> PdfToolsPanel(
                        onSuccess = { file, name, msg ->
                            lastOutputFile = file to name
                            statusBanner = msg
                        },
                        onError = { statusBanner = it },
                    )
                    ToolCategoryTab.IMAGE -> ImageToolsPanel(
                        onSuccess = { file, name, msg ->
                            lastOutputFile = file to name
                            statusBanner = msg
                        },
                        onError = { statusBanner = it },
                    )
                    ToolCategoryTab.AUDIO_VIDEO -> MediaToolsPanel(
                        onSuccess = { file, name, msg ->
                            lastOutputFile = file to name
                            statusBanner = msg
                        },
                        onError = { statusBanner = it },
                    )
                    ToolCategoryTab.ARCHIVE -> ArchiveToolsPanel(
                        onSuccess = { file, name, msg ->
                            lastOutputFile = if (file != null) file to name else null
                            statusBanner = msg
                        },
                        onError = { statusBanner = it },
                    )
                    ToolCategoryTab.GENERAL -> GeneralToolsSection(
                        onOpenInspector = onOpenInspector,
                        onOpenBatchConvert = onOpenBatchConvert,
                        onStatusMessage = { statusBanner = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfToolsPanel(
    onSuccess: (File, String, String) -> Unit,
    onError: (String) -> Unit,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<Pair<File, String>?>(null) }
    var pdfMeta by remember { mutableStateOf<PdfMetadataInfo?>(null) }
    var password by remember { mutableStateOf("") }
    var pageRangeText by remember { mutableStateOf("1") }
    var rotateDeg by remember { mutableIntStateOf(90) }
    var compressQuality by remember { mutableIntStateOf(65) }
    var busy by remember { mutableStateOf(false) }

    val pickSinglePdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val file = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    val meta = PdfEngine.inspectPdf(file, password.ifBlank { null })
                    Triple(file, insp.fileName, meta)
                }.onSuccess { (file, name, meta) ->
                    selectedPdf = file to name
                    pdfMeta = meta
                    pageRangeText = if (meta.pageCount > 1) "1-${meta.pageCount}" else "1"
                }.onFailure { onError(it.message ?: "Could not open PDF.") }
            }
        }
    }

    val mergePdfsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size >= 2) {
            scope.launch {
                busy = true
                runCatching {
                    val files = uris.mapIndexed { idx, u ->
                        FileStorageHelper.copyUriToWorkFile(context, u, "merge_$idx.pdf")
                    }
                    val out = File(FileStorageHelper.outputsDir(context), "Merged_${System.currentTimeMillis()}.pdf")
                    PdfEngine.mergePdfs(files, out)
                    out
                }.onSuccess { out ->
                    onSuccess(out, out.name, "Merged ${uris.size} PDFs -> ${out.name} (${FileStorageHelper.formatBytes(out.length())})")
                }.onFailure { onError(it.message ?: "PDF merge failed.") }
                busy = false
            }
        } else if (uris.isNotEmpty()) {
            onError("Select at least 2 PDF files to merge.")
        }
    }

    val imagesToPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                runCatching {
                    val files = uris.mapIndexed { idx, u ->
                        FileStorageHelper.copyUriToWorkFile(context, u, "img_$idx.png")
                    }
                    val out = File(FileStorageHelper.outputsDir(context), "Images_${System.currentTimeMillis()}.pdf")
                    PdfEngine.imagesToPdf(files, out)
                    out
                }.onSuccess { out ->
                    onSuccess(out, out.name, "Created PDF from ${uris.size} image(s): ${out.name}")
                }.onFailure { onError(it.message ?: "Images to PDF failed.") }
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        ToolActionCard(
            title = "Merge PDFs",
            subtitle = "Combine multiple PDF documents into one offline",
            buttonLabel = "Select 2+ PDFs to Merge",
            onClick = { mergePdfsPicker.launch(arrayOf("application/pdf")) },
            testTag = "tool_pdf_merge",
        )

        ToolActionCard(
            title = "Image to PDF",
            subtitle = "Convert photos from Photo Picker into a multi-page PDF",
            buttonLabel = "Select Images",
            onClick = { imagesToPdfPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            testTag = "tool_img_to_pdf",
        )

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Split, Extract, Rotate, Compress & Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pickSinglePdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPdf?.second ?: "Choose PDF File")
                }

                pdfMeta?.let { meta ->
                    if (meta.requiresPassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("PDF Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {
                            val file = selectedPdf?.first ?: return@Button
                            scope.launch {
                                runCatching { PdfEngine.inspectPdf(file, password) }
                                    .onSuccess { pdfMeta = it }
                                    .onFailure { onError("Incorrect PDF password.") }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Unlock PDF") }
                    } else {
                        Text("Pages: ${meta.pageCount} • PDF v${meta.version} • ${if (meta.isEncrypted) "Encrypted" else "Standard"}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                        if (meta.title.isNotBlank()) InspectorRow("Title", meta.title)
                        if (meta.author.isNotBlank()) InspectorRow("Author", meta.author)
                        if (meta.producer.isNotBlank()) InspectorRow("Producer", meta.producer)

                        OutlinedTextField(
                            value = pageRangeText,
                            onValueChange = { pageRangeText = it },
                            label = { Text("Pages (e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val src = selectedPdf?.first ?: return@Button
                                    scope.launch {
                                        busy = true
                                        runCatching {
                                            val indices = PdfEngine.parsePageSelection(pageRangeText, meta.pageCount)
                                            val out = File(FileStorageHelper.outputsDir(context), "Extracted_${selectedPdf?.second}")
                                            PdfEngine.extractOrSplitPdf(src, indices, out, password.ifBlank { null })
                                            out
                                        }.onSuccess { out ->
                                            onSuccess(out, out.name, "Split/Extracted pages -> ${out.name}")
                                        }.onFailure { onError(it.message ?: "Split failed.") }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Split / Extract") }

                            OutlinedButton(
                                onClick = {
                                    val src = selectedPdf?.first ?: return@OutlinedButton
                                    scope.launch {
                                        busy = true
                                        runCatching {
                                            val indices = PdfEngine.parsePageSelection(pageRangeText, meta.pageCount).toSet()
                                            val out = File(FileStorageHelper.outputsDir(context), "Rotated_${selectedPdf?.second}")
                                            PdfEngine.rotatePdfPages(src, rotateDeg, indices, out, password.ifBlank { null })
                                            out
                                        }.onSuccess { out ->
                                            onSuccess(out, out.name, "Rotated pages by ${rotateDeg}° -> ${out.name}")
                                        }.onFailure { onError(it.message ?: "Rotate failed.") }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Rotate ${rotateDeg}°") }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Compression Quality $compressQuality%", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = compressQuality.toFloat(),
                                onValueChange = { compressQuality = it.toInt() },
                                valueRange = 30f..90f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            onClick = {
                                val src = selectedPdf?.first ?: return@Button
                                scope.launch {
                                    busy = true
                                    runCatching {
                                        val out = File(FileStorageHelper.outputsDir(context), "Compressed_${selectedPdf?.second}")
                                        PdfEngine.compressPdf(src, compressQuality, 1100, FileStorageHelper.workDir(context), out, password.ifBlank { null })
                                        out
                                    }.onSuccess { out ->
                                        onSuccess(out, out.name, "Compressed PDF: ${FileStorageHelper.formatBytes(src.length())} -> ${FileStorageHelper.formatBytes(out.length())}")
                                    }.onFailure { onError(it.message ?: "Compression failed.") }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("tool_pdf_compress"),
                        ) { Text("Compress PDF") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageToolsPanel(
    onSuccess: (File, String, String) -> Unit,
    onError: (String) -> Unit,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImg by remember { mutableStateOf<Triple<File, FileInspectionResult, ImageMetadataInfo>?>(null) }
    var targetWidth by remember { mutableStateOf("") }
    var targetHeight by remember { mutableStateOf("") }
    var quality by remember { mutableIntStateOf(80) }
    var rotateDeg by remember { mutableIntStateOf(0) }
    var cropMargin by remember { mutableFloatStateOf(0f) }
    var outExt by remember { mutableStateOf("jpg") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val file = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    val meta = ImageEngine.inspectMetadata(file, insp.detectedFormatId)
                    Triple(file, insp, meta)
                }.onSuccess { triple ->
                    selectedImg = triple
                    targetWidth = triple.third.width.toString()
                    targetHeight = triple.third.height.toString()
                }.onFailure { onError(it.message ?: "Could not load image.") }
            }
        }
    }

    MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resize, Crop, Rotate, Compress & EXIF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().testTag("tool_image_pick"),
            ) {
                Text(selectedImg?.second?.fileName ?: "Select Image via Photo Picker")
            }

            selectedImg?.let { (file, insp, meta) ->
                Text("Dimensions: ${meta.width} × ${meta.height} px • ${meta.mimeType}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                meta.exifItems.forEach { (k, v) -> InspectorRow(k, v) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = targetWidth, onValueChange = { targetWidth = it }, label = { Text("Width") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = targetHeight, onValueChange = { targetHeight = it }, label = { Text("Height") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quality $quality%", style = MaterialTheme.typography.labelSmall)
                    Slider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 20f..100f, modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Crop Inset ${(cropMargin * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(value = cropMargin, onValueChange = { cropMargin = it }, valueRange = 0f..0.35f, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { rotateDeg = (rotateDeg + 90) % 360 }) { Text("Rotate: $rotateDeg°") }
                    listOf("jpg", "png", "webp", "pdf").forEach { ext ->
                        FilterChip(selected = outExt == ext, onClick = { outExt = ext }, label = { Text(ext.uppercase()) })
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val outName = "${insp.fileName.substringBeforeLast('.')}_edited.$outExt"
                                val out = File(FileStorageHelper.outputsDir(context), outName)
                                val crop = if (cropMargin > 0.01f) ImageEngine.CropRect(cropMargin, cropMargin, 1f - cropMargin, 1f - cropMargin) else null
                                ImageEngine.convertOrProcessImage(
                                    inputFile = file,
                                    sourceFormatId = insp.detectedFormatId,
                                    targetExtension = outExt,
                                    outFile = out,
                                    quality = quality,
                                    targetWidth = targetWidth.toIntOrNull(),
                                    targetHeight = targetHeight.toIntOrNull(),
                                    rotateDegrees = rotateDeg,
                                    cropNormalized = crop,
                                )
                                out
                            }.onSuccess { out ->
                                onSuccess(out, out.name, "Processed image saved: ${out.name} (${FileStorageHelper.formatBytes(out.length())})")
                            }.onFailure { onError(it.message ?: "Image processing failed.") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("tool_image_apply"),
                ) { Text("Process & Save Image") }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaToolsPanel(
    onSuccess: (File, String, String) -> Unit,
    onError: (String) -> Unit,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMedia by remember { mutableStateOf<Triple<File, FileInspectionResult, MediaMetadataInfo>?>(null) }
    var trimStartSec by remember { mutableFloatStateOf(0f) }
    var trimEndSec by remember { mutableFloatStateOf(10f) }
    var videoHeight by remember { mutableIntStateOf(720) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var thumbnailBmp by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val file = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    val meta = MediaEngine.inspectMedia(file)
                    Triple(file, insp, meta)
                }.onSuccess { triple ->
                    selectedMedia = triple
                    trimStartSec = 0f
                    trimEndSec = (triple.third.durationMs / 1000f).coerceAtLeast(1f)
                    thumbnailBmp = null
                }.onFailure { onError(it.message ?: "Could not inspect media.") }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audio & Video Processor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Button(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth().testTag("tool_media_pick")) {
                    Text(selectedMedia?.second?.fileName ?: "Select Audio or Video File")
                }

                selectedMedia?.let { (file, insp, meta) ->
                    val maxSec = (meta.durationMs / 1000f).coerceAtLeast(1f)
                    Text(
                        text = buildString {
                            append("Duration: ${maxSec.toInt()}s")
                            if (meta.hasVideo) append(" • ${meta.width}×${meta.height}")
                            if (meta.bitrateBps > 0) append(" • ${meta.bitrateBps / 1000} kbps")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )

                    Text("Trim Range: ${trimStartSec.toInt()}s – ${trimEndSec.toInt()}s", style = MaterialTheme.typography.labelSmall)
                    Slider(value = trimStartSec, onValueChange = { trimStartSec = minOf(it, trimEndSec - 0.5f).coerceAtLeast(0f) }, valueRange = 0f..maxSec)
                    Slider(value = trimEndSec, onValueChange = { trimEndSec = maxOf(it, trimStartSec + 0.5f).coerceAtMost(maxSec) }, valueRange = 0f..maxSec)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true; progress = 0f
                                    runCatching {
                                        val ext = if (meta.hasVideo) "mp4" else "m4a"
                                        val out = File(FileStorageHelper.outputsDir(context), "${file.nameWithoutExtension}_trim.$ext")
                                        MediaEngine.runMedia3Transform(
                                            context = context,
                                            inputFile = file,
                                            outputFile = out,
                                            removeVideo = !meta.hasVideo,
                                            startMs = (trimStartSec * 1000).toLong(),
                                            endMs = (trimEndSec * 1000).toLong(),
                                            onProgress = { progress = it },
                                        )
                                        out
                                    }.onSuccess { out ->
                                        onSuccess(out, out.name, "Trimmed media saved: ${out.name}")
                                    }.onFailure { onError(it.message ?: "Trim failed.") }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Trim") }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true; progress = 0f
                                    runCatching {
                                        val out = File(FileStorageHelper.outputsDir(context), "${file.nameWithoutExtension}_audio.m4a")
                                        MediaEngine.runMedia3Transform(context, file, out, removeVideo = true, onProgress = { progress = it })
                                        out
                                    }.onSuccess { out ->
                                        onSuccess(out, out.name, "Extracted audio: ${out.name}")
                                    }.onFailure { onError(it.message ?: "Audio extraction failed.") }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Extract M4A") }
                    }

                    if (meta.hasVideo) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1080, 720, 480).forEach { h ->
                                FilterChip(selected = videoHeight == h, onClick = { videoHeight = h }, label = { Text("${h}p") })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true; progress = 0f
                                        runCatching {
                                            val out = File(FileStorageHelper.outputsDir(context), "${file.nameWithoutExtension}_${videoHeight}p.mp4")
                                            MediaEngine.runMedia3Transform(context, file, out, targetHeight = videoHeight, onProgress = { progress = it })
                                            out
                                        }.onSuccess { out ->
                                            onSuccess(out, out.name, "Compressed video: ${FileStorageHelper.formatBytes(file.length())} -> ${FileStorageHelper.formatBytes(out.length())}")
                                        }.onFailure { onError(it.message ?: "Compression failed.") }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("Compress MP4") }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true; progress = 0f
                                        runCatching {
                                            val out = File(FileStorageHelper.outputsDir(context), "${file.nameWithoutExtension}.gif")
                                            MediaEngine.videoToAnimatedGif(
                                                inputFile = file,
                                                outFile = out,
                                                startMs = (trimStartSec * 1000).toLong(),
                                                endMs = (trimEndSec * 1000).toLong(),
                                                onProgress = { progress = it },
                                            )
                                            out
                                        }.onSuccess { out ->
                                            onSuccess(out, out.name, "Created Animated GIF: ${out.name}")
                                        }.onFailure { onError(it.message ?: "GIF export failed.") }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("To GIF") }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val bmp = MediaEngine.extractThumbnail(file, (trimStartSec * 1000).toLong())
                                        val out = File(FileStorageHelper.outputsDir(context), "${file.nameWithoutExtension}_thumb.png")
                                        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                        bmp to out
                                    }.onSuccess { (bmp, out) ->
                                        thumbnailBmp = bmp
                                        onSuccess(out, out.name, "Extracted thumbnail PNG: ${out.name}")
                                    }.onFailure { onError(it.message ?: "Thumbnail extraction failed.") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Extract Video Thumbnail at ${trimStartSec.toInt()}s") }

                        thumbnailBmp?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Extracted video thumbnail",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveToolsPanel(
    onSuccess: (File?, String, String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zipPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val createZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                runCatching {
                    val pairs = uris.mapIndexed { idx, u ->
                        val insp = FileSignatureInspector.inspectUri(context, u)
                        val f = FileStorageHelper.copyUriToWorkFile(context, u, insp.fileName)
                        f to insp.fileName
                    }
                    val out = File(FileStorageHelper.outputsDir(context), "Archive_${System.currentTimeMillis()}.zip")
                    ArchiveEngine.createZip(pairs, out, zipPassword.ifBlank { null })
                    out
                }.onSuccess { out ->
                    onSuccess(out, out.name, "Created ZIP (${uris.size} files): ${out.name}")
                }.onFailure { onError(it.message ?: "ZIP creation failed.") }
                busy = false
            }
        }
    }

    var extractFile by remember { mutableStateOf<Pair<File, String>?>(null) }
    var extractPassword by remember { mutableStateOf("") }

    val extractPickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val f = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    f to insp.detectedFormatId
                }.onSuccess { extractFile = it }
            }
        }
    }

    val extractDestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        val src = extractFile ?: return@rememberLauncherForActivityResult
        if (treeUri != null) {
            scope.launch {
                busy = true
                val temp = File(FileStorageHelper.workDir(context), "ext_${System.currentTimeMillis()}")
                runCatching {
                    val items = ArchiveEngine.extractEntries(src.first, src.second, null, temp, extractPassword.ifBlank { null })
                    items.forEach { f -> FileStorageHelper.saveFileToTreeUri(context, treeUri, f, f.name, "*/*") }
                    items.size
                }.onSuccess { count ->
                    onSuccess(null, "", "Extracted $count file(s) into chosen folder.")
                }.onFailure { onError(it.message ?: "Extraction failed.") }
                temp.deleteRecursively()
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create ZIP Archive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = zipPassword,
                    onValueChange = { zipPassword = it },
                    label = { Text("Optional AES Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { createZipPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("tool_create_zip"),
                ) { Text("Select Files & Create ZIP") }
            }
        }

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Extract Archive (ZIP, 7Z, RAR, TAR, GZ, BZ2, XZ)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { extractPickLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(extractFile?.first?.name ?: "Choose Archive File")
                }
                if (extractFile != null) {
                    OutlinedTextField(
                        value = extractPassword,
                        onValueChange = { extractPassword = it },
                        label = { Text("Password (if encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { extractDestLauncher.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose Destination & Extract")
                    }
                }
            }
        }
    }
}
