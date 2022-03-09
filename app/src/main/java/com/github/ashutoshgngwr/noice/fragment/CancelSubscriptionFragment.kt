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
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.CancelSubscriptionFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.trynoice.api.client.models.Subscription
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
class CancelSubscriptionFragment : BottomSheetDialogFragment() {

  private lateinit var binding: CancelSubscriptionFragmentBinding
  private val viewModel: CancelSubscriptionViewModel by viewModels()
  private val args: CancelSubscriptionFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = CancelSubscriptionFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.confirmationMessage.text = getString(
      R.string.cancel_subscription_confirmation,
      args.subscription.renewsAt?.let {
        val fmtFlags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        DateUtils.formatDateTime(requireContext(), it.time, fmtFlags)
      }
    )

    viewModel.onCancelCompleted = { isAborted ->
      dismiss()
      setFragmentResult(RESULT_KEY, bundleOf(EXTRA_WAS_ABORTED to isAborted))
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isCancelling.collect { isCancelable = !it }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.errorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
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

  var onCancelCompleted: (isAborted: Boolean) -> Unit = {}
  val subscription: Subscription = CancelSubscriptionFragmentArgs
    .fromSavedStateHandle(savedStateHandle)
    .subscription

  private val cancelResource = MutableStateFlow<Resource<Unit>?>(null)
  val isCancelling: StateFlow<Boolean> = cancelResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val errorStrRes: StateFlow<Int?> = cancelResource.transform { r ->
    emit(
      when (r?.error) {
        null -> null
        is SubscriptionNotFoundError -> null // ignore
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  fun cancel() {
    viewModelScope.launch {
      subscriptionRepository.cancel(subscription)
        .flowOn(Dispatchers.IO)
        .onCompletion { onCancelCompleted.invoke(false) }
        .collect(cancelResource)
    }
  }

  fun abort() {
    onCancelCompleted.invoke(true)
  }
}
