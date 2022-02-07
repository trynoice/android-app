package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AccountFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchInCustomTab
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.trynoice.api.client.models.Profile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private lateinit var binding: AccountFragmentBinding
  private val viewModel: AccountViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AccountFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.loadProfile()
    viewModel.onItemClickListener = View.OnClickListener { item ->
      when (item.id) {
        R.id.report_issues -> {
          var url = getString(R.string.app_issues_github_url)
          if (!BuildConfig.IS_FREE_BUILD) {
            url = getString(R.string.app_issues_form_url)
          }

          Uri.parse(url).launchInCustomTab(requireContext())
          analyticsProvider.logEvent("issue_tracker_open", bundleOf())
        }

        R.id.submit_feedback -> {
          Uri.parse(getString(R.string.feedback_form_url)).launchInCustomTab(requireContext())
          analyticsProvider.logEvent("feedback_form_open", bundleOf())
        }

        else -> {
          Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
            .navigate(item.id)
        }
      }
    }
  }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  var onItemClickListener = View.OnClickListener { }
  val isSignedIn = accountRepository.isSignedIn()
  val isLoadingProfile = MutableLiveData(false)
  val profile = MutableLiveData<Profile>()
  val profileLoadError = MutableLiveData<Throwable?>()
  val profileLoadErrorStringRes = MediatorLiveData<Int?>().also {
    it.addSource(profileLoadError) { e ->
      it.postValue(
        when (e) {
          null -> null
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }
  }

  internal fun loadProfile() {
    if (isLoadingProfile.value == true || profile.value != null) {
      return
    }

    profileLoadError.postValue(null)
    isLoadingProfile.postValue(true)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        profile.postValue(accountRepository.getProfile())
      } catch (e: Throwable) {
        profileLoadError.postValue(e)
      } finally {
        isLoadingProfile.postValue(false)
      }
    }
  }
}
