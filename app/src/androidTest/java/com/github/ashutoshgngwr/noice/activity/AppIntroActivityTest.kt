package com.github.ashutoshgngwr.noice.activity

import android.app.Activity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIntroActivityTest {

  private lateinit var activityScenario: ActivityScenario<AppIntroActivity>

  @Before
  fun setup() {
    activityScenario = ActivityScenario.launch(AppIntroActivity::class.java)
  }

  @After
  fun teardown() {
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit { clear() }
  }

  @Test
  fun testOnSkipPressed() {
    onView(withId(R.id.skip))
      .check(matches(isDisplayed()))
      .perform(click())

    // not using activityScenario.state since it never transitions to destroyed without adding an
    // arbitrary wait before assertions.
    assertEquals(Activity.RESULT_CANCELED, activityScenario.result?.resultCode)

    // should update the preferences
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .getBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, false)
      .also { assertTrue(it) }
  }

  @Test
  fun testOnDonePressed() {
    while (true) {
      try {
        onView(withId(R.id.done))
          .check(matches(isDisplayed()))
          .perform(click())

        break
      } catch (e: AssertionError) {
        onView(withId(R.id.view_pager))
          .check(matches(isDisplayed()))
          .perform(swipeLeft())
      }
    }

    // not using activityScenario.state since it never transitions to destroyed without adding an
    // arbitrary wait before assertions.
    assertEquals(Activity.RESULT_CANCELED, activityScenario.result?.resultCode)

    // should update the preferences
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .getBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, false)
      .also { assertTrue(it) }
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
      PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        .edit { putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true) }

      activityScenario.onActivity {
        AppIntroActivity.maybeStart(it)
      }

      assertEquals(0, Intents.getIntents().size)
    } finally {
      Intents.release()
    }
  }
}
