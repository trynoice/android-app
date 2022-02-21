package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignOutFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SignOutFragment : BottomSheetDialogFragment() {

  private lateinit var binding: SignOutFragmentBinding
  private val viewModel: SignOutViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignOutFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onSignOutFlowComplete = this::dismiss
    lifecycleScope.launch {
      viewModel.isSigningOut.collect { isSigningOut -> isCancelable = !isSigningOut }
    }

    lifecycleScope.launch {
      viewModel.signOutErrorStrRes
        .filterNotNull()
        .filterNot { strRes -> strRes == ResourcesCompat.ID_NULL }
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class SignOutViewModel @Inject constructor(
  private val accountRepository: AccountRepository
) : ViewModel() {

  var onSignOutFlowComplete: () -> Unit = {}
  val isSigningOut = MutableStateFlow(false)
  private val signOutError = MutableStateFlow<Throwable?>(null)
  internal val signOutErrorStrRes: StateFlow<Int?> = signOutError.transform { error ->
    emit(
      when (error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  fun signOut() {
    viewModelScope.launch(Dispatchers.IO) {
      isSigningOut.emit(true)
      try {
        accountRepository.signOut()
      } catch (e: Throwable) {
        signOutError.emit(e)
      } finally {
        withContext(Dispatchers.Main) { onSignOutFlowComplete.invoke() }
      }
    }
  }

  fun cancel() {
    onSignOutFlowComplete.invoke()
  }
}
