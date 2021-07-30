package com.github.ashutoshgngwr.noice.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isClosed
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.LibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SavedPresetsFragment
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.navigation.NavigationView
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.AssertionFailedError
import org.junit.After
import org.junit.Assert.*
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
  fun testIfLibraryIsVisibleOnStart() {
    activityScenario.onActivity {
      assertEquals(
        LibraryFragment::class.java.simpleName,
        it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
      )

      assertEquals(
        R.id.library,
        it.findViewById<NavigationView>(R.id.navigation_drawer).checkedItem?.itemId
      )
    }
  }

  @Test
  fun testIfSavedPresetsIsVisibleOnStart() {
    mockkObject(SettingsRepository)
    every { SettingsRepository.newInstance(any()) } returns mockk(relaxed = true) {
      every { shouldDisplaySavedPresetsAsHomeScreen() } returns true
    }

    // recreate activity with null bundle
    activityScenario = launch(MainActivity::class.java)
    activityScenario.onActivity {
      assertEquals(
        SavedPresetsFragment::class.java.simpleName,
        it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
      )

      assertEquals(
        R.id.saved_presets,
        it.findViewById<NavigationView>(R.id.navigation_drawer).checkedItem?.itemId
      )
    }
  }

  @Test
  fun testNavigatedFragmentsMenuItems() {
    for ((menuItemID, fragmentClass) in MainActivity.NAVIGATED_FRAGMENTS) {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))

      onView(withId(R.id.navigation_drawer))
        .perform(NavigationViewActions.navigateTo(menuItemID))

      EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
        onView(withId(R.id.layout_main)).check(matches(isClosed(Gravity.START)))
      }

      activityScenario.onActivity {
        assertEquals(
          fragmentClass.simpleName,
          it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
        )

        it.findViewById<NavigationView>(R.id.navigation_drawer).also { navView ->
          assertEquals(menuItemID, navView.checkedItem?.itemId)
        }
      }
    }
  }

  @Test
  fun testHelpMenuItem() {
    Intents.init()
    try {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))

      onView(withId(R.id.navigation_drawer))
        .perform(NavigationViewActions.navigateTo(R.id.help))

      intended(hasComponent(AppIntroActivity::class.qualifiedName))
    } finally {
      Intents.release()
    }
  }

  @Test
  fun testReportIssuesMenuItem() {
    Intents.init()
    try {
      Intents.intending(hasAction(Intent.ACTION_VIEW))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))

      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))

      onView(withId(R.id.navigation_drawer))
        .perform(NavigationViewActions.navigateTo(R.id.report_issue))

      val context = ApplicationProvider.getApplicationContext<Context>()
      var url = context.getString(R.string.app_issues_github_url)
      if (BuildConfig.IS_PLAY_STORE_BUILD) {
        url = context.getString(R.string.app_issues_form_url)
      }

      intended(filterEquals(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
    } finally {
      Intents.release()
    }
  }

  @Test
  fun testSubmitFeedbackMenuItem() {
    Intents.init()
    try {
      Intents.intending(hasAction(Intent.ACTION_VIEW))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))

      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))

      onView(withId(R.id.navigation_drawer))
        .perform(NavigationViewActions.navigateTo(R.id.submit_feedback))

      intended(
        filterEquals(
          Intent(
            Intent.ACTION_VIEW, Uri.parse(
              ApplicationProvider.getApplicationContext<Context>()
                .getString(R.string.feedback_form_url)
            )
          )
        )
      )
    } finally {
      Intents.release()
    }
  }

  @Test
  fun testBackNavigation() {
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.saved_presets))

    EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))
    }

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.about))

    EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
    }

    activityScenario.onActivity {
      it.onBackPressed()
      assertEquals(
        SavedPresetsFragment::class.java.simpleName,
        it.supportFragmentManager
          .getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1)
          .name
      )

      it.onBackPressed()
      assertEquals(
        LibraryFragment::class.java.simpleName,
        it.supportFragmentManager
          .getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1)
          .name
      )

      // ensure that fragments are not present in the back stack
      assertFalse(
        it.supportFragmentManager.popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
      )

      assertFalse(
        it.supportFragmentManager.popBackStackImmediate(
          SavedPresetsFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
      )

      it.onBackPressed()
      assertTrue(it.isFinishing || it.isDestroyed)
    }
  }

  @Test
  fun testReClickingNavigationItemForFragments() {
    // should have just 1 entry back stack
    activityScenario.onActivity {
      assertEquals(1, it.supportFragmentManager.backStackEntryCount)
    }

    // open nav drawer
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    // open a new fragment using the menu item
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.about))
    activityScenario.onActivity {
      assertEquals(2, it.supportFragmentManager.backStackEntryCount)
    }

    EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))
    }

    // try to reselect the same nav item
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.about))
    activityScenario.onActivity {
      // back stack entry count should remain the same
      assertEquals(2, it.supportFragmentManager.backStackEntryCount)
    }
  }

  @Test
  fun testPlayPauseToggleMenuItem() {
    mockkObject(PlaybackController)
    every { PlaybackController.resume(any()) } returns Unit

    // shouldn't be displayed by default
    onView(withId(R.id.action_play_pause_toggle)).check(doesNotExist())

    // with ongoing playback
    activityScenario.onActivity {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PLAYING
      })
    }

    EspressoX.retryWithWaitOnError(NoMatchingViewException::class) {
      onView(withId(R.id.action_play_pause_toggle))
        .check(matches(isDisplayed()))
        .perform(click())
    }

    verify(exactly = 1) { PlaybackController.pause(any()) }

    // with paused playback
    activityScenario.onActivity {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PAUSED
      })
    }

    EspressoX.retryWithWaitOnError(NoMatchingViewException::class) {
      onView(withId(R.id.action_play_pause_toggle))
        .check(matches(isDisplayed()))
        .perform(click())
    }

    verify(exactly = 1) { PlaybackController.resume(any()) }

    // with stopped playback
    activityScenario.onActivity {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_STOPPED
      })
    }

    EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
      onView(withId(R.id.action_play_pause_toggle)).check(doesNotExist())
    }
  }

  @Test
  fun testNavigatedFragmentIntentExtra() {
    activityScenario = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
      .let {
        it.putExtra(MainActivity.EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.about)
        launch(it)
      }

    activityScenario.onActivity {
      assertEquals(
        AboutFragment::class.simpleName,
        it.supportFragmentManager
          .getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1)
          .name
      )
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
