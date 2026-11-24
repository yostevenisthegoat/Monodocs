package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.FileCategory
import com.example.core.model.SupportedFormatsRegistry
import com.example.data.local.FileStorageHelper
import com.example.data.local.RecentFileEntity
import com.example.ui.components.FileTypeBadge
import com.example.ui.components.MonoDocsLogo
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.components.categoryIcon
import com.example.ui.theme.LocalMonoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentFiles: List<RecentFileEntity>,
    onOpenFilePicker: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenCompress: () -> Unit,
    onOpenExtractArchive: () -> Unit,
    onOpenScanInspector: () -> Unit,
    onOpenBatchConvert: () -> Unit,
    onOpenToolsTab: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onOpenRecentFile: (RecentFileEntity) -> Unit,
    onInspectRecentFile: (RecentFileEntity) -> Unit,
    onRemoveRecentFile: (RecentFileEntity) -> Unit,
    onOpenCategoryPicker: (FileCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllQuickActions by remember { mutableStateOf(false) }
    var showAllRecentsSheet by remember { mutableStateOf(false) }
    var selectedCategorySheet by remember { mutableStateOf<FileCategory?>(null) }

    val homeCategories = remember {
        listOf(
            FileCategory.DOCUMENTS,
            FileCategory.IMAGES,
            FileCategory.VIDEOS,
            FileCategory.AUDIO,
            FileCategory.ARCHIVES,
            FileCategory.EBOOKS,
            FileCategory.SPREADSHEETS,
            FileCategory.PRESENTATIONS,
            FileCategory.DEVELOPER,
            FileCategory.MODELS_3D,
            FileCategory.FONTS,
        )
    }

    val filteredRecents = remember(recentFiles, searchQuery) {
        if (searchQuery.isBlank()) recentFiles
        else recentFiles.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) ||
                it.extension.contains(searchQuery, ignoreCase = true) ||
                it.categoryName.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header matching reference Screen 2
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello there!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mono.textSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Welcome to Mono Docs",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = mono.textPrimary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" },
                            modifier = Modifier.testTag("home_search_button"),
                        ) {
                            Icon(
                                imageVector = if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = "Search files and formats",
                                tint = mono.textPrimary,
                            )
                        }
                        IconButton(
                            onClick = onOpenSettingsTab,
                            modifier = Modifier.testTag("home_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Open Settings",
                                tint = mono.textPrimary,
                            )
                        }
                    }
                }
            }

            // Expandable Search Bar
            item {
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_input"),
                        placeholder = { Text("Search recent files or extensions...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mono.textPrimary,
                            unfocusedBorderColor = mono.cardBorder,
                            focusedContainerColor = mono.cardSurface,
                            unfocusedContainerColor = mono.cardSurface,
                        ),
                    )
                }
            }

            // Hero Mono Docs Card matching reference Screen 2
            item {
                MonoSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_banner_card"),
                    onClick = onOpenScanInspector,
                    elevated = true,
                    cornerRadius = 20.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoDocsLogo(size = 42.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mono Docs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = mono.textPrimary,
                            )
                            Text(
                                text = "Your all-in-one file utility",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mono.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Inspect file",
                            tint = mono.textSecondary,
                        )
                    }
                }
            }

            // Quick Actions Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = mono.textPrimary,
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAllQuickActions = !showAllQuickActions }
                                .testTag("quick_actions_see_all")
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (showAllQuickActions) "Less" else "See all",
                                style = MaterialTheme.typography.bodySmall,
                                color = mono.textSecondary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = mono.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionCard(
                            title = "Open File",
                            icon = Icons.Outlined.FolderOpen,
                            onClick = onOpenFilePicker,
                            testTag = "quick_open_file",
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionCard(
                            title = "Convert File",
                            icon = Icons.Outlined.SwapHoriz,
                            onClick = onOpenConverter,
                            testTag = "quick_convert_file",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionCard(
                            title = "Compress File",
                            icon = Icons.Outlined.Compress,
                            onClick = onOpenCompress,
                            testTag = "quick_compress_file",
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionCard(
                            title = "Extract Archive",
                            icon = Icons.Outlined.Unarchive,
                            onClick = onOpenExtractArchive,
                            testTag = "quick_extract_archive",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Scan File Quick Action (always reachable on Home, matching prompt spec)
                    MonoSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_scan_file"),
                        onClick = onOpenScanInspector,
                        cornerRadius = 14.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DocumentScanner,
                                contentDescription = "Scan File",
                                tint = mono.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Scan File — Identify Format & Signature",
                                style = MaterialTheme.typography.labelLarge,
                                color = mono.textPrimary,
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAllQuickActions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickActionCard(
                                title = "Batch Convert",
                                icon = Icons.Outlined.SwapHoriz,
                                onClick = onOpenBatchConvert,
                                testTag = "quick_batch_convert",
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionCard(
                                title = "All File Tools",
                                icon = Icons.Outlined.FolderZip,
                                onClick = onOpenToolsTab,
                                testTag = "quick_all_tools",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Categories 4-column grid matching reference Screen 2
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mono.textPrimary,
                    )
                    homeCategories.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { category ->
                                CategoryTile(
                                    category = category,
                                    onClick = { selectedCategorySheet = category },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Recent Files Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mono.textPrimary,
                    )
                    if (recentFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAllRecentsSheet = true }
                                .testTag("recent_files_see_all")
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "See all",
                                style = MaterialTheme.typography.bodySmall,
                                color = mono.textSecondary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = mono.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            if (filteredRecents.isEmpty()) {
                item {
                    MonoSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_empty_card"),
                        onClick = onOpenFilePicker,
                        cornerRadius = 16.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = mono.textSecondary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (searchQuery.isBlank()) "No recent files yet" else "No matching files",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = mono.textPrimary,
                                )
                                Text(
                                    text = "Tap to open any document, media, archive, e-book, 3D model, or font.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mono.textSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredRecents.take(6), key = { it.uriString }) { item ->
                    RecentFileRowCard(
                        item = item,
                        onOpen = { onOpenRecentFile(item) },
                        onInspect = { onInspectRecentFile(item) },
                        onRemove = { onRemoveRecentFile(item) },
                    )
                }
            }
        }
    }

    // Category Details & Picker Bottom Sheet
    selectedCategorySheet?.let { category ->
        val exts = remember(category) { SupportedFormatsRegistry.supportedExtensionsForCategory(category) }
        val catRecents = remember(recentFiles, category) {
            recentFiles.filter { it.categoryName == category.name }
        }
        ModalBottomSheet(
            onDismissRequest = { selectedCategorySheet = null },
            containerColor = mono.cardSurface,
            contentColor = mono.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileTypeBadge(extension = exts.firstOrNull().orEmpty(), category = category, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(category.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Supported: ${exts.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = mono.textSecondary,
                        )
                    }
                }

                MonoSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_open_picker_${category.name.lowercase()}"),
                    onClick = {
                        val cat = category
                        selectedCategorySheet = null
                        onOpenCategoryPicker(cat)
                    },
                    elevated = true,
                    cornerRadius = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = mono.textPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Open ${category.title} File", style = MaterialTheme.typography.titleSmall)
                    }
                }

                if (catRecents.isNotEmpty()) {
                    Text("Recent in ${category.title}", style = MaterialTheme.typography.labelLarge, color = mono.textSecondary)
                    catRecents.take(4).forEach { rec ->
                        RecentFileRowCard(
                            item = rec,
                            onOpen = {
                                selectedCategorySheet = null
                                onOpenRecentFile(rec)
                            },
                            onInspect = {
                                selectedCategorySheet = null
                                onInspectRecentFile(rec)
                            },
                            onRemove = { onRemoveRecentFile(rec) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAllRecentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllRecentsSheet = false },
            containerColor = mono.cardSurface,
            contentColor = mono.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("All Recent Files (${recentFiles.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LazyColumn(
                    modifier = Modifier.height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recentFiles, key = { it.uriString }) { item ->
                        RecentFileRowCard(
                            item = item,
                            onOpen = {
                                showAllRecentsSheet = false
                                onOpenRecentFile(item)
                            },
                            onInspect = {
                                showAllRecentsSheet = false
                                onInspectRecentFile(item)
                            },
                            onRemove = { onRemoveRecentFile(item) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    MonoSurfaceCard(
        modifier = modifier
            .height(78.dp)
            .testTag(testTag),
        onClick = onClick,
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = mono.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = mono.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryTile(
    category: FileCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    MonoSurfaceCard(
        modifier = modifier
            .height(76.dp)
            .testTag("category_${category.name.lowercase()}"),
        onClick = onClick,
        cornerRadius = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = category.title,
                tint = mono.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = mono.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RecentFileRowCard(
    item: RecentFileEntity,
    onOpen: () -> Unit,
    onInspect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val category = runCatching { FileCategory.valueOf(item.categoryName) }.getOrDefault(FileCategory.UNKNOWN)
    var menuExpanded by remember { mutableStateOf(false) }

    MonoSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_item_${item.fileName}"),
        onClick = onOpen,
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTypeBadge(extension = item.extension, category = category, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = mono.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${FileStorageHelper.formatBytes(item.sizeBytes)} • ${item.extension.uppercase()} • ${FileStorageHelper.formatTimestamp(item.lastOpenedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "File options",
                        tint = mono.textSecondary,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Viewer") },
                        onClick = { menuExpanded = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Inspect Format") },
                        onClick = { menuExpanded = false; onInspect() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Recent") },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                }
            }
        }
    }
}
