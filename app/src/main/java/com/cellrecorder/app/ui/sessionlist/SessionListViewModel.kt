package com.cellrecorder.app.ui.sessionlist

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.SessionSummary
import com.cellrecorder.app.domain.usecase.CreateSessionUseCase
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.domain.usecase.GetSessionsUseCase
import com.cellrecorder.app.domain.usecase.import_.ImportSessionUseCase
import com.cellrecorder.app.domain.usecase.import_.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val getSessionsUseCase: GetSessionsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val sessionRepository: SessionRepository,
    private val exportSessionUseCase: ExportSessionUseCase,
    private val getSessionPointsUseCase: GetSessionPointsUseCase,
    private val importSessionUseCase: ImportSessionUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessions: StateFlow<List<SessionSummary>> = getSessionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _createdSessionId = MutableStateFlow<Long?>(null)
    val createdSessionId: StateFlow<Long?> = _createdSessionId

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    val selectionMode = combine(sessions, _selectedIds) { list, selected ->
        selected.isNotEmpty() && list.any { it.id in selected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun createSession(name: String) {
        viewModelScope.launch {
            val id = createSessionUseCase(name)
            _createdSessionId.value = id
        }
    }

    fun renameSession(sessionId: Long, newName: String) {
        viewModelScope.launch {
            sessionRepository.updateName(sessionId, newName)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteById(sessionId)
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearCreatedFlag() {
        _createdSessionId.value = null
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun importFile(content: String, fileName: String) {
        viewModelScope.launch {
            val name = fileName.substringBeforeLast(".").replace("_", " ").take(100)
            val ext = fileName.substringAfterLast(".", "").lowercase()
            val summary = if (ext == "geojson" || ext == "json") {
                importSessionUseCase.importGeoJson(content, name)
            } else {
                importSessionUseCase.importCsv(content, name)
            }
            _importSummary.value = summary
        }
    }

    fun clearImportSummary() {
        _importSummary.value = null
    }

    fun updatePrimarySimSlot(sessionId: Long, simSlotIndex: Int?) {
        viewModelScope.launch {
            sessionRepository.updatePrimarySimSlot(sessionId, simSlotIndex)
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { sessionRepository.deleteById(it) }
            _selectedIds.value = emptySet()
        }
    }

    fun exportSelected(sessionsToExport: List<SessionSummary>, format: String, treeUri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                } catch (_: Exception) { }

                for (session in sessionsToExport) {
                    val records = getSessionPointsUseCase.getOnce(session.id)
                    if (records.isEmpty()) continue

                    val export = if (format == "GeoJSON") {
                        exportSessionUseCase.exportGeoJson(session.toEntity(), records)
                    } else {
                        exportSessionUseCase.exportCsv(session.toEntity(), records)
                    }

                    val mime = if (format == "GeoJSON") "application/geo+json" else "text/csv"
                    val filename = "${session.name.replace(" ", "_")}_records.${if (format == "GeoJSON") "geojson" else "csv"}"
                    try {
                        val docUri = DocumentsContract.createDocument(
                            context.contentResolver, treeUri, mime, filename
                        ) ?: continue
                        context.contentResolver.openOutputStream(docUri)?.use { stream ->
                            stream.write(export.content.toByteArray())
                        }
                    } catch (_: Exception) { }
                }
            }
            _selectedIds.value = emptySet()
        }
    }

    private fun SessionSummary.toEntity() = com.cellrecorder.app.data.local.entity.SessionEntity(
        id = id, name = name, createdAt = createdAt, endedAt = endedAt, pointCount = pointCount
    )
}