package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.engine.ArchiveListingResult
import com.example.core.engine.FontMetadataInfo
import com.example.core.engine.MediaMetadataInfo
import com.example.core.engine.Mesh3DData
import com.example.data.local.FileStorageHelper
import com.example.ui.components.MonoSurfaceCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMonoColors
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioPlayerViewer(
    file: File,
    meta: MediaMetadataInfo?,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(meta?.durationMs ?: 0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration > 0) durationMs = player.duration
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying, player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) durationMs = player.duration
            delay(250L)
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true, cornerRadius = 20.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(mono.cardSurfaceElevated)
                        .border(1.dp, mono.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(34.dp))
                }
                Text(
                    text = meta?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!meta?.artist.isNullOrBlank()) {
                    Text(meta!!.artist, style = MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
                }

                // Minimalist Waveform Bars
                Row(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progressRatio = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    for (i in 0 until 28) {
                        val barProgress = i / 28f
                        val h = (14 + ((i * 19 + if (isPlaying) (positionMs / 120).toInt() else 0) % 24)).dp
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(h)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (barProgress <= progressRatio) mono.textPrimary else mono.textMuted.copy(alpha = 0.4f))
                        )
                    }
                }

                Slider(
                    value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    onValueChange = { ratio ->
                        val target = (ratio * durationMs).toLong()
                        player.seekTo(target)
                        positionMs = target
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDurationMs(positionMs), style = MaterialTheme.typography.labelSmall, color = mono.textSecondary)
                    Text(formatDurationMs(durationMs), style = MaterialTheme.typography.labelSmall, color = mono.textSecondary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OutlinedButton(onClick = {
                        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                        player.setPlaybackSpeed(speed)
                    }) {
                        Text("${speed}x")
                    }
                    Surface(
                        shape = CircleShape,
                        color = mono.textPrimary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        IconButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            modifier = Modifier.testTag("audio_play_pause"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = mono.background,
                            )
                        }
                    }
                }
            }
        }

        if (meta != null) {
            MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Audio Properties", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("Bitrate: ${if (meta.bitrateBps > 0) "${meta.bitrateBps / 1000} kbps" else "Unknown"} • Duration: ${formatDurationMs(meta.durationMs)}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    if (meta.album.isNotBlank()) Text("Album: ${meta.album}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }
    }
}

@Composable
fun VideoPlayerViewer(
    file: File,
    meta: MediaMetadataInfo?,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (meta != null) {
            MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${meta.width} × ${meta.height}", style = MaterialTheme.typography.labelLarge)
                    Text("${formatDurationMs(meta.durationMs)} • ${if (meta.bitrateBps > 0) "${meta.bitrateBps / 1000} kbps" else ""}", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }
    }
}

@Composable
fun ArchiveBrowserViewer(
    listing: ArchiveListingResult,
    searchQuery: String,
    onUnlockArchive: (String) -> Unit,
    onExtractSelected: (Set<String>?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var password by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val visibleEntries = remember(listing, searchQuery) {
        if (searchQuery.isBlank()) listing.entries
        else listing.entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (listing.isPasswordProtected && listing.entries.isEmpty()) {
        MonoSurfaceCard(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(36.dp))
                Text("Encrypted Archive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Archive Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onUnlockArchive(password) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock Archive")
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listing.warningNote != null) {
            MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                Text(listing.warningNote, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary, modifier = Modifier.padding(12.dp))
            }
        }

        if (listing.isPasswordProtected) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (required for encrypted entries)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onExtractSelected(null, password.ifBlank { null }) },
                enabled = listing.entries.any { !it.isDirectory },
                modifier = Modifier.weight(1f).testTag("archive_extract_all"),
            ) {
                Text("Extract All")
            }
            OutlinedButton(
                onClick = { onExtractSelected(selected.toSet(), password.ifBlank { null }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("archive_extract_selected"),
            ) {
                Text("Extract (${selected.size})")
            }
        }

        MonoSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 16.dp) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(visibleEntries) { entry ->
                    val checked = entry.name in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !entry.isDirectory) {
                                if (checked) selected.remove(entry.name) else selected.add(entry.name)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!entry.isDirectory) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (it) selected.add(entry.name) else selected.remove(entry.name)
                            })
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                            contentDescription = null,
                            tint = mono.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!entry.isDirectory) {
                                Text(
                                    text = "${FileStorageHelper.formatBytes(entry.sizeBytes)}${if (entry.isEncrypted) " • Encrypted" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mono.textMuted,
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
fun CodeTextViewer(
    rawText: String,
    isJson: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var prettyJson by remember { mutableStateOf(isJson) }
    var wrapLines by remember { mutableStateOf(false) }

    val displayText = remember(rawText, prettyJson, isJson) {
        if (isJson && prettyJson) {
            runCatching {
                val trimmed = rawText.trim()
                if (trimmed.startsWith("{")) org.json.JSONObject(trimmed).toString(2)
                else if (trimmed.startsWith("[")) org.json.JSONArray(trimmed).toString(2)
                else rawText
            }.getOrDefault(rawText)
        } else rawText
    }
    val lines = remember(displayText, searchQuery) {
        val all = displayText.lines()
        if (searchQuery.isBlank()) all.mapIndexed { i, s -> (i + 1) to s }
        else all.mapIndexedNotNull { i, s -> if (s.contains(searchQuery, ignoreCase = true)) (i + 1) to s else null }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isJson) {
                FilterChip(selected = prettyJson, onClick = { prettyJson = !prettyJson }, label = { Text("Pretty JSON") })
            }
            FilterChip(selected = wrapLines, onClick = { wrapLines = !wrapLines }, label = { Text("Wrap Lines") })
        }

        MonoSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 16.dp) {
            val hScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!wrapLines) Modifier.horizontalScroll(hScroll) else Modifier)
                    .padding(12.dp),
            ) {
                items(lines) { (lineNum, text) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "%4d  ".format(lineNum),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = mono.textMuted,
                        )
                        Text(
                            text = text,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            color = mono.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Mesh3DViewer(
    mesh: Mesh3DData,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var rotY by remember { mutableFloatStateOf(0.55f) }
    var rotX by remember { mutableFloatStateOf(0.35f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var autoRotate by remember { mutableStateOf(true) }
    var shaded by remember { mutableStateOf(true) }

    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            rotY += 0.015f
            delay(16L)
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = autoRotate, onClick = { autoRotate = !autoRotate }, label = { Text("Auto-Rotate") })
            FilterChip(selected = shaded, onClick = { shaded = !shaded }, label = { Text(if (shaded) "Shaded" else "Wireframe") })
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(mono.cardSurface)
                .border(1.dp, mono.cardBorder, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        autoRotate = false
                        rotY += drag.x * 0.01f
                        rotX = (rotX + drag.y * 0.01f).coerceIn(-1.4f, 1.4f)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        zoom = (zoom * z).coerceIn(0.4f, 3.5f)
                    }
                },
        ) {
            val lineColor = mono.textPrimary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scalePx = minOf(size.width, size.height) * 0.36f * zoom
                val cosY = cos(rotY); val sinY = sin(rotY)
                val cosX = cos(rotX); val sinX = sin(rotX)

                val projected = mesh.vertices.map { v ->
                    val x1 = v.x * cosY - v.z * sinY
                    val z1 = v.x * sinY + v.z * cosY
                    val y2 = v.y * cosX - z1 * sinX
                    val z2 = v.y * sinX + z1 * cosX
                    val perspective = 3.2f / (3.2f - z2).coerceAtLeast(0.5f)
                    Triple(cx + x1 * scalePx * perspective, cy - y2 * scalePx * perspective, z2)
                }

                val sortedFaces = if (shaded) {
                    mesh.faces.sortedBy { f ->
                        (projected[f.a].third + projected[f.b].third + projected[f.c].third) / 3f
                    }
                } else mesh.faces

                val path = Path()
                sortedFaces.forEach { f ->
                    val pA = projected[f.a]
                    val pB = projected[f.b]
                    val pC = projected[f.c]
                    path.reset()
                    path.moveTo(pA.first, pA.second)
                    path.lineTo(pB.first, pB.second)
                    path.lineTo(pC.first, pC.second)
                    path.close()
                    if (shaded) {
                        val depth = ((pA.third + pB.third + pC.third) / 3f + 1.2f) / 2.4f
                        val shade = depth.coerceIn(0.18f, 0.88f)
                        drawPath(path, color = lineColor.copy(alpha = shade * 0.35f))
                    }
                    drawPath(path, color = lineColor.copy(alpha = 0.55f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
                }
            }
        }

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(mesh.formatLabel, style = MaterialTheme.typography.labelLarge)
                Text("${mesh.vertexCount} vertices • ${mesh.triangleCount} triangles", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
        }
    }
}

@Composable
fun FontSpecimenViewer(
    fontInfo: FontMetadataInfo,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    var sampleText by remember { mutableStateOf("The quick brown fox jumps over the lazy dog. 0123456789") }
    var fontSizeSp by remember { mutableFloatStateOf(28f) }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true, cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fontInfo.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${fontInfo.familyName} • ${fontInfo.subfamily}", style = MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
                Text(
                    text = buildString {
                        if (fontInfo.numGlyphs > 0) append("${fontInfo.numGlyphs} glyphs • ")
                        append("${fontInfo.unitsPerEm} UPM")
                        if (fontInfo.version.isNotBlank()) append(" • ${fontInfo.version}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textMuted,
                )
            }
        }

        OutlinedTextField(
            value = sampleText,
            onValueChange = { sampleText = it },
            label = { Text("Live Font Preview Text") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Size ${fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelMedium)
            Slider(value = fontSizeSp, onValueChange = { fontSizeSp = it }, valueRange = 14f..56f, modifier = Modifier.weight(1f))
        }

        val textColor = mono.textPrimary.toArgb()
        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            AndroidView(
                factory = { ctx ->
                    android.widget.TextView(ctx).apply {
                        setPadding(32, 32, 32, 32)
                    }
                },
                update = { tv ->
                    tv.typeface = fontInfo.typeface
                    tv.textSize = fontSizeSp
                    tv.setTextColor(textColor)
                    tv.text = sampleText.ifBlank { "Mono Docs" }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MonoSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            AndroidView(
                factory = { ctx ->
                    android.widget.TextView(ctx).apply {
                        setPadding(32, 24, 32, 24)
                    }
                },
                update = { tv ->
                    tv.typeface = fontInfo.typeface
                    tv.textSize = 20f
                    tv.setTextColor(textColor)
                    tv.text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !@#\$%^&*()_+-=[]{}|;:',.<>/?"
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
