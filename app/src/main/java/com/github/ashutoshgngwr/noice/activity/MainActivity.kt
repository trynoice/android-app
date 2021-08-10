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
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.MainActivityBinding
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), BillingProvider.PurchaseListener {

  companion object {
    /**
     * [EXTRA_CURRENT_NAVIGATED_FRAGMENT] declares the key for intent extra value that is passed to
     * [MainActivity] for setting the given fragment to the top. The required value for this extra
     * should be an integer representing the menu item's id in the navigation view corresponding to
     * the fragment being requested.
     */
    internal const val EXTRA_CURRENT_NAVIGATED_FRAGMENT = "current_fragment"

    @VisibleForTesting
    internal const val PREF_HAS_SEEN_DATA_COLLECTION_CONSENT = "has_seen_data_collection_consent"
  }

  private lateinit var binding: MainActivityBinding
  private lateinit var settingsRepository: SettingsRepository
  private lateinit var analyticsProvider: AnalyticsProvider
  private lateinit var crashlyticsProvider: CrashlyticsProvider
  private lateinit var billingProvider: BillingProvider
  private lateinit var navController: NavController

  override fun onCreate(savedInstanceState: Bundle?) {
    val app = NoiceApplication.of(this)
    analyticsProvider = app.getAnalyticsProvider()
    crashlyticsProvider = app.getCrashlyticsProvider()
    billingProvider = app.getBillingProvider()
    settingsRepository = SettingsRepository.newInstance(this)

    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)
    binding = MainActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    navController = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
      .let { (it as NavHostFragment).navController }

    setupActionBarWithNavController(navController, AppBarConfiguration(navController.graph))

    handleNewIntent()
    AppIntroActivity.maybeStart(this)
    if (BuildConfig.IS_PLAY_STORE_BUILD) {
      maybeShowDataCollectionConsent()
    }

    NoiceApplication.of(application).getReviewFlowProvider().init(this)
    billingProvider.init(this, this)
    analyticsProvider.logEvent("ui_open", bundleOf("theme" to settingsRepository.getAppTheme()))
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
        analyticsProvider.setCollectionEnabled(true)
        crashlyticsProvider.setCollectionEnabled(true)
        prefs.edit { putBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true) }
      }

      negativeButton(R.string.decline) {
        settingsRepository.setShouldShareUsageData(false)
        analyticsProvider.setCollectionEnabled(false)
        crashlyticsProvider.setCollectionEnabled(false)
        prefs.edit { putBoolean(PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true) }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNewIntent()
    navController.handleDeepLink(intent)
  }

  private fun handleNewIntent() {
    if (intent.hasExtra(EXTRA_CURRENT_NAVIGATED_FRAGMENT)) {
      navController.navigate(intent.getIntExtra(EXTRA_CURRENT_NAVIGATED_FRAGMENT, 0))
    } else if (Intent.ACTION_APPLICATION_PREFERENCES == intent.action) {
      navController.navigate(R.id.settings)
    }

    if (Intent.ACTION_VIEW == intent.action &&
      (intent.dataString?.startsWith(getString(R.string.app_website)) == true ||
        intent.dataString?.startsWith("noice://preset") == true)
    ) {
      intent.data?.also { PlaybackController.playPresetFromUri(this, it) }
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
    Snackbar.make(binding.navHostFragment, R.string.payment_pending, Snackbar.LENGTH_LONG).show()
    analyticsProvider.logEvent("purchase_pending", bundleOf())
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
}
