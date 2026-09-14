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

        binding.btnExport.setOnClickListener { viewModel.exportPdf() }
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
                binding.progress.isVisible = exportState is PdfExportViewModel.ExportState.Exporting
                binding.btnExport.isEnabled = exportState !is PdfExportViewModel.ExportState.Exporting

                when (exportState) {
                    is PdfExportViewModel.ExportState.Done -> {
                        binding.tvStatus.isVisible = true
                        binding.tvStatus.text = "PDF Generated Successfully!\nFile: ${exportState.displayName} (${exportState.fileSizeFormatted})\nSaved in Documents / GRT_Exam_Arrangement"
                        binding.tvStatus.setBackgroundResource(R.drawable.bg_banner_green)
                        binding.layoutDoneActions.isVisible = true

                        binding.btnViewPdf.setOnClickListener { openPdf(exportState.uri) }
                        binding.btnSharePdf.setOnClickListener { sharePdf(exportState.uri) }
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

    private fun openPdf(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open PDF with"))
        } catch (_: Throwable) {
            sharePdf(uri)
        }
    }

    private fun sharePdf(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "GRT Exam Hall Seating Arrangement")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Share Seating Arrangement PDF"))
        } catch (_: Throwable) {
            android.widget.Toast.makeText(requireContext(), "PDF saved to Public Documents", android.widget.Toast.LENGTH_SHORT).show()
        }
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
            holder.binding.tvRoom.text = row.roomNumber
            holder.binding.tvYearSem.text = row.yearSem
            holder.binding.tvCount.text = row.studentCount.toString()
            holder.binding.tvTotal.text = row.totalCount.toString()
        }

        class RowViewHolder(val binding: ItemPreviewRowBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
