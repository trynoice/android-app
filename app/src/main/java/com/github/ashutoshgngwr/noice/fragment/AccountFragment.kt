package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AccountFragmentBinding
import com.github.ashutoshgngwr.noice.ext.getInternetConnectivityFlow
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.models.Profile
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.NotSignedInError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private lateinit var binding: AccountFragmentBinding
  private val viewModel: AccountViewModel by viewModels()
  private var isConnectedToInternet = false
  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AccountFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.listItemClickListener = View.OnClickListener { item ->
      when (item.id) {
        R.id.faqs -> item.context.startCustomTab(R.string.app_faqs_url)
        R.id.report_issues -> {
          val url = if (BuildConfig.IS_FREE_BUILD) {
            R.string.app_issues_github_url
          } else {
            R.string.app_issues_form_url
          }

          item.context.startCustomTab(url)
          analyticsProvider.logEvent("issue_tracker_open", bundleOf())
        }

        R.id.submit_feedback -> {
          item.context.startCustomTab(R.string.feedback_form_url)
          analyticsProvider.logEvent("feedback_form_open", bundleOf())
        }

        else -> mainNavController.navigate(item.id)
      }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      requireContext().getInternetConnectivityFlow().collect { isConnectedToInternet = it }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.loadErrorStrRes
        .filterNotNull()
        .filter { isConnectedToInternet || viewModel.profile.value == null } // suppress errors when offline.
        .collect { causeStrRes ->
          val msg = getString(R.string.profile_load_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace())
        }
    }

    viewModel.loadData()
  }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
  subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  val isSignedIn = accountRepository.isSignedIn()
  val isSubscribed = subscriptionRepository.isSubscribed()
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

  private val profileResource = MutableSharedFlow<Resource<Profile>>()

  val profile: StateFlow<Profile?> = profileResource.transform { resource ->
    resource.data?.also { emit(it) }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val isLoading: StateFlow<Boolean> = profileResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  internal val loadErrorStrRes: Flow<Int?> = profileResource.transform { resource ->
    emit(
      when (resource.error) {
        null -> null
        is NotSignedInError -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  fun loadData() {
    if (!isSignedIn.value) {
      return
    }

    viewModelScope.launch {
      accountRepository.getProfile().collect(profileResource)
    }
  }
}
