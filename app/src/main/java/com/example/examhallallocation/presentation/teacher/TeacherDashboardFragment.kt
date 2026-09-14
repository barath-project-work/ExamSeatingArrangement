package com.example.examhallallocation.presentation.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.FragmentTeacherDashboardBinding
import com.example.examhallallocation.databinding.ItemTeacherStudentBinding
import com.example.examhallallocation.domain.model.DutyDay
import com.example.examhallallocation.domain.model.HallStudentAttendance
import com.example.examhallallocation.domain.model.UserRole
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class TeacherDashboardFragment : Fragment() {

    private var _binding: FragmentTeacherDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TeacherDashboardViewModel by viewModels()

    private lateinit var studentsAdapter: StudentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTeacherDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        studentsAdapter = StudentsAdapter(onToggle = viewModel::toggleAttendance)
        binding.recyclerStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStudents.adapter = studentsAdapter

        binding.btnProfile.setOnClickListener { findNavController().navigate(R.id.action_teacher_to_profile) }
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.confirm_logout))
                .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
                    viewModel.logout { findNavController().navigate(R.id.action_teacher_to_login) }
                }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        }

        binding.btnExportAttendance.setOnClickListener {
            showExportAttendanceDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collect { ui ->
                val session = ui.session
                if (session?.role == UserRole.ADMIN) {
                    findNavController().navigate(R.id.action_teacher_to_admin)
                    return@collect
                }
                binding.tvWelcome.text = getString(
                    R.string.teacher_welcome_format,
                    session?.name ?: "",
                )

                binding.tvRoleNote.isVisible = when (session?.role) {
                    UserRole.HOD -> true
                    UserRole.EXAM_CELL_COORDINATOR -> true
                    else -> false
                }
                binding.tvRoleNote.text = when (session?.role) {
                    UserRole.HOD -> getString(R.string.hod_note)
                    UserRole.EXAM_CELL_COORDINATOR -> getString(R.string.coordinator_note)
                    else -> ""
                }

                if (ui.duties.isEmpty()) {
                    binding.tvDutyStatus.text = getString(R.string.teacher_no_duty)
                    binding.tvDutyStatus.setTextColor(requireContext().getColor(R.color.text_secondary))
                    binding.rowsDutyDetails.removeAllViews()
                    binding.tvStudentsHeader.isVisible = false
                    binding.recyclerStudents.isVisible = false
                    binding.layoutAttendanceStats.isVisible = false
                    binding.btnExportAttendance.isVisible = false
                    binding.tvAttendanceHint.isVisible = false
                } else {
                    val first = ui.duties.first()
                    val dateLabel = runCatching {
                        LocalDate.parse(first.date).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
                    }.getOrDefault(first.date)
                    binding.tvDutyStatus.text = getString(R.string.teacher_duty_on, dateLabel)
                    binding.tvDutyStatus.setTextColor(requireContext().getColor(R.color.status_green_text))
                    renderDutyDetails(ui.duties)

                    val hasStudents = ui.hallStudents.isNotEmpty()
                    binding.tvStudentsHeader.isVisible = hasStudents
                    binding.recyclerStudents.isVisible = hasStudents
                    binding.layoutAttendanceStats.isVisible = hasStudents
                    binding.btnExportAttendance.isVisible = hasStudents
                    binding.tvAttendanceHint.isVisible = hasStudents

                    binding.tvTallyEligible.text = ui.totalEligible.toString()
                    binding.tvTallyPresent.text = ui.totalPresent.toString()
                    binding.tvTallyAbsent.text = ui.totalAbsent.toString()

                    studentsAdapter.submit(ui.hallStudents)
                }

                binding.tvTotalDuties.text = ui.duties.size.toString()
                binding.tvFreeDays.text = (ui.totalExamDays - ui.duties.size).coerceAtLeast(0).toString()
            }
        }
    }

    private fun showExportAttendanceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Hall Attendance Sheet")
            .setItems(
                arrayOf(
                    "Download PDF Attendance Sheet",
                    "Download Excel / CSV Attendance Sheet",
                )
            ) { _, which ->
                val asPdf = which == 0
                Toast.makeText(requireContext(), "Generating attendance sheet...", Toast.LENGTH_SHORT).show()
                viewModel.exportAttendance(
                    asPdf = asPdf,
                    onDone = { uri, fileName ->
                        Toast.makeText(requireContext(), "Saved: $fileName", Toast.LENGTH_LONG).show()
                        openOrShareFile(uri, asPdf)
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun openOrShareFile(uri: Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, "Open Attendance Sheet")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Attendance Sheet"))
        }
    }

    private fun renderDutyDetails(duties: List<DutyDay>) {
        binding.rowsDutyDetails.removeAllViews()
        duties.take(1).forEach { duty ->
            listOf(
                getString(R.string.teacher_my_hall) to "${duty.roomNumber} (Block ${duty.block}, Floor ${duty.floor})",
                getString(R.string.preview_col_year) to duty.yearSemester,
                getString(R.string.generate_exam_label) to duty.subjects,
            ).forEach { (label, value) ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, resources.getDimensionPixelSize(R.dimen.space_4), 0, 0)
                }
                val labelView = TextView(requireContext()).apply {
                    text = label
                    textSize = 12f
                    setTextColor(requireContext().getColor(R.color.text_secondary))
                }
                val valueView = TextView(requireContext()).apply {
                    text = value
                    textSize = 13f
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(labelView)
                row.addView(valueView)
                binding.rowsDutyDetails.addView(row)
            }
        }
    }

    private class StudentsAdapter(
        private val onToggle: (studentId: String, isPresent: Boolean) -> Unit,
    ) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

        private val items = mutableListOf<HallStudentAttendance>()

        fun submit(list: List<HallStudentAttendance>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val binding = ItemTeacherStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return StudentViewHolder(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context

            holder.binding.tvRegister.text = item.registerNumber
            holder.binding.tvName.text = item.name
            holder.binding.tvYearBadge.text = "${item.yearLabel} · Sec ${item.section}"

            // Detach listener prior to programmatically updating state
            holder.binding.switchAttendance.setOnCheckedChangeListener(null)
            holder.binding.switchAttendance.isChecked = item.isPresent

            if (item.isPresent) {
                holder.binding.tvAttendanceStatus.text = "PRESENT"
                holder.binding.tvAttendanceStatus.setTextColor(context.getColor(R.color.status_green_text))
            } else {
                holder.binding.tvAttendanceStatus.text = "ABSENT"
                holder.binding.tvAttendanceStatus.setTextColor(context.getColor(R.color.status_red_text))
            }

            holder.binding.switchAttendance.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    holder.binding.tvAttendanceStatus.text = "PRESENT"
                    holder.binding.tvAttendanceStatus.setTextColor(context.getColor(R.color.status_green_text))
                } else {
                    holder.binding.tvAttendanceStatus.text = "ABSENT"
                    holder.binding.tvAttendanceStatus.setTextColor(context.getColor(R.color.status_red_text))
                }
                onToggle(item.id, isChecked)
            }
        }

        class StudentViewHolder(val binding: ItemTeacherStudentBinding) :
            RecyclerView.ViewHolder(binding.root)
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
