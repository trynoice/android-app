package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LaunchSubscriptionFlowFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.trynoice.api.client.models.SubscriptionPlan
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LaunchSubscriptionFlowFragment : BottomSheetDialogFragment() {

  private lateinit var binding: LaunchSubscriptionFlowFragmentBinding
  private val viewModel: LaunchSubscriptionFlowViewModel by viewModels()
  private val args: LaunchSubscriptionFlowFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LaunchSubscriptionFlowFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    viewModel.onLaunchCompleted = this::dismiss
    viewModel.launchBillingFlow(requireActivity(), args.plan)

    lifecycleScope.launch {
      viewModel.launchErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class LaunchSubscriptionFlowViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

  internal var onLaunchCompleted: () -> Unit = {}
  private val launchResource = MutableStateFlow<Resource<Unit>?>(null)
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

  internal fun launchBillingFlow(activity: Activity, plan: SubscriptionPlan) {
    if (launchResource.value != null) {
      return
    }

    launchResource.value = Resource.Loading()
    viewModelScope.launch {
      subscriptionRepository.launchBillingFlow(activity, plan)
        .flowOn(Dispatchers.IO)
        .onCompletion { onLaunchCompleted.invoke() }
        .collect(launchResource)
    }
  }
}
