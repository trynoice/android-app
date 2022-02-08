package com.github.ashutoshgngwr.noice.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInResultFragmentBinding
import com.github.ashutoshgngwr.noice.model.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

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
      Snackbar.make(requireView(), R.string.mailbox_app_not_found, Snackbar.LENGTH_LONG).show()
    }
  }
}

@HiltViewModel
class SignInResultViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val accountRepository: AccountRepository
) : ViewModel() {

  var onOpenMailboxClicked: () -> Unit = {}
  val isSigningIn = MutableLiveData(false)
  val isSignInComplete = MutableLiveData(false)
  val signInError = MutableLiveData<Throwable?>()
  val isAccountLocked = MediatorLiveData<Boolean>().also {
    it.addSource(signInError) { e -> it.postValue(e is AccountTemporarilyLockedError) }
  }

  val signInErrorStrRes = MediatorLiveData<Int?>().also {
    it.addSource(signInError) { e ->
      it.postValue(
        when (e) {
          null -> null
          is AccountTemporarilyLockedError -> null
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }
  }

  val accountLockTimeoutMinutes = MediatorLiveData<Int>().also {
    it.value = 0 // data binding causes NPE without it.
    it.addSource(signInError) { e ->
      if (e is AccountTemporarilyLockedError) {
        it.postValue((e.timeoutSeconds / 60.0).roundToInt())
      }
    }
  }

  val email: String
  private val isReturningUser: Boolean
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
    if (isSignInComplete.value == true || isSigningIn.value == true) {
      return
    }

    signInError.postValue(null)
    isSigningIn.postValue(true)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        if (isReturningUser) {
          accountRepository.signIn(email)
        } else {
          accountRepository.signUp(email, requireNotNull(name))
        }

        isSignInComplete.postValue(true)
      } catch (e: Throwable) {
        signInError.postValue(e)
      } finally {
        isSigningIn.postValue(false)
      }
    }
  }
}
