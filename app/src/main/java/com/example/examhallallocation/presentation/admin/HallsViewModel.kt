package com.example.examhallallocation.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.domain.usecase.HallCsvParser
import com.example.examhallallocation.domain.model.Hall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HallsViewModel @Inject constructor(
    private val hallRepository: HallRepository,
    private val dataExportManager: com.example.examhallallocation.domain.usecase.DataExportManager,
) : ViewModel() {

    val halls: StateFlow<List<Hall>> = hallRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    fun addHall(roomNumber: String, block: String, floor: Int, capacity: Int) {
        viewModelScope.launch {
            when {
                roomNumber.isBlank() || block.isBlank() ->
                    _events.value = "Room number and block are required"
                capacity !in 1..60 ->
                    _events.value = "Exam capacity must be between 1 and 60"
                else -> {
                    val ok = hallRepository.add(
                        Hall(id = "", roomNumber = roomNumber, block = block, floor = floor, capacity = capacity, active = true)
                    )
                    _events.value = if (ok) "Hall saved" else "A hall with this room number already exists"
                }
            }
        }
    }

    fun updateHall(hall: Hall, roomNumber: String, block: String, floor: Int, capacity: Int, active: Boolean) {
        viewModelScope.launch {
            hallRepository.update(hall.copy(roomNumber = roomNumber, block = block, floor = floor, capacity = capacity, active = active))
            _events.value = "Hall saved"
        }
    }

    fun deleteHall(hall: Hall) {
        viewModelScope.launch {
            hallRepository.delete(hall)
            _events.value = "Hall removed"
        }
    }

    /**
     * Bulk CSV / Excel (.xlsx) import of halls (RoomNumber,Block,Floor,Capacity,Active).
     * With [replaceExisting] the whole hall list is replaced — the real-data load path.
     */
    fun importFile(bytes: ByteArray, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            if (replaceExisting) hallRepository.clearAll()
            val result = com.example.examhallallocation.domain.usecase.SmartDataExtractor.parseHalls(bytes)
            var imported = 0
            result.halls.forEach { hall -> if (hallRepository.add(hall)) imported++ }
            _events.value = if (imported == 0) {
                "No valid hall rows found (${result.skipped} skipped)"
            } else {
                "Imported $imported halls successfully (${result.skipped} skipped)"
            }
            if (imported == 0 && result.errors.isNotEmpty()) {
                _events.value = result.errors.first()
            }
        }
    }

    fun importCsv(content: String, replaceExisting: Boolean = false) {
        importFile(content.toByteArray(Charsets.UTF_8), replaceExisting)
    }

    fun exportHalls(
        asPdf: Boolean,
        onDone: (android.net.Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportHalls(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to export halls directory")
            }
        }
    }

    fun consumeEvent() { _events.value = null }
}
