package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LaunchSubscriptionFlowFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LaunchSubscriptionFlowFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private lateinit var binding: LaunchSubscriptionFlowFragmentBinding
  private val viewModel: LaunchSubscriptionFlowViewModel by viewModels()
  private val args: LaunchSubscriptionFlowFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LaunchSubscriptionFlowFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isCompleted
        .filter { it }
        .collect { dismiss() }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.launchErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.subscription_initialisation_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace())
        }
    }

    viewModel.launchBillingFlow(requireActivity(), args.plan, args.activeSubscription)
    analyticsProvider.setCurrentScreen(this::class)
  }
}

@HiltViewModel
class LaunchSubscriptionFlowViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

  private val launchResource = MutableStateFlow<Resource<Unit>?>(null)
  internal val isCompleted = MutableStateFlow(false)
  internal val launchErrorStrRes: Flow<Int?> = launchResource.transform { r ->
    emit(
      when (r?.error) {
        null -> null
        is AlreadySubscribedError -> R.string.user_already_subscribed
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  internal fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
    activeSubscription: Subscription?,
  ) {
    if (launchResource.value != null) {
      return
    }

    launchResource.value = Resource.Loading()
    viewModelScope.launch {
      subscriptionRepository.launchBillingFlow(activity, plan, activeSubscription)
        .onCompletion { isCompleted.emit(true) }
        .collect(launchResource)
    }
  }
}
