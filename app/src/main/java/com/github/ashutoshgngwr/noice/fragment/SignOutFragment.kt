package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignOutFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
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
    viewModel.onFlowComplete = this::dismiss
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isSigningOut.collect { isSigningOut -> isCancelable = !isSigningOut }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.signOutErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class SignOutViewModel @Inject constructor(
  private val accountRepository: AccountRepository
) : ViewModel() {

  var onFlowComplete: () -> Unit = {}
  private val signOutResource = MutableStateFlow<Resource<Unit>?>(null)

  val isSigningOut: StateFlow<Boolean> = signOutResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val signOutErrorStrRes: Flow<Int?> = signOutResource.transform { r ->
    emit(
      when (r?.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  fun signOut() = viewModelScope.launch {
    accountRepository.signOut()
      .flowOn(Dispatchers.IO)
      .onCompletion { onFlowComplete.invoke() }
      .collect(signOutResource)
  }

  fun cancel() {
    onFlowComplete.invoke()
  }
}
