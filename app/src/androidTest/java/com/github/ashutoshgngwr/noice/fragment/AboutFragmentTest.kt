package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.filterEquals
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

  private lateinit var fragmentScenario: FragmentScenario<AboutFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<AboutFragment>()
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
