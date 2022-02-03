package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.MainActivityBinding
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import com.github.ashutoshgngwr.noice.navigation.Navigable
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BillingProvider.PurchaseListener {

  companion object {
    /**
     * [EXTRA_NAV_DESTINATION] declares the key for intent extra value passed to [MainActivity] for
     * setting the current destination on the [NavController]. The value for this extra should be an
     * id resource representing the action/destination id present in the [main][R.navigation.main]
     * or [home][R.navigation.home] navigation graphs.
     */
    internal const val EXTRA_NAV_DESTINATION = "nav_destination"

    @VisibleForTesting
    internal const val PREF_HAS_SEEN_DATA_COLLECTION_CONSENT = "has_seen_data_collection_consent"
  }

  private lateinit var binding: MainActivityBinding
  private lateinit var navController: NavController

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var billingProvider: BillingProvider

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  /**
   * indicates whether the activity was delivered a new intent since it was last resumed.
   */
  private var hasNewIntent = false
  private val settingsRepository by lazy {
    EntryPointAccessors.fromApplication(application, MainActivityEntryPoint::class.java)
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

    reviewFlowProvider.init(this)
    billingProvider.init(this, this)
    analyticsProvider.logEvent("ui_open", bundleOf("theme" to settingsRepository.getAppTheme()))
    hasNewIntent = true
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

      positiveButton(R.string.accept) {
        settingsRepository.setShouldShareUsageData(true)
        prefs.edit { putBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true) }
      }

      negativeButton(R.string.decline) {
        settingsRepository.setShouldShareUsageData(false)
        prefs.edit { putBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true) }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    navController.handleDeepLink(intent)
    hasNewIntent = true
  }

  override fun onResume() {
    super.onResume()

    // handle the new intent here since onResume() is guaranteed to be called after onNewIntent().
    // https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
    if (!hasNewIntent) {
      return
    }

    hasNewIntent = false
    if (intent.hasExtra(EXTRA_NAV_DESTINATION)) {
      val destID = intent.getIntExtra(EXTRA_NAV_DESTINATION, 0)
      if (!Navigable.navigate(binding.mainNavHostFragment.getFragment(), destID)) {
        navController.navigate(destID)
      }
    } else if (Intent.ACTION_APPLICATION_PREFERENCES == intent.action) {
      navController.navigate(R.id.settings)
    }

    if (Intent.ACTION_VIEW == intent.action &&
      (intent.dataString?.startsWith("https://ashutoshgngwr.github.io/noice/preset") == true ||
        intent.dataString?.startsWith("noice://preset") == true)
    ) {
      intent.data?.also { playbackController.playPresetFromUri(it) }
    }
  }

  override fun onDestroy() {
    billingProvider.close()
    super.onDestroy()
  }

  override fun onSupportNavigateUp(): Boolean {
    return navController.navigateUp() || super.onSupportNavigateUp()
  }

  override fun onPending(skus: List<String>) {
    analyticsProvider.logEvent("purchase_pending", bundleOf())
    Snackbar.make(binding.mainNavHostFragment, R.string.payment_pending, Snackbar.LENGTH_LONG)
      .show()
  }

  override fun onComplete(skus: List<String>, orderId: String) {
    analyticsProvider.logEvent("purchase_complete", bundleOf())
    billingProvider.consumePurchase(orderId)
    DialogFragment.show(supportFragmentManager) {
      title(R.string.support_development__donate_thank_you)
      message(R.string.support_development__donate_thank_you_description)
      positiveButton(R.string.okay)
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface MainActivityEntryPoint {
    fun settingsRepository(): SettingsRepository
  }
}
