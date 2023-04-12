package com.github.ashutoshgngwr.noice.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
        R.id.blog -> item.context.startCustomTab(R.string.app_blog_url)
        R.id.whats_new -> item.context.startCustomTab(R.string.app_changelog_url)
        R.id.faqs -> item.context.startCustomTab(R.string.app_faqs_url)
        R.id.email_us -> {
          val intent = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("trynoiceapp@gmail.com"))

          try {
            startActivity(intent)
          } catch (e: ActivityNotFoundException) {
            showErrorSnackBar(R.string.email_app_not_found)
          }
        }

        R.id.report_issues -> {
          Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLSdYhyYjxhJ7IKyiqdc3AE3uINSoRWBw8ROB003gkZ47KeSjWw/viewform")
            .buildUpon()
            .also { b ->
              val email = viewModel.profile.value?.email
              if (email != null) b.appendQueryParameter("entry.1204080881", email)
            }
            .appendQueryParameter("entry.486797125", "Android")
            .appendQueryParameter(
              "entry.997774143",
              "v${BuildConfig.VERSION_NAME} (${if (BuildConfig.IS_FREE_BUILD) "Free" else "Full"})",
            )
            .appendQueryParameter(
              "entry.1513858713",
              "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
            )
            .toString()
            .also { item.context.startCustomTab(it) }

          analyticsProvider.logEvent("issue_tracker_open", bundleOf())
        }

        R.id.submit_feedback -> {
          Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLSdEfOCyWfQ4QFMnLqlLj3BF27VKS1C-CQIokbmkXFchf6QZ6g/viewform")
            .buildUpon()
            .also { b ->
              val email = viewModel.profile.value?.email
              if (email != null) b.appendQueryParameter("entry.417281718", email)
            }
            .toString()
            .also { item.context.startCustomTab(it) }

          analyticsProvider.logEvent("feedback_form_open", bundleOf())
        }

        R.id.privacy_policy -> item.context.startCustomTab(R.string.app_privacy_policy_url)
        R.id.terms_of_service -> item.context.startCustomTab(R.string.app_tos_url)
        R.id.twitter -> launchActivityForUrl(R.string.app_twitter_url)
        R.id.instagram -> launchActivityForUrl(R.string.app_instagram_url)
        R.id.linkedin -> launchActivityForUrl(R.string.app_linkedin_url)
        R.id.facebook -> launchActivityForUrl(R.string.app_facebook_url)
        R.id.github -> launchActivityForUrl(R.string.app_github_url)
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

  private fun launchActivityForUrl(@StringRes resId: Int) {
    Intent(Intent.ACTION_VIEW)
      .setData(Uri.parse(getString(resId)))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .also { startActivity(it) }
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
