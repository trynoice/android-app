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
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.models.Profile
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
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
    binding.cancel.setOnClickListener { dismiss() }
    binding.delete.setOnClickListener { viewModel.deleteAccount() }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isDeletingAccount.collect { isCancelable = !it }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isFlowComplete
        .filter { it }
        .collect { dismiss() }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.delete_account_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace())
        }
    }
  }
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
  private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  val hasConfirmed = MutableStateFlow(false)

  private val activeSubscriptionResource = MutableStateFlow<Resource<Subscription>?>(null)
  private val cancelSubscriptionResource = MutableStateFlow<Resource<Unit>?>(null)
  private val profileResource = MutableStateFlow<Resource<Profile>?>(null)
  private val deleteResource = MutableStateFlow<Resource<Unit>?>(null)
  private val signOutResource = MutableStateFlow<Resource<Unit>?>(null)

  val isDeletingAccount: StateFlow<Boolean> = merge(
    activeSubscriptionResource,
    cancelSubscriptionResource,
    profileResource,
    deleteResource,
    signOutResource
  ).transform { r ->
    emit(r != null && r !is Resource.Failure) // only failure is a terminal state until the final operation.
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val apiErrorStrRes: StateFlow<Int?> = merge(
    activeSubscriptionResource.filterNot { it?.error is SubscriptionNotFoundError },
    cancelSubscriptionResource.filterNot { it?.error is SubscriptionNotFoundError },
    profileResource,
    deleteResource,
    signOutResource
  ).transform { r ->
    emit(
      when (r?.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal val isFlowComplete = MutableStateFlow(false)

  fun deleteAccount() = viewModelScope.launch {
    subscriptionRepository.getActive().collect(activeSubscriptionResource)

    val subscription = activeSubscriptionResource.value?.data
    if (apiErrorStrRes.value == null && subscription != null) {
      subscriptionRepository.cancel(subscription).collect(cancelSubscriptionResource)
    }

    if (apiErrorStrRes.value == null) {
      accountRepository.getProfile().collect(profileResource)
    }

    val accountId = profileResource.value?.data?.accountId
    if (apiErrorStrRes.value == null && accountId != null) {
      accountRepository.deleteAccount(accountId).collect(deleteResource)
    }

    if (apiErrorStrRes.value == null) {
      // call signOut to clear api client's state.
      accountRepository.signOut().collect(signOutResource)
    }

    isFlowComplete.emit(true)
  }
}
