package com.example.examhallallocation.presentation.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.databinding.ItemStudentBoxBinding
import com.example.examhallallocation.domain.model.Student

/**
 * Grid list adapter rendering roll number boxes.
 * Clicking any roll number box opens the clean student details modal.
 */
class StudentsAdapter(
    private val onSelect: (Student) -> Unit,
) : ListAdapter<Student, StudentsAdapter.StudentBoxViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentBoxViewHolder {
        val binding = ItemStudentBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StudentBoxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentBoxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StudentBoxViewHolder(
        private val binding: ItemStudentBoxBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(student: Student) {
            binding.tvBoxName.text = student.name
            binding.tvBoxRollNumber.text = student.registerNumber
            binding.tvBoxPosition.text = "#%03d".format(student.position)
            binding.tvBoxSubtitle.text = "Sec ${student.section.ifBlank { "A" }} · ${student.year.label}"

            binding.tvBoxStatusBadge.isVisible = !student.active
            if (!student.active) {
                binding.tvBoxStatusBadge.text = "INACTIVE (TC)"
            }

            binding.root.setOnClickListener { onSelect(student) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Student>() {
            override fun areItemsTheSame(oldItem: Student, newItem: Student) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Student, newItem: Student) = oldItem == newItem
        }
    }
}
