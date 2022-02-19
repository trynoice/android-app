package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
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
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.trynoice.api.client.models.Profile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
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

    lifecycleScope.launch {
      viewModel.profileLoadErrorStringRes
        .filterNotNull()
        .filter { errRes -> errRes != ResourcesCompat.ID_NULL }
        .collect { errRes ->
          showErrorSnackbar(errRes)
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

  var onItemClickListener = View.OnClickListener { }
  val isSignedIn = accountRepository.isSignedIn()

  val profile = MutableStateFlow<Profile?>(null)
  private val profileLoadError = MutableStateFlow<Throwable?>(null)
  val profileLoadErrorStringRes: StateFlow<Int?> = profileLoadError.transform { error ->
    emit(
      when (error) {
        null -> null
        is NotSignedInError -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal fun loadProfile(force: Boolean = false) {
    if (!force && (isLoadingProfile || profile.value != null)) {
      return
    }

    isLoadingProfile = true
    viewModelScope.launch {
      profileLoadError.emit(null)
      accountRepository.getProfile()
        .flowOn(Dispatchers.IO)
        .catch { e -> profileLoadError.emit(e) }
        .collect { p -> profile.emit(p) }

      isLoadingProfile = false
    }
  }
}
