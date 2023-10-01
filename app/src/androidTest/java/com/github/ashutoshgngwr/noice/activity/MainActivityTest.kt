package com.github.ashutoshgngwr.noice.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.EspressoX.withBottomNavSelectedItem
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.billing.DonationFlowProvider
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.di.DonationFlowProviderModule
import com.github.ashutoshgngwr.noice.di.SubscriptionBillingProviderModule
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchasesFragment
import com.github.ashutoshgngwr.noice.models.AudioQuality
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(
  DonationFlowProviderModule::class,
  SubscriptionBillingProviderModule::class,
)
class MainActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var mockPresetRepository: PresetRepository

  @BindValue
  internal lateinit var mockSettingsRepository: SettingsRepository

  @BindValue
  internal lateinit var playbackServiceControllerMock: SoundPlaybackService.Controller

  @BindValue
  internal lateinit var donationFlowProviderMock: DonationFlowProvider

  @BindValue
  internal lateinit var subscriptionBillingProviderMock: SubscriptionBillingProvider

  @Before
  fun setUp() {
    // mark app intro as seen to run main activity tests in peace
    val context = ApplicationProvider.getApplicationContext<Context>()
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit {
        putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
      }

    // initialise work manager with a no-op executor
    Configuration.Builder()
      .setMinimumLoggingLevel(Log.ERROR)
      .setExecutor { } // no-op
      .build()
      .also { WorkManagerTestInitHelper.initializeTestWorkManager(context, it) }

    mockPresetRepository = mockk(relaxed = true)
    mockSettingsRepository = mockk(relaxed = true)
    playbackServiceControllerMock = mockk(relaxed = true)
    donationFlowProviderMock = mockk(relaxed = true)
    subscriptionBillingProviderMock = mockk(relaxed = true)
  }

  @After
  fun tearDown() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { clear() }
  }

  @Test
  fun appTheme() {
    arrayOf(
      AppCompatDelegate.MODE_NIGHT_NO,
      AppCompatDelegate.MODE_NIGHT_YES,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ).forEach { nightMode ->
      every { mockSettingsRepository.getAppThemeAsNightMode() } returns nightMode
      launch(MainActivity::class.java).use {
        assertEquals(nightMode, AppCompatDelegate.getDefaultNightMode())
      }
    }
  }

  @Test
  fun homeDestinationExtra() {
    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.presets)
      .let { launch<MainActivity>(it) }
      .use { scenario ->
        onView(withId(R.id.bottom_nav))
          .check(matches(withBottomNavSelectedItem(R.id.presets)))

        scenario.onActivity {
          val mainNavController = it.findNavController(R.id.main_nav_host_fragment)
          assertEquals(R.id.home, mainNavController.currentDestination?.id)

          val homeNavController = it.findNavController(R.id.home_nav_host_fragment)
          assertEquals(R.id.presets, homeNavController.currentDestination?.id)
        }
      }
  }

  @Test
  fun applicationPreferencesIntent() {
    // prevent crash in SettingsFragment.
    every { mockSettingsRepository.getAudioQuality() } returns AudioQuality.LOW

    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .setAction(Intent.ACTION_APPLICATION_PREFERENCES)
      .let { launch<MainActivity>(it) }
      .onActivity { activity ->
        val navController = activity.findNavController(R.id.main_nav_host_fragment)
        assertEquals(R.id.settings, navController.currentDestination?.id)
      }
  }

  @Test
  fun showAlarmsIntent() {
    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .setAction(AlarmClock.ACTION_SHOW_ALARMS)
      .let { launch<MainActivity>(it) }
      .onActivity { activity ->
        val mainNavController = activity.findNavController(R.id.main_nav_host_fragment)
        assertEquals(R.id.home, mainNavController.currentDestination?.id)

        val homeNavController = activity.findNavController(R.id.home_nav_host_fragment)
        assertEquals(R.id.alarms, homeNavController.currentDestination?.id)
      }
  }

  @Test
  fun presetUriIntent() {
    arrayOf(
      "https://trynoice.com/preset?n=test&ps=[]",
      "noice://preset?n=test&ps=[]",
    ).forEach { url ->
      val mockPreset = mockk<Preset>()
      every { mockPresetRepository.readFromUrl(url) } returns mockPreset
      Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse(url))
        .let { launch<MainActivity>(it) }
        .use {
          verify(exactly = 1, timeout = 5000L) {
            playbackServiceControllerMock.playPreset(mockPreset)
          }
        }
    }
  }

  @Test
  fun subscriptionPurchaseListIntent() {
    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .setAction(Intent.ACTION_VIEW)
      .setData(Uri.parse(SubscriptionPurchasesFragment.URI))
      .let { launch<MainActivity>(it) }
      .onActivity { activity ->
        val navController = activity.findNavController(R.id.main_nav_host_fragment)
        assertEquals(R.id.subscription_purchases, navController.currentDestination?.id)
      }
  }

  @Test
  fun usageDataCollectionConsent() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { remove(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT) }

    launch(MainActivity::class.java)
    if (BuildConfig.IS_FREE_BUILD) {
      onView(withText(R.string.share_usage_data_consent_title)).check(doesNotExist())
      return
    }

    onView(withText(R.string.share_usage_data_consent_title))
      .check(matches(isDisplayed()))

    onView(withText(R.string.accept)).perform(click())
    verify(exactly = 1) { mockSettingsRepository.setShouldShareUsageData(true) }
  }

  @Test
  fun subscriptionBillingProviderListener() {
    val slot = slot<SubscriptionBillingProvider.Listener>()
    every { subscriptionBillingProviderMock.setListener(capture(slot)) } returns Unit

    launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { slot.captured.onSubscriptionPurchasePending(1) }
      onView(withText(R.string.payment_pending))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))
    }

    launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity {
        slot.captured.onSubscriptionPurchaseComplete(1)
        val mainNavController = it.findNavController(R.id.main_nav_host_fragment)
        assertEquals(R.id.subscription_purchased, mainNavController.currentDestination?.id)
      }
    }
  }
}
