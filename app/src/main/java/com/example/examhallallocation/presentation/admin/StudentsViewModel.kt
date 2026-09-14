package com.example.examhallallocation.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.domain.model.*
import com.example.examhallallocation.domain.usecase.CsvParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val pdfGenerator: com.example.examhallallocation.domain.usecase.PdfGenerator,
    private val databaseSeeder: com.example.examhallallocation.data.seed.DatabaseSeeder,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    init {
        viewModelScope.launch {
            databaseSeeder.hydrateFromCloud()
        }
    }

    data class StudentsUi(
        val students: List<Student> = emptyList(),
        val activeCount: Int = 0,
        val year1: Int = 0,
        val year2: Int = 0,
        val year3: Int = 0,
        val year4: Int = 0,
    )

    private val searchQuery = MutableStateFlow("")
    private val yearFilter = MutableStateFlow<StudentYear>(StudentYear.YEAR_2)
    private val refresh = MutableStateFlow(0)

    val currentYear: StateFlow<StudentYear> = yearFilter.asStateFlow()

    val ui: StateFlow<StudentsUi> = combine(
        studentRepository.observeAll(),
        searchQuery,
        yearFilter,
        refresh,
    ) { students, query, year, _ ->
        val filtered = students.filter { student ->
            student.year == year &&
                (query.isBlank() ||
                    student.name.contains(query, ignoreCase = true) ||
                    student.registerNumber.contains(query, ignoreCase = true))
        }.sortedWith(
            compareBy<Student>(
                { it.extractRollNumber() },
                { it.registerNumber },
                { it.name }
            )
        )
        StudentsUi(
            students = filtered,
            activeCount = students.count { it.active },
            year1 = students.count { it.active && it.year == StudentYear.YEAR_1 },
            year2 = students.count { it.active && it.year == StudentYear.YEAR_2 },
            year3 = students.count { it.active && it.year == StudentYear.YEAR_3 },
            year4 = students.count { it.active && it.year == StudentYear.YEAR_4 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUi())

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    fun search(text: String) { searchQuery.value = text.trim() }

    fun filterYear(year: StudentYear) { yearFilter.value = year }

    fun addStudent(registerNumber: String, name: String, yearValue: Int, section: String, position: Int) {
        viewModelScope.launch {
            val year = StudentYear.fromValue(yearValue)
            when {
                registerNumber.isBlank() || name.isBlank() ->
                    _events.value = "Register number and student name are required"
                year == null -> _events.value = "Please select a valid academic year (2nd, 3rd, or Final Year)"
                studentRepository.findByRegisterNumber(registerNumber) != null ->
                    _events.value = "Roll number $registerNumber is already allocated"
                else -> {
                    val positionFinal = if (position > 0) position else studentRepository.maxPosition(year) + 1
                    val success = studentRepository.add(
                        Student(
                            id = "",
                            registerNumber = registerNumber,
                            name = name,
                            year = year,
                            section = section.ifBlank { "A" },
                            position = positionFinal,
                            active = true,
                        )
                    )
                    _events.value = if (success) "Roll number $registerNumber added successfully" else "Failed to add roll number"
                }
            }
        }
    }

    fun updateStudent(
        student: Student,
        registerNumber: String,
        name: String,
        yearValue: Int,
        section: String,
        position: Int,
        active: Boolean
    ) {
        viewModelScope.launch {
            val year = StudentYear.fromValue(yearValue)
            if (registerNumber.isBlank() || name.isBlank()) {
                _events.value = "Register number and student name are required"
                return@launch
            }
            if (year == null) {
                _events.value = "Please select a valid academic year"
                return@launch
            }
            val existingWithReg = studentRepository.findByRegisterNumber(registerNumber)
            if (existingWithReg != null && existingWithReg.id != student.id) {
                _events.value = "Roll number $registerNumber is already allocated to ${existingWithReg.name}"
                return@launch
            }

            val updated = student.copy(
                registerNumber = registerNumber,
                name = name,
                year = year,
                section = section.ifBlank { "A" },
                position = if (position > 0) position else student.position,
                active = active,
            )
            val success = studentRepository.update(updated)
            _events.value = if (success) "Roll number $registerNumber updated successfully" else "Failed to update roll number"
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            studentRepository.delete(student)
            _events.value = "Student removed"
        }
    }

    fun clearAllStudents() {
        viewModelScope.launch {
            studentRepository.clearAll()
            _events.value = "All students cleared from database"
        }
    }

    /**
     * Bulk import from file bytes (.xlsx, .csv, .tsv).
     * With [replaceExisting] the current list is cleared first.
     */
    fun importFile(bytes: ByteArray, defaultYear: StudentYear? = yearFilter.value, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            if (replaceExisting) studentRepository.clearAll()
            val result = com.example.examhallallocation.domain.usecase.SmartDataExtractor.parseStudents(
                bytes,
                defaultYear = defaultYear ?: yearFilter.value,
                existingRegisterNumbers = currentRegisterNumbers()
            )
            var tcCount = 0
            val valid = mutableListOf<Student>()
            result.students.forEach { student ->
                if (!student.active) tcCount++
                valid.add(student)
            }
            if (valid.isNotEmpty()) {
                studentRepository.addAll(valid)
            }
            val imported = valid.size
            val skipped = result.skipped
            _events.value = when {
                imported > 0 && tcCount > 0 ->
                    "Imported $imported students ($tcCount marked inactive/TC, $skipped skipped)"
                imported > 0 ->
                    "Imported $imported students successfully ($skipped skipped)"
                else ->
                    "No valid student rows found ($skipped skipped)"
            }
            if (imported == 0 && result.errors.isNotEmpty()) {
                _events.value = result.errors.first()
            }
        }
    }

    fun importCsv(content: String, defaultYear: StudentYear? = yearFilter.value, replaceExisting: Boolean = false) {
        importFile(content.toByteArray(Charsets.UTF_8), defaultYear, replaceExisting)
    }

    fun exportStudents(
        asPdf: Boolean,
        onlyCurrentYear: Boolean,
        onDone: (android.net.Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val all = studentRepository.observeAll().first()
                val rawList = if (onlyCurrentYear) {
                    all.filter { it.year == yearFilter.value }
                } else {
                    all
                }

                if (rawList.isEmpty()) {
                    onError("No student records available to export")
                    return@launch
                }

                // Strictly sort: 1st Year, 2nd Year (roll numbers 1 to 120 in order), 3rd Year, 4th Year
                val targetList = rawList.sortedWith(StudentOrderComparator)

                val title = if (onlyCurrentYear) {
                    "${yearFilter.value.label} Student Directory"
                } else {
                    "GRT College - All Years Student Directory"
                }

                val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val extension = if (asPdf) "pdf" else "csv"
                val fileName = "GRT_Students_$stamp.$extension"

                val docDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
                if (!docDir.exists()) docDir.mkdirs()
                val targetFile = java.io.File(docDir, fileName)

                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (asPdf) {
                        pdfGenerator.generateStudentListPdf(targetList, title, targetFile)
                    } else {
                        pdfGenerator.generateStudentListCsv(targetList, title, targetFile)
                    }
                }

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    targetFile
                )

                onDone(contentUri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to export student records")
            }
        }
    }

    private suspend fun currentRegisterNumbers(): Set<String> =
        studentRepository.allRegisterNumbers()

    fun consumeEvent() { _events.value = null }
}
