package com.github.ashutoshgngwr.noice.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInResultFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showSnackbar
import com.github.ashutoshgngwr.noice.model.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onOpenMailboxClicked = this::openMailbox
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
      showSnackbar(R.string.mailbox_app_not_found)
    }
  }
}

@HiltViewModel
class SignInResultViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val accountRepository: AccountRepository
) : ViewModel() {

  var onOpenMailboxClicked: () -> Unit = {}
  val isSigningIn = MutableStateFlow(false)
  val isSignInComplete = MutableStateFlow(false)
  val signInError = MutableStateFlow<Throwable?>(null)
  val isAccountLocked: StateFlow<Boolean> = signInError.transform { e ->
    emit(e is AccountTemporarilyLockedError)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

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

  val accountLockTimeoutMinutes: StateFlow<Int> = signInError.transform { e ->
    if (e is AccountTemporarilyLockedError) {
      emit(ceil(e.timeoutSeconds / 60.0).toInt())
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

  val email: String
  val isReturningUser: Boolean
  private val name: String?

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
    if (isSignInComplete.value || isSigningIn.value) {
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      signInError.emit(null)
      isSigningIn.emit(true)

      try {
        if (isReturningUser) {
          accountRepository.signIn(email)
        } else {
          accountRepository.signUp(email, requireNotNull(name))
        }

        isSignInComplete.emit(true)
      } catch (e: Throwable) {
        signInError.emit(e)
      } finally {
        isSigningIn.emit(false)
      }
    }
  }
}
