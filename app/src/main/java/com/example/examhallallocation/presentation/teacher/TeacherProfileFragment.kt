package com.example.examhallallocation.presentation.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.examhallallocation.R
import com.example.examhallallocation.data.repository.AuthRepository
import com.example.examhallallocation.databinding.FragmentTeacherProfileBinding
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TeacherProfileFragment : Fragment() {

    private var _binding: FragmentTeacherProfileBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTeacherProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            val session = authRepository.currentUser()
            binding.tvName.text = session?.name.orEmpty()
            binding.tvUsername.text = "@${session?.username.orEmpty()}"
            binding.tvRole.text = session?.role?.label.orEmpty()
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                findNavController().navigate(R.id.action_profile_to_login)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
