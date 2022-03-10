package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionBillingCallbackFragmentBinding
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionBillingCallbackFragment : BottomSheetDialogFragment() {

  private lateinit var binding: SubscriptionBillingCallbackFragmentBinding
  private val viewModel: SubscriptionBillingCallbackViewModel by viewModels()
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SubscriptionBillingCallbackFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onDismissClicked = {
      dismiss()
      mainNavController.navigate(R.id.subscription_purchase_list)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isLoading.collect { isCancelable = it }
    }
  }

  companion object {
    private const val SUBSCRIPTION_ID_PARAM = "subscriptionId"
    private const val ACTION_PARAM = "action"
    internal const val ACTION_SUCCESS = "success"
    internal const val ACTION_CANCEL = "cancel"

    internal const val STRIPE_CALLBACK_URL = "https://trynoice.com/subscriptions/stripe/callback"
    internal const val STRIPE_CANCEL_CALLBACK_URL = STRIPE_CALLBACK_URL +
      "?${ACTION_PARAM}=${ACTION_CANCEL}"

    internal const val STRIPE_SUCCESS_CALLBACK_URL = STRIPE_CALLBACK_URL +
      "?${ACTION_PARAM}=${ACTION_SUCCESS}&${SUBSCRIPTION_ID_PARAM}={subscriptionId}"

    /**
     * Builder function to build [SubscriptionBillingCallbackFragmentArgs] from a
     * [STRIPE_CALLBACK_URL] ([STRIPE_SUCCESS_CALLBACK_URL] or [STRIPE_CANCEL_CALLBACK_URL]).
     */
    fun args(uri: Uri): Bundle {
      return SubscriptionBillingCallbackFragmentArgs(
        action = requireNotNull(uri.getQueryParameter(ACTION_PARAM)),
        subscriptionId = uri.getQueryParameter(SUBSCRIPTION_ID_PARAM)?.toLongOrNull() ?: 0
      ).toBundle()
    }
  }
}

@HiltViewModel
class SubscriptionBillingCallbackViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  var onDismissClicked: () -> Unit = {}
  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)
  val wasCancelled: Boolean

  init {
    val args = SubscriptionBillingCallbackFragmentArgs.fromSavedStateHandle(savedStateHandle)
    wasCancelled = args.action == SubscriptionBillingCallbackFragment.ACTION_CANCEL
    when (args.action) {
      SubscriptionBillingCallbackFragment.ACTION_CANCEL -> {
        isLoading.value = false
      }

      SubscriptionBillingCallbackFragment.ACTION_SUCCESS -> {
        require(args.subscriptionId > 0) {
          "given subscriptionId was zero when callback result was success"
        }

        waitForSubscription(args.subscriptionId)
      }
    }
  }

  private fun waitForSubscription(subscriptionId: Long) {
    viewModelScope.launch {
      val endTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(150)
      subscriptionRepository.get(subscriptionId)
        .transform { r ->
          if (r.error != null) {
            throw r.error // throw errors so retry is invoked.
          }

          emit(r)
        }
        .retry { e ->
          // retry for approximately 150 seconds with a 2.5 seconds delay between each attempt.
          delay(2500)
          e is SubscriptionNotFoundError && System.currentTimeMillis() < endTimestamp
        }
        .flowOn(Dispatchers.IO)
        .catch { error.emit(it) }
        .onCompletion { isLoading.emit(false) }
        .collect()
    }
  }
}
