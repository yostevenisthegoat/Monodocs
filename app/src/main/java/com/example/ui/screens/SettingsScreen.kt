package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversionQuality
import com.example.data.local.MonoSettingsState
import com.example.data.local.RecentFileEntity
import com.example.data.local.VideoQualityPreset
import com.example.ui.components.MonoDocsLogo
import com.example.ui.components.MonoEcosystemLogo
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: MonoSettingsState,
    recentFiles: List<RecentFileEntity>,
    historyCount: Int,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleSystemTheme: (Boolean) -> Unit,
    onSetSaveLocation: (String?, String) -> Unit,
    onSetConversionQuality: (ConversionQuality) -> Unit,
    onSetVideoQuality: (VideoQualityPreset) -> Unit,
    onSetImageCompression: (Int) -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onRemoveRecentFile: (RecentFileEntity) -> Unit,
    onClearRecentFiles: () -> Unit,
    onClearConversionHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var showImageCompDialog by remember { mutableStateOf(false) }
    var showManageRecentsSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val saveTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = treeUri.lastPathSegment?.substringAfter(':') ?: "Selected Folder"
            onSetSaveLocation(treeUri.toString(), label)
            feedbackMessage = "Default save location updated to $label."
        }
    }

    val matchesFilter: (String) -> Boolean = { label ->
        searchQuery.isBlank() || label.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header matching Screen 4
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = mono.textPrimary,
                    )
                    IconButton(
                        onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" },
                        modifier = Modifier.testTag("settings_search_button"),
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = "Search settings",
                            tint = mono.textPrimary,
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            // Mono Docs by Mono Ecosystem Card matching Screen 4
            item {
                MonoSurfaceCard(
                    modifier = Modifier.fillMaxWidth().testTag("settings_about_card"),
                    onClick = { showAboutSheet = true },
                    elevated = true,
                    cornerRadius = 20.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoDocsLogo(size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mono Docs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("by Mono Ecosystem", style = MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "About Mono Docs", tint = mono.textSecondary)
                    }
                }
            }

            feedbackMessage?.let { msg ->
                item {
                    MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MonoSuccess, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // Appearance Section matching Screen 4
            item {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = mono.textPrimary)
            }

            item {
                MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column {
                        if (matchesFilter("Dark Mode")) {
                            SettingsSwitchRow(
                                icon = Icons.Outlined.DarkMode,
                                title = "Dark Mode",
                                checked = settings.darkModeEnabled,
                                onCheckedChange = onToggleDarkMode,
                                testTag = "switch_dark_mode",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("System Theme")) {
                            SettingsSwitchRow(
                                icon = Icons.Outlined.BrightnessAuto,
                                title = "System Theme",
                                checked = settings.systemThemeEnabled,
                                onCheckedChange = onToggleSystemTheme,
                                testTag = "switch_system_theme",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Default Save Location")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.Folder,
                                title = "Default Save Location",
                                subtitle = settings.defaultSaveLabel,
                                onClick = { saveTreeLauncher.launch(null) },
                                testTag = "row_save_location",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Conversion Quality")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.StarOutline,
                                title = "Conversion Quality",
                                subtitle = settings.conversionQuality.label,
                                onClick = { showQualityDialog = true },
                                testTag = "row_conversion_quality",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Video Quality")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.VideoSettings,
                                title = "Video Quality",
                                subtitle = settings.videoQuality.label,
                                onClick = { showVideoDialog = true },
                                testTag = "row_video_quality",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Image Compression")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.PhotoSizeSelectLarge,
                                title = "Image Compression",
                                subtitle = "${settings.imageCompressionPercent}% Quality",
                                onClick = { showImageCompDialog = true },
                                testTag = "row_image_compression",
                            )
                        }
                    }
                }
            }

            // File Management Section matching Screen 4
            item {
                Text("File Management", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = mono.textPrimary)
            }

            item {
                MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column {
                        if (matchesFilter("Automatic File Detection")) {
                            SettingsSwitchRow(
                                icon = Icons.Outlined.DocumentScanner,
                                title = "Automatic File Detection",
                                checked = settings.autoDetectFileFormat,
                                onCheckedChange = onToggleAutoDetect,
                                testTag = "switch_auto_detect",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Manage Recent Files")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.History,
                                title = "Manage Recent Files",
                                subtitle = "${recentFiles.size} recent item(s)",
                                onClick = { showManageRecentsSheet = true },
                                testTag = "row_manage_recent",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Clear Conversion History")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.DeleteOutline,
                                title = "Clear Conversion History",
                                subtitle = "$historyCount conversion record(s)",
                                onClick = {
                                    onClearConversionHistory()
                                    feedbackMessage = "Conversion history cleared."
                                },
                                testTag = "row_clear_history",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("Privacy Information")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.Lock,
                                title = "Privacy Information",
                                subtitle = "100% offline local processing",
                                onClick = { showPrivacySheet = true },
                                testTag = "row_privacy_info",
                            )
                            HorizontalDivider(color = mono.cardBorder.copy(alpha = 0.6f))
                        }
                        if (matchesFilter("About Mono Docs")) {
                            SettingsNavRow(
                                icon = Icons.Outlined.Info,
                                title = "About Mono Docs",
                                subtitle = "An application by Mono Ecosystem",
                                onClick = { showAboutSheet = true },
                                testTag = "row_about_mono",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Conversion Quality") },
            text = {
                Column {
                    ConversionQuality.entries.forEach { q ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSetConversionQuality(q)
                                showQualityDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.conversionQuality == q, onClick = { onSetConversionQuality(q); showQualityDialog = false })
                            Spacer(Modifier.width(8.dp))
                            Text(q.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Close") } },
        )
    }

    if (showVideoDialog) {
        AlertDialog(
            onDismissRequest = { showVideoDialog = false },
            title = { Text("Video Quality") },
            text = {
                Column {
                    VideoQualityPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSetVideoQuality(preset)
                                showVideoDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.videoQuality == preset, onClick = { onSetVideoQuality(preset); showVideoDialog = false })
                            Spacer(Modifier.width(8.dp))
                            Text(preset.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVideoDialog = false }) { Text("Close") } },
        )
    }

    if (showImageCompDialog) {
        var tempPercent by remember(settings.imageCompressionPercent) { mutableIntStateOf(settings.imageCompressionPercent) }
        AlertDialog(
            onDismissRequest = { showImageCompDialog = false },
            title = { Text("Image Compression: $tempPercent%") },
            text = {
                Column {
                    Text("Higher values preserve maximum detail; lower values create smaller files.")
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = tempPercent.toFloat(),
                        onValueChange = { tempPercent = it.toInt() },
                        valueRange = 20f..100f,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSetImageCompression(tempPercent); showImageCompDialog = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showImageCompDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showManageRecentsSheet) {
        ModalBottomSheet(onDismissRequest = { showManageRecentsSheet = false }, containerColor = mono.cardSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Manage Recent Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (recentFiles.isNotEmpty()) {
                        TextButton(onClick = { onClearRecentFiles(); showManageRecentsSheet = false }) {
                            Text("Clear All")
                        }
                    }
                }
                if (recentFiles.isEmpty()) {
                    Text("No recent files recorded.", color = mono.textSecondary)
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentFiles, key = { it.uriString }) { item ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.fileName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onRemoveRecentFile(item) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showPrivacySheet) {
        ModalBottomSheet(onDismissRequest = { showPrivacySheet = false }, containerColor = mono.cardSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Offline Privacy Guarantee", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Mono Docs processes all documents, images, audio, video, archives, e-books, 3D models, and fonts 100% locally on your device. No account registration, no cloud uploads, no tracking, and no internet access is required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mono.textSecondary,
                )
                Text("Storage Access Framework (SAF) grants access only to files and folders you explicitly choose.", style = MaterialTheme.typography.bodySmall, color = mono.textMuted)
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showAboutSheet) {
        ModalBottomSheet(onDismissRequest = { showAboutSheet = false }, containerColor = mono.cardSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoDocsLogo(size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Mono Docs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Open Anything. Convert Anything.", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoEcosystemLogo(size = 18.dp, tint = mono.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("An application by Mono Ecosystem", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text("Version 1.0 • Offline Local Engine", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                Text(
                    text = "Powered by local libraries: Android PdfRenderer & PdfDocument, PDFBox-Android (Apache 2.0), Jetpack Media3 (Apache 2.0), Apache Commons Compress (Apache 2.0), Zip4j (Apache 2.0), AndroidSVG (Apache 2.0), Junrar, and native Kotlin OpenXML/OpenDocument/EPUB/3D/Font parsers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textMuted,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val mono = LocalMonoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = mono.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = mono.background,
                checkedTrackColor = mono.textPrimary,
            ),
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val mono = LocalMonoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = mono.textPrimary)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = mono.textSecondary)
    }
}
