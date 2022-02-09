package com.github.ashutoshgngwr.noice.fragment

import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.AppIntroActivity
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  // activityScenario is more appropriate than FragmentScenario for two reasons.
  // 1. Activity in fragment scenario doesn't have an action bar (for menu items)
  // 2. NavController need to be manually injected in a fragment in fragment scenario.
  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @BindValue
  internal lateinit var mockSettingsRepository: SettingsRepository

  @Before
  fun setup() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit {
        putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
      }

    mockPlaybackController = mockk(relaxed = true)
    mockSettingsRepository = mockk(relaxed = true)
    activityScenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @After
  fun teardown() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { clear() }
  }

  @Test
  fun testStartDestination() {
    onView(withId(R.id.bottom_nav))
      .check(matches(EspressoX.withBottomNavSelectedItem(R.id.library)))

    withChildNavController {
      assertEquals(R.id.library, it.currentDestination?.id)
    }

    every { mockSettingsRepository.shouldDisplayPresetsAsHomeScreen() } returns true
    activityScenario = ActivityScenario.launch(MainActivity::class.java)
    onView(withId(R.id.bottom_nav))
      .check(matches(EspressoX.withBottomNavSelectedItem(R.id.presets)))

    withChildNavController {
      assertEquals(R.id.presets, it.currentDestination?.id)
    }
  }

  @Test
  fun testPlaybackToggleMenuItem() {
    onView(withId(R.id.action_pause))
      .check(doesNotExist())

    onView(withId(R.id.action_resume))
      .check(doesNotExist())

    onHomeFragment {
      val event = MediaPlayerService.PlaybackUpdateEvent(PlaybackStateCompat.STATE_PLAYING, mapOf())
      it.onPlayerManagerUpdate(event)
    }

    withRetries {
      onView(withId(R.id.action_pause))
        .check(matches(isDisplayed()))
        .perform(click())
    }

    verify(exactly = 1) { mockPlaybackController.pause() }
    onHomeFragment {
      val event = MediaPlayerService.PlaybackUpdateEvent(PlaybackStateCompat.STATE_PAUSED, mapOf())
      it.onPlayerManagerUpdate(event)
    }

    withRetries {
      onView(withId(R.id.action_resume))
        .check(matches(isDisplayed()))
        .perform(click())
    }
  }

  /**
   * Attempts a view action with retries with [delayMillis] between each attempt. Throws the
   * original error, if the [action] still fails after all attempts.
   */
  private inline fun withRetries(
    times: Int = 10,
    delayMillis: Long = 500,
    crossinline action: () -> Unit
  ) {
    var originalError: Throwable? = null
    repeat(times) {
      try {
        action.invoke()
        return
      } catch (e: Throwable) {
        Thread.sleep(delayMillis)
        if (originalError == null) {
          originalError = e
        }
      }
    }

    throw requireNotNull(originalError) { "all retry attempts exhausted but original error is null" }
  }

  private inline fun onHomeFragment(crossinline block: (HomeFragment) -> Unit) {
    activityScenario.onActivity {
      val parent = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as Fragment
      val home = parent.childFragmentManager.fragments.first() as HomeFragment
      block.invoke(home)
    }
  }

  private inline fun withChildNavController(crossinline block: (NavController) -> Unit) {
    onHomeFragment {
      val f = it.childFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
      block.invoke(f.navController)
    }
  }
}
