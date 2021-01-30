package com.github.ashutoshgngwr.noice

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    activityScenario.close()
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .edit(commit = true) {
        clear()
      }
  }

  @Test
  fun testOnSkipPressed() {
    activityScenario.onActivity {
      it.onSkipPressed(null)

      // should destroy the activity
      assertTrue(it.isFinishing || it.isDestroyed)
    }

    // should update the preferences
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .also {
        assertTrue(it.getBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, false))
      }
  }

  @Test
  fun testOnDonePressed() {
    activityScenario.onActivity {
      it.onDonePressed(null)

      // should destroy the activity
      assertTrue(it.isFinishing || it.isDestroyed)
    }

    // should update the preferences
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
      .also {
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
      PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        .edit(commit = true) {
          putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
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
