package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

  private lateinit var mFragmentScenario: FragmentScenario<AboutFragment>

  @Before
  fun setup() {
    mFragmentScenario = launchFragmentInContainer<AboutFragment>()
  }

  @Test
  fun `should create without any errors`() {
    mFragmentScenario
      .moveToState(Lifecycle.State.CREATED)
      .onFragment { fragment ->
        assert(fragment.view != null)
      }
  }

  @Test
  fun `should start action view intent on about item click`() {
    onView(withChild(withText(R.string.app_copyright))).perform(click())
    mFragmentScenario.onFragment { fragment ->
      val actualIntent = shadowOf(fragment.requireActivity()).nextStartedActivity
      val expectedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fragment.getString(R.string.app_license_url)))
      assert(expectedIntent.filterEquals(actualIntent))
    }
  }
}
