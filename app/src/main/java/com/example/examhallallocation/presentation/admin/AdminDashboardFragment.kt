package com.example.examhallallocation.presentation.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.FragmentAdminDashboardBinding
import com.example.examhallallocation.domain.model.ArrangementStatus
import com.example.examhallallocation.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminDashboardViewModel by viewModels()

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

        // Top stat cards instant navigation
        binding.cardStudents.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_students) }
        binding.cardTeachers.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_teachers) }
        binding.cardHalls.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_halls) }
        binding.cardExams.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_exams) }

        // Primary actions: Direct access to unified Seating Allocation & PDF Export
        binding.btnGenerate.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_pdf) }
        binding.btnPreview.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_pdf) }

        // Data Stored & College Records rows
        binding.rowStudents.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_students) }
        binding.rowTeachers.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_teachers) }
        binding.rowExams.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_exams) }
        binding.rowHalls.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_halls) }
        binding.rowPdf.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_pdf) }
        binding.rowMasterArchive.setOnClickListener { showMasterExportDialog() }
        binding.rowLogout.setOnClickListener { confirmLogout() }
        binding.rowResetData.setOnClickListener { confirmResetData() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                binding.tvStatStudents.text = ui.totalStudents.toString()
                binding.tvStatTeachers.text = ui.activeTeachers.toString()
                binding.tvStatHalls.text = ui.activeHalls.toString()
                binding.tvNoticeExam.text = ui.latestExamName ?: getString(R.string.status_none)
                binding.tvStatExam.text = when {
                    ui.arrangementDates.isEmpty() -> getString(R.string.status_none)
                    else -> getString(R.string.status_draft)
                }
            }
        }
    }

    private fun confirmResetData() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Wipe Mock Data & Clean Slate?")
            .setMessage("This will wipe all mock/cached students, exams, and arrangements from the database so you can start with a 100% clean production environment. Physical halls and admin account will be preserved.")
            .setPositiveButton("Wipe All Data") { _, _ ->
                viewModel.wipeAllMockData {
                    android.widget.Toast.makeText(requireContext(), "Database wiped clean. Ready for real data.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showMasterExportDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
        android.widget.Toast.makeText(requireContext(), "Compiling master institutional archive...", android.widget.Toast.LENGTH_SHORT).show()
        viewModel.exportMasterArchive(
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

    private fun openOrShareFile(uri: android.net.Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(viewIntent, "Open Master Archive")
        chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Master Archive"))
        }
    }

    private fun confirmLogout() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
