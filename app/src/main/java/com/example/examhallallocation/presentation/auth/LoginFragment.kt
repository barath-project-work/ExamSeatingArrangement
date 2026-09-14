package com.example.examhallallocation.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.examhallallocation.R
import com.example.examhallallocation.data.seed.SeedDataProvider
import com.example.examhallallocation.databinding.FragmentLoginBinding
import com.example.examhallallocation.domain.model.UserRole
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDemoNote.isVisible = viewModel.isDemoMode

        binding.btnSignIn.setOnClickListener {
            viewModel.login(
                username = binding.etUsername.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty(),
            )
        }

        binding.etUsername.doAfterTextChanged { binding.tvError.isVisible = false }
        binding.etPassword.doAfterTextChanged { binding.tvError.isVisible = false }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is LoginViewModel.LoginUiState.Idle -> Unit
                    is LoginViewModel.LoginUiState.Loading -> {
                        binding.btnSignIn.isEnabled = false
                    }
                    is LoginViewModel.LoginUiState.Error -> {
                        binding.btnSignIn.isEnabled = true
                        binding.tvError.text = state.message
                        binding.tvError.isVisible = true
                        viewModel.consumeError()
                    }
                    is LoginViewModel.LoginUiState.Authenticated -> navigateByRole(state.role)
                }
            }
        }
    }

    private fun navigateByRole(role: UserRole) {
        when (role) {
            UserRole.ADMIN -> findNavController().navigate(R.id.action_login_to_admin)
            else -> findNavController().navigate(R.id.action_login_to_teacher)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
