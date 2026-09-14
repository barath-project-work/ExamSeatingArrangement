package com.example.examhallallocation.presentation.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.DialogSubjectBinding
import com.example.examhallallocation.databinding.FragmentSubjectsBinding
import com.example.examhallallocation.domain.model.StudentYear
import com.example.examhallallocation.domain.model.Subject
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubjectsFragment : Fragment() {

    private var _binding: FragmentSubjectsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubjectsViewModel by viewModels()

    private lateinit var adapter: SubjectsAdapter
    private val yearOptions = listOf(StudentYear.YEAR_2, StudentYear.YEAR_3, StudentYear.YEAR_4)
    private var replaceExistingOnPick: Boolean = false

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readAndImportFile(it, replaceExistingOnPick) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSubjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SubjectsAdapter(
            onEdit = { subject -> showAddOrEditDialog(subject) },
            onDelete = ::confirmDelete,
        )
        binding.recyclerSubjects.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSubjects.adapter = adapter

        binding.emptyState.tvEmptyTitle.text = "No Curriculum Subjects"
        binding.emptyState.tvEmptyHint.text = "Tap + Add Subject or Import to add course catalog subjects"

        // Action buttons
        binding.btnAddSubject.setOnClickListener { showAddOrEditDialog(null) }
        binding.btnImportSubjects.setOnClickListener { showImportDialog() }
        binding.btnExportSubjects.setOnClickListener { showExportDialog() }
        binding.btnQuickOptions.setOnClickListener { showQuickOptionsDialog() }

        // Search text listener
        binding.etSearchSubject.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString()?.trim().orEmpty())
        }

        // Year filter chips
        binding.chipGroupSubjectYears.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipSubjectYear2) -> viewModel.setYearFilter(StudentYear.YEAR_2)
                checkedIds.contains(R.id.chipSubjectYear3) -> viewModel.setYearFilter(StudentYear.YEAR_3)
                checkedIds.contains(R.id.chipSubjectYear4) -> viewModel.setYearFilter(StudentYear.YEAR_4)
                else -> viewModel.setYearFilter(null)
            }
        }

        // Observe subjects
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.subjects.collect { list ->
                adapter.submitList(list)
                binding.emptyState.root.isVisible = list.isEmpty()
                binding.recyclerSubjects.isVisible = list.isNotEmpty()
            }
        }

        // Dynamic counts on chips
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.yearCounts.collect { counts ->
                binding.chipSubjectAll.text = "All Years (${counts[null] ?: 0})"
                binding.chipSubjectYear2.text = "2nd Year (${counts[StudentYear.YEAR_2] ?: 0})"
                binding.chipSubjectYear3.text = "3rd Year (${counts[StudentYear.YEAR_3] ?: 0})"
                binding.chipSubjectYear4.text = "Final Year (${counts[StudentYear.YEAR_4] ?: 0})"
                binding.tvSubjectsSubtitle.text = "${counts[null] ?: 0} Curriculum Courses · Official Department Catalog"
            }
        }

        // Events / Toasts
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { msg ->
                if (msg != null) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
            }
        }
    }

    private fun showAddOrEditDialog(subjectToEdit: Subject?) {
        val dialogBinding = DialogSubjectBinding.inflate(layoutInflater)
        var selectedYear = subjectToEdit?.year ?: StudentYear.YEAR_2

        if (subjectToEdit != null) {
            dialogBinding.etSubjectCode.setText(subjectToEdit.code)
            dialogBinding.etSubjectName.setText(subjectToEdit.name)
            dialogBinding.etYear.setText(subjectToEdit.year.label)
            dialogBinding.etSemester.setText(subjectToEdit.semester.toString())
            dialogBinding.etDepartment.setText(subjectToEdit.department)
        } else {
            dialogBinding.etYear.setText(selectedYear.label)
            dialogBinding.etSemester.setText("3")
            dialogBinding.etDepartment.setText("CSE")
        }

        dialogBinding.etYear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.hint_year))
                .setSingleChoiceItems(yearOptions.map { it.label }.toTypedArray(), yearOptions.indexOf(selectedYear)) { dialog, which ->
                    selectedYear = yearOptions[which]
                    dialogBinding.etYear.setText(selectedYear.label)
                    val suggestedSem = when (selectedYear) {
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

        val title = if (subjectToEdit == null) "Add Curriculum Subject" else "Edit Subject"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val code = dialogBinding.etSubjectCode.text?.toString()?.trim().orEmpty()
                val name = dialogBinding.etSubjectName.text?.toString()?.trim().orEmpty()
                val sem = dialogBinding.etSemester.text?.toString()?.toIntOrNull() ?: 3
                val dept = dialogBinding.etDepartment.text?.toString()?.trim().orEmpty().ifBlank { "CSE" }

                if (subjectToEdit != null) {
                    viewModel.updateSubject(subjectToEdit, code, name, selectedYear, sem, dept)
                } else {
                    viewModel.addSubject(code, name, selectedYear, sem, dept)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun confirmDelete(subject: Subject) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Subject?")
            .setMessage("Are you sure you want to remove [${subject.code}] ${subject.name} from the curriculum catalog?")
            .setPositiveButton(getString(R.string.action_delete)) { _, _ -> viewModel.deleteSubject(subject) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showImportDialog() {
        val options = arrayOf(
            "Upload Spreadsheet (.xlsx / .csv)",
            "Paste Subject Rows from Clipboard",
            "Copy Sample CSV Template",
            "Load Official 18 CSE Curriculum Subjects"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Curriculum Subjects")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFileLauncher.launch("*/*")
                    1 -> showPasteDialog()
                    2 -> copySampleTemplate()
                    3 -> viewModel.loadOfficialCurriculum()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showPasteDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Paste rows here...\nFormat: Code, Name, Year, Semester, Department\nExample: CS24301, DATA STRUCTURES, 2nd Year, 3, CSE"
            minLines = 6
            setPadding(36, 28, 36, 28)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Subject Records")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) viewModel.importCsv(text, replaceExisting = false)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun copySampleTemplate() {
        val sample = buildString {
            appendLine("Subject Code,Subject Name,Year,Semester,Department")
            appendLine("CS24301,DATA STRUCTURES AND ALGORITHMS,2nd Year,3,CSE")
            appendLine("CS24502,CLOUD COMPUTING,3rd Year,5,CSE")
            appendLine("AI3021,IT IN AGRICULTURAL SYSTEM,Final Year,7,CSE")
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Subject Template", sample))
        Toast.makeText(requireContext(), "Template copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showExportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Curriculum Subjects")
            .setItems(arrayOf("Export as PDF Document", "Export as Excel / CSV Spreadsheet")) { _, which ->
                val asPdf = which == 0
                Toast.makeText(requireContext(), "Generating subjects export...", Toast.LENGTH_SHORT).show()
                viewModel.exportSubjects(
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

    private fun showQuickOptionsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Curriculum Options")
            .setItems(
                arrayOf(
                    "Export Subjects Directory (PDF / CSV)",
                    "Import Subjects from File",
                    "Restore Official 18 CSE Subjects",
                    "Clear All Subjects",
                )
            ) { _, which ->
                when (which) {
                    0 -> showExportDialog()
                    1 -> showImportDialog()
                    2 -> viewModel.loadOfficialCurriculum()
                    3 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Clear All Curriculum Subjects?")
                            .setMessage("Are you sure you want to clear all subjects from the catalog?")
                            .setPositiveButton("Clear All") { _, _ -> viewModel.clearAllSubjects() }
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun readAndImportFile(uri: Uri, replaceExisting: Boolean) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull()?.let { text ->
            viewModel.importCsv(text, replaceExisting)
        } ?: Toast.makeText(requireContext(), "Could not read the selected file", Toast.LENGTH_SHORT).show()
    }

    private fun openOrShareFile(uri: Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, "Open Subjects Directory")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Subjects Directory"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
