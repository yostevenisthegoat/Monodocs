package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.core.engine.ArchiveEngine
import com.example.core.engine.ArchiveListingResult
import com.example.core.engine.FontMetadataInfo
import com.example.core.engine.ImageEngine
import com.example.core.engine.ImageMetadataInfo
import com.example.core.engine.MediaEngine
import com.example.core.engine.MediaMetadataInfo
import com.example.core.engine.Mesh3DData
import com.example.core.engine.Model3DAndFontEngine
import com.example.core.engine.OfficeAndEbookEngine
import com.example.core.engine.ParsedDocument
import com.example.core.engine.ParsedEbook
import com.example.core.engine.PdfEngine
import com.example.core.engine.PdfMetadataInfo
import com.example.core.engine.SheetData
import com.example.core.engine.SlideData
import com.example.core.model.FileInspectionResult
import com.example.data.local.FileStorageHelper
import com.example.ui.components.FileTypeBadge
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.components.MonoWaveBackground
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoWarning
import kotlinx.coroutines.launch
import java.io.File

private sealed interface ViewerPayload {
    data object Loading : ViewerPayload
    data class Error(val message: String) : ViewerPayload
    data class Pdf(val meta: PdfMetadataInfo, val unlockedFile: File?) : ViewerPayload
    data class Image(val bitmap: Bitmap, val meta: ImageMetadataInfo) : ViewerPayload
    data class RichDoc(val doc: ParsedDocument) : ViewerPayload
    data class Spreadsheet(val sheets: List<SheetData>) : ViewerPayload
    data class Presentation(val slides: List<SlideData>) : ViewerPayload
    data class Ebook(val book: ParsedEbook) : ViewerPayload
    data class ComicPages(val imageFiles: List<File>) : ViewerPayload
    data class Audio(val meta: MediaMetadataInfo) : ViewerPayload
    data class Video(val meta: MediaMetadataInfo) : ViewerPayload
    data class Archive(val listing: ArchiveListingResult) : ViewerPayload
    data class Code(val text: String, val isJson: Boolean) : ViewerPayload
    data class Model3D(val mesh: Mesh3DData) : ViewerPayload
    data class Font(val info: FontMetadataInfo) : ViewerPayload
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    inspection: FileInspectionResult,
    localFile: File,
    onBack: () -> Unit,
    onOpenInConverter: (FileInspectionResult, File) -> Unit,
    onOpenInInspector: (FileInspectionResult) -> Unit,
) {
    BackHandler(onBack = onBack)

    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var payload by remember(localFile) { mutableStateOf<ViewerPayload>(ViewerPayload.Loading) }
    var passwordAttempt by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTopMenu by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // PDF-specific state
    var pdfPageIndex by remember { mutableIntStateOf(0) }
    var pdfRotation by remember { mutableIntStateOf(0) }
    var pdfPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfLoadingPage by remember { mutableStateOf(false) }
    var pdfMatches by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Image rotation state
    var imageRotation by remember { mutableIntStateOf(0) }
    // Comic page state
    var comicPageIndex by remember { mutableIntStateOf(0) }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(inspection.mimeType)
    ) { targetUri ->
        if (targetUri != null) {
            scope.launch {
                runCatching { FileStorageHelper.saveFileToUri(context, localFile, targetUri) }
                    .onSuccess { snackbarHostState.showSnackbar("Saved copy locally.") }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not save file.") }
            }
        }
    }

    val extractDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            scope.launch {
                val tempOut = File(FileStorageHelper.workDir(context), "extract_${System.currentTimeMillis()}")
                runCatching {
                    val files = ArchiveEngine.extractEntries(localFile, inspection.detectedFormatId, null, tempOut, passwordAttempt)
                    files.forEach { f ->
                        FileStorageHelper.saveFileToTreeUri(context, treeUri, f, f.name, "*/*")
                    }
                    files.size
                }.onSuccess { count ->
                    snackbarHostState.showSnackbar("Extracted $count file(s) to selected folder.")
                }.onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "Extraction failed.")
                }
                tempOut.deleteRecursively()
            }
        }
    }

    // Load Viewer Payload
    LaunchedEffect(localFile, inspection.detectedFormatId, passwordAttempt) {
        payload = ViewerPayload.Loading
        val fmt = inspection.detectedFormatId
        val result = runCatching {
            when (fmt) {
                "pdf" -> {
                    val meta = PdfEngine.inspectPdf(localFile, passwordAttempt)
                    if (meta.requiresPassword) {
                        ViewerPayload.Pdf(meta, null)
                    } else {
                        val unlocked = PdfEngine.ensureUnlockedPdfFile(localFile, passwordAttempt, FileStorageHelper.workDir(context))
                        ViewerPayload.Pdf(meta, unlocked)
                    }
                }
                "jpg", "png", "webp", "gif", "bmp", "ico", "svg", "heic", "avif" -> {
                    val bmp = ImageEngine.decodeBitmap(localFile, fmt)
                    val meta = ImageEngine.inspectMetadata(localFile, fmt)
                    ViewerPayload.Image(bmp, meta)
                }
                "docx" -> ViewerPayload.RichDoc(OfficeAndEbookEngine.parseDocx(localFile))
                "odt" -> ViewerPayload.RichDoc(OfficeAndEbookEngine.parseOdt(localFile))
                "rtf" -> ViewerPayload.RichDoc(OfficeAndEbookEngine.parseRtf(localFile))
                "html" -> ViewerPayload.RichDoc(OfficeAndEbookEngine.parseHtmlToBlocks(localFile.readText(), inspection.fileName))
                "md" -> ViewerPayload.RichDoc(OfficeAndEbookEngine.parseMarkdownToBlocks(localFile.readText(), inspection.fileName))
                "txt" -> ViewerPayload.Code(localFile.readText(), isJson = false)
                "json", "xml", "yaml", "toml", "sql", "code" -> ViewerPayload.Code(localFile.readText(), isJson = fmt == "json")
                "xlsx", "ods", "csv", "tsv" -> ViewerPayload.Spreadsheet(OfficeAndEbookEngine.parseSpreadsheet(localFile, fmt))
                "pptx", "odp" -> ViewerPayload.Presentation(OfficeAndEbookEngine.parsePresentation(localFile, fmt))
                "epub", "fb2", "mobi" -> ViewerPayload.Ebook(OfficeAndEbookEngine.parseEbook(localFile, fmt))
                "cbz" -> {
                    val cbzDir = File(FileStorageHelper.workDir(context), "cbz_view_${System.currentTimeMillis()}")
                    val imgs = ArchiveEngine.extractEntries(localFile, "zip", null, cbzDir)
                        .filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                        .sortedBy { it.name }
                    ViewerPayload.ComicPages(imgs)
                }
                "mp3", "wav", "flac", "aac", "ogg", "opus", "m4a" -> ViewerPayload.Audio(MediaEngine.inspectMedia(localFile))
                "mp4", "mkv", "mov", "webm", "3gp" -> ViewerPayload.Video(MediaEngine.inspectMedia(localFile))
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar" ->
                    ViewerPayload.Archive(ArchiveEngine.listEntries(localFile, fmt, passwordAttempt))
                "obj", "stl", "glb" -> ViewerPayload.Model3D(Model3DAndFontEngine.parse3DModel(localFile, fmt))
                "ttf", "otf" -> ViewerPayload.Font(Model3DAndFontEngine.parseFont(localFile))
                else -> ViewerPayload.Error("This file format is not currently supported by Mono Docs.")
            }
        }.getOrElse { err ->
            ViewerPayload.Error(err.message ?: "Mono Docs couldn't read this file. It may be damaged.")
        }
        payload = result
    }

    // Render current PDF page when pageIndex or rotation changes
    val pdfState = payload as? ViewerPayload.Pdf
    LaunchedEffect(pdfState?.unlockedFile, pdfPageIndex, pdfRotation) {
        val unlocked = pdfState?.unlockedFile ?: return@LaunchedEffect
        pdfLoadingPage = true
        runCatching {
            PdfEngine.renderPageBitmap(unlocked, pdfPageIndex, targetWidthPx = 1240, rotationDegrees = pdfRotation)
        }.onSuccess { bmp ->
            pdfPageBitmap = bmp
        }
        pdfLoadingPage = false
    }

    // Search inside PDF
    LaunchedEffect(searchQuery, pdfState?.unlockedFile) {
        val unlocked = pdfState?.unlockedFile ?: return@LaunchedEffect
        if (searchQuery.length >= 2) {
            pdfMatches = runCatching { PdfEngine.searchPages(unlocked, searchQuery) }.getOrDefault(emptyList())
        } else {
            pdfMatches = emptyList()
        }
    }

    MonoWaveBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = mono.textPrimary)
                    }
                    Row {
                        IconButton(
                            onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" },
                            modifier = Modifier.testTag("viewer_search_button"),
                        ) {
                            Icon(
                                imageVector = if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = "Search inside file",
                                tint = mono.textPrimary,
                            )
                        }
                        Box {
                            IconButton(onClick = { showTopMenu = true }, modifier = Modifier.testTag("viewer_more_menu")) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More actions", tint = mono.textPrimary)
                            }
                            DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Inspect File Signature") },
                                    onClick = { showTopMenu = false; onOpenInInspector(inspection) },
                                )
                                if (inspection.supportedOutputs.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Convert File") },
                                        onClick = { showTopMenu = false; onOpenInConverter(inspection, localFile) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Save Copy As...") },
                                    onClick = { showTopMenu = false; saveCopyLauncher.launch(inspection.fileName) },
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Bottom 3 Action Tiles matching Screen 3 of screenshot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ViewerBottomAction(
                        label = "Share",
                        icon = Icons.Outlined.Share,
                        onClick = { FileStorageHelper.shareFile(context, localFile, inspection.mimeType) },
                        modifier = Modifier.weight(1f).testTag("viewer_share_btn"),
                    )
                    val isRotatable = inspection.detectedFormatId == "pdf" || inspection.category == com.example.core.model.FileCategory.IMAGES
                    ViewerBottomAction(
                        label = if (isRotatable) "Rotate" else "Convert",
                        icon = if (isRotatable) Icons.Outlined.Refresh else Icons.Outlined.SwapHoriz,
                        onClick = {
                            if (inspection.detectedFormatId == "pdf") {
                                pdfRotation = (pdfRotation + 90) % 360
                            } else if (inspection.category == com.example.core.model.FileCategory.IMAGES) {
                                imageRotation = (imageRotation + 90) % 360
                            } else {
                                onOpenInConverter(inspection, localFile)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("viewer_middle_btn"),
                    )
                    ViewerBottomAction(
                        label = "More",
                        icon = Icons.Outlined.MoreHoriz,
                        onClick = { showMoreSheet = true },
                        modifier = Modifier.weight(1f).testTag("viewer_more_btn"),
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // File Header Row matching Screen 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FileTypeBadge(
                        extension = inspection.detectedExtension,
                        category = inspection.category,
                        size = 54.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = inspection.fileName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = mono.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${FileStorageHelper.formatBytes(inspection.sizeBytes)} • ${inspection.detectedExtension.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mono.textSecondary,
                        )
                    }
                }

                if (inspection.hasExtensionMismatch) {
                    MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MonoWarning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Extension mismatch: '.${inspection.declaredExtension}' is actually ${inspection.detectedFormatName}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = mono.textPrimary,
                            )
                        }
                    }
                }

                // Pill Save / Export Button matching Screen 3 ("Download" pill)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = mono.pillButton,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { saveCopyLauncher.launch(inspection.fileName) }
                        .testTag("viewer_save_copy_pill"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Copy", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                    }
                }

                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search inside file...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("viewer_search_input"),
                    )
                }

                // Specialized Content Area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val p = payload) {
                        ViewerPayload.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = mono.textPrimary)
                        }
                        is ViewerPayload.Error -> MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(40.dp))
                                Text(p.message, style = MaterialTheme.typography.bodyLarge, color = mono.textPrimary)
                                OutlinedButton(onClick = { onOpenInInspector(inspection) }) {
                                    Text("Inspect File Signature")
                                }
                            }
                        }
                        is ViewerPayload.Pdf -> PdfPageCanvasViewer(
                            bitmap = pdfPageBitmap,
                            currentPage = pdfPageIndex,
                            totalPages = p.meta.pageCount,
                            requiresPassword = p.meta.requiresPassword,
                            isLoadingPage = pdfLoadingPage,
                            searchMatches = pdfMatches,
                            onUnlockWithPassword = { pwd -> passwordAttempt = pwd },
                            onPreviousPage = { if (pdfPageIndex > 0) pdfPageIndex-- },
                            onNextPage = { if (pdfPageIndex < p.meta.pageCount - 1) pdfPageIndex++ },
                            onJumpToPage = { pdfPageIndex = it },
                        )
                        is ViewerPayload.Image -> ImageAndSvgViewer(
                            file = localFile,
                            formatId = inspection.detectedFormatId,
                            bitmap = p.bitmap,
                            rotationDeg = imageRotation,
                            metadata = p.meta,
                        )
                        is ViewerPayload.RichDoc -> RichDocumentViewer(doc = p.doc, searchQuery = searchQuery)
                        is ViewerPayload.Spreadsheet -> SpreadsheetGridViewer(sheets = p.sheets, searchQuery = searchQuery)
                        is ViewerPayload.Presentation -> PresentationDeckViewer(slides = p.slides)
                        is ViewerPayload.Ebook -> EbookReaderViewer(ebook = p.book, searchQuery = searchQuery)
                        is ViewerPayload.ComicPages -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pageFile = p.imageFiles.getOrNull(comicPageIndex)
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(mono.cardSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (pageFile != null) {
                                    val bmp = remember(pageFile) { BitmapFactory.decodeFile(pageFile.absolutePath) }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Comic page ${comicPageIndex + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                } else Text("No image pages in CBZ.", color = mono.textSecondary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { if (comicPageIndex > 0) comicPageIndex-- }, enabled = comicPageIndex > 0) { Text("Prev") }
                                Text("${comicPageIndex + 1} / ${p.imageFiles.size.coerceAtLeast(1)}")
                                OutlinedButton(onClick = { if (comicPageIndex < p.imageFiles.lastIndex) comicPageIndex++ }, enabled = comicPageIndex < p.imageFiles.lastIndex) { Text("Next") }
                            }
                        }
                        is ViewerPayload.Audio -> AudioPlayerViewer(file = localFile, meta = p.meta)
                        is ViewerPayload.Video -> VideoPlayerViewer(file = localFile, meta = p.meta)
                        is ViewerPayload.Archive -> ArchiveBrowserViewer(
                            listing = p.listing,
                            searchQuery = searchQuery,
                            onUnlockArchive = { pwd -> passwordAttempt = pwd },
                            onExtractSelected = { selectedNames, pwd ->
                                if (selectedNames == null) {
                                    extractDirLauncher.launch(null)
                                } else {
                                    scope.launch {
                                        val outDir = File(FileStorageHelper.outputsDir(context), "Extracted_${localFile.nameWithoutExtension}")
                                        runCatching {
                                            ArchiveEngine.extractEntries(localFile, inspection.detectedFormatId, selectedNames, outDir, pwd)
                                        }.onSuccess { extracted ->
                                            snackbarHostState.showSnackbar("Extracted ${extracted.size} file(s) to ${outDir.name}.")
                                        }.onFailure {
                                            snackbarHostState.showSnackbar(it.message ?: "Extraction failed.")
                                        }
                                    }
                                }
                            },
                        )
                        is ViewerPayload.Code -> CodeTextViewer(rawText = p.text, isJson = p.isJson, searchQuery = searchQuery)
                        is ViewerPayload.Model3D -> Mesh3DViewer(mesh = p.mesh)
                        is ViewerPayload.Font -> FontSpecimenViewer(fontInfo = p.info)
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = mono.cardSurface,
            contentColor = mono.textPrimary,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("File Details & Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Format: ${inspection.detectedFormatName}", style = MaterialTheme.typography.bodyMedium)
                Text("MIME: ${inspection.mimeType}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                Text("Signature: ${inspection.magicHexPreview.ifBlank { "Text/Empty" }}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)

                if (inspection.supportedOutputs.isNotEmpty()) {
                    Button(
                        onClick = { showMoreSheet = false; onOpenInConverter(inspection, localFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Convert to ${inspection.supportedOutputs.joinToString(", ") { it.uppercase() }}")
                    }
                }
                OutlinedButton(
                    onClick = { showMoreSheet = false; onOpenInInspector(inspection) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Full File Inspector")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ViewerBottomAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    MonoSurfaceCard(
        modifier = modifier.height(64.dp),
        onClick = onClick,
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = mono.textPrimary)
        }
    }
}
