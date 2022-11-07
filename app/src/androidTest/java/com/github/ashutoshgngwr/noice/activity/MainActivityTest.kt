package com.github.ashutoshgngwr.noice.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.InAppBillingProviderModule
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(InAppBillingProviderModule::class)
class MainActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @BindValue
  internal lateinit var mockPresetRepository: PresetRepository

  @BindValue
  internal lateinit var mockSettingsRepository: SettingsRepository

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @BindValue
  internal lateinit var mockBillingProvider: InAppBillingProvider

  @Before
  fun setup() {
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
    mockPlaybackController = mockk(relaxed = true)
    mockBillingProvider = mockk(relaxed = true)
    activityScenario = launch(MainActivity::class.java)
  }

  @After
  fun teardown() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { clear() }
  }

  @Test
  fun testNavDestinationExtra() {
    activityScenario = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .let {
        it.putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.about)
        launch(it)
      }

    activityScenario.onActivity {
      val navController = it.findNavController(R.id.main_nav_host_fragment)
      assertEquals(R.id.about, navController.currentDestination?.id)
    }
  }

  @Test
  fun testAppTheme() {
    val nightModes = arrayOf(
      AppCompatDelegate.MODE_NIGHT_NO,
      AppCompatDelegate.MODE_NIGHT_YES,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    for (nightMode in nightModes) {
      every { mockSettingsRepository.getAppThemeAsNightMode() } returns nightMode
      activityScenario.recreate()
      assertEquals(nightMode, AppCompatDelegate.getDefaultNightMode())
    }
  }

  @Test
  fun testPresetUriIntent() {
    val inputs = arrayOf("https://trynoice.com/preset?n=test&ps=[]", "noice://preset?n=test&ps=[]")
    for (input in inputs) {
      val mockPreset = mockk<Preset>()
      every { mockPresetRepository.readFromUrl(input) } returns mockPreset
      activityScenario.onActivity {
        it.startActivity(
          Intent(it, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(input))
        )
      }

      verify(exactly = 1, timeout = 5000L) { mockPlaybackController.play(mockPreset) }
    }
  }

  @Test
  fun testUsageDataCollectionDialog() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { remove(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT) }

    activityScenario.recreate()
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
  fun testBillingProviderListener() {
    if (BuildConfig.IS_FREE_BUILD) { // free flavor doesn't have billing provider scenarios
      // a dummy operation to prevent the instrumentation process crash.
      onView(isRoot()).check(matches(isDisplayed()))
      return
    }

    val slot = slot<InAppBillingProvider.PurchaseListener>()
    verify { mockBillingProvider.setPurchaseListener(capture(slot)) }
    assertTrue(slot.isCaptured)

    activityScenario.onActivity {
      slot.captured.onPending(mockk())
    }

    onView(withText(R.string.payment_pending))
      .check(matches(isDisplayed()))

    val purchase = mockk<InAppBillingProvider.Purchase>(relaxed = true)
    activityScenario.onActivity {
      slot.captured.onComplete(purchase)
    }

    coVerify(exactly = 1, timeout = 15000) { mockBillingProvider.consumePurchase(purchase) }
  }
}
