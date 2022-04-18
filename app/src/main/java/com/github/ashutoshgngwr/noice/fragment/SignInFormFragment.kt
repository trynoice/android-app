package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInFormFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.commons.validator.routines.EmailValidator
import javax.inject.Inject

@AndroidEntryPoint
class SignInFormFragment : Fragment() {

  private lateinit var binding: SignInFormFragmentBinding
  private val viewModel: SignInFormViewModel by viewModels()
  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignInFormFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // TODO: workaround until the upstream issue resolved!
    //  https://issuetracker.google.com/issues/167959935
    (activity as? AppCompatActivity)
      ?.supportActionBar
      ?.setTitle(
        if (viewModel.isReturningUser) {
          R.string.sign_in
        } else {
          R.string.sign_up
        }
      )

    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.signIn.setOnClickListener {
      mainNavController.navigate(
        R.id.sign_in_result,
        SignInResultFragmentArgs(
          isReturningUser = viewModel.isReturningUser,
          name = viewModel.name.value,
          email = requireNotNull(viewModel.email.value),
        ).toBundle()
      )
    }
  }
}

@HiltViewModel
class SignInFormViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

  val isReturningUser: Boolean = SignInFormFragmentArgs.fromSavedStateHandle(savedStateHandle)
    .isReturningUser

  val name = MutableStateFlow("")
  val isNameValid = MutableStateFlow(isReturningUser)
  val email = MutableStateFlow("")
  val isEmailValid = MutableStateFlow(false)

  fun validateName() {
    // maxLength: 64
    // minLength: 1
    val v = name.value
    isNameValid.value = isReturningUser || (v.isNotBlank() && v.length <= 64)
  }

  fun validateEmail() {
    // maxLength: 64
    // minLength: 3
    val v = email.value
    isEmailValid.value = v.isNotBlank()
      && v.length <= 64
      && EmailValidator.getInstance(false, false).isValid(v)
  }
}
