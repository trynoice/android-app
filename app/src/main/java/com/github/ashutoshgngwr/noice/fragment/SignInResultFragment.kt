package com.github.ashutoshgngwr.noice.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInResultFragmentBinding
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.AccountTemporarilyLockedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.signInError
        .filterNotNull()
        .collect { cause ->
          val causeStr = when (cause) {
            is AccountTemporarilyLockedError -> {
              val lockedUntil = System.currentTimeMillis() + cause.timeoutSeconds * 1000L
              val lockedUntilStr = DateUtils.getRelativeTimeSpanString(context, lockedUntil)
              getString(R.string.account_locked_error, lockedUntilStr)
            }
            is NetworkError -> getString(R.string.network_error)
            else -> getString(R.string.unknown_error)
          }

          val msg = getString(R.string.sign_in_email_error, causeStr)
          binding.error.text = msg.normalizeSpace()
        }
    }

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
      showErrorSnackBar(R.string.mailbox_app_not_found)
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
  private val signInResource = MutableSharedFlow<Resource<Unit>>()

  val isSigningIn: StateFlow<Boolean> = signInResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

  val signInError: StateFlow<Throwable?> = signInResource
    .map { it.error }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    viewModelScope.launch {
      val flow = if (isReturningUser) {
        accountRepository.signIn(email)
      } else {
        accountRepository.signUp(email, requireNotNull(name))
      }

      flow.collect(signInResource)
    }
  }
}
