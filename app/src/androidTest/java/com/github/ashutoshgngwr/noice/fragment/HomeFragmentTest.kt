package com.github.ashutoshgngwr.noice.fragment

import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.AppIntroActivity
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.AssertionFailedError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

  // activityScenario is more appropriate than FragmentScenario for two reasons.
  // 1. Activity in fragment scenario doesn't have an action bar (for menu items)
  // 2. NavController need to be manually injected in a fragment in fragment scenario.
  private lateinit var activityScenario: ActivityScenario<MainActivity>
  private lateinit var navController: NavController

  @Before
  fun setup() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit {
        putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
      }

    activityScenario = ActivityScenario.launch(MainActivity::class.java)
    activityScenario.onActivity {
      navController = it.findNavController(R.id.nav_host_fragment)
    }
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testStartDestination() {
    onView(withId(R.id.bottom_nav))
      .check(matches(EspressoX.withBottomNavSelectedItem(R.id.library)))

    withChildNavController {
      assertEquals(R.id.library, it.currentDestination?.id)
    }

    mockkObject(SettingsRepository)
    every { SettingsRepository.newInstance(any()) } returns mockk(relaxed = true) {
      every { shouldDisplayPresetsAsHomeScreen() } returns true
    }

    setup() // haha!
    onView(withId(R.id.bottom_nav))
      .check(matches(EspressoX.withBottomNavSelectedItem(R.id.presets)))

    withChildNavController {
      assertEquals(R.id.presets, it.currentDestination?.id)
    }
  }

  @Test
  fun testPlaybackToggleMenuItem() {
    mockkObject(PlaybackController)
    every { PlaybackController.resume(any()) } returns Unit

    // shouldn't be displayed by default
    onView(withId(R.id.action_playback_toggle)).check(ViewAssertions.doesNotExist())

    // with ongoing playback
    onHomeFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PLAYING
      })
    }

    EspressoX.retryWithWaitOnError(NoMatchingViewException::class) {
      onView(withId(R.id.action_playback_toggle))
        .check(matches(ViewMatchers.isDisplayed()))
        .perform(ViewActions.click())
    }

    verify(exactly = 1) { PlaybackController.pause(any()) }

    // with paused playback
    onHomeFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_PAUSED
      })
    }

    EspressoX.retryWithWaitOnError(NoMatchingViewException::class) {
      onView(withId(R.id.action_playback_toggle))
        .check(matches(ViewMatchers.isDisplayed()))
        .perform(ViewActions.click())
    }

    verify(exactly = 1) { PlaybackController.resume(any()) }

    // with stopped playback
    onHomeFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlaybackStateCompat.STATE_STOPPED
      })
    }

    EspressoX.retryWithWaitOnError(AssertionFailedError::class) {
      onView(withId(R.id.action_playback_toggle)).check(ViewAssertions.doesNotExist())
    }
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
