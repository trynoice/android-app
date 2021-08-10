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
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @Before
  fun setup() {
    // mark app intro as seen to run main activity tests in peace
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit {
        putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
      }

    activityScenario = launch(MainActivity::class.java)
  }

  @After
  fun teardown() {
    unmockkAll()
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { clear() }
  }

  @Test
  fun testNavigatedFragmentIntentExtra() {
    activityScenario = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .let {
        it.putExtra(MainActivity.EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.about)
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

    val mockRepo = mockk<SettingsRepository>(relaxed = true)
    mockkObject(SettingsRepository)
    every { SettingsRepository.newInstance(any()) } returns mockRepo
    for (nightMode in nightModes) {
      every { mockRepo.getAppThemeAsNightMode() } returns nightMode
      activityScenario.recreate()
      assertEquals(nightMode, AppCompatDelegate.getDefaultNightMode())
    }
  }

  @Test
  fun testPresetUriIntent() {
    mockkObject(PlaybackController)
    val inputs = arrayOf(
      "https://ashutoshgngwr.github.io/noice/preset?name=test&playerStates=[]",
      "noice://preset?name=test&playerStates=[]"
    )

    for (input in inputs) {
      every { PlaybackController.playPresetFromUri(any(), any()) } returns Unit

      val uri = Uri.parse(input)
      val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(uri)

      activityScenario = launch(intent)
      verify(exactly = 1) { PlaybackController.playPresetFromUri(any(), uri) }
      clearMocks(PlaybackController)
    }
  }

  @Test
  fun testUsageDataCollectionDialog() {
    val mockAnalyticsProvider: AnalyticsProvider = mockk(relaxed = true)
    val mockCrashlyticsProvider: CrashlyticsProvider = mockk(relaxed = true)

    ApplicationProvider.getApplicationContext<NoiceApplication>().apply {
      setAnalyticsProvider(mockAnalyticsProvider)
      setCrashlyticsProvider(mockCrashlyticsProvider)
    }

    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { remove(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT) }

    activityScenario.recreate()
    if (!BuildConfig.IS_PLAY_STORE_BUILD) {
      onView(withText(R.string.share_usage_data_consent_title)).check(doesNotExist())
      return
    }

    onView(withText(R.string.share_usage_data_consent_title))
      .check(matches(isDisplayed()))

    onView(withText(R.string.accept)).perform(click())
    verify(exactly = 1) {
      mockAnalyticsProvider.setCollectionEnabled(true)
      mockCrashlyticsProvider.setCollectionEnabled(true)
    }
  }

  @Test
  fun testBillingProviderListener() {
    if (!BuildConfig.IS_PLAY_STORE_BUILD) {
      // F-Droid flavor doesn't have a billing provider scenario
      return
    }

    val mockBillingProvider: BillingProvider = mockk(relaxed = true)
    ApplicationProvider.getApplicationContext<NoiceApplication>()
      .setBillingProvider(mockBillingProvider)

    activityScenario.recreate()

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

    onView(withId(R.id.positive)).perform(click()) // close the dialog
    verify { mockBillingProvider.consumePurchase(testOrderID) }
  }
}
