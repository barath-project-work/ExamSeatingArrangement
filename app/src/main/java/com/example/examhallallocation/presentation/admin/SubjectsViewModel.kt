package com.example.examhallallocation.presentation.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.SubjectRepository
import com.example.examhallallocation.data.seed.SeedDataProvider
import com.example.examhallallocation.domain.model.StudentYear
import com.example.examhallallocation.domain.model.Subject
import com.example.examhallallocation.domain.usecase.DataExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val dataExportManager: DataExportManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedYear = MutableStateFlow<StudentYear?>(null)
    val selectedYear: StateFlow<StudentYear?> = _selectedYear

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events

    val allSubjects: StateFlow<List<Subject>> = subjectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subjects: StateFlow<List<Subject>> = combine(
        allSubjects,
        _searchQuery,
        _selectedYear,
    ) { list, query, year ->
        list.filter { subject ->
            val matchesYear = year == null || subject.year == year
            val matchesQuery = query.isBlank() ||
                subject.code.contains(query, ignoreCase = true) ||
                subject.name.contains(query, ignoreCase = true) ||
                subject.department.contains(query, ignoreCase = true)
            matchesYear && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val yearCounts: StateFlow<Map<StudentYear?, Int>> = allSubjects.map { list ->
        val map = mutableMapOf<StudentYear?, Int>()
        map[null] = list.size
        map[StudentYear.YEAR_2] = list.count { it.year == StudentYear.YEAR_2 }
        map[StudentYear.YEAR_3] = list.count { it.year == StudentYear.YEAR_3 }
        map[StudentYear.YEAR_4] = list.count { it.year == StudentYear.YEAR_4 }
        map
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setYearFilter(year: StudentYear?) {
        _selectedYear.value = year
    }

    fun addSubject(code: String, name: String, year: StudentYear, semester: Int, department: String) {
        viewModelScope.launch {
            if (code.isBlank() || name.isBlank()) {
                _events.value = "Subject code and name cannot be empty"
                return@launch
            }
            val cleanCode = code.trim().uppercase()
            val cleanName = name.trim().uppercase()
            val cleanDept = department.trim().uppercase().ifBlank { "CSE" }
            val subject = Subject(
                id = "sub_${cleanCode.lowercase()}_${year.value}",
                code = cleanCode,
                name = cleanName,
                year = year,
                semester = semester,
                department = cleanDept,
                active = true,
            )
            subjectRepository.upsert(subject)
            _events.value = "Added subject: $cleanCode"
        }
    }

    fun updateSubject(original: Subject, code: String, name: String, year: StudentYear, semester: Int, department: String) {
        viewModelScope.launch {
            val cleanCode = code.trim().uppercase().ifBlank { original.code }
            val cleanName = name.trim().uppercase().ifBlank { original.name }
            val cleanDept = department.trim().uppercase().ifBlank { original.department }
            val updated = original.copy(
                code = cleanCode,
                name = cleanName,
                year = year,
                semester = semester,
                department = cleanDept,
            )
            subjectRepository.upsert(updated)
            _events.value = "Updated subject: $cleanCode"
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            subjectRepository.delete(subject)
            _events.value = "Removed subject: ${subject.code}"
        }
    }

    fun loadOfficialCurriculum() {
        viewModelScope.launch {
            subjectRepository.upsertAll(SeedDataProvider.subjects())
            _events.value = "Loaded official curriculum (18 subjects)"
        }
    }

    fun clearAllSubjects() {
        viewModelScope.launch {
            subjectRepository.clearAll()
            _events.value = "Cleared all curriculum subjects"
        }
    }

    fun importCsv(text: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            runCatching {
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                val parsed = mutableListOf<Subject>()
                for (line in lines) {
                    val cols = line.split(",", "\t").map { it.trim().removeSurrounding("\"") }
                    if (cols.isEmpty() || cols[0].equals("Code", true) || cols[0].equals("Subject Code", true) || cols[0].equals("S.NO", true)) continue
                    // Accept formats:
                    // 1) Code, Name, Year, Sem, Dept
                    // 2) S.No, Code, Name, Year, Sem, Dept
                    val codeIdx = if (cols[0].all { it.isDigit() } && cols.size > 1) 1 else 0
                    if (cols.size > codeIdx + 1) {
                        val code = cols[codeIdx].uppercase()
                        val name = cols[codeIdx + 1].uppercase()
                        val yearStr = cols.getOrNull(codeIdx + 2).orEmpty()
                        val semStr = cols.getOrNull(codeIdx + 3).orEmpty()
                        val dept = cols.getOrNull(codeIdx + 4)?.uppercase()?.ifBlank { "CSE" } ?: "CSE"

                        val year = when {
                            yearStr.contains("4", true) || yearStr.contains("IV", true) || yearStr.contains("Final", true) -> StudentYear.YEAR_4
                            yearStr.contains("3", true) || yearStr.contains("III", true) -> StudentYear.YEAR_3
                            else -> StudentYear.YEAR_2
                        }
                        val sem = semStr.filter { it.isDigit() }.toIntOrNull() ?: when (year) {
                            StudentYear.YEAR_2 -> 3
                            StudentYear.YEAR_3 -> 5
                            StudentYear.YEAR_4 -> 7
                            else -> 3
                        }
                        parsed.add(Subject("sub_${code.lowercase()}_${year.value}", code, name, year, sem, dept))
                    }
                }
                if (parsed.isNotEmpty()) {
                    if (replaceExisting) {
                        subjectRepository.replaceAll(parsed)
                    } else {
                        subjectRepository.upsertAll(parsed)
                    }
                    _events.value = "Imported ${parsed.size} curriculum subjects successfully"
                } else {
                    _events.value = "No valid subjects found to import"
                }
            }.onFailure {
                _events.value = "Import error: ${it.localizedMessage ?: "Invalid file format"}"
            }
        }
    }

    fun exportSubjects(
        asPdf: Boolean,
        onDone: (Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportSubjects(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to export subjects directory")
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
