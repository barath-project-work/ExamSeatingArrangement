package com.example.examhallallocation.presentation.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.FragmentPdfExportBinding
import com.example.examhallallocation.databinding.ItemDutyRowBinding
import com.example.examhallallocation.databinding.ItemPreviewRowBinding
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class PdfExportFragment : Fragment() {

    private var _binding: FragmentPdfExportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PdfExportViewModel by viewModels()

    private val seatingAdapter = SeatingAdapter()
    private val dutyAdapter = DutyAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPdfExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerSeating.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSeating.adapter = seatingAdapter

        binding.recyclerDuties.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDuties.adapter = dutyAdapter

        binding.tvStaffBadge.setOnClickListener {
            binding.root.post {
                binding.root.smoothScrollTo(0, binding.cardInvigilationDuty.top)
            }
        }

        binding.btnViewExamDutySummary.setOnClickListener { showExamDutySummaryDialog() }
        binding.btnExport.setOnClickListener { viewModel.exportPdf() }
        binding.btnExportCsv.setOnClickListener { viewModel.exportCsv() }
        binding.btnRegenerate.setOnClickListener { viewModel.generateForCurrentDate() }
        binding.btnGenerateNow.setOnClickListener { viewModel.generateForCurrentDate() }
        binding.btnBackDashboard.setOnClickListener { findNavController().navigateUp() }

        // Observe UI state (dates, rows, counts)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.tvExamTitle.text = state.examName

                val formattedDate = runCatching {
                    LocalDate.parse(state.selectedDate).format(DateTimeFormatter.ofPattern("dd-MM-yyyy (EEE)"))
                }.getOrDefault(state.selectedDate)
                binding.tvDateSubtitle.text = "Date: $formattedDate · Department of Computer Science & Engineering"
                binding.tvExamTitle.text = state.examName.ifBlank { "Exam Seating Allocation" }

                binding.tvHallsBadge.text = "${state.hallsCount} Halls"
                binding.tvStudentsBadge.text = "${state.studentsCount} Students"
                binding.tvStaffBadge.text = "${state.invigilatorsCount} Invigilators"

                seatingAdapter.submitList(state.rows)
                dutyAdapter.submitList(state.dutyRows)
                binding.tvDutyCountBadge.text = "${state.dutyRows.size} Teachers Assigned"
                binding.cardInvigilationDuty.isVisible = state.isGenerated && state.dutyRows.isNotEmpty()

                binding.recyclerSeating.isVisible = state.isGenerated && state.rows.isNotEmpty()
                binding.layoutEmptyState.isVisible = !state.isGenerated || state.rows.isEmpty()
                binding.layoutMetrics.isVisible = state.isGenerated && state.rows.isNotEmpty()

                binding.progress.isVisible = state.isGenerating
                binding.btnGenerateNow.isEnabled = !state.isGenerating
                binding.btnRegenerate.isEnabled = !state.isGenerating

                populateDateChips(state.availableDates, state.selectedDate)

                if (!state.statusMessage.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), state.statusMessage, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe Export state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.exportState.collect { exportState ->
                val isExporting = exportState is PdfExportViewModel.ExportState.Exporting
                binding.progress.isVisible = isExporting
                binding.btnExport.isEnabled = !isExporting
                binding.btnExportCsv.isEnabled = !isExporting

                when (exportState) {
                    is PdfExportViewModel.ExportState.Done -> {
                        val typeLabel = if (exportState.isPdf) "PDF Document" else "Excel / CSV Spreadsheet"
                        binding.tvStatus.isVisible = true
                        binding.tvStatus.text = "$typeLabel Generated Successfully!\nFile: ${exportState.displayName} (${exportState.fileSizeFormatted})\nSaved in Documents / GRT_Exam_Arrangement"
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_banner_green)
                        binding.layoutDoneActions.isVisible = true

                        binding.btnViewPdf.text = if (exportState.isPdf) "Open & View PDF" else "Open in Excel / Sheets"
                        binding.btnSharePdf.text = if (exportState.isPdf) "Share PDF (WhatsApp / Drive)" else "Share Excel / CSV Spreadsheet"

                        binding.btnViewPdf.setOnClickListener { openFile(exportState.uri, exportState.isPdf) }
                        binding.btnSharePdf.setOnClickListener { shareFile(exportState.uri, exportState.isPdf) }
                    }
                    is PdfExportViewModel.ExportState.Error -> {
                        binding.tvStatus.isVisible = true
                        binding.tvStatus.text = exportState.message
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_banner_red)
                        binding.layoutDoneActions.isVisible = false
                    }
                    else -> {
                        binding.tvStatus.isVisible = false
                        binding.layoutDoneActions.isVisible = false
                    }
                }
            }
        }
    }

    private fun populateDateChips(dates: List<String>, selectedDate: String) {
        val chipGroup = binding.chipGroupDates
        // Check if chips already match
        if (chipGroup.childCount == dates.size) {
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                val tag = chip.tag as? String ?: continue
                chip.isChecked = (tag == selectedDate)
            }
            return
        }

        chipGroup.removeAllViews()
        for (dateStr in dates) {
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter).apply {
                tag = dateStr
                val label = runCatching {
                    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("dd MMM (EEE)"))
                }.getOrDefault(dateStr)
                text = label
                isCheckable = true
                isChecked = (dateStr == selectedDate)
                setOnClickListener { viewModel.selectDate(dateStr) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun openFile(uri: Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val chooserTitle = if (isPdf) "Open PDF with" else "Open Spreadsheet with"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (_: Throwable) {
            shareFile(uri, isPdf)
        }
    }

    private fun shareFile(uri: Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val subject = if (isPdf) "GRT Exam Hall Seating Arrangement PDF" else "GRT Exam Hall Seating Arrangement Excel/CSV"
        val chooserTitle = if (isPdf) "Share Seating Arrangement PDF" else "Share Seating Arrangement Spreadsheet"
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (_: Throwable) {
            android.widget.Toast.makeText(requireContext(), "File saved to Public Documents / GRT_Exam_Arrangement", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExamDutySummaryDialog() {
        val summaries = viewModel.uiState.value.facultyDutySummaries
        if (summaries.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No faculty duty records available yet.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val examName = viewModel.uiState.value.examName.ifBlank { "Assessment Test - I" }
        val items = summaries.map { summary ->
            val datesList = summary.assignments.map { "${it.date.takeLast(5)}: ${it.hallRoomNumber}" }.joinToString(", ")
            val datesStr = if (datesList.isNotBlank()) " ($datesList)" else ""
            "${summary.teacherName} (${summary.role.label})\n👉 ${summary.totalDuties} Total Duties in $examName$datesStr"
        }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Faculty Duty Summary · $examName")
            .setItems(items) { _, which ->
                val selected = summaries.getOrNull(which) ?: return@setItems
                showTeacherDutyBreakdown(selected)
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTeacherDutyBreakdown(summary: com.example.examhallallocation.domain.model.TeacherDutySummary) {
        val details = if (summary.assignments.isEmpty()) {
            "No active duties assigned yet for this exam."
        } else {
            summary.assignments.mapIndexed { idx, duty ->
                "${idx + 1}. Date: ${duty.date}\n   Hall: ${duty.hallRoomNumber} (${duty.floor}, ${duty.block})\n   Session: ${duty.session}"
            }.joinToString("\n\n")
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("${summary.teacherName} · ${summary.totalDuties} Total Duties")
            .setMessage("Exam: ${summary.examName}\nRole: ${summary.role.label}\n\nAllocated Duties:\n\n$details")
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SeatingAdapter : RecyclerView.Adapter<SeatingAdapter.RowViewHolder>() {
        private val items = mutableListOf<SeatingRow>()

        fun submitList(newItems: List<SeatingRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val rowBinding = ItemPreviewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RowViewHolder(rowBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            val row = items[position]
            holder.binding.tvSno.text = row.sno
            holder.binding.tvRoom.text = row.roomNumber
            holder.binding.tvFloor.text = row.floor
            holder.binding.tvDept.text = row.dept
            holder.binding.tvYearSem.text = row.yearSem
            holder.binding.tvRegFrom.text = row.regFrom
            holder.binding.tvRegTo.text = row.regTo
            holder.binding.tvCount.text = row.studentCount.toString()
            holder.binding.tvTotal.text = row.totalCount

            val hallGroup = items.take(position + 1).count { it.isNewHall }
            if (hallGroup % 2 == 0) {
                holder.binding.rowContainer.setBackgroundColor(android.graphics.Color.parseColor("#F8FAFC"))
            } else {
                holder.binding.rowContainer.setBackgroundColor(android.graphics.Color.WHITE)
            }
        }

        class RowViewHolder(val binding: ItemPreviewRowBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private class DutyAdapter : RecyclerView.Adapter<DutyAdapter.DutyViewHolder>() {
        private val items = mutableListOf<DutyRow>()

        fun submitList(newItems: List<DutyRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DutyViewHolder {
            val rowBinding = ItemDutyRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return DutyViewHolder(rowBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: DutyViewHolder, position: Int) {
            val row = items[position]
            holder.binding.tvDutySno.text = row.sno.toString()
            holder.binding.tvDutyTeacherName.text = row.teacherName
            holder.binding.tvDutyTeacherRole.text = row.teacherRole
            holder.binding.tvDutyHall.text = row.hallNumber
            holder.binding.tvDutyFloor.text = row.floor
            holder.binding.tvDutyBlock.text = row.block
            holder.binding.tvDutyTotalInExam.text = "${row.totalDutiesInExam} Duties"
            holder.binding.tvDutySession.text = row.session

            if (position % 2 == 1) {
                holder.binding.dutyRowContainer.setBackgroundColor(android.graphics.Color.parseColor("#F8FAFC"))
            } else {
                holder.binding.dutyRowContainer.setBackgroundColor(android.graphics.Color.WHITE)
            }
        }

        class DutyViewHolder(val binding: ItemDutyRowBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
