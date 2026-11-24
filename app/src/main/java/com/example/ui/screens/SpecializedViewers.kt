package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.core.engine.DocumentBlock
import com.example.core.engine.ImageMetadataInfo
import com.example.core.engine.ParsedDocument
import com.example.core.engine.ParsedEbook
import com.example.core.engine.SheetData
import com.example.core.engine.SlideData
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoWhite
import java.io.File

@Composable
fun PdfPageCanvasViewer(
    bitmap: Bitmap?,
    currentPage: Int,
    totalPages: Int,
    requiresPassword: Boolean,
    isLoadingPage: Boolean,
    searchMatches: List<Int>,
    onUnlockWithPassword: (String) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var passwordInput by remember { mutableStateOf("") }
    var scale by remember(currentPage) { mutableFloatStateOf(1f) }
    var offsetX by remember(currentPage) { mutableFloatStateOf(0f) }
    var offsetY by remember(currentPage) { mutableFloatStateOf(0f) }

    if (requiresPassword) {
        MonoSurfaceCard(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = "Locked PDF", tint = mono.textPrimary, modifier = Modifier.size(36.dp))
                Text("Password-Protected PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Enter the PDF password to decrypt and view locally.", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("PDF Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pdf_password_input"),
                )
                Button(
                    onClick = { onUnlockWithPassword(passwordInput) },
                    modifier = Modifier.fillMaxWidth().testTag("pdf_unlock_button"),
                ) {
                    Text("Unlock PDF")
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (searchMatches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Matches:", style = MaterialTheme.typography.labelSmall, color = mono.textSecondary)
                searchMatches.forEach { pIdx ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (pIdx == currentPage) mono.textPrimary else mono.cardSurfaceElevated,
                        modifier = Modifier.clickable { onJumpToPage(pIdx) },
                    ) {
                        Text(
                            text = "P.${pIdx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pIdx == currentPage) mono.background else mono.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        // Document Canvas Area matching Screen 3 of screenshot
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(mono.cardSurface)
                .pointerInput(currentPage) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f; offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingPage || bitmap == null) {
                CircularProgressIndicator(color = mono.textPrimary)
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MonoWhite,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Floating Page Controls (<  1 / 12  >) matching Screen 3
            if (totalPages > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC1C1C20),
                        modifier = Modifier.size(44.dp),
                    ) {
                        IconButton(
                            onClick = onPreviousPage,
                            enabled = currentPage > 0,
                            modifier = Modifier.testTag("pdf_prev_page"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                tint = if (currentPage > 0) MonoWhite else MonoWhite.copy(alpha = 0.35f),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xCC2A2A2F),
                    ) {
                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelLarge,
                            color = MonoWhite,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC1C1C20),
                        modifier = Modifier.size(44.dp),
                    ) {
                        IconButton(
                            onClick = onNextPage,
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier.testTag("pdf_next_page"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Next page",
                                tint = if (currentPage < totalPages - 1) MonoWhite else MonoWhite.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageAndSvgViewer(
    file: File,
    formatId: String,
    bitmap: Bitmap?,
    rotationDeg: Int,
    metadata: ImageMetadataInfo?,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(mono.cardSurface)
                .border(1.dp, mono.cardBorder, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f; offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (formatId == "gif") {
                val gifLoader = remember {
                    ImageLoader.Builder(context)
                        .components {
                            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                            else add(GifDecoder.Factory())
                        }
                        .build()
                }
                AsyncImage(
                    model = file,
                    imageLoader = gifLoader,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                            rotationZ = rotationDeg.toFloat()
                        },
                )
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                            rotationZ = rotationDeg.toFloat()
                        },
                )
            } else {
                CircularProgressIndicator(color = mono.textPrimary)
            }
        }

        if (metadata != null) {
            MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${metadata.width} × ${metadata.height} px", style = MaterialTheme.typography.labelLarge, color = mono.textPrimary)
                    Text(metadata.exifItems.entries.take(2).joinToString(" • ") { "${it.key}: ${it.value}" }.ifBlank { metadata.mimeType },
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun RichDocumentViewer(
    doc: ParsedDocument,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var fontSize by remember { mutableFloatStateOf(15f) }
    val blocks = remember(doc, searchQuery) {
        if (searchQuery.isBlank()) doc.blocks
        else doc.blocks.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (doc.author.isNotBlank()) "By ${doc.author} • ${doc.blocks.size} blocks" else "${doc.blocks.size} blocks",
                style = MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { fontSize = (fontSize - 1.5f).coerceAtLeast(11f) }) {
                    Text("A-", style = MaterialTheme.typography.labelLarge, color = mono.textPrimary)
                }
                IconButton(onClick = { fontSize = (fontSize + 1.5f).coerceAtMost(24f) }) {
                    Text("A+", style = MaterialTheme.typography.labelLarge, color = mono.textPrimary)
                }
            }
        }

        MonoSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 16.dp) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(blocks) { block ->
                    when {
                        block.isHeading -> Text(
                            text = block.text,
                            style = when (block.headingLevel) {
                                1 -> MaterialTheme.typography.headlineSmall
                                2 -> MaterialTheme.typography.titleLarge
                                else -> MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.Bold,
                            color = mono.textPrimary,
                        )
                        block.isCode -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(mono.cardSurfaceElevated)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = block.text,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = (fontSize - 2f).sp,
                                color = mono.textPrimary,
                            )
                        }
                        else -> Text(
                            text = block.text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.48f).sp,
                            color = mono.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpreadsheetGridViewer(
    sheets: List<SheetData>,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var activeSheetIdx by remember { mutableIntStateOf(0) }
    val currentSheet = sheets.getOrNull(activeSheetIdx) ?: SheetData("Empty", emptyList())
    val maxCols = remember(currentSheet) { (currentSheet.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1) }
    val visibleRows = remember(currentSheet, searchQuery) {
        if (searchQuery.isBlank()) currentSheet.rows.take(500)
        else currentSheet.rows.filter { row -> row.any { it.contains(searchQuery, ignoreCase = true) } }.take(500)
    }
    val horizScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sheets.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sheets.forEachIndexed { idx, sheet ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (idx == activeSheetIdx) mono.textPrimary else mono.cardSurfaceElevated,
                        modifier = Modifier.clickable { activeSheetIdx = idx },
                    ) {
                        Text(
                            text = sheet.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (idx == activeSheetIdx) mono.background else mono.textPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        MonoSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Column Headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mono.cardSurfaceElevated)
                        .horizontalScroll(horizScroll)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.labelSmall,
                        color = mono.textMuted,
                        modifier = Modifier.width(42.dp).padding(horizontal = 8.dp),
                    )
                    for (c in 0 until maxCols) {
                        val colLetter = (('A'.code + (c % 26)).toChar()).toString() + if (c >= 26) "${c / 26}" else ""
                        Text(
                            text = colLetter,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = mono.textSecondary,
                            modifier = Modifier.width(120.dp).padding(horizontal = 8.dp),
                        )
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visibleRows) { rIdx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizScroll)
                                .border(0.5.dp, mono.cardBorder.copy(alpha = 0.5f))
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = "${rIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = mono.textMuted,
                                modifier = Modifier.width(42.dp).padding(horizontal = 8.dp),
                            )
                            for (c in 0 until maxCols) {
                                val cell = row.getOrNull(c).orEmpty()
                                val highlighted = searchQuery.isNotBlank() && cell.contains(searchQuery, ignoreCase = true)
                                Text(
                                    text = cell,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (rIdx == 0 || highlighted) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (highlighted) mono.textPrimary else mono.textPrimary.copy(alpha = 0.9f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(120.dp).padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresentationDeckViewer(
    slides: List<SlideData>,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var idx by remember { mutableIntStateOf(0) }
    val slide = slides.getOrNull(idx)

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonoSurfaceCard(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            elevated = true,
            cornerRadius = 18.dp,
        ) {
            if (slide == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No slides found.", color = mono.textSecondary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("SLIDE ${slide.number}", style = MaterialTheme.typography.labelSmall, color = mono.textMuted)
                    Text(slide.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = mono.textPrimary)
                    slide.bodyLines.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyLarge, color = mono.textPrimary)
                    }
                    if (slide.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Speaker Notes", style = MaterialTheme.typography.labelMedium, color = mono.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(slide.notes, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { if (idx > 0) idx-- }, enabled = idx > 0) {
                Text("Previous")
            }
            Text("${idx + 1} / ${slides.size.coerceAtLeast(1)}", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
            OutlinedButton(onClick = { if (idx < slides.lastIndex) idx++ }, enabled = idx < slides.lastIndex) {
                Text("Next")
            }
        }
    }
}

@Composable
fun EbookReaderViewer(
    ebook: ParsedEbook,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var chapterIdx by remember { mutableIntStateOf(0) }
    var fontSize by remember { mutableFloatStateOf(16f) }
    var readerPalette by remember { mutableIntStateOf(0) } // 0=Theme, 1=Sepia, 2=Light
    var showChapters by remember { mutableStateOf(false) }

    val chapter = ebook.chapters.getOrNull(chapterIdx)
    val bgColor = when (readerPalette) {
        1 -> Color(0xFFF4ECD8)
        2 -> Color(0xFFFAFAFA)
        else -> mono.cardSurface
    }
    val fgColor = when (readerPalette) {
        1 -> Color(0xFF3E3022)
        2 -> Color(0xFF141416)
        else -> mono.textPrimary
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showChapters = !showChapters }) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chapters (${ebook.chapters.size})")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = mono.cardSurfaceElevated,
                    modifier = Modifier.clickable { readerPalette = (readerPalette + 1) % 3 },
                ) {
                    Text(
                        text = when (readerPalette) { 1 -> "Sepia"; 2 -> "Paper"; else -> "Mono" },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = { fontSize = (fontSize - 1f).coerceAtLeast(12f) }) {
                    Text("A-", style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { fontSize = (fontSize + 1f).coerceAtMost(24f) }) {
                    Text("A+", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (showChapters) {
            MonoSurfaceCard(modifier = Modifier.fillMaxWidth().height(190.dp), cornerRadius = 14.dp) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    itemsIndexed(ebook.chapters) { idx, ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (idx == chapterIdx) mono.cardSurfaceElevated else Color.Transparent)
                                .clickable { chapterIdx = idx; showChapters = false }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text("${idx + 1}. ${ch.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(1.dp, mono.cardBorder, RoundedCornerShape(16.dp)),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (chapterIdx == 0 && ebook.coverBytes != null) {
                    item {
                        val coverBmp = remember(ebook.coverBytes) {
                            BitmapFactory.decodeByteArray(ebook.coverBytes, 0, ebook.coverBytes.size)
                        }
                        if (coverBmp != null) {
                            Image(
                                bitmap = coverBmp.asImageBitmap(),
                                contentDescription = "Book Cover",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                            )
                        }
                    }
                }
                if (chapter != null) {
                    item {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = fgColor,
                        )
                    }
                    val paras = if (searchQuery.isBlank()) chapter.paragraphs
                    else chapter.paragraphs.filter { it.contains(searchQuery, ignoreCase = true) }
                    items(paras) { p ->
                        Text(
                            text = p,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.55f).sp,
                            color = fgColor,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { if (chapterIdx > 0) chapterIdx-- }, enabled = chapterIdx > 0) { Text("Prev Chapter") }
            Text("Ch ${chapterIdx + 1}/${ebook.chapters.size.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { if (chapterIdx < ebook.chapters.lastIndex) chapterIdx++ }, enabled = chapterIdx < ebook.chapters.lastIndex) { Text("Next Chapter") }
        }
    }
}
