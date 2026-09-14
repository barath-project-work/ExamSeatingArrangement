package com.example.examhallallocation.presentation.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.ItemRowManageBinding
import com.example.examhallallocation.domain.model.Hall

class HallsAdapter(
    private val onEdit: (Hall) -> Unit,
    private val onDelete: (Hall) -> Unit,
) : ListAdapter<Hall, HallsAdapter.HallViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HallViewHolder {
        val binding = ItemRowManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HallViewHolder, position: Int) = holder.bind(getItem(position))

    inner class HallViewHolder(private val binding: ItemRowManageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(hall: Hall) {
            binding.tvRowTitle.text = hall.roomNumber
            binding.tvRowSubtitle.text = "Block ${hall.block} · Floor ${hall.floor} · Capacity ${hall.capacity}"
            binding.tvRowBadge.isVisible = true
            binding.tvRowBadge.text = binding.root.context.getString(
                if (hall.active) R.string.hall_status_active else R.string.hall_status_inactive
            )
            val context = binding.root.context
            if (hall.active) {
                binding.tvRowBadge.background =
                    context.getDrawable(R.drawable.bg_badge)?.mutate()?.apply { setTint(context.getColor(R.color.status_green_bg)) }
                binding.tvRowBadge.setTextColor(context.getColor(R.color.status_green_text))
            } else {
                binding.tvRowBadge.background =
                    context.getDrawable(R.drawable.bg_badge)?.mutate()?.apply { setTint(context.getColor(R.color.status_red_bg)) }
                binding.tvRowBadge.setTextColor(context.getColor(R.color.status_red_text))
            }
            binding.ivRowIcon.setImageResource(R.drawable.ic_hall)
            binding.btnEdit.setOnClickListener { onEdit(hall) }
            binding.btnDelete.setOnClickListener { onDelete(hall) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Hall>() {
            override fun areItemsTheSame(oldItem: Hall, newItem: Hall) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Hall, newItem: Hall) = oldItem == newItem
        }
    }
}
