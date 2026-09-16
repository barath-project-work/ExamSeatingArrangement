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
    private val onDutyClick: (Teacher, com.example.examhallallocation.domain.model.TeacherDutySummary?) -> Unit,
) : ListAdapter<Teacher, TeachersAdapter.TeacherViewHolder>(DIFF) {

    private var dutyMap: Map<String, com.example.examhallallocation.domain.model.TeacherDutySummary> = emptyMap()
    private var examName: String = "Assessment Test - I"

    fun setDutySummaries(summaries: List<com.example.examhallallocation.domain.model.TeacherDutySummary>, exam: String) {
        dutyMap = summaries.associateBy { it.teacherId }
        examName = exam
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val binding = ItemRowManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TeacherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TeacherViewHolder(private val binding: ItemRowManageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(teacher: Teacher) {
            val dutySummary = dutyMap[teacher.id]
            binding.tvRowTitle.text = teacher.name
            binding.tvRowSubtitle.text = "@${teacher.username} · ${teacher.role.label}"

            val isStaff = teacher.role != com.example.examhallallocation.domain.model.UserRole.ADMIN &&
                teacher.role != com.example.examhallallocation.domain.model.UserRole.HOD

            if (!teacher.active) {
                binding.tvRowBadge.isVisible = true
                binding.tvRowBadge.text = binding.root.context.getString(R.string.teacher_inactive)
                binding.tvRowBadge.setTextColor(binding.root.context.getColor(R.color.status_red_text))
            } else if (isStaff && dutySummary != null) {
                binding.tvRowBadge.isVisible = true
                binding.tvRowBadge.text = "${dutySummary.totalDuties} Duties ($examName)"
                binding.tvRowBadge.setTextColor(binding.root.context.getColor(R.color.navy_primary))
                binding.tvRowBadge.setBackgroundResource(R.drawable.bg_badge)
            } else {
                binding.tvRowBadge.isVisible = false
            }

            binding.ivRowIcon.setImageResource(R.drawable.ic_person)
            binding.btnEdit.setOnClickListener { onEdit(teacher) }
            binding.btnDelete.setOnClickListener { onDelete(teacher) }
            binding.root.setOnClickListener { onDutyClick(teacher, dutySummary) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Teacher>() {
            override fun areItemsTheSame(oldItem: Teacher, newItem: Teacher) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Teacher, newItem: Teacher) = oldItem == newItem
        }
    }
}
