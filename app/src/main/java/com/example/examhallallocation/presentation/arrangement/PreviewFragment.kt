package com.example.examhallallocation.presentation.arrangement

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
import com.example.examhallallocation.databinding.FragmentPreviewBinding
import com.example.examhallallocation.databinding.ItemPreviewRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PreviewViewModel by viewModels()

    private val adapter = PreviewAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPreview.adapter = adapter

        binding.btnApprove.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.preview_approve_confirm))
                .setPositiveButton(getString(R.string.action_approve)) { _, _ -> viewModel.approveAll() }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        }

        binding.btnRegenerate.setOnClickListener {
            viewModel.ui.value.dates.lastOrNull()?.let { date ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.action_regenerate))
                    .setMessage(getString(R.string.preview_regenerate_confirm))
                    .setPositiveButton(getString(R.string.action_ok)) { _, _ -> viewModel.regenerateForDate(date) }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            }
        }

        binding.btnExport.setOnClickListener {
            if (viewModel.ui.value.status == com.example.examhallallocation.domain.model.ArrangementStatus.APPROVED) {
                findNavController().navigate(R.id.action_preview_to_pdf)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Approval Required")
                    .setMessage("Seating arrangement must be approved before exporting the official PDF. Would you like to approve and export now?")
                    .setPositiveButton("Approve & Export") { _, _ ->
                        viewModel.approveAll()
                        findNavController().navigate(R.id.action_preview_to_pdf)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                adapter.submitList(ui.rows)
                binding.emptyState.root.isVisible = ui.rows.isEmpty()
                binding.recyclerPreview.isVisible = ui.rows.isNotEmpty()
                binding.btnExport.isEnabled = ui.rows.isNotEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                if (message != null) {
                    android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
                    viewModel.consumeEvent()
                }
            }
        }
    }

    private class PreviewAdapter : RecyclerView.Adapter<PreviewAdapter.RowViewHolder>() {

        private val items = mutableListOf<PreviewRow>()

        fun submitList(rows: List<PreviewRow>) {
            items.clear()
            items.addAll(rows)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val binding = ItemPreviewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RowViewHolder(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            val row = items[position]
            holder.binding.tvRoom.text = row.roomNumber
            holder.binding.tvYearSem.text = "${row.yearSemester} · ${row.positions}"
            holder.binding.tvCount.text = row.studentCount.toString()
            holder.binding.tvInvigilator.text = row.invigilator
        }

        class RowViewHolder(val binding: ItemPreviewRowBinding) : RecyclerView.ViewHolder(binding.root)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
