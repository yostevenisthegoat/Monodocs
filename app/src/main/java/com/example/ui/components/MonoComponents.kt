package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.FileCategory
import com.example.ui.theme.Badge3DCyan
import com.example.ui.theme.BadgeArchiveBronze
import com.example.ui.theme.BadgeAudioTeal
import com.example.ui.theme.BadgeCodeZinc
import com.example.ui.theme.BadgeDocBlue
import com.example.ui.theme.BadgeEbookIndigo
import com.example.ui.theme.BadgeFontCharcoal
import com.example.ui.theme.BadgeImageViolet
import com.example.ui.theme.BadgePdfRed
import com.example.ui.theme.BadgeSheetGreen
import com.example.ui.theme.BadgeSlideAmber
import com.example.ui.theme.BadgeVideoSlate
import com.example.ui.theme.LocalMonoColors
import com.example.ui.theme.MonoBlack
import com.example.ui.theme.MonoWhite

enum class MonoNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TOOLS("tools", "Tools", Icons.Filled.Build, Icons.Outlined.Build),
    CONVERTER("converter", "Converter", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MonoWaveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val mono = LocalMonoColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(mono.background)
            .drawBehind {
                val w = size.width
                val h = size.height

                // Flowing atmospheric Mono Ecosystem wave layers
                val wave1 = Path().apply {
                    moveTo(0f, h * 0.14f)
                    cubicTo(w * 0.35f, h * 0.04f, w * 0.65f, h * 0.28f, w, h * 0.16f)
                    lineTo(w, 0f)
                    lineTo(0f, 0f)
                    close()
                }
                drawPath(
                    path = wave1,
                    brush = Brush.verticalGradient(
                        colors = listOf(mono.wavePrimary.copy(alpha = 0.9f), mono.background.copy(alpha = 0.2f))
                    )
                )

                val wave2 = Path().apply {
                    moveTo(0f, h * 0.56f)
                    cubicTo(w * 0.28f, h * 0.44f, w * 0.72f, h * 0.70f, w, h * 0.50f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = wave2,
                    brush = Brush.linearGradient(
                        colors = listOf(mono.waveSecondary.copy(alpha = 0.75f), mono.background),
                        start = Offset(0f, h * 0.45f),
                        end = Offset(w, h)
                    )
                )
            },
        content = content
    )
}

@Composable
fun MonoDocsLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    inverted: Boolean = false,
) {
    val mono = LocalMonoColors.current
    val docColor = if (inverted) {
        if (mono.isDark) MonoBlack else MonoWhite
    } else {
        if (mono.isDark) MonoWhite else Color(0xFF141418)
    }
    val letterColor = if (inverted) {
        if (mono.isDark) MonoWhite else MonoBlack
    } else {
        if (mono.isDark) Color(0xFF101012) else MonoWhite
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val left = w * 0.14f
        val right = w * 0.86f
        val top = h * 0.08f
        val bottom = h * 0.92f
        val fold = w * 0.24f
        val radius = w * 0.11f

        val docPath = Path().apply {
            moveTo(left + radius, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom - radius)
            quadraticTo(right, bottom, right - radius, bottom)
            lineTo(left + radius, bottom)
            quadraticTo(left, bottom, left, bottom - radius)
            lineTo(left, top + radius)
            quadraticTo(left, top, left + radius, top)
            close()
        }
        drawPath(docPath, color = docColor)

        // Folded corner cutout line
        val foldPath = Path().apply {
            moveTo(right - fold, top)
            lineTo(right - fold, top + fold - radius * 0.3f)
            quadraticTo(right - fold, top + fold, right - fold + radius * 0.3f, top + fold)
            lineTo(right, top + fold)
        }
        drawPath(
            path = foldPath,
            color = letterColor.copy(alpha = 0.35f),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        )

        // Geometric "M" inside document
        val mLeft = w * 0.32f
        val mRight = w * 0.68f
        val mTop = h * 0.42f
        val mBottom = h * 0.74f
        val mCenterX = w * 0.50f
        val mMidY = h * 0.60f

        val mPath = Path().apply {
            moveTo(mLeft, mBottom)
            lineTo(mLeft, mTop)
            lineTo(mCenterX, mMidY)
            lineTo(mRight, mTop)
            lineTo(mRight, mBottom)
        }
        drawPath(
            path = mPath,
            color = letterColor,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Square)
        )
    }
}

@Composable
fun MonoEcosystemLogo(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalMonoColors.current.textPrimary,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Three slanted minimalist pillars matching Mono Ecosystem logo in screenshot
        val p1 = Path().apply {
            moveTo(w * 0.08f, h * 0.85f)
            lineTo(w * 0.28f, h * 0.20f)
            lineTo(w * 0.43f, h * 0.20f)
            lineTo(w * 0.23f, h * 0.85f)
            close()
        }
        val p2 = Path().apply {
            moveTo(w * 0.35f, h * 0.85f)
            lineTo(w * 0.55f, h * 0.20f)
            lineTo(w * 0.70f, h * 0.20f)
            lineTo(w * 0.50f, h * 0.85f)
            close()
        }
        val p3 = Path().apply {
            moveTo(w * 0.63f, h * 0.85f)
            lineTo(w * 0.78f, h * 0.38f)
            lineTo(w * 0.93f, h * 0.38f)
            lineTo(w * 0.78f, h * 0.85f)
            close()
        }
        drawPath(p1, tint)
        drawPath(p2, tint)
        drawPath(p3, tint.copy(alpha = 0.7f))
    }
}

@Composable
fun MonoSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevated: Boolean = false,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val mono = LocalMonoColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = if (elevated) {
        Brush.linearGradient(
            colors = listOf(mono.cardSurfaceElevated, mono.cardSurface)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(mono.cardSurface, mono.cardSurface)
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, mono.cardBorder, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        content = content
    )
}

@Composable
fun MonoBottomNavBar(
    currentTab: MonoNavTab,
    onTabSelected: (MonoNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mono.navBarSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, mono.cardBorder.copy(alpha = 0.6f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoNavTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val contentColor = if (selected) mono.textPrimary else mono.textMuted
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { onTabSelected(tab) }
                        .testTag("nav_${tab.route}")
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun FileTypeBadge(
    extension: String,
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val extLower = extension.lowercase()
    val bg = when {
        extLower == "pdf" -> BadgePdfRed
        category == FileCategory.DOCUMENTS -> BadgeDocBlue
        category == FileCategory.SPREADSHEETS -> BadgeSheetGreen
        category == FileCategory.PRESENTATIONS -> BadgeSlideAmber
        category == FileCategory.IMAGES -> BadgeImageViolet
        category == FileCategory.VIDEOS -> BadgeVideoSlate
        category == FileCategory.AUDIO -> BadgeAudioTeal
        category == FileCategory.ARCHIVES -> BadgeArchiveBronze
        category == FileCategory.EBOOKS -> BadgeEbookIndigo
        category == FileCategory.DEVELOPER -> BadgeCodeZinc
        category == FileCategory.MODELS_3D -> Badge3DCyan
        category == FileCategory.FONTS -> BadgeFontCharcoal
        else -> BadgeCodeZinc
    }

    val icon = categoryIcon(category)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (extLower == "pdf") {
            Text(
                text = "PDF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MonoWhite,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = category.title,
                tint = MonoWhite,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.DOCUMENTS -> Icons.Outlined.Description
    FileCategory.IMAGES -> Icons.Outlined.Image
    FileCategory.VIDEOS -> Icons.Outlined.VideoFile
    FileCategory.AUDIO -> Icons.Outlined.AudioFile
    FileCategory.ARCHIVES -> Icons.Outlined.FolderZip
    FileCategory.EBOOKS -> Icons.Outlined.Book
    FileCategory.SPREADSHEETS -> Icons.Outlined.TableChart
    FileCategory.PRESENTATIONS -> Icons.Outlined.Slideshow
    FileCategory.DEVELOPER -> Icons.Outlined.Code
    FileCategory.MODELS_3D -> Icons.Outlined.ViewInAr
    FileCategory.FONTS -> Icons.Outlined.FontDownload
    FileCategory.UNKNOWN -> Icons.Outlined.InsertDriveFile
}
