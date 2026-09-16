package com.example.examhallallocation.presentation.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.DialogStudentBinding
import com.example.examhallallocation.databinding.DialogStudentDetailsBinding
import com.example.examhallallocation.databinding.FragmentStudentsBinding
import com.example.examhallallocation.domain.model.Student
import com.example.examhallallocation.domain.model.StudentYear
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentsFragment : Fragment() {

    private var _binding: FragmentStudentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StudentsViewModel by viewModels()

    private lateinit var adapter: StudentsAdapter

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readCsv(uri)
    }

    /** True when the pending import should wipe existing rows first. */
    private var pendingImportReplace = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStudentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StudentsAdapter(onSelect = ::showStudentDetailsDialog)
        binding.recyclerStudents.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerStudents.adapter = adapter

        binding.etSearch.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }

        binding.chipGroupYears.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedYear = when (checkedIds.firstOrNull()) {
                R.id.chipYear3 -> StudentYear.YEAR_3
                R.id.chipYear4 -> StudentYear.YEAR_4
                else -> StudentYear.YEAR_2
            }
            viewModel.filterYear(selectedYear)
        }

        binding.btnAddStudent.setOnClickListener { showAddOrEditDialog(null) }
        binding.btnImport.setOnClickListener { showImportChoiceDialog() }
        binding.btnExportStudents.setOnClickListener { showExportStudentsDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                adapter.submitList(ui.students)
                binding.tvTotalActive.text =
                    getString(R.string.students_count_format, ui.activeCount)
                val breakdownParts = mutableListOf<String>()
                breakdownParts.add("2nd Year: ${ui.year2}")
                breakdownParts.add("3rd Year: ${ui.year3}")
                breakdownParts.add("Final Year: ${ui.year4}")
                binding.tvYearBreakdown.text = breakdownParts.joinToString("  ·  ")
                binding.chipYear2.text = "Second Year (${ui.year2})"
                binding.chipYear3.text = "Third Year (${ui.year3})"
                binding.chipYear4.text = "Final Year (${ui.year4})"
                binding.emptyState.root.isVisible = ui.students.isEmpty()
                binding.recyclerStudents.isVisible = ui.students.isNotEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                if (message != null) {
                    android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
            }
        }
    }

    /**
     * Modal dialog displayed when tapping any roll number box.
     * Shows exclusively the student name and roll number along with options to modify or remove.
     */
    private fun showStudentDetailsDialog(student: Student) {
        val detailBinding = DialogStudentDetailsBinding.inflate(layoutInflater)
        detailBinding.tvDetailName.text = student.name
        detailBinding.tvDetailRollNo.text = student.registerNumber
        detailBinding.tvDetailYear.text = "${student.year.label} (CSE)"
        detailBinding.tvDetailPosSec.text = "Position #${student.position} · Sec ${student.section.ifBlank { "A" }}"

        if (student.active) {
            detailBinding.tvDetailStatus.text = "ACTIVE ELIGIBLE"
            detailBinding.tvDetailStatus.setTextColor(requireContext().getColor(R.color.status_green_text))
        } else {
            detailBinding.tvDetailStatus.text = "INACTIVE / DEBARRED"
            detailBinding.tvDetailStatus.setTextColor(requireContext().getColor(R.color.status_red_text))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(detailBinding.root)
            .create()

        detailBinding.btnDetailClose.setOnClickListener { dialog.dismiss() }
        detailBinding.btnDetailEdit.setOnClickListener {
            dialog.dismiss()
            showAddOrEditDialog(student)
        }
        detailBinding.btnDetailDelete.setOnClickListener {
            dialog.dismiss()
            confirmDelete(student)
        }

        dialog.show()
    }

    /** Asks whether the file replaces everything, merges into current data, or paste from clipboard. */
    private fun showImportChoiceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Student List")
            .setItems(
                arrayOf(
                    "Upload Excel / CSV File (.xlsx, .csv)",
                    "Paste Rows from Excel / Clipboard",
                    "Merge File with existing students",
                    "Clear all students from database",
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        pendingImportReplace = true
                        csvPicker.launch("*/*")
                    }
                    1 -> showPasteDialog()
                    2 -> {
                        pendingImportReplace = false
                        csvPicker.launch("*/*")
                    }
                    3 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Clear All Students?")
                            .setMessage("Are you sure you want to clear all student records from the database?")
                            .setPositiveButton("Clear All") { _, _ -> viewModel.clearAllStudents() }
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showPasteDialog() {
        val dialogBinding = com.example.examhallallocation.databinding.DialogPasteDataBinding.inflate(layoutInflater)
        dialogBinding.tvPasteHint.text = "Copy roll numbers and names from your college spreadsheet and paste them below."
        dialogBinding.etPasteInput.hint = "Paste Excel rows or CSV text here...\n(Columns: RegisterNumber, Name, Year, Section)"
        dialogBinding.tvFormatGuide.text = "Format: RegisterNumber, Student Name, Year, Section"

        dialogBinding.etPasteInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val lines = s?.toString()?.lineSequence()?.filter { it.isNotBlank() }?.count() ?: 0
                dialogBinding.tvLineCount.text = "$lines students detected"
            }
        })

        dialogBinding.btnPasteClipboard.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0).coerceToText(requireContext()).toString()
                dialogBinding.etPasteInput.setText(pasteText)
                dialogBinding.etPasteInput.setSelection(dialogBinding.etPasteInput.text.length)
            } else {
                toast("Clipboard is empty")
            }
        }

        dialogBinding.btnClearText.setOnClickListener {
            dialogBinding.etPasteInput.setText("")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Student Data")
            .setView(dialogBinding.root)
            .setPositiveButton("Import Students") { _, _ ->
                val text = dialogBinding.etPasteInput.text.toString().trim()
                if (text.isNotBlank()) {
                    viewModel.importCsv(text, defaultYear = viewModel.currentYear.value, replaceExisting = false)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun readCsv(uri: Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { bytes ->
            viewModel.importFile(bytes, defaultYear = viewModel.currentYear.value, replaceExisting = pendingImportReplace)
        } ?: toast("Could not read the selected file")
    }

    /**
     * Dialog for both adding a new roll number within a year and modifying existing roll numbers.
     */
    private fun showAddOrEditDialog(existing: Student?) {
        val dialogBinding = DialogStudentBinding.inflate(layoutInflater)
        val activeYear = viewModel.currentYear.value

        if (existing == null) {
            // Adding a new student: default to currently selected year tab
            when (activeYear) {
                StudentYear.YEAR_3 -> dialogBinding.chipDialogYear3.isChecked = true
                StudentYear.YEAR_4 -> dialogBinding.chipDialogYear4.isChecked = true
                else -> dialogBinding.chipDialogYear2.isChecked = true
            }

            fun updatePrefix(prefix: String) {
                val current = dialogBinding.etRegisterNumber.text?.toString().orEmpty()
                val suffix = if (current.length >= 9 && (current.startsWith("110325104") || current.startsWith("110324104") || current.startsWith("110323104"))) {
                    current.substring(9)
                } else ""
                dialogBinding.etRegisterNumber.setText(prefix + suffix)
                dialogBinding.etRegisterNumber.setSelection(dialogBinding.etRegisterNumber.text?.length ?: 0)
            }

            val initialPrefix = when (activeYear) {
                StudentYear.YEAR_3 -> "110324104"
                StudentYear.YEAR_4 -> "110323104"
                else -> "110325104"
            }
            updatePrefix(initialPrefix)

            dialogBinding.chipGroupDialogYear.setOnCheckedStateChangeListener { _, checkedIds ->
                val newPrefix = when (checkedIds.firstOrNull()) {
                    R.id.chipDialogYear3 -> "110324104"
                    R.id.chipDialogYear4 -> "110323104"
                    else -> "110325104"
                }
                updatePrefix(newPrefix)
            }

            dialogBinding.etSection.setText("A")
            dialogBinding.switchActive.isChecked = true
        } else {
            // Modifying an existing roll number: pre-fill existing attributes
            when (existing.year) {
                StudentYear.YEAR_3 -> dialogBinding.chipDialogYear3.isChecked = true
                StudentYear.YEAR_4 -> dialogBinding.chipDialogYear4.isChecked = true
                else -> dialogBinding.chipDialogYear2.isChecked = true
            }
            dialogBinding.etRegisterNumber.setText(existing.registerNumber)
            dialogBinding.etName.setText(existing.name)
            dialogBinding.etSection.setText(existing.section)
            dialogBinding.etPosition.setText(existing.position.toString())
            dialogBinding.switchActive.isChecked = existing.active
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Add Roll Number" else "Modify Roll Number")
            .setView(dialogBinding.root)
            .setPositiveButton(if (existing == null) "Add Roll Number" else "Save Changes") { _, _ ->
                val selectedYear = when (dialogBinding.chipGroupDialogYear.checkedChipId) {
                    R.id.chipDialogYear3 -> 3
                    R.id.chipDialogYear4 -> 4
                    else -> 2
                }
                val registerNumber = dialogBinding.etRegisterNumber.text?.toString()?.trim().orEmpty()
                val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
                val section = dialogBinding.etSection.text?.toString()?.trim().orEmpty().ifBlank { "A" }
                val position = dialogBinding.etPosition.text?.toString()?.trim()?.toIntOrNull() ?: 0

                if (existing == null) {
                    viewModel.addStudent(registerNumber, name, selectedYear, section, position)
                } else {
                    viewModel.updateStudent(
                        existing,
                        registerNumber,
                        name,
                        selectedYear,
                        section,
                        position,
                        dialogBinding.switchActive.isChecked,
                    )
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showExportStudentsDialog() {
        val activeYearLabel = viewModel.currentYear.value.label
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Student Records")
            .setItems(
                arrayOf(
                    "Export as PDF (All Years Combined)",
                    "Export as PDF ($activeYearLabel Only)",
                    "Export as Excel / CSV (All Years Combined)",
                    "Export as Excel / CSV ($activeYearLabel Only)",
                )
            ) { _, which ->
                val asPdf = which == 0 || which == 1
                val onlyCurrentYear = which == 1 || which == 3
                viewModel.exportStudents(
                    asPdf = asPdf,
                    onlyCurrentYear = onlyCurrentYear,
                    onDone = { uri, fileName ->
                        toast("Exported: $fileName")
                        openOrShareExportedFile(uri, asPdf)
                    },
                    onError = { err -> toast(err) },
                )
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun openOrShareExportedFile(uri: Uri, isPdf: Boolean) {
        try {
            val mime = if (isPdf) "application/pdf" else "text/csv"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open Student Directory").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (t: Throwable) {
            // Safe fallback: try sharing if no direct viewer installed
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (isPdf) "application/pdf" else "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "GRT Student Directory")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Student Directory").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Throwable) {
                toast("File saved to device Documents folder")
            }
        }
    }

    private fun confirmDelete(student: Student) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title, student.name))
            .setMessage("Are you sure you want to remove roll number ${student.registerNumber}?")
            .setPositiveButton(getString(R.string.action_delete)) { _, _ -> viewModel.deleteStudent(student) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
