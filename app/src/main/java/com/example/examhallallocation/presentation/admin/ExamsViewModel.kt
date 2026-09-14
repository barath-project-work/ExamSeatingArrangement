package com.example.examhallallocation.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.model.StudentYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExamsViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val databaseSeeder: com.example.examhallallocation.data.seed.DatabaseSeeder,
    private val dataExportManager: com.example.examhallallocation.domain.usecase.DataExportManager,
) : ViewModel() {

    init {
        viewModelScope.launch {
            databaseSeeder.hydrateFromCloud()
        }
    }

    private val _yearFilter = MutableStateFlow<StudentYear?>(null)
    val yearFilter: StateFlow<StudentYear?> = _yearFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    val allExams: StateFlow<List<Exam>> = examRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exams: StateFlow<List<Exam>> = combine(allExams, _yearFilter, _searchQuery) { list, year, query ->
        list.filter { exam ->
            val matchesYear = (year == null || exam.year == year)
            val matchesQuery = query.isBlank() ||
                exam.subjectCode.contains(query, ignoreCase = true) ||
                exam.subjectName.contains(query, ignoreCase = true) ||
                exam.date.contains(query, ignoreCase = true) ||
                exam.timing.contains(query, ignoreCase = true)
            matchesYear && matchesQuery
        }.sortedWith(
            compareBy<Exam> { it.date }
                .thenBy { it.session }
                .thenBy { it.year.value }
                .thenBy { it.subjectCode }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val yearCounts: StateFlow<Map<StudentYear?, Int>> = allExams.combine(_yearFilter) { list, _ ->
        val counts = mutableMapOf<StudentYear?, Int>()
        counts[null] = list.size
        counts[StudentYear.YEAR_1] = list.count { it.year == StudentYear.YEAR_1 }
        counts[StudentYear.YEAR_2] = list.count { it.year == StudentYear.YEAR_2 }
        counts[StudentYear.YEAR_3] = list.count { it.year == StudentYear.YEAR_3 }
        counts[StudentYear.YEAR_4] = list.count { it.year == StudentYear.YEAR_4 }
        counts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val scheduleSummary: StateFlow<String> = allExams.combine(_yearFilter) { list, _ ->
        if (list.isEmpty()) {
            "No exams scheduled yet"
        } else {
            val total = list.size
            val dates = list.mapNotNull {
                runCatching { LocalDate.parse(it.date) }.getOrNull()
            }.sorted()
            if (dates.isNotEmpty()) {
                val dFmt = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
                val startStr = dates.first().format(dFmt)
                val endStr = dates.last().format(dFmt)
                val yearStr = dates.last().year
                if (startStr == endStr) {
                    "$total Subjects Scheduled · $startStr $yearStr"
                } else {
                    "$total Subjects Scheduled · $startStr – $endStr $yearStr"
                }
            } else {
                "$total Subjects Scheduled"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Loading timetable…")

    fun setYearFilter(year: StudentYear?) {
        _yearFilter.value = year
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addExam(
        date: String,
        year: StudentYear,
        semester: Int,
        subjectCode: String,
        subjectName: String,
        session: String = "FN",
        timing: String = "8:40 a.m. TO 10:10 a.m.",
        department: String = "CSE",
        studentCount: Int = 0,
        examName: String = "Assessment Test - I",
    ) {
        viewModelScope.launch {
            when {
                date.isBlank() -> _events.value = "Exam date is required"
                semester !in 1..8 -> _events.value = "Semester must be between 1 and 8"
                subjectCode.isBlank() || subjectName.isBlank() ->
                    _events.value = "Subject code and name are required"
                else -> {
                    examRepository.add(
                        Exam(
                            id = "exam_${date}_${year.value}_${subjectCode.trim().lowercase()}",
                            examName = examName,
                            date = date,
                            year = year,
                            semester = semester,
                            subjectCode = subjectCode.trim().uppercase(),
                            subjectName = subjectName.trim().uppercase(),
                            session = session.trim().uppercase(),
                            timing = timing.trim(),
                            department = department.trim().uppercase(),
                            studentCount = studentCount,
                        )
                    )
                    _events.value = "Exam [${subjectCode.trim().uppercase()}] saved for ${year.label} on $date"
                }
            }
        }
    }

    fun updateExam(
        originalExam: Exam,
        date: String,
        year: StudentYear,
        semester: Int,
        subjectCode: String,
        subjectName: String,
        session: String,
        timing: String,
        department: String,
        studentCount: Int,
    ) {
        viewModelScope.launch {
            when {
                date.isBlank() -> _events.value = "Exam date is required"
                semester !in 1..8 -> _events.value = "Semester must be between 1 and 8"
                subjectCode.isBlank() || subjectName.isBlank() ->
                    _events.value = "Subject code and name are required"
                else -> {
                    // If ID or date/year/code changed, remove previous and insert new
                    val newId = "exam_${date}_${year.value}_${subjectCode.trim().lowercase()}"
                    if (newId != originalExam.id) {
                        examRepository.delete(originalExam)
                    }
                    examRepository.add(
                        Exam(
                            id = newId,
                            examName = originalExam.examName,
                            date = date,
                            year = year,
                            semester = semester,
                            subjectCode = subjectCode.trim().uppercase(),
                            subjectName = subjectName.trim().uppercase(),
                            session = session.trim().uppercase(),
                            timing = timing.trim(),
                            department = department.trim().uppercase(),
                            studentCount = studentCount,
                        )
                    )
                    _events.value = "Updated [${subjectCode.trim().uppercase()}] schedule"
                }
            }
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            examRepository.delete(exam)
            _events.value = "Exam [${exam.subjectCode}] deleted"
        }
    }

    fun importFile(bytes: ByteArray, replaceExisting: Boolean = true) {
        viewModelScope.launch {
            val result = com.example.examhallallocation.domain.usecase.SmartDataExtractor.parseExams(bytes)
            if (result.exams.isEmpty()) {
                _events.value = if (result.errors.isNotEmpty()) result.errors.first() else "No valid exam rows found"
                return@launch
            }
            if (replaceExisting) {
                examRepository.clearAll()
            }
            examRepository.addAll(result.exams)
            _events.value = "Successfully imported ${result.exams.size} exams (${result.skipped} skipped)"
        }
    }

    fun importCsv(content: String, replaceExisting: Boolean = true) {
        importFile(content.toByteArray(Charsets.UTF_8), replaceExisting)
    }

    fun clearAllExams() {
        viewModelScope.launch {
            examRepository.clearAll()
            _events.value = "All exams cleared"
        }
    }

    fun loadOfficialAssessmentTest1() {
        viewModelScope.launch {
            examRepository.clearAll()
            val officialList = listOf(
                // II Year / Sem 03 (104 students)
                Exam("exam_2026-08-17_cs24301_2", "Assessment Test - I", "2026-08-17", StudentYear.YEAR_2, 3, "CS24301", "DATA STRUCTURES AND ALGORITHMS", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),
                Exam("exam_2026-08-18_ma24303_2", "Assessment Test - I", "2026-08-18", StudentYear.YEAR_2, 3, "MA24303", "DISCRETE MATHEMATICS", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),
                Exam("exam_2026-08-19_ec24303_2", "Assessment Test - I", "2026-08-19", StudentYear.YEAR_2, 3, "EC24303", "COMPUTER ORGANIZATION AND DIGITAL PR", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),
                Exam("exam_2026-08-20_cs24302_2", "Assessment Test - I", "2026-08-20", StudentYear.YEAR_2, 3, "CS24302", "PROGRAMMING IN JAVA", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),
                Exam("exam_2026-08-21_cs24303_2", "Assessment Test - I", "2026-08-21", StudentYear.YEAR_2, 3, "CS24303", "FOUNDATION OF DATASCIENCE", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),
                Exam("exam_2026-08-22_cs24304_2", "Assessment Test - I", "2026-08-22", StudentYear.YEAR_2, 3, "CS24304", "OPERATING SYSTEMS", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 104),

                // III Year / Sem 05 (119 students)
                Exam("exam_2026-08-17_cs24502_3", "Assessment Test - I", "2026-08-17", StudentYear.YEAR_3, 5, "CS24502", "CLOUD COMPUTING", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-18_cs24503_3", "Assessment Test - I", "2026-08-18", StudentYear.YEAR_3, 5, "CS24503", "MOBILE APPLICATION DEVELOPMENT", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-19_cs24p01_3", "Assessment Test - I", "2026-08-19", StudentYear.YEAR_3, 5, "CS24P01", "EXPLORATORY DATA ANALYSIS", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-20_cs24p06_3", "Assessment Test - I", "2026-08-20", StudentYear.YEAR_3, 5, "CS24P06", "UI & UX DESIGN", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-21_ge24501_3", "Assessment Test - I", "2026-08-21", StudentYear.YEAR_3, 5, "GE24501", "PROFESSIONAL ETHICS AND HUMAN VALUES", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-22_mg24903_3", "Assessment Test - I", "2026-08-22", StudentYear.YEAR_3, 5, "MG24903", "BUISNESS STRAGEGY", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),
                Exam("exam_2026-08-24_cs24501_3", "Assessment Test - I", "2026-08-24", StudentYear.YEAR_3, 5, "CS24501", "OBJECT ORIENTED SOFTWARE ENGINEERING", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 119),

                // IV Year / Sem 07 (117 students)
                Exam("exam_2026-08-17_ai3021_4", "Assessment Test - I", "2026-08-17", StudentYear.YEAR_4, 7, "AI3021", "IT IN AGRICULTURAL SYSTEM", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 117),
                Exam("exam_2026-08-18_obt356_4", "Assessment Test - I", "2026-08-18", StudentYear.YEAR_4, 7, "OBT356", "LIFESTYLE DISEASES", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 117),
                Exam("exam_2026-08-19_ge3791_4", "Assessment Test - I", "2026-08-19", StudentYear.YEAR_4, 7, "GE3791", "HUMAN VALUES AND ETHICS", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 117),
                Exam("exam_2026-08-20_ge3751_4", "Assessment Test - I", "2026-08-20", StudentYear.YEAR_4, 7, "GE3751", "PRINCIPLES OF MANAGEMENT", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 117),
                Exam("exam_2026-08-21_oim351_4", "Assessment Test - I", "2026-08-21", StudentYear.YEAR_4, 7, "OIM351", "INDUSTRIAL MANAGEMENT", "FN", "8:40 a.m. TO 10:10 a.m.", "CSE", 117),
            )
            examRepository.addAll(officialList)
            _events.value = "Loaded ${officialList.size} official Assessment Test - I exams"
        }
    }

    fun exportTimetable(
        asPdf: Boolean,
        onDone: (android.net.Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportTimetable(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to export timetable")
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
