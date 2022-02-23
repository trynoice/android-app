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
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
      viewModel.deleteAccountErrorStrRes
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
  val isDeletingAccount = MutableStateFlow(false)
  private val deleteAccountError = MutableStateFlow<Throwable?>(null)
  internal val deleteAccountErrorStrRes: StateFlow<Int?> = deleteAccountError.transform { error ->
    emit(
      when (error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  fun deleteAccount() {
    viewModelScope.launch(Dispatchers.IO) {
      isDeletingAccount.emit(true)
      try {
        // TODO: cancel subscription before deleting the account.
        val profile = accountRepository.getProfile()
          .transform { emit(it.data) }
          .filterNotNull()
          .first()

        accountRepository.deleteAccount(profile.accountId)
        accountRepository.signOut() // call signOut to clear api client's state.
      } catch (e: Throwable) {
        deleteAccountError.emit(e)
      } finally {
        withContext(Dispatchers.Main) { onDeleteFlowComplete.invoke() }
      }
    }
  }

  fun cancel() {
    onDeleteFlowComplete.invoke()
  }
}
