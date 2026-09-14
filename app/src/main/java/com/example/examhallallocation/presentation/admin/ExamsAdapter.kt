package com.example.examhallallocation.presentation.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.ItemRowManageBinding
import com.example.examhallallocation.domain.model.Exam
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExamsAdapter(
    private val onEdit: (Exam) -> Unit,
    private val onDelete: (Exam) -> Unit,
) : ListAdapter<Exam, ExamsAdapter.ExamViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val binding = ItemRowManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExamViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ExamViewHolder(private val binding: ItemRowManageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(exam: Exam) {
            val dateLabel = runCatching {
                LocalDate.parse(exam.date).format(DateTimeFormatter.ofPattern("dd-MM-yyyy (EEE)", Locale.ENGLISH))
            }.getOrDefault(exam.date)

            binding.tvRowTitle.text = "[${exam.subjectCode}] ${exam.subjectName}"

            val timingStr = if (exam.timing.isNotBlank()) " (${exam.timing})" else ""
            binding.tvRowSubtitle.text = "$dateLabel · ${exam.session}$timingStr · ${exam.department}"

            binding.tvRowBadge.isVisible = true
            val countStr = if (exam.studentCount > 0) " · ${exam.studentCount} Students" else ""
            binding.tvRowBadge.text = "${exam.year.label} · Sem ${exam.semester.toString().padStart(2, '0')}$countStr"

            binding.ivRowIcon.setImageResource(R.drawable.ic_calendar)

            binding.btnEdit.isVisible = true
            binding.btnEdit.setOnClickListener { onEdit(exam) }
            binding.btnDelete.setOnClickListener { onDelete(exam) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Exam>() {
            override fun areItemsTheSame(oldItem: Exam, newItem: Exam) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Exam, newItem: Exam) = oldItem == newItem
        }
    }
}
