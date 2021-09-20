package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
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
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.AppIntroActivity
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
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
    onView(withId(R.id.action_pause))
      .check(doesNotExist())

    onView(withId(R.id.action_resume))
      .check(doesNotExist())

    // play a sound
    val context = ApplicationProvider.getApplicationContext<Context>()
    PlaybackController.play(context, Sound.LIBRARY.keys.first())

    try {
      onView(withId(R.id.action_pause))
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withId(R.id.action_pause))
        .check(doesNotExist())

      onView(withId(R.id.action_resume))
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withId(R.id.action_resume))
        .check(doesNotExist())
    } finally {
        PlaybackController.stop(context)
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
