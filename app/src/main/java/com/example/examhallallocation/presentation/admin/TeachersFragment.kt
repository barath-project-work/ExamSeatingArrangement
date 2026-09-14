package com.example.examhallallocation.presentation.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.DialogTeacherBinding
import com.example.examhallallocation.databinding.FragmentTeachersBinding
import com.example.examhallallocation.domain.model.Teacher
import com.example.examhallallocation.domain.model.UserRole
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TeachersFragment : Fragment() {

    private var _binding: FragmentTeachersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TeachersViewModel by viewModels()

    private lateinit var adapter: TeachersAdapter

    private val roleOptions = listOf(UserRole.NORMAL_TEACHER, UserRole.EXAM_CELL_COORDINATOR, UserRole.HOD, UserRole.ADMIN)

    private val pickFileLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { readCsv(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTeachersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TeachersAdapter(onEdit = ::showEditDialog, onDelete = ::confirmDelete)
        binding.recyclerTeachers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTeachers.adapter = adapter
        binding.btnExportTeachers.setOnClickListener { showExportTeachersDialog() }
        binding.btnAddTeacher.setOnClickListener { showEditDialog(null) }
        binding.btnImportTeachers.setOnClickListener { showImportChoiceDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.teachers.collect { teachers ->
                adapter.submitList(teachers)
                binding.emptyState.root.isVisible = teachers.isEmpty()
                binding.recyclerTeachers.isVisible = teachers.isNotEmpty()
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

    private fun showExportTeachersDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Faculty Directory")
            .setItems(
                arrayOf(
                    "Export as PDF Document",
                    "Export as Excel / CSV Spreadsheet",
                )
            ) { _, which ->
                val asPdf = which == 0
                android.widget.Toast.makeText(requireContext(), "Generating faculty export...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.exportTeachers(
                    asPdf = asPdf,
                    onDone = { uri, fileName ->
                        android.widget.Toast.makeText(requireContext(), "Saved: $fileName", android.widget.Toast.LENGTH_LONG).show()
                        openOrShareFile(uri, asPdf)
                    },
                    onError = { error ->
                        android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_LONG).show()
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
        val chooser = android.content.Intent.createChooser(viewIntent, "Open Faculty Directory")
        chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Faculty Directory"))
        }
    }

    private fun showEditDialog(existing: Teacher?) {
        val dialogBinding = DialogTeacherBinding.inflate(layoutInflater)
        var selectedRole = existing?.role ?: UserRole.NORMAL_TEACHER

        dialogBinding.etRole.setText(selectedRole.label)
        dialogBinding.etRole.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.nav_profile))
                .setSingleChoiceItems(roleOptions.map { it.label }.toTypedArray(), roleOptions.indexOf(selectedRole)) { dialog, which ->
                    selectedRole = roleOptions[which]
                    dialogBinding.etRole.setText(selectedRole.label)
                    dialog.dismiss()
                }
                .show()
        }

        existing?.let {
            dialogBinding.etName.setText(it.name)
            dialogBinding.etUsername.setText(it.username)
            dialogBinding.etUsername.isEnabled = false
            dialogBinding.tilPassword.isVisible = false
            dialogBinding.switchActive.isChecked = it.active
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(if (existing == null) R.string.teachers_add else R.string.action_edit))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
                val username = dialogBinding.etUsername.text?.toString()?.trim().orEmpty()
                val password = dialogBinding.etPassword.text?.toString().orEmpty()
                when (existing) {
                    null -> viewModel.addTeacher(name, username, password, selectedRole)
                    else -> viewModel.updateTeacher(existing, name, selectedRole, dialogBinding.switchActive.isChecked)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun confirmDelete(teacher: Teacher) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title, teacher.name))
            .setMessage("Are you sure you want to permanently delete this faculty member? Their login account will be removed completely from both local and cloud databases. (To temporarily disable login, use the Active toggle switch in Edit instead.)")
            .setPositiveButton(getString(R.string.action_delete)) { _, _ -> viewModel.deleteTeacher(teacher) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun readCsv(uri: android.net.Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { viewModel.importFile(it) }
            ?: android.widget.Toast.makeText(requireContext(), "Could not read the selected file", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showImportChoiceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Faculty List")
            .setItems(arrayOf("Choose CSV / Excel File (.csv, .tsv)", "Paste Rows from Excel / Clipboard")) { _, which ->
                when (which) {
                    0 -> pickFileLauncher.launch("*/*")
                    1 -> showPasteDialog()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showPasteDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Paste Excel rows or CSV text here...\n(Columns: Name, Username, Password, Role)"
            minLines = 6
            setPadding(36, 28, 36, 28)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Faculty Data")
            .setMessage("Copy rows from your Excel sheet or CSV and paste below. The app will automatically detect columns and provision login accounts.")
            .setView(input)
            .setPositiveButton("Import Data") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    viewModel.importCsv(text)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
