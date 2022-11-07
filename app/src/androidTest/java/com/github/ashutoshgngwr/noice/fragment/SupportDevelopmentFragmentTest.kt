package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SupportDevelopmentFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var fragmentScenario: HiltFragmentScenario<SupportDevelopmentFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInHiltContainer()
    Intents.init()
  }

  @After
  fun teardown() {
    Intents.release()
  }

  @Test
  fun testShareWithFriendsButton() {
    onView(withId(R.id.share_button)).perform(scrollTo(), click())

    Intents.intended(
      IntentMatchers.filterEquals(
        Intent(Intent.ACTION_CHOOSER)
      )
    )
  }
}
