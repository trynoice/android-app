package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInFormFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import org.apache.commons.validator.routines.EmailValidator
import javax.inject.Inject

@AndroidEntryPoint
class SignInFormFragment : Fragment() {

  private lateinit var binding: SignInFormFragmentBinding
  private val viewModel: SignInFormViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignInFormFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onSignFormSubmitted = { args ->
      Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
        .navigate(R.id.sign_in_result, args.toBundle())
    }
  }
}

@HiltViewModel
class SignInFormViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

  val isReturningUser: Boolean
  val name = MutableLiveData("")
  val isNameValid = MutableLiveData(true)
  val email = MutableLiveData("")
  val isEmailValid = MutableLiveData(true)
  var onSignFormSubmitted: (SignInResultFragmentArgs) -> Unit = {}

  init {
    val navArgs = SignInFormFragmentArgs.fromSavedStateHandle(savedStateHandle)
    isReturningUser = navArgs.isReturningUser
  }

  fun validateName(): Boolean {
    // maxLength: 64
    // minLength: 1
    val text = name.value ?: ""
    val isValid = text.isNotBlank() && text.length <= 64
    isNameValid.postValue(isValid)
    return isValid
  }

  fun validateEmail(): Boolean {
    // maxLength: 64
    // minLength: 3
    val text = email.value ?: ""
    val isValid = text.isNotBlank()
      && text.length <= 64
      && EmailValidator.getInstance(false, false).isValid(text)

    isEmailValid.postValue(isValid)
    return isValid
  }

  fun signIn() {
    val isNameValid = isReturningUser || validateName()
    val isEmailValid = validateEmail()
    if (!isNameValid || !isEmailValid) {
      return
    }

    onSignFormSubmitted.invoke(
      SignInResultFragmentArgs(
        isReturningUser = isReturningUser,
        name = name.value,
        email = requireNotNull(email.value),
      )
    )
  }
}
