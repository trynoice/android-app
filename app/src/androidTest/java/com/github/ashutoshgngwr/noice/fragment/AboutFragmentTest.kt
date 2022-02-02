package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.filterEquals
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var fragmentScenario: HiltFragmentScenario<AboutFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInHiltContainer()
  }

  @Test
  fun testAboutItemClick() {
    Intents.init()

    // can't test everything. Picking one item at random
    onView(withChild(withText(R.string.app_copyright))).perform(click())
    intended(
      filterEquals(
        Intent(
          Intent.ACTION_VIEW,
          Uri.parse(
            InstrumentationRegistry.getInstrumentation()
              .targetContext
              .getString(R.string.app_license_url)
          )
        )
      )
    )

    Intents.release()
  }
}
