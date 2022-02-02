package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.net.Uri
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.BillingProviderModule
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(BillingProviderModule::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @BindValue
  internal lateinit var mockSettingsRepository: SettingsRepository

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @BindValue
  internal lateinit var mockBillingProvider: BillingProvider

  @Before
  fun setup() {
    // mark app intro as seen to run main activity tests in peace
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit {
        putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
      }

    mockSettingsRepository = mockk(relaxed = true)
    mockPlaybackController = mockk(relaxed = true)
    mockBillingProvider = mockk(relaxed = true)
    activityScenario = launch(MainActivity::class.java)
  }

  @After
  fun teardown() {
    unmockkAll()
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
      val navController = it.findNavController(R.id.nav_host_fragment)
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
    val inputs = arrayOf(
      "https://ashutoshgngwr.github.io/noice/preset?name=test&playerStates=[]",
      "noice://preset?name=test&playerStates=[]"
    )

    for (input in inputs) {
      val uri = Uri.parse(input)
      activityScenario.onActivity {
        it.startActivity(
          Intent(it, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
        )
      }

      verify(exactly = 1, timeout = 5000L) { mockPlaybackController.playPresetFromUri(uri) }
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
      return
    }

    val slot = slot<BillingProvider.PurchaseListener>()
    verify { mockBillingProvider.init(any(), capture(slot)) }
    assertTrue(slot.isCaptured)

    activityScenario.onActivity {
      slot.captured.onPending(listOf("test-sku"))
    }

    onView(withText(R.string.payment_pending))
      .check(matches(isDisplayed()))

    val testOrderID = "test-order-id"
    activityScenario.onActivity {
      slot.captured.onComplete(listOf(), testOrderID)
    }

    EspressoX.onViewInDialog(withId(R.id.positive)).perform(click()) // close the dialog
    verify { mockBillingProvider.consumePurchase(testOrderID) }
  }
}
