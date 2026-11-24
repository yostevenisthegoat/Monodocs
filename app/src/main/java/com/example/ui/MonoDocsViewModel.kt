package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MonoDocsApplication
import com.example.core.detector.FileSignatureInspector
import com.example.core.model.FileInspectionResult
import com.example.data.local.ConversionHistoryEntity
import com.example.data.local.ConversionQuality
import com.example.data.local.FileStorageHelper
import com.example.data.local.MonoDocsRepository
import com.example.data.local.MonoSettingsState
import com.example.data.local.PreferencesRepository
import com.example.data.local.RecentFileEntity
import com.example.data.local.VideoQualityPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ActiveViewerSession(
    val inspection: FileInspectionResult,
    val localFile: File,
)

class MonoDocsViewModel(
    application: Application,
    private val repository: MonoDocsRepository,
    private val preferences: PreferencesRepository,
) : AndroidViewModel(application) {

    val settings: StateFlow<MonoSettingsState> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonoSettingsState(),
    )

    val recentFiles: StateFlow<List<RecentFileEntity>> = repository.recentFiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val conversionHistory: StateFlow<List<ConversionHistoryEntity>> = repository.conversionHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _activeViewer = MutableStateFlow<ActiveViewerSession?>(null)
    val activeViewer: StateFlow<ActiveViewerSession?> = _activeViewer.asStateFlow()

    private val _inspectorResult = MutableStateFlow<FileInspectionResult?>(null)
    val inspectorResult: StateFlow<FileInspectionResult?> = _inspectorResult.asStateFlow()

    private val _converterPreselected = MutableStateFlow<Pair<FileInspectionResult, File>?>(null)
    val converterPreselected: StateFlow<Pair<FileInspectionResult, File>?> = _converterPreselected.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    fun openUriInViewer(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val insp = FileSignatureInspector.inspectUri(context, uri)
                val localFile = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                repository.recordOpenedFile(
                    RecentFileEntity(
                        uriString = uri.toString(),
                        fileName = insp.fileName,
                        extension = insp.detectedExtension.ifBlank { insp.declaredExtension },
                        mimeType = insp.mimeType,
                        categoryName = insp.category.name,
                        sizeBytes = insp.sizeBytes,
                        lastOpenedAt = System.currentTimeMillis(),
                    )
                )
                ActiveViewerSession(insp, localFile)
            }.onSuccess { session ->
                _activeViewer.value = session
            }.onFailure { err ->
                _uiError.value = err.message ?: "Mono Docs couldn't read this file. It may be damaged."
            }
        }
    }

    fun openLocalFileInViewer(file: File, displayName: String) {
        viewModelScope.launch {
            runCatching {
                val insp = FileSignatureInspector.inspectFile(file).copy(fileName = displayName)
                ActiveViewerSession(insp, file)
            }.onSuccess { session ->
                _activeViewer.value = session
            }.onFailure { err ->
                _uiError.value = err.message ?: "Mono Docs couldn't read this file. It may be damaged."
            }
        }
    }

    fun closeViewer() {
        _activeViewer.value = null
    }

    fun inspectUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                FileSignatureInspector.inspectUri(context, uri, computeHash = true)
            }.onSuccess { res ->
                _inspectorResult.value = res
            }.onFailure { err ->
                _uiError.value = err.message ?: "Mono Docs couldn't read this file. It may be damaged."
            }
        }
    }

    fun showInspectorResult(result: FileInspectionResult) {
        _inspectorResult.value = result
    }

    fun dismissInspector() {
        _inspectorResult.value = null
    }

    fun preselectForConverter(inspection: FileInspectionResult, localFile: File) {
        _converterPreselected.value = inspection to localFile
    }

    fun preselectUriForConverter(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val insp = FileSignatureInspector.inspectUri(context, uri)
                val file = FileStorageHelper.copyUriToWorkFile(context, uri, insp.fileName)
                insp to file
            }.onSuccess { pair ->
                _converterPreselected.value = pair
            }.onFailure { err ->
                _uiError.value = err.message ?: "Could not load file for conversion."
            }
        }
    }

    fun clearPreselectedConverter() {
        _converterPreselected.value = null
    }

    fun clearError() {
        _uiError.value = null
    }

    fun removeRecentFile(item: RecentFileEntity) {
        viewModelScope.launch { repository.removeRecentFile(item.uriString) }
    }

    fun clearRecentFiles() {
        viewModelScope.launch { repository.clearRecentFiles() }
    }

    fun recordConversion(entry: ConversionHistoryEntity) {
        viewModelScope.launch { repository.recordConversion(entry) }
    }

    fun clearConversionHistory() {
        viewModelScope.launch { repository.clearConversionHistory() }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkMode(enabled) }
    }

    fun setSystemTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.setSystemTheme(enabled) }
    }

    fun setDefaultSaveLocation(treeUri: String?, label: String) {
        viewModelScope.launch { preferences.setDefaultSaveLocation(treeUri, label) }
    }

    fun setConversionQuality(quality: ConversionQuality) {
        viewModelScope.launch { preferences.setConversionQuality(quality) }
    }

    fun setVideoQuality(preset: VideoQualityPreset) {
        viewModelScope.launch { preferences.setVideoQuality(preset) }
    }

    fun setImageCompression(percent: Int) {
        viewModelScope.launch { preferences.setImageCompression(percent) }
    }

    fun setAutoDetect(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoDetect(enabled) }
    }

    class Factory(private val application: MonoDocsApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return MonoDocsViewModel(
                application = application,
                repository = application.container.repository,
                preferences = application.container.preferencesRepository,
            ) as T
        }
    }
}
