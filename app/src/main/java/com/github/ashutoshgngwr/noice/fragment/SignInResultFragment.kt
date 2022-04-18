package com.github.ashutoshgngwr.noice.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInResultFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

@AndroidEntryPoint
class SignInResultFragment : Fragment() {

  private lateinit var binding: SignInResultFragmentBinding
  private val viewModel: SignInResultViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignInResultFragmentBinding.inflate(inflater, container, false)
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
    binding.openMailbox.setOnClickListener { openMailbox() }
    viewModel.signIn()
  }

  private fun openMailbox() {
    try {
      requireContext().startActivity(
        Intent(Intent.ACTION_MAIN)
          .addCategory(Intent.CATEGORY_APP_EMAIL)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (e: ActivityNotFoundException) {
      showErrorSnackbar(R.string.mailbox_app_not_found)
    }
  }
}

@HiltViewModel
class SignInResultViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val accountRepository: AccountRepository
) : ViewModel() {

  val isReturningUser: Boolean
  val email: String
  private val name: String?
  private val signInResource = MutableStateFlow<Resource<Unit>>(Resource.Loading())

  val isSigningIn: StateFlow<Boolean> = signInResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val isSignInSuccess: StateFlow<Boolean> = signInResource.transform { r ->
    emit(r is Resource.Success)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val signInError: StateFlow<Throwable?> = signInResource.transform { r ->
    emit(r.error)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  val isAccountLocked: StateFlow<Boolean> = signInError.transform { e ->
    emit(e is AccountTemporarilyLockedError)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val accountLockTimeoutMinutes: StateFlow<Int> = signInError.transform { e ->
    if (e is AccountTemporarilyLockedError) {
      emit(ceil(e.timeoutSeconds / 60.0).toInt())
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

  val signInErrorStrRes: StateFlow<Int?> = signInError.transform { e ->
    emit(
      when (e) {
        null -> null
        is AccountTemporarilyLockedError -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  init {
    val navArgs = SignInResultFragmentArgs.fromSavedStateHandle(savedStateHandle)
    isReturningUser = navArgs.isReturningUser
    email = navArgs.email
    name = navArgs.name

    if (!isReturningUser && name == null) {
      throw IllegalArgumentException("name is required to perform sign-up")
    }
  }

  fun signIn() {
    if (isSignInSuccess.value || isSigningIn.value) {
      return
    }

    viewModelScope.launch {
      val flow = if (isReturningUser) {
        accountRepository.signIn(email)
      } else {
        accountRepository.signUp(email, requireNotNull(name))
      }

      flow.flowOn(Dispatchers.IO).collect(signInResource)
    }
  }
}
