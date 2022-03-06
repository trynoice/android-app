package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AccountFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.NotSignedInError
import com.trynoice.api.client.models.Profile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
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
    viewModel.onItemClickListener = View.OnClickListener { item ->
      when (item.id) {
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

    lifecycleScope.launch {
      viewModel.loadErrorStrRes
        .filterNotNull()
        .collect { errRes -> showErrorSnackbar(errRes) }
    }

    viewModel.loadProfile()
  }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
  private val networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  var onItemClickListener = View.OnClickListener {}
  val isSignedIn = accountRepository.isSignedIn()
  private val profileResource = MutableStateFlow<Resource<Profile>>(Resource.Loading())

  val profile = profileResource.transform { resource ->
    resource.data?.also { emit(it) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal val loadErrorStrRes: Flow<Int?> = profileResource.transform { resource ->
    emit(
      when {
        // ignore errors when cached profile is present and network is offline.
        resource.data != null && networkInfoProvider.isOffline.value -> null
        resource.error == null -> null
        resource.error is NotSignedInError -> null
        resource.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  internal fun loadProfile() {
    if (!isSignedIn.value) {
      return
    }

    viewModelScope.launch {
      networkInfoProvider.isOnline.collect {
        accountRepository.getProfile()
          .flowOn(Dispatchers.IO)
          .collect(profileResource)
      }
    }
  }
}
