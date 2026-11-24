package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.detector.FileSignatureInspector
import com.example.core.model.FileInspectionResult
import com.example.data.local.FileStorageHelper
import com.example.ui.components.FileTypeBadge
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoError
import com.example.ui.theme.MonoSuccess
import com.example.ui.theme.MonoWarning
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInspectorModal(
    inspection: FileInspectionResult,
    onDismiss: () -> Unit,
    onOpenInViewer: (Uri) -> Unit,
    onOpenInConverter: (Uri) -> Unit,
) {
    val mono = LocalMonoColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mono.cardSurface,
        contentColor = mono.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeBadge(inspection.detectedExtension, inspection.category, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("File Signature Inspector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(inspection.fileName, style = MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
                }
            }

            if (inspection.hasExtensionMismatch) {
                MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true, cornerRadius = 14.dp) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MonoWarning)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Extension mismatch detected! File is named '.${inspection.declaredExtension}' but binary signature identifies ${inspection.detectedFormatName}.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InspectorRow("Detected Format", inspection.detectedFormatName)
                    InspectorRow("Actual MIME Type", inspection.mimeType)
                    InspectorRow("Declared Extension", if (inspection.declaredExtension.isBlank()) "None" else ".${inspection.declaredExtension}")
                    InspectorRow("File Size", FileStorageHelper.formatBytes(inspection.sizeBytes))
                    InspectorRow("Category", inspection.category.title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Status", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (inspection.isSupported) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (inspection.isSupported) MonoSuccess else MonoError,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (inspection.isSupported) "Supported" else "Unsupported",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (inspection.isSupported) MonoSuccess else MonoError,
                            )
                        }
                    }
                    if (inspection.magicHexPreview.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Magic Bytes (Hex)", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                            Text(
                                text = inspection.magicHexPreview,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = mono.textPrimary,
                            )
                        }
                    }
                    if (inspection.sha256.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("SHA-256", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                            Text(
                                text = inspection.sha256,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = mono.textMuted,
                            )
                        }
                    }
                }
            }

            if (inspection.isSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onDismiss(); onOpenInViewer(inspection.uri) },
                        modifier = Modifier.weight(1f).testTag("inspector_open_viewer"),
                    ) {
                        Text("Open File")
                    }
                    if (inspection.supportedOutputs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onDismiss(); onOpenInConverter(inspection.uri) },
                            modifier = Modifier.weight(1f).testTag("inspector_open_converter"),
                        ) {
                            Text("Convert")
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun InspectorRow(label: String, value: String) {
    val mono = LocalMonoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = mono.textPrimary)
    }
}

@Composable
fun GeneralToolsSection(
    onOpenInspector: (FileInspectionResult) -> Unit,
    onOpenBatchConvert: () -> Unit,
    onStatusMessage: (String) -> Unit,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sizeBreakdown by remember { mutableStateOf<List<FileInspectionResult>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<List<FileInspectionResult>>>(emptyList()) }
    var duplicatesScannedCount by remember { mutableStateOf(0) }
    var renameSource by remember { mutableStateOf<Pair<FileInspectionResult, File>?>(null) }
    var newFileName by remember { mutableStateOf("") }

    val inspectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { FileSignatureInspector.inspectUri(context, uri, computeHash = true) }
                    .onSuccess { onOpenInspector(it) }
                    .onFailure { onStatusMessage(it.message ?: "Could not inspect file.") }
            }
        }
    }

    val sizeCalcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                sizeBreakdown = uris.mapNotNull { u ->
                    runCatching { FileSignatureInspector.inspectUri(context, u) }.getOrNull()
                }
            }
        }
    }

    val dupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val inspected = uris.mapNotNull { u ->
                    runCatching { FileSignatureInspector.inspectUri(context, u, computeHash = true) }.getOrNull()
                }
                duplicatesScannedCount = inspected.size
                duplicateGroups = inspected
                    .filter { it.sha256.isNotBlank() }
                    .groupBy { it.sha256 }
                    .values
                    .filter { it.size > 1 }
                    .toList()
            }
        }
    }

    val renameSourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val insp = FileSignatureInspector.inspectUri(context, uri)
                    val f = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                    insp to f
                }.onSuccess { (insp, file) ->
                    renameSource = insp to file
                    newFileName = insp.fileName
                }
            }
        }
    }

    val renameSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(renameSource?.first?.mimeType ?: "*/*")
    ) { targetUri ->
        val src = renameSource?.second ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            scope.launch {
                runCatching { FileStorageHelper.saveFileToUri(context, src, targetUri) }
                    .onSuccess { onStatusMessage("Saved renamed file as '$newFileName'.") }
                    .onFailure { onStatusMessage(it.message ?: "Rename failed.") }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolActionCard(
            title = "File Identifier & Metadata Inspector",
            subtitle = "Inspect magic bytes, actual MIME type, SHA-256 & extension mismatches",
            buttonLabel = "Select Unknown File",
            onClick = { inspectLauncher.launch(arrayOf("*/*")) },
            testTag = "tool_file_inspector",
        )

        ToolActionCard(
            title = "Batch Conversion",
            subtitle = "Convert multiple files simultaneously with retry & folder export",
            buttonLabel = "Open Batch Converter",
            onClick = onOpenBatchConvert,
            testTag = "tool_batch_convert",
        )

        // File Renamer
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File Renamer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Select a file and export a renamed local copy safely.", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                OutlinedButton(onClick = { renameSourceLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(renameSource?.first?.fileName ?: "Choose File to Rename")
                }
                if (renameSource != null) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("New File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { renameSaveLauncher.launch(newFileName.ifBlank { "renamed_file" }) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Renamed File")
                    }
                }
            }
        }

        // File Size Calculator
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File Size Calculator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { sizeCalcLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Select Files to Calculate")
                }
                if (sizeBreakdown.isNotEmpty()) {
                    val total = sizeBreakdown.sumOf { it.sizeBytes }
                    Text("Total (${sizeBreakdown.size} files): ${FileStorageHelper.formatBytes(total)}", style = MaterialTheme.typography.labelLarge)
                    sizeBreakdown.forEach { item ->
                        InspectorRow(item.fileName, FileStorageHelper.formatBytes(item.sizeBytes))
                    }
                }
            }
        }

        // Duplicate File Detector (SHA-256)
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duplicate File Detector (SHA-256)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Compare multiple files byte-for-byte using cryptographic SHA-256 hashes.", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                OutlinedButton(onClick = { dupLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan Files for Duplicates")
                }
                if (duplicatesScannedCount > 0) {
                    if (duplicateGroups.isEmpty()) {
                        Text("Scanned $duplicatesScannedCount files — no duplicates found.", style = MaterialTheme.typography.bodySmall, color = MonoSuccess)
                    } else {
                        Text("Found ${duplicateGroups.size} duplicate group(s):", style = MaterialTheme.typography.labelLarge, color = MonoWarning)
                        duplicateGroups.forEachIndexed { idx, group ->
                            Text("Group ${idx + 1}: ${group.joinToString(", ") { it.fileName }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolActionCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    testTag: String = "",
) {
    val mono = LocalMonoColors.current
    MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
    }
}
