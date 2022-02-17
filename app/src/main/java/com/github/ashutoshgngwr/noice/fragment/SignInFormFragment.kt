package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInFormFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.validator.routines.EmailValidator
import javax.inject.Inject

@AndroidEntryPoint
class SignInFormFragment : Fragment() {

  private lateinit var binding: SignInFormFragmentBinding
  private val viewModel: SignInFormViewModel by viewModels()
  private val navArgs: SignInFormFragmentArgs by navArgs()

  override fun onAttach(context: Context) {
    super.onAttach(context)

    // workaround until the upstream issue resolved!
    // https://issuetracker.google.com/issues/167959935
    (activity as? AppCompatActivity)
      ?.supportActionBar
      ?.setTitle(navArgs.title)
  }

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
  val name = MutableStateFlow("")
  val isNameValid = MutableStateFlow(true)
  val email = MutableStateFlow("")
  val isEmailValid = MutableStateFlow(true)
  var onSignFormSubmitted: (SignInResultFragmentArgs) -> Unit = {}

  init {
    val navArgs = SignInFormFragmentArgs.fromSavedStateHandle(savedStateHandle)
    isReturningUser = navArgs.isReturningUser
  }

  fun validateName(): Boolean {
    // maxLength: 64
    // minLength: 1
    val v = name.value
    val isValid = v.isNotBlank() && v.length <= 64
    viewModelScope.launch { isNameValid.emit(isValid) }
    return isValid
  }

  fun validateEmail(): Boolean {
    // maxLength: 64
    // minLength: 3
    val v = email.value
    val isValid = v.isNotBlank()
      && v.length <= 64
      && EmailValidator.getInstance(false, false).isValid(v)

    viewModelScope.launch { isEmailValid.emit(isValid) }
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
