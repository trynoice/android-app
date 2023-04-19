package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LaunchStripeCustomerPortalFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@AndroidEntryPoint
class LaunchStripeCustomerPortalFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private lateinit var binding: LaunchStripeCustomerPortalFragmentBinding
  private val viewModel: LaunchStripeCustomerPortalViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LaunchStripeCustomerPortalFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.customerPortalUrl
        .filterNotNull()
        .collect { url ->
          dismiss()
          activity?.startCustomTab(url)
        }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.errorStrRes
        .filterNotNull()
        .map { getString(it) }
        .map { getString(R.string.stripe_customer_portal_error, it).normalizeSpace() }
        .collect {
          dismiss()
          showErrorSnackBar(it)
        }
    }

    analyticsProvider.setCurrentScreen(this::class)
  }
}

@HiltViewModel
class LaunchStripeCustomerPortalViewModel @Inject constructor(
  subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  private val customerPortalUrlResource = subscriptionRepository.stripeCustomerPortalUrl()
    .shareIn(viewModelScope, SharingStarted.Eagerly)

  internal val customerPortalUrl: StateFlow<String?> = customerPortalUrlResource.transform { r ->
    r.data?.also { emit(it) }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal val errorStrRes: StateFlow<Int?> = customerPortalUrlResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
