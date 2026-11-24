package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.model.FileCategory
import com.example.ui.MonoDocsViewModel
import com.example.ui.components.MonoBottomNavBar
import com.example.ui.components.MonoNavTab
import com.example.ui.components.MonoWaveBackground
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.FileInspectorModal
import com.example.ui.screens.FileViewerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.ToolCategoryTab
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.MonoDocsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialViewUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            val app = application as MonoDocsApplication
            val viewModel: MonoDocsViewModel = viewModel(factory = MonoDocsViewModel.Factory(app))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = if (settings.systemThemeEnabled) systemDark else settings.darkModeEnabled

            MonoDocsTheme(darkTheme = useDarkTheme) {
                MonoDocsRoot(
                    viewModel = viewModel,
                    initialExternalUri = initialViewUri,
                )
            }
        }
    }
}

@Composable
private fun MonoDocsRoot(
    viewModel: MonoDocsViewModel,
    initialExternalUri: Uri?,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val history by viewModel.conversionHistory.collectAsStateWithLifecycle()
    val activeViewer by viewModel.activeViewer.collectAsStateWithLifecycle()
    val inspectorResult by viewModel.inspectorResult.collectAsStateWithLifecycle()
    val converterPreselected by viewModel.converterPreselected.collectAsStateWithLifecycle()
    val uiError by viewModel.uiError.collectAsStateWithLifecycle()

    var showSplash by remember { mutableStateOf(initialExternalUri == null) }
    var toolsInitialTab by remember { mutableStateOf(ToolCategoryTab.PDF) }
    var converterBatchInitial by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialExternalUri) {
        if (initialExternalUri != null) {
            viewModel.openUriInViewer(context, initialExternalUri)
        }
    }

    LaunchedEffect(uiError) {
        uiError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val openAnyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.openUriInViewer(context, uri)
        }
    }

    val openPhotoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.openUriInViewer(context, uri)
    }

    val scanFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspectUri(context, uri)
    }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val viewerSession = activeViewer
    if (viewerSession != null) {
        FileViewerScreen(
            inspection = viewerSession.inspection,
            localFile = viewerSession.localFile,
            onBack = { viewModel.closeViewer() },
            onOpenInConverter = { insp, file ->
                viewModel.closeViewer()
                viewModel.preselectForConverter(insp, file)
            },
            onOpenInInspector = { insp ->
                viewModel.showInspectorResult(insp)
            },
        )
    } else {
        val navController = rememberNavController()
        val navBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStack?.destination?.route ?: MonoNavTab.HOME.route
        val currentTab = MonoNavTab.entries.firstOrNull { it.route == currentRoute } ?: MonoNavTab.HOME

        LaunchedEffect(converterPreselected) {
            if (converterPreselected != null && currentRoute != MonoNavTab.CONVERTER.route) {
                converterBatchInitial = false
                navController.navigate(MonoNavTab.CONVERTER.route) {
                    launchSingleTop = true
                }
            }
        }

        val navigateToTab: (MonoNavTab) -> Unit = { tab ->
            navController.navigate(tab.route) {
                popUpTo(MonoNavTab.HOME.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        MonoWaveBackground {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    MonoBottomNavBar(
                        currentTab = currentTab,
                        onTabSelected = navigateToTab,
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = MonoNavTab.HOME.route,
                    ) {
                        composable(MonoNavTab.HOME.route) {
                            HomeScreen(
                                recentFiles = recentFiles,
                                onOpenFilePicker = { openAnyFileLauncher.launch(arrayOf("*/*")) },
                                onOpenConverter = {
                                    converterBatchInitial = false
                                    navigateToTab(MonoNavTab.CONVERTER)
                                },
                                onOpenCompress = {
                                    toolsInitialTab = ToolCategoryTab.ARCHIVE
                                    navigateToTab(MonoNavTab.TOOLS)
                                },
                                onOpenExtractArchive = {
                                    toolsInitialTab = ToolCategoryTab.ARCHIVE
                                    navigateToTab(MonoNavTab.TOOLS)
                                },
                                onOpenScanInspector = { scanFileLauncher.launch(arrayOf("*/*")) },
                                onOpenBatchConvert = {
                                    converterBatchInitial = true
                                    navigateToTab(MonoNavTab.CONVERTER)
                                },
                                onOpenToolsTab = {
                                    toolsInitialTab = ToolCategoryTab.PDF
                                    navigateToTab(MonoNavTab.TOOLS)
                                },
                                onOpenSettingsTab = { navigateToTab(MonoNavTab.SETTINGS) },
                                onOpenRecentFile = { item ->
                                    viewModel.openUriInViewer(context, Uri.parse(item.uriString))
                                },
                                onInspectRecentFile = { item ->
                                    viewModel.inspectUri(context, Uri.parse(item.uriString))
                                },
                                onRemoveRecentFile = { item -> viewModel.removeRecentFile(item) },
                                onOpenCategoryPicker = { category ->
                                    when (category) {
                                        FileCategory.IMAGES -> openPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                        FileCategory.VIDEOS -> openAnyFileLauncher.launch(arrayOf("video/*"))
                                        FileCategory.AUDIO -> openAnyFileLauncher.launch(arrayOf("audio/*"))
                                        else -> openAnyFileLauncher.launch(arrayOf("*/*"))
                                    }
                                },
                            )
                        }

                        composable(MonoNavTab.TOOLS.route) {
                            ToolsScreen(
                                initialTab = toolsInitialTab,
                                onOpenInspector = { insp -> viewModel.showInspectorResult(insp) },
                                onOpenBatchConvert = {
                                    converterBatchInitial = true
                                    navigateToTab(MonoNavTab.CONVERTER)
                                },
                                onOpenFileInViewer = { file, name ->
                                    viewModel.openLocalFileInViewer(file, name)
                                },
                            )
                        }

                        composable(MonoNavTab.CONVERTER.route) {
                            ConverterScreen(
                                settings = settings,
                                initialBatchMode = converterBatchInitial,
                                preselectedFile = converterPreselected,
                                onClearPreselected = { viewModel.clearPreselectedConverter() },
                                onRecordConversion = { entry -> viewModel.recordConversion(entry) },
                                onOpenConvertedFile = { file, name ->
                                    viewModel.openLocalFileInViewer(file, name)
                                },
                            )
                        }

                        composable(MonoNavTab.SETTINGS.route) {
                            SettingsScreen(
                                settings = settings,
                                recentFiles = recentFiles,
                                historyCount = history.size,
                                onToggleDarkMode = { viewModel.setDarkMode(it) },
                                onToggleSystemTheme = { viewModel.setSystemTheme(it) },
                                onSetSaveLocation = { uri, label -> viewModel.setDefaultSaveLocation(uri, label) },
                                onSetConversionQuality = { viewModel.setConversionQuality(it) },
                                onSetVideoQuality = { viewModel.setVideoQuality(it) },
                                onSetImageCompression = { viewModel.setImageCompression(it) },
                                onToggleAutoDetect = { viewModel.setAutoDetect(it) },
                                onRemoveRecentFile = { viewModel.removeRecentFile(it) },
                                onClearRecentFiles = { viewModel.clearRecentFiles() },
                                onClearConversionHistory = { viewModel.clearConversionHistory() },
                            )
                        }
                    }
                }
            }
        }
    }

    inspectorResult?.let { insp ->
        FileInspectorModal(
            inspection = insp,
            onDismiss = { viewModel.dismissInspector() },
            onOpenInViewer = { uri -> viewModel.openUriInViewer(context, uri) },
            onOpenInConverter = { uri ->
                viewModel.preselectUriForConverter(context, uri)
            },
        )
    }
}
