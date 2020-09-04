package com.github.ashutoshgngwr.noice

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIntroActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var activityScenario: ActivityScenario<AppIntroActivity>

  @Before
  fun setup() {
    activityScenario = ActivityScenario.launch(AppIntroActivity::class.java)
  }

  @After
  fun teardown() {
    Utils.withDefaultSharedPreferences(ApplicationProvider.getApplicationContext()) {
      it.edit().clear().commit()
    }
  }

  @Test
  fun testOnSkipPressed() {
    onView(withText(R.string.app_intro_skip_button))
      .check(matches(isDisplayed()))
      .perform(click())

    // should destroy the activity and update the preferences
    assertEquals(Lifecycle.State.DESTROYED, activityScenario.state)
    Utils.withDefaultSharedPreferences(ApplicationProvider.getApplicationContext()) {
      assertTrue(it.getBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, false))
    }
  }

  @Test
  fun testOnDonePressed() {
    do { // find the last slide (where done button is shown)
      try {
        onView(allOf(isDisplayed(), withText(R.string.app_intro_done_button)))
          .check(matches(isDisplayed()))
        break
      } catch (e: NoMatchingViewException) {
        onView(withId(R.id.view_pager)).perform(swipeLeft())
      }
    } while (true)

    onView(withText(R.string.app_intro_done_button))
      .perform(click())

    // should destroy the activity and update the preferences
    assertEquals(Lifecycle.State.DESTROYED, activityScenario.state)
    Utils.withDefaultSharedPreferences(ApplicationProvider.getApplicationContext()) {
      assertTrue(it.getBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, false))
    }

  }

  @Test
  fun testMaybeStart_whenUserIsTryingToSeeItForTheFirstTime() {
    Intents.init()
    try {
      // leveraging currently created activityScenario to start another instance of self. (:
      activityScenario.onActivity {
        AppIntroActivity.maybeStart(it)
      }

      intended(hasComponent(AppIntroActivity::class.qualifiedName))
    } finally {
      Intents.release()
    }
  }

  @Test
  fun testMaybeStart_whenUserHasAlreadySeenIt() {
    Intents.init()
    try {
      // when user has already seen the activity once, i.e., if the preference is present in the
      // storage, maybeStart shouldn't start the activity.
      Utils.withDefaultSharedPreferences(ApplicationProvider.getApplicationContext()) {
        it.edit().putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true).commit()
      }

      activityScenario.onActivity {
        AppIntroActivity.maybeStart(it)
      }

      assertEquals(0, Intents.getIntents().size)
    } finally {
      Intents.release()
    }
  }
}
