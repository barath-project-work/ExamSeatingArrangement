package com.example.examhallallocation.presentation.admin

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.FragmentAdminDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminDashboardViewModel by viewModels()

    private val pickTimetableFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readAndImportTimetable(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top stat cards navigation
        binding.cardStudents.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_students) }
        binding.cardTeachers.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_teachers) }
        binding.cardHalls.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_halls) }
        binding.cardSubjects.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_subjects) }

        // Primary action: Direct access to unified Seating Allocation & PDF Export
        binding.btnGenerate.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_pdf) }

        // Timetable Upload Card actions
        binding.btnUploadTimetableDashboard.setOnClickListener { pickTimetableFileLauncher.launch("*/*") }
        binding.btnPasteTimetableDashboard.setOnClickListener { showPasteTimetableDialog() }
        binding.btnLoadOfficialDashboard.setOnClickListener { loadOfficialTimetable() }

        // Active Timetable actions
        binding.btnExportTimetableDashboard.setOnClickListener { showExportTimetableDialog() }
        binding.btnEditTimetableDashboard.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_exams) }
        binding.btnClearTimetableDashboard.setOnClickListener { confirmClearTimetable() }

        // College Database & Stored Records rows (Strictly the 6 requested sections)
        binding.rowSubjects.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_subjects) }
        binding.rowStudents.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_students) }
        binding.rowHalls.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_halls) }
        binding.rowTeachers.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_teachers) }
        binding.rowPdf.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_pdf) }
        binding.rowMasterArchive.setOnClickListener { showMasterExportDialog() }
        binding.rowResetData.setOnClickListener { confirmResetData() }
        binding.rowLogout.setOnClickListener { confirmLogout() }

        // Observe dashboard state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                binding.tvStatStudents.text = ui.totalStudents.toString()
                binding.tvStatTeachers.text = ui.activeTeachers.toString()
                binding.tvStatHalls.text = ui.activeHalls.toString()
                binding.tvStatSubjects.text = ui.totalSubjects.toString()

                if (ui.isTimetableActive) {
                    binding.tvBadgeTimetable.text = "ACTIVE TIMETABLE"
                    binding.tvBadgeTimetable.setBackgroundResource(R.drawable.bg_banner_green)
                    binding.tvNoticeTitle.text = ui.examName ?: "Upcoming Examination"
                    binding.tvNoticeExam.text = ui.examDatesSummary ?: "Active exam timetable generated"
                    binding.layoutTimetableActions.isVisible = true
                    binding.btnClearTimetableDashboard.isVisible = true
                    binding.btnGenerate.isEnabled = true
                    binding.btnGenerate.alpha = 1.0f
                } else {
                    binding.tvBadgeTimetable.text = "NO TIMETABLE ACTIVE"
                    binding.tvBadgeTimetable.setBackgroundResource(R.drawable.bg_badge)
                    binding.tvNoticeTitle.text = "Examination Schedule"
                    binding.tvNoticeExam.text = "Upload a timetable above or load Assessment Test - I to generate examination dates and hall allocations"
                    binding.layoutTimetableActions.isVisible = false
                    binding.btnClearTimetableDashboard.isVisible = false
                    binding.btnGenerate.isEnabled = false
                    binding.btnGenerate.alpha = 0.5f
                }
            }
        }
    }

    private fun readAndImportTimetable(uri: Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { bytes ->
            Toast.makeText(requireContext(), "Processing timetable spreadsheet...", Toast.LENGTH_SHORT).show()
            viewModel.importTimetableFile(bytes) { success, message ->
                Toast.makeText(requireContext(), message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(requireContext(), "Could not read the selected file", Toast.LENGTH_SHORT).show()
    }

    private fun showPasteTimetableDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Paste Excel rows or CSV text here...\n(Date, Session, Timing, Year, Dept, Subject Code, Subject Name, Students Count)"
            minLines = 6
            setPadding(36, 28, 36, 28)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Exam Schedule")
            .setMessage("Paste rows copied from your college timetable spreadsheet. Dates and subjects will be generated automatically.")
            .setView(input)
            .setPositiveButton("Generate Dates") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    viewModel.importTimetableCsv(text) { success, message ->
                        Toast.makeText(requireContext(), message, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun loadOfficialTimetable() {
        viewModel.loadOfficialAssessmentTest1 { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showExportTimetableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Exam Timetable")
            .setItems(arrayOf("Export as PDF Document", "Export as Excel / CSV Spreadsheet")) { _, which ->
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

    private fun confirmClearTimetable() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Active Timetable?")
            .setMessage("This will remove the current exam timetable and generated dates so you can upload a fresh timetable schedule.")
            .setPositiveButton("Clear Timetable") { _, _ ->
                viewModel.clearActiveTimetable {
                    Toast.makeText(requireContext(), "Timetable cleared. You can now upload a new schedule.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun confirmResetData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Wipe Mock Data & Clean Slate?")
            .setMessage("This will wipe all mock/cached students, exams, and arrangements from the database so you can start with a 100% clean production environment. Physical halls and admin account will be preserved.")
            .setPositiveButton("Wipe All Data") { _, _ ->
                viewModel.wipeAllMockData {
                    Toast.makeText(requireContext(), "Database wiped clean. Ready for real data.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showMasterExportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Institutional Master Archive Export")
            .setMessage("Generate a unified complete institutional archive containing all current examination timetables, student master directory, examination halls, faculty accounts, and seating allocations.")
            .setPositiveButton("Export Full Excel / CSV") { _, _ ->
                runMasterExport(asPdf = false)
            }
            .setNeutralButton("Export Master PDF") { _, _ ->
                runMasterExport(asPdf = true)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun runMasterExport(asPdf: Boolean) {
        Toast.makeText(requireContext(), "Compiling master institutional archive...", Toast.LENGTH_SHORT).show()
        viewModel.exportMasterArchive(
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

    private fun openOrShareFile(uri: Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, "Open Export")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Export"))
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_logout))
            .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
                viewModel.logout {
                    findNavController().navigate(R.id.action_any_to_login)
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
