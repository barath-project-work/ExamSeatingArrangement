package com.example.examhallallocation.presentation.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.ItemRowManageBinding
import com.example.examhallallocation.domain.model.Teacher

class TeachersAdapter(
    private val onEdit: (Teacher) -> Unit,
    private val onDelete: (Teacher) -> Unit,
) : ListAdapter<Teacher, TeachersAdapter.TeacherViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val binding = ItemRowManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TeacherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TeacherViewHolder(private val binding: ItemRowManageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(teacher: Teacher) {
            binding.tvRowTitle.text = teacher.name
            binding.tvRowSubtitle.text = "@${teacher.username} · ${teacher.role.label}"
            binding.tvRowBadge.isVisible = !teacher.active
            binding.tvRowBadge.text = binding.root.context.getString(R.string.teacher_inactive)
            binding.tvRowBadge.setTextColor(binding.root.context.getColor(R.color.status_red_text))
            binding.ivRowIcon.setImageResource(R.drawable.ic_person)
            binding.btnEdit.setOnClickListener { onEdit(teacher) }
            binding.btnDelete.setOnClickListener { onDelete(teacher) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Teacher>() {
            override fun areItemsTheSame(oldItem: Teacher, newItem: Teacher) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Teacher, newItem: Teacher) = oldItem == newItem
        }
    }
}
