package com.example.examhallallocation.presentation.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.databinding.ItemSubjectBinding
import com.example.examhallallocation.domain.model.Subject

class SubjectsAdapter(
    private val onEdit: (Subject) -> Unit,
    private val onDelete: (Subject) -> Unit,
) : ListAdapter<Subject, SubjectsAdapter.SubjectViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) = holder.bind(getItem(position))

    inner class SubjectViewHolder(private val binding: ItemSubjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subject: Subject) {
            binding.tvSubjectCode.text = subject.code
            binding.tvDepartment.text = subject.department.ifBlank { "CSE" }
            binding.tvSubjectName.text = subject.name
            binding.tvYearSem.text = "${subject.year.label} · Semester ${subject.semester}"

            binding.btnEdit.setOnClickListener { onEdit(subject) }
            binding.btnDelete.setOnClickListener { onDelete(subject) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Subject>() {
            override fun areItemsTheSame(oldItem: Subject, newItem: Subject) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Subject, newItem: Subject) = oldItem == newItem
        }
    }
}
