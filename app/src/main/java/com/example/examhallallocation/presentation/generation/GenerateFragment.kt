package com.example.examhallallocation.presentation.generation

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
import com.example.examhallallocation.databinding.FragmentGenerateBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class GenerateFragment : Fragment() {

    private var _binding: FragmentGenerateBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GenerateViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGenerateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGenerate.setOnClickListener { viewModel.generate() }
        binding.btnGoPreview.setOnClickListener { findNavController().navigate(R.id.action_generate_to_preview) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                binding.tvExamName.text = ui.examName.ifBlank { getString(R.string.exams_empty) }
                binding.tvDate.text = ui.date.takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")) }.getOrDefault(it)
                } ?: getString(R.string.exams_empty)
                binding.tvPhase.text = when (ui.phase) {
                    com.example.examhallallocation.domain.model.ExamPhase.PHASE_1 -> getString(R.string.phase_1)
                    com.example.examhallallocation.domain.model.ExamPhase.PHASE_2 -> getString(R.string.phase_2)
                    com.example.examhallallocation.domain.model.ExamPhase.PHASE_3 -> getString(R.string.phase_3)
                }
                binding.tvYears.text = ui.yearsLabel

                binding.btnGenerate.isEnabled = !ui.running && ui.date.isNotBlank()
                binding.progress.isVisible = ui.running

                binding.panelError.isVisible = ui.errors.isNotEmpty()
                binding.tvErrors.text = ui.errors.joinToString("\n• ", prefix = "• ")

                val summary = ui.summary
                binding.cardSummary.isVisible = summary != null
                summary?.let {
                    binding.tvHallsUsed.text = it.hallsUsed.toString()
                    binding.tvStudentsSeated.text = it.studentsSeated.toString()
                    binding.tvInvigilators.text = it.invigilators.toString()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNextDate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
