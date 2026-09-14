package com.example.examhallallocation.presentation.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.DialogExamBinding
import com.example.examhallallocation.databinding.DialogUploadTimetableBinding
import com.example.examhallallocation.databinding.FragmentExamsBinding
import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.model.StudentYear
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ExamsFragment : Fragment() {

    private var _binding: FragmentExamsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamsViewModel by viewModels()

    private lateinit var adapter: ExamsAdapter
    private val yearOptions = listOf(StudentYear.YEAR_1, StudentYear.YEAR_2, StudentYear.YEAR_3, StudentYear.YEAR_4)
    private var replaceExistingOnPick: Boolean = true

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readAndImportFile(it, replaceExistingOnPick) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExamsAdapter(
            onEdit = { exam -> showAddOrEditDialog(exam) },
            onDelete = ::confirmDelete,
        )
        binding.recyclerExams.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerExams.adapter = adapter

        // Action buttons
        binding.btnUploadTimetablePortal.setOnClickListener { showUploadTimetablePortalDialog() }
        binding.btnAddExam.setOnClickListener { showAddOrEditDialog(null) }
        binding.btnExportTimetable.setOnClickListener { showExportTimetableDialog() }
        binding.btnQuickOptions.setOnClickListener { showQuickOptionsDialog() }

        // Search text change
        binding.etSearchExam.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString()?.trim().orEmpty())
        }

        // Year filter chips
        binding.chipGroupExamYears.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipExamYear2) -> viewModel.setYearFilter(StudentYear.YEAR_2)
                checkedIds.contains(R.id.chipExamYear3) -> viewModel.setYearFilter(StudentYear.YEAR_3)
                checkedIds.contains(R.id.chipExamYear4) -> viewModel.setYearFilter(StudentYear.YEAR_4)
                else -> viewModel.setYearFilter(null)
            }
        }

        // Observe filtered exams
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.exams.collect { exams ->
                adapter.submitList(exams)
                binding.emptyState.root.isVisible = exams.isEmpty()
                binding.recyclerExams.isVisible = exams.isNotEmpty()
            }
        }

        // Observe all exams for dynamic title
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allExams.collect { list ->
                val detectedTitle = list.firstOrNull()?.examName?.takeIf { it.isNotBlank() }
                binding.tvExamPortalTitle.text = detectedTitle ?: "Exam Schedule Portal"
            }
        }

        // Observe year counts to dynamically update chip badges
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.yearCounts.collect { counts ->
                binding.chipExamAll.text = "All Years (${counts[null] ?: 0})"
                binding.chipExamYear2.text = "2nd Year (${counts[StudentYear.YEAR_2] ?: 0})"
                binding.chipExamYear3.text = "3rd Year (${counts[StudentYear.YEAR_3] ?: 0})"
                binding.chipExamYear4.text = "Final Year (${counts[StudentYear.YEAR_4] ?: 0})"
            }
        }

        // Observe schedule summary (dates span, counts)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scheduleSummary.collect { summary ->
                binding.tvExamPortalSubtitle.text = summary
            }
        }

        // Observe user events / messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                if (message != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
            }
        }
    }

    /**
     * Dedicated Timetable Upload Portal Dialog
     */
    private fun showUploadTimetablePortalDialog() {
        val dialogBinding = DialogUploadTimetableBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // 1. Upload file (.xlsx / .csv)
        dialogBinding.btnUploadFile.setOnClickListener {
            replaceExistingOnPick = dialogBinding.cbReplaceExisting.isChecked
            dialog.dismiss()
            pickFileLauncher.launch("*/*")
        }

        // 2. Paste table from spreadsheet / clipboard
        dialogBinding.btnPasteSchedule.setOnClickListener {
            val replace = dialogBinding.cbReplaceExisting.isChecked
            dialog.dismiss()
            showPasteDialog(replace)
        }

        // 3. Load official assessment test 1
        dialogBinding.btnLoadOfficial.setOnClickListener {
            dialog.dismiss()
            viewModel.loadOfficialAssessmentTest1()
        }

        // 4. Copy sample CSV template
        dialogBinding.btnCopyTemplate.setOnClickListener {
            copySampleTemplateToClipboard()
            Toast.makeText(requireContext(), "Sample Timetable template copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showPasteDialog(replaceExisting: Boolean) {
        val input = EditText(requireContext()).apply {
            hint = "Paste Excel rows or CSV text here...\n(Date, Session, Timing, Year/Sem, Dept, Subject Code, Subject Name, Students Count)"
            minLines = 6
            setPadding(36, 28, 36, 28)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Timetable Data")
            .setMessage("Copy rows from your college exam timetable spreadsheet (.xlsx) and paste below. The app will automatically parse dates, sessions, and subject codes.")
            .setView(input)
            .setPositiveButton("Import Timetable") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    viewModel.importCsv(text, replaceExisting)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showExportTimetableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Exam Timetable")
            .setItems(
                arrayOf(
                    "Export as PDF Document",
                    "Export as Excel / CSV Spreadsheet",
                )
            ) { _, which ->
                val asPdf = which == 0
                Toast.makeText(requireContext(), "Generating timetable export...", Toast.LENGTH_SHORT).show()
                viewModel.exportTimetable(
                    asPdf = asPdf,
                    onDone = { uri, fileName ->
                        Toast.makeText(requireContext(), "Saved: $fileName", Toast.LENGTH_LONG).show()
                        openOrShareFile(uri, asPdf)
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun openOrShareFile(uri: android.net.Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(viewIntent, "Open Timetable")
        chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Timetable"))
        }
    }

    private fun showQuickOptionsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Timetable Actions")
            .setItems(
                arrayOf(
                    "Export Timetable (PDF / Excel)",
                    "Upload Timetable (.xlsx / .csv)",
                    "Load Official Assessment Test - I (18 Subjects)",
                    "Copy Sample CSV Template",
                    "Clear all scheduled exams",
                )
            ) { _, which ->
                when (which) {
                    0 -> showExportTimetableDialog()
                    1 -> showUploadTimetablePortalDialog()
                    2 -> viewModel.loadOfficialAssessmentTest1()
                    3 -> copySampleTemplateToClipboard()
                    4 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Clear All Scheduled Exams?")
                            .setMessage("Are you sure you want to remove all exams from the timetable?")
                            .setPositiveButton("Clear All") { _, _ -> viewModel.clearAllExams() }
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showAddOrEditDialog(examToEdit: Exam?) {
        val dialogBinding = DialogExamBinding.inflate(layoutInflater)
        var selectedYear = examToEdit?.year ?: StudentYear.YEAR_2
        var selectedDateStr = examToEdit?.date.orEmpty()

        // Populate fields if editing
        if (examToEdit != null) {
            dialogBinding.etDate.setText(examToEdit.date)
            dialogBinding.etYear.setText(examToEdit.year.label)
            dialogBinding.etSemester.setText(examToEdit.semester.toString())
            dialogBinding.etSession.setText(examToEdit.session)
            dialogBinding.etTiming.setText(examToEdit.timing)
            dialogBinding.etDepartment.setText(examToEdit.department)
            dialogBinding.etSubjectCode.setText(examToEdit.subjectCode)
            dialogBinding.etSubjectName.setText(examToEdit.subjectName)
            dialogBinding.etStudentCount.setText(examToEdit.studentCount.toString())
        } else {
            dialogBinding.etYear.setText(selectedYear.label)
            dialogBinding.etSemester.setText("3")
            dialogBinding.etSession.setText("FN")
            dialogBinding.etTiming.setText("8:40 a.m. TO 10:10 a.m.")
            dialogBinding.etDepartment.setText("CSE")
            dialogBinding.etStudentCount.setText("104")
        }

        // Year picker
        dialogBinding.etYear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.hint_year))
                .setSingleChoiceItems(yearOptions.map { it.label }.toTypedArray(), yearOptions.indexOf(selectedYear)) { dialog, which ->
                    selectedYear = yearOptions[which]
                    dialogBinding.etYear.setText(selectedYear.label)
                    // Auto-suggest semester based on selected year
                    val suggestedSem = when (selectedYear) {
                        StudentYear.YEAR_1 -> 1
                        StudentYear.YEAR_2 -> 3
                        StudentYear.YEAR_3 -> 5
                        StudentYear.YEAR_4 -> 7
                        else -> 3
                    }
                    dialogBinding.etSemester.setText(suggestedSem.toString())
                    dialog.dismiss()
                }
                .show()
        }

        // Date picker
        dialogBinding.etDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.hint_exam_date))
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                selectedDateStr = localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                dialogBinding.etDate.setText(selectedDateStr)
            }
            picker.show(childFragmentManager, "datePicker")
        }

        val title = if (examToEdit == null) getString(R.string.exams_add) else "Edit Exam Schedule"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val date = selectedDateStr.ifBlank { dialogBinding.etDate.text?.toString()?.trim().orEmpty() }
                val sem = dialogBinding.etSemester.text?.toString()?.toIntOrNull() ?: 3
                val code = dialogBinding.etSubjectCode.text?.toString()?.trim().orEmpty()
                val name = dialogBinding.etSubjectName.text?.toString()?.trim().orEmpty()
                val session = dialogBinding.etSession.text?.toString()?.trim().orEmpty().ifBlank { "FN" }
                val timing = dialogBinding.etTiming.text?.toString()?.trim().orEmpty().ifBlank { "8:40 a.m. TO 10:10 a.m." }
                val dept = dialogBinding.etDepartment.text?.toString()?.trim().orEmpty().ifBlank { "CSE" }
                val count = dialogBinding.etStudentCount.text?.toString()?.toIntOrNull() ?: 0

                if (examToEdit != null) {
                    viewModel.updateExam(
                        originalExam = examToEdit,
                        date = date,
                        year = selectedYear,
                        semester = sem,
                        subjectCode = code,
                        subjectName = name,
                        session = session,
                        timing = timing,
                        department = dept,
                        studentCount = count,
                    )
                } else {
                    viewModel.addExam(
                        date = date,
                        year = selectedYear,
                        semester = sem,
                        subjectCode = code,
                        subjectName = name,
                        session = session,
                        timing = timing,
                        department = dept,
                        studentCount = count,
                    )
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun confirmDelete(exam: Exam) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title, exam.subjectCode))
            .setMessage("Remove [${exam.subjectCode}] ${exam.subjectName} on ${exam.date} from the exam schedule?")
            .setPositiveButton(getString(R.string.action_delete)) { _, _ -> viewModel.deleteExam(exam) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun readAndImportFile(uri: Uri, replaceExisting: Boolean) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { bytes ->
            viewModel.importFile(bytes, replaceExisting)
        } ?: Toast.makeText(requireContext(), "Could not read the selected file", Toast.LENGTH_SHORT).show()
    }

    private fun copySampleTemplateToClipboard() {
        val sampleCsv = buildString {
            appendLine("Date,Session,Timing,Year / Sem,Department,Subject Code,Subject Name,Students Count")
            appendLine("17-08-2026,FN,8:40 a.m. TO 10:10 a.m.,II / 03,CSE,CS24301,DATA STRUCTURES AND ALGORITHMS,104")
            appendLine("17-08-2026,FN,8:40 a.m. TO 10:10 a.m.,III / 05,CSE,CS24502,CLOUD COMPUTING,119")
            appendLine("17-08-2026,FN,8:40 a.m. TO 10:10 a.m.,IV / 07,CSE,AI3021,IT IN AGRICULTURAL SYSTEM,117")
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Exam Timetable Template", sampleCsv)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Sample Timetable template copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
