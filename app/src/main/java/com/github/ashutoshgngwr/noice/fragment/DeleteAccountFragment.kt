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
import com.github.ashutoshgngwr.noice.databinding.DeleteAccountFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.Resource
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.trynoice.api.client.models.Profile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeleteAccountFragment : BottomSheetDialogFragment() {

  private lateinit var binding: DeleteAccountFragmentBinding
  private val viewModel: DeleteAccountViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = DeleteAccountFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onDeleteFlowComplete = this::dismiss
    lifecycleScope.launch {
      viewModel.isDeletingAccount.collect { isDeletingAccount -> isCancelable = !isDeletingAccount }
    }

    lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  var onDeleteFlowComplete: () -> Unit = {}
  val hasConfirmed = MutableStateFlow(false)

  private val profileResource = MutableStateFlow<Resource<Profile>?>(null)
  private val deleteResource = MutableStateFlow<Resource<Unit>?>(null)
  private val signOutResource = MutableStateFlow<Resource<Unit>?>(null)

  val isDeletingAccount: StateFlow<Boolean> =
    merge(profileResource, deleteResource, signOutResource).transform { r ->
      emit(r !is Resource.Failure) // only failure is a terminal state until the final operation.
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val apiErrorStrRes: StateFlow<Int?> =
    merge(profileResource, deleteResource, signOutResource).transform { r ->
      emit(
        when (r?.error) {
          null -> null
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  fun deleteAccount() = viewModelScope.launch {
    // TODO: cancel subscription before deleting the account.
    accountRepository.getProfile()
      .flowOn(Dispatchers.IO)
      .collect(profileResource)

    val accountId = profileResource.value?.data?.accountId
    if (apiErrorStrRes.value == null && accountId != null) {
      accountRepository.deleteAccount(accountId)
        .flowOn(Dispatchers.IO)
        .collect(deleteResource)
    }

    if (apiErrorStrRes.value == null) {
      accountRepository.signOut() // call signOut to clear api client's state.
        .flowOn(Dispatchers.IO)
        .collect(signOutResource)
    }

    onDeleteFlowComplete.invoke()
  }

  fun cancel() {
    onDeleteFlowComplete.invoke()
  }
}
