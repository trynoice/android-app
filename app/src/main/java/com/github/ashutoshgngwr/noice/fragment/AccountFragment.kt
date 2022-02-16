package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
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
import com.github.ashutoshgngwr.noice.ext.showSnackbar
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.google.android.material.snackbar.Snackbar
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

        else -> {
          Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
            .navigate(item.id)
        }
      }
    }

    viewModel.profileLoadErrorStringRes.observe(viewLifecycleOwner) { errRes ->
      if (errRes != null && errRes != ResourcesCompat.ID_NULL) {
        showSnackbar(errRes, Snackbar.LENGTH_LONG)
          .setAction(R.string.retry) { viewModel.loadProfile(force = true) }
      }
    }

    viewModel.loadProfile()
  }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  @Volatile
  private var isLoadingProfile = false
  private val profileLoadError = MutableLiveData<Throwable?>()

  var onItemClickListener = View.OnClickListener { }
  val isSignedIn = accountRepository.isSignedIn()
  val profile = MutableLiveData<Profile>()
  val profileLoadErrorStringRes = MediatorLiveData<Int?>().also {
    it.addSource(profileLoadError) { e ->
      it.postValue(
        when (e) {
          null -> null
          is NotSignedInError -> null
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }
  }

  internal fun loadProfile(force: Boolean = false) {
    if (!force && (isLoadingProfile || profile.value != null)) {
      return
    }

    profileLoadError.postValue(null)
    isLoadingProfile = true
    viewModelScope.launch(Dispatchers.IO) {
      try {
        profile.postValue(accountRepository.getProfile())
      } catch (e: Throwable) {
        profileLoadError.postValue(e)
      } finally {
        isLoadingProfile = false
      }
    }
  }
}
