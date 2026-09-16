package com.example.examhallallocation.presentation.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.examhallallocation.R
import com.example.examhallallocation.databinding.DialogHallBinding
import com.example.examhallallocation.databinding.FragmentHallsBinding
import com.example.examhallallocation.domain.model.Hall
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HallsFragment : Fragment() {

    private var _binding: FragmentHallsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HallsViewModel by viewModels()

    private lateinit var adapter: HallsAdapter

    private val pickFileLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { readCsv(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HallsAdapter(onEdit = ::showEditDialog, onDelete = ::confirmDelete)
        binding.recyclerHalls.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHalls.adapter = adapter
        binding.btnExportHalls.setOnClickListener { showExportHallsDialog() }
        binding.btnAddHall.setOnClickListener { showEditDialog(null) }
        binding.btnImportHalls.setOnClickListener { showImportChoiceDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.halls.collect { halls ->
                adapter.submitList(halls)
                binding.emptyState.root.isVisible = halls.isEmpty()
                binding.recyclerHalls.isVisible = halls.isNotEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                if (message != null) {
                    android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }
            }
        }
    }

    private fun showExportHallsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Examination Halls")
            .setItems(
                arrayOf(
                    "Export as PDF Document",
                    "Export as Excel / CSV Spreadsheet",
                )
            ) { _, which ->
                val asPdf = which == 0
                android.widget.Toast.makeText(requireContext(), "Generating halls export...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.exportHalls(
                    asPdf = asPdf,
                    onDone = { uri, fileName ->
                        android.widget.Toast.makeText(requireContext(), "Saved: $fileName", android.widget.Toast.LENGTH_LONG).show()
                        openOrShareFile(uri, asPdf)
                    },
                    onError = { error ->
                        android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun openOrShareFile(uri: android.net.Uri, isPdf: Boolean) {
        val mimeType = if (isPdf) "application/pdf" else "text/csv"
        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(viewIntent, "Open Halls Directory")
        chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(chooser)
        }.onFailure {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Halls Directory"))
        }
    }

    private fun showEditDialog(existing: Hall?) {
        val dialogBinding = DialogHallBinding.inflate(layoutInflater)
        existing?.let {
            dialogBinding.etRoomNumber.setText(it.roomNumber)
            dialogBinding.etBlock.setText(it.block)
            dialogBinding.etFloor.setText(it.floor.toString())
            dialogBinding.etCapacity.setText(it.capacity.toString())
            dialogBinding.switchActive.isChecked = it.active
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(if (existing == null) R.string.halls_add else R.string.action_edit))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val room = dialogBinding.etRoomNumber.text?.toString()?.trim().orEmpty()
                val block = dialogBinding.etBlock.text?.toString()?.trim().orEmpty()
                val floor = dialogBinding.etFloor.text?.toString()?.toIntOrNull() ?: 0
                val capacity = dialogBinding.etCapacity.text?.toString()?.toIntOrNull() ?: 0
                if (existing == null) {
                    viewModel.addHall(room, block, floor, capacity)
                } else {
                    viewModel.updateHall(existing, room, block, floor, capacity, dialogBinding.switchActive.isChecked)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun confirmDelete(hall: Hall) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title, hall.roomNumber))
            .setMessage(getString(R.string.confirm_delete_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ -> viewModel.deleteHall(hall) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun readCsv(uri: android.net.Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { viewModel.importFile(it) }
            ?: android.widget.Toast.makeText(requireContext(), "Could not read the selected file", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showImportChoiceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Exam Halls / Rooms")
            .setItems(arrayOf("Choose CSV / Excel File (.csv, .tsv)", "Paste Rooms from Excel / Clipboard")) { _, which ->
                when (which) {
                    0 -> pickFileLauncher.launch("*/*")
                    1 -> showPasteDialog()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showPasteDialog() {
        val dialogBinding = com.example.examhallallocation.databinding.DialogPasteDataBinding.inflate(layoutInflater)
        dialogBinding.tvPasteHint.text = "Copy rooms from your Excel sheet or CSV and paste below. The app will automatically extract room number, block, and capacity."
        dialogBinding.etPasteInput.hint = "Paste Excel rows or CSV text here...\n(Columns: RoomNumber, Block, Floor, Capacity)"
        dialogBinding.tvFormatGuide.text = "Format: RoomNumber, Block, Floor, Capacity"

        dialogBinding.etPasteInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val lines = s?.toString()?.lineSequence()?.filter { it.isNotBlank() }?.count() ?: 0
                dialogBinding.tvLineCount.text = "$lines rooms detected"
            }
        })

        dialogBinding.btnPasteClipboard.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0).coerceToText(requireContext()).toString()
                dialogBinding.etPasteInput.setText(pasteText)
                dialogBinding.etPasteInput.setSelection(dialogBinding.etPasteInput.text.length)
            } else {
                android.widget.Toast.makeText(requireContext(), "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnClearText.setOnClickListener {
            dialogBinding.etPasteInput.setText("")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Paste Room Data")
            .setView(dialogBinding.root)
            .setPositiveButton("Import Rooms") { _, _ ->
                val text = dialogBinding.etPasteInput.text.toString().trim()
                if (text.isNotBlank()) {
                    viewModel.importCsv(text)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
