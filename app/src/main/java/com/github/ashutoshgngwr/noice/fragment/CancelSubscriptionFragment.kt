package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.CancelSubscriptionFragmentBinding
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CancelSubscriptionFragment : BottomSheetDialogFragment() {

  private lateinit var binding: CancelSubscriptionFragmentBinding
  private val viewModel: CancelSubscriptionViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = CancelSubscriptionFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.confirmationMessage.text = getString(
      R.string.cancel_subscription_confirmation,
      viewModel.subscription.renewsAt?.let {
        val fmtFlags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        DateUtils.formatDateTime(requireContext(), it.time, fmtFlags)
      }
    )

    binding.dismiss.setOnClickListener { dismissAndSetFragmentResult(true) }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isCancelling.collect { isCancelable = !it }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.cancelResource
        .filterNot { it is Resource.Loading }
        .collect { dismissAndSetFragmentResult(false) }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.errorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.cancel_subscription_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace())
        }
    }
  }

  private fun dismissAndSetFragmentResult(wasAborted: Boolean) {
    dismiss()
    setFragmentResult(RESULT_KEY, bundleOf(EXTRA_WAS_ABORTED to wasAborted))
  }

  companion object {
    internal const val RESULT_KEY = "CancelSubscriptionFragmentResult"
    internal const val EXTRA_WAS_ABORTED = "was_aborted"
  }
}

@HiltViewModel
class CancelSubscriptionViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val subscription: Subscription = CancelSubscriptionFragmentArgs
    .fromSavedStateHandle(savedStateHandle)
    .subscription

  internal val cancelResource = MutableSharedFlow<Resource<Unit>>()

  val isCancelling: StateFlow<Boolean> = cancelResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val errorStrRes: Flow<Int?> = cancelResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is SubscriptionNotFoundError -> null // ignore
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  fun cancel() {
    viewModelScope.launch {
      subscriptionRepository.cancel(subscription)
        .flowOn(Dispatchers.IO)
        .collect(cancelResource)
    }
  }
}
