package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.billing.DonationFlowProvider
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.databinding.MainActivityBinding
import com.github.ashutoshgngwr.noice.ext.getInternetConnectivityFlow
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import com.github.ashutoshgngwr.noice.fragment.HomeFragmentArgs
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchasedFragmentArgs
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchasesFragment
import com.github.ashutoshgngwr.noice.metrics.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import com.github.ashutoshgngwr.noice.widget.SnackBar
import com.github.ashutoshgngwr.noice.worker.SoundDownloadsRefreshWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SubscriptionBillingProvider.Listener {

  companion object {
    /**
     * If set, the activity navigates to the given destination in the home fragment. The value is a
     * reference id to a destination inside the home navigation graph.
     */
    internal const val EXTRA_HOME_DESTINATION = "homeDestination"

    /**
     * If set, the activity uses the given value as navigation args to the destination specified by
     * [EXTRA_HOME_DESTINATION]. The value is a [Bundle].
     */
    internal const val EXTRA_HOME_DESTINATION_ARGS = "homeDestinationArgs"

    @VisibleForTesting
    internal const val PREF_HAS_SEEN_DATA_COLLECTION_CONSENT = "has_seen_data_collection_consent"
  }

  private lateinit var binding: MainActivityBinding
  private lateinit var navController: NavController

  @set:Inject
  internal lateinit var donationFlowProvider: DonationFlowProvider

  @set:Inject
  internal lateinit var subscriptionBillingProvider: SubscriptionBillingProvider

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  /**
   * indicates whether the activity was delivered a new intent since it was last resumed.
   */
  private var hasNewIntent = false
  private val handler = Handler(Looper.getMainLooper())
  private val settingsRepository by lazy {
    EntryPointAccessors.fromApplication(application, HiltEntryPoint::class.java)
      .settingsRepository()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)
    binding = MainActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val navHostFragment = requireNotNull(binding.mainNavHostFragment.getFragment<NavHostFragment>())
    navController = navHostFragment.navController
    setupActionBarWithNavController(navController, AppBarConfiguration(navController.graph))
    AppIntroActivity.maybeStart(this)
    if (!BuildConfig.IS_FREE_BUILD) {
      maybeShowDataCollectionConsent()
    }

    SoundDownloadsRefreshWorker.refreshDownloads(this)
    reviewFlowProvider.init(this)
    hasNewIntent = true
    initOfflineIndicator()
    donationFlowProvider.setCallbackFragmentHost(this)
  }

  private fun maybeShowDataCollectionConsent() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (prefs.getBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, false)) {
      return
    }

    DialogFragment.show(supportFragmentManager) {
      isCancelable = false
      title(R.string.share_usage_data_consent_title)
      message(R.string.share_usage_data_consent_message)
      positiveButton(R.string.accept) { settingsRepository.setShouldShareUsageData(true) }
      negativeButton(R.string.decline) { settingsRepository.setShouldShareUsageData(false) }
      onDismiss { prefs.edit { putBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true) } }
    }
  }

  private fun initOfflineIndicator() {
    launchAndRepeatOnStarted {
      getInternetConnectivityFlow().collect { isConnected ->
        if (isConnected) {
          hideOfflineIndicator()
        } else {
          showOfflineIndicator()
        }
      }
    }
  }

  private fun showOfflineIndicator() {
    handler.removeCallbacksAndMessages(binding.networkIndicator)
    binding.networkIndicator.apply {
      setText(R.string.offline)
      isVisible = true
      TextViewCompat.setTextAppearance(
        this,
        R.style.TextAppearance_App_MainActivity_NetworkIndicator_Offline
      )
    }
  }

  private fun hideOfflineIndicator() {
    if (!binding.networkIndicator.isVisible) {
      return
    }

    binding.networkIndicator.apply {
      setText(R.string.back_online)
      TextViewCompat.setTextAppearance(
        this,
        R.style.TextAppearance_App_MainActivity_NetworkIndicator_Online
      )
    }

    handler.postDelayed(2500, binding.networkIndicator) {
      binding.networkIndicator.isVisible = false
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (!navController.handleDeepLink(intent)) {
      hasNewIntent = true
    }
  }

  override fun onResume() {
    super.onResume()
    subscriptionBillingProvider.setListener(this)

    // handle the new intent here since onResume() is guaranteed to be called after onNewIntent().
    // https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
    if (!hasNewIntent) {
      return
    }

    hasNewIntent = false
    val dataString = intent.dataString ?: ""
    when {
      intent.hasExtra(EXTRA_HOME_DESTINATION) -> {
        val args = HomeFragmentArgs(
          navDestination = intent.getIntExtra(EXTRA_HOME_DESTINATION, 0),
          navDestinationArgs = intent.getBundleExtra(EXTRA_HOME_DESTINATION_ARGS),
        ).toBundle()

        navController.navigate(R.id.home, args)
      }

      Intent.ACTION_APPLICATION_PREFERENCES == intent.action -> {
        navController.navigate(R.id.settings)
      }

      AlarmClock.ACTION_SHOW_ALARMS == intent.action -> {
        navController.navigate(R.id.home, HomeFragmentArgs(R.id.alarms).toBundle())
      }

      Intent.ACTION_VIEW == intent.action &&
        (dataString.startsWith("https://trynoice.com/preset") ||
          dataString.startsWith("noice://preset")) -> {

        val preset = presetRepository.readFromUrl(dataString)
        if (preset != null) {
          playbackServiceController.playPreset(preset)
        } else {
          SnackBar.error(binding.mainNavHostFragment, R.string.preset_url_invalid)
            .setAnchorView(findSnackBarAnchorView())
            .show()
        }
      }

      Intent.ACTION_VIEW == intent.action && SubscriptionPurchasesFragment.URI == dataString -> {
        navController.navigate(R.id.subscription_purchases)
      }
    }
  }

  override fun onPause() {
    subscriptionBillingProvider.removeListener()
    super.onPause()
  }

  override fun onSupportNavigateUp(): Boolean {
    return navController.navigateUp() || super.onSupportNavigateUp()
  }

  override fun onSubscriptionPurchasePending(subscriptionId: Long) {
    DialogFragment.show(supportFragmentManager) {
      title(R.string.processing_payment)
      message(R.string.payment_pending)
      positiveButton(R.string.okay)
    }
  }

  override fun onSubscriptionPurchaseComplete(subscriptionId: Long) {
    SubscriptionPurchasedFragmentArgs(subscriptionId)
      .toBundle()
      .also { navController.navigate(R.id.subscription_purchased, it) }
  }

  fun findSnackBarAnchorView(): View? {
    return findViewById(R.id.bottom_nav)
      ?: findViewById<View>(R.id.network_indicator).takeIf { it.isVisible }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface HiltEntryPoint {
    fun settingsRepository(): SettingsRepository
  }
}
