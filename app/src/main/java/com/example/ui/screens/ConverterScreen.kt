package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.core.detector.FileSignatureInspector
import com.example.core.engine.ConversionOutput
import com.example.core.engine.UniversalConversionEngine
import com.example.core.model.FileInspectionResult
import com.example.data.local.ConversionHistoryEntity
import com.example.data.local.FileStorageHelper
import com.example.data.local.MonoSettingsState
import com.example.ui.components.FileTypeBadge
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoError
import com.example.ui.theme.MonoSuccess
import kotlinx.coroutines.launch
import java.io.File

data class BatchConversionItem(
    val uri: Uri,
    val inspection: FileInspectionResult,
    val localFile: File,
    val status: BatchStatus = BatchStatus.PENDING,
    val progress: Float = 0f,
    val output: ConversionOutput? = null,
    val error: String? = null,
)

enum class BatchStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    settings: MonoSettingsState,
    initialBatchMode: Boolean,
    preselectedFile: Pair<FileInspectionResult, File>?,
    onClearPreselected: () -> Unit,
    onRecordConversion: (ConversionHistoryEntity) -> Unit,
    onOpenConvertedFile: (File, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var batchMode by remember(initialBatchMode) { mutableStateOf(initialBatchMode) }

    // Single Converter State
    var singleInspection by remember { mutableStateOf<FileInspectionResult?>(null) }
    var singleLocalFile by remember { mutableStateOf<File?>(null) }
    var selectedOutputFormat by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var conversionResult by remember { mutableStateOf<ConversionOutput?>(null) }
    var savedLocationMsg by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Batch Converter State
    val batchItems = remember { mutableStateListOf<BatchConversionItem>() }
    var batchTargetFormat by remember { mutableStateOf<String?>(null) }
    var batchRunning by remember { mutableStateOf(false) }
    var batchMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preselectedFile) {
        if (preselectedFile != null) {
            batchMode = false
            singleInspection = preselectedFile.first
            singleLocalFile = preselectedFile.second
            selectedOutputFormat = preselectedFile.first.supportedOutputs.firstOrNull()
            conversionResult = null
            errorMessage = null
            savedLocationMsg = null
            onClearPreselected()
        }
    }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                errorMessage = null
                conversionResult = null
                savedLocationMsg = null
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val copied = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    insp to copied
                }.onSuccess { (insp, file) ->
                    singleInspection = insp
                    singleLocalFile = file
                    selectedOutputFormat = insp.supportedOutputs.firstOrNull()
                }.onFailure {
                    errorMessage = it.message ?: "Mono Docs couldn't read this file. It may be damaged."
                }
            }
        }
    }

    val saveSinglePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(conversionResult?.outputMimeType ?: "*/*")
    ) { targetUri ->
        val out = conversionResult ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            scope.launch {
                runCatching { FileStorageHelper.saveFileToUri(context, out.outputFile, targetUri) }
                    .onSuccess { savedLocationMsg = "Saved as ${out.outputFileName} to chosen folder." }
                    .onFailure { errorMessage = it.message }
            }
        }
    }

    val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                batchMessage = null
                batchItems.clear()
                uris.forEach { uri ->
                    runCatching {
                        val insp = FileSignatureInspector.inspectUri(context, uri)
                        val file = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                        BatchConversionItem(uri, insp, file)
                    }.onSuccess { item ->
                        batchItems += item
                    }
                }
                val commonOutputs = batchItems.map { it.inspection.supportedOutputs.toSet() }
                    .reduceOrNull { acc, set -> acc intersect set }
                    ?.toList()
                    .orEmpty()
                batchTargetFormat = commonOutputs.firstOrNull()
                    ?: batchItems.firstOrNull()?.inspection?.supportedOutputs?.firstOrNull()
            }
        }
    }

    val batchSaveTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            scope.launch {
                var saved = 0
                batchItems.filter { it.status == BatchStatus.SUCCEEDED && it.output != null }.forEach { item ->
                    val out = item.output!!
                    runCatching {
                        FileStorageHelper.saveFileToTreeUri(context, treeUri, out.outputFile, out.outputFileName, out.outputMimeType)
                    }.onSuccess { if (it != null) saved++ }
                }
                batchMessage = "Saved $saved converted file(s) into selected folder."
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Converter", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("100% offline conversion — original files stay untouched.", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }

            // Mode Switch: Single vs Batch
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModePillButton(
                        text = "Single File",
                        selected = !batchMode,
                        onClick = { batchMode = false },
                        modifier = Modifier.weight(1f).testTag("converter_single_tab"),
                    )
                    ModePillButton(
                        text = "Batch Convert",
                        selected = batchMode,
                        onClick = { batchMode = true },
                        modifier = Modifier.weight(1f).testTag("converter_batch_tab"),
                    )
                }
            }

            if (!batchMode) {
                // Step 1: Select File
                item {
                    MonoSurfaceCard(
                        modifier = Modifier.fillMaxWidth().testTag("converter_select_file"),
                        onClick = { singlePicker.launch(arrayOf("*/*")) },
                        elevated = true,
                        cornerRadius = 18.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (singleInspection != null) {
                                FileTypeBadge(singleInspection!!.detectedExtension, singleInspection!!.category, size = 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(singleInspection!!.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "Detected: ${singleInspection!!.detectedFormatName} • ${FileStorageHelper.formatBytes(singleInspection!!.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mono.textSecondary,
                                    )
                                }
                            } else {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("Select a File to Convert", style = MaterialTheme.typography.titleMedium)
                                    Text("Format is auto-detected from binary signature", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                                }
                            }
                        }
                    }
                }

                singleInspection?.let { insp ->
                    item {
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Compatible Output Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (insp.supportedOutputs.isEmpty()) {
                                    Text(
                                        text = "No direct conversion outputs are available for ${insp.detectedFormatName}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mono.textSecondary,
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        insp.supportedOutputs.forEach { ext ->
                                            FilterChip(
                                                selected = selectedOutputFormat == ext,
                                                onClick = { selectedOutputFormat = ext },
                                                label = { Text(ext.uppercase()) },
                                                modifier = Modifier.testTag("output_fmt_$ext"),
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val file = singleLocalFile ?: return@Button
                                            val tgt = selectedOutputFormat ?: return@Button
                                            scope.launch {
                                                isConverting = true
                                                progress = 0.05f
                                                errorMessage = null
                                                conversionResult = null
                                                savedLocationMsg = null
                                                runCatching {
                                                    val out = UniversalConversionEngine.convert(
                                                        context = context,
                                                        sourceFile = file,
                                                        inspection = insp,
                                                        targetExtension = tgt,
                                                        settings = settings,
                                                        onProgress = { p -> progress = p },
                                                    )
                                                    val localSaved = if (settings.defaultSaveTreeUri != null) {
                                                        runCatching {
                                                            FileStorageHelper.saveFileToTreeUri(
                                                                context,
                                                                Uri.parse(settings.defaultSaveTreeUri),
                                                                out.outputFile,
                                                                out.outputFileName,
                                                                out.outputMimeType,
                                                            )
                                                        }.getOrNull()
                                                    } else null
                                                    val finalLocal = FileStorageHelper.saveToLocalMonoFolder(context, out.outputFile, out.outputFileName)
                                                    out.copy(outputFile = finalLocal) to (localSaved != null)
                                                }.onSuccess { (out, savedToTree) ->
                                                    conversionResult = out
                                                    savedLocationMsg = if (savedToTree) "Saved to ${settings.defaultSaveLabel} & MonoDocs" else "Saved locally: ${out.outputFile.name}"
                                                    onRecordConversion(
                                                        ConversionHistoryEntity(
                                                            sourceName = insp.fileName,
                                                            sourceFormat = insp.detectedExtension.uppercase(),
                                                            targetFormat = tgt.uppercase(),
                                                            outputName = out.outputFileName,
                                                            outputPath = out.outputFile.absolutePath,
                                                            outputSizeBytes = out.outputFile.length(),
                                                            succeeded = true,
                                                        )
                                                    )
                                                }.onFailure { err ->
                                                    errorMessage = err.message ?: "Conversion failed. Your original file has not been modified."
                                                    onRecordConversion(
                                                        ConversionHistoryEntity(
                                                            sourceName = insp.fileName,
                                                            sourceFormat = insp.detectedExtension.uppercase(),
                                                            targetFormat = tgt.uppercase(),
                                                            outputName = "",
                                                            outputPath = "",
                                                            outputSizeBytes = 0L,
                                                            succeeded = false,
                                                            errorMessage = errorMessage,
                                                        )
                                                    )
                                                }
                                                isConverting = false
                                            }
                                        },
                                        enabled = !isConverting && selectedOutputFormat != null,
                                        modifier = Modifier.fillMaxWidth().testTag("start_conversion_btn"),
                                    ) {
                                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isConverting) "Converting..." else "Convert to ${selectedOutputFormat?.uppercase().orEmpty()}")
                                    }
                                }
                            }
                        }
                    }
                }

                if (isConverting) {
                    item {
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Processing locally... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                errorMessage?.let { err ->
                    item {
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MonoError)
                                Spacer(Modifier.width(10.dp))
                                Text(err, style = MaterialTheme.typography.bodyMedium, color = mono.textPrimary)
                            }
                        }
                    }
                }

                conversionResult?.let { out ->
                    item {
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true, cornerRadius = 16.dp) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MonoSuccess)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Conversion Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Text("${out.outputFileName} • ${FileStorageHelper.formatBytes(out.outputFile.length())}", style = MaterialTheme.typography.bodyMedium)
                                savedLocationMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = { onOpenConvertedFile(out.outputFile, out.outputFileName) }, modifier = Modifier.weight(1f)) {
                                        Text("Open")
                                    }
                                    OutlinedButton(onClick = { saveSinglePicker.launch(out.outputFileName) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save As")
                                    }
                                    OutlinedButton(onClick = { FileStorageHelper.shareFile(context, out.outputFile, out.outputMimeType) }) {
                                        Icon(Icons.Outlined.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // BATCH CONVERTER WORKFLOW
                item {
                    MonoSurfaceCard(
                        modifier = Modifier.fillMaxWidth().testTag("batch_select_files"),
                        onClick = { batchPicker.launch(arrayOf("*/*")) },
                        elevated = true,
                        cornerRadius = 18.dp,
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Select Multiple Files", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (batchItems.isEmpty()) "Pick 2 or more files to convert together" else "${batchItems.size} file(s) queued",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mono.textSecondary,
                                )
                            }
                        }
                    }
                }

                if (batchItems.isNotEmpty()) {
                    val allPossibleOutputs = batchItems.flatMap { it.inspection.supportedOutputs }.distinct()
                    item {
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Batch Output Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    allPossibleOutputs.forEach { ext ->
                                        FilterChip(
                                            selected = batchTargetFormat == ext,
                                            onClick = { batchTargetFormat = ext },
                                            label = { Text(ext.uppercase()) },
                                        )
                                    }
                                }
                                val doneCount = batchItems.count { it.status == BatchStatus.SUCCEEDED }
                                val overall = if (batchItems.isEmpty()) 0f else batchItems.sumOf { it.progress.toDouble() }.toFloat() / batchItems.size
                                LinearProgressIndicator(progress = { overall.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("$doneCount / ${batchItems.size} completed", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val tgt = batchTargetFormat ?: return@Button
                                            scope.launch {
                                                batchRunning = true
                                                for (idx in batchItems.indices) {
                                                    val item = batchItems[idx]
                                                    if (item.status == BatchStatus.SUCCEEDED) continue
                                                    batchItems[idx] = item.copy(status = BatchStatus.RUNNING, progress = 0.05f, error = null)
                                                    runCatching {
                                                        val out = UniversalConversionEngine.convert(
                                                            context = context,
                                                            sourceFile = item.localFile,
                                                            inspection = item.inspection,
                                                            targetExtension = tgt,
                                                            settings = settings,
                                                            onProgress = { p -> batchItems[idx] = batchItems[idx].copy(progress = p) },
                                                        )
                                                        val saved = FileStorageHelper.saveToLocalMonoFolder(context, out.outputFile, out.outputFileName)
                                                        out.copy(outputFile = saved)
                                                    }.onSuccess { out ->
                                                        batchItems[idx] = batchItems[idx].copy(status = BatchStatus.SUCCEEDED, progress = 1f, output = out)
                                                    }.onFailure { err ->
                                                        batchItems[idx] = batchItems[idx].copy(status = BatchStatus.FAILED, error = err.message)
                                                    }
                                                }
                                                batchRunning = false
                                            }
                                        },
                                        enabled = !batchRunning && batchTargetFormat != null,
                                        modifier = Modifier.weight(1f).testTag("batch_start_btn"),
                                    ) {
                                        Text(if (batchRunning) "Converting..." else "Start Batch")
                                    }

                                    if (batchItems.any { it.status == BatchStatus.FAILED } && !batchRunning) {
                                        OutlinedButton(onClick = {
                                            for (i in batchItems.indices) {
                                                if (batchItems[i].status == BatchStatus.FAILED) {
                                                    batchItems[i] = batchItems[i].copy(status = BatchStatus.PENDING, progress = 0f, error = null)
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Retry Failed")
                                        }
                                    }
                                }

                                if (batchItems.any { it.status == BatchStatus.SUCCEEDED }) {
                                    OutlinedButton(
                                        onClick = { batchSaveTreePicker.launch(null) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Save All Results to Folder")
                                    }
                                }
                                batchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MonoSuccess) }
                            }
                        }
                    }

                    items(batchItems, key = { it.uri.toString() }) { item ->
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FileTypeBadge(item.inspection.detectedExtension, item.inspection.category, size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.inspection.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = when (item.status) {
                                                BatchStatus.PENDING -> "Ready (${item.inspection.detectedExtension.uppercase()})"
                                                BatchStatus.RUNNING -> "Converting ${(item.progress * 100).toInt()}%"
                                                BatchStatus.SUCCEEDED -> "Saved: ${item.output?.outputFileName.orEmpty()}"
                                                BatchStatus.FAILED -> item.error ?: "Conversion failed"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (item.status == BatchStatus.FAILED) MonoError else mono.textSecondary,
                                        )
                                    }
                                }
                                if (item.status == BatchStatus.RUNNING) {
                                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) mono.textPrimary else mono.cardSurface,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) mono.background else mono.textSecondary,
            )
        }
    }
}
