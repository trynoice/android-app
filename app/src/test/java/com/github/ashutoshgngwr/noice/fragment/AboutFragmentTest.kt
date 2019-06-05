package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutFragmentTest {

  private lateinit var fragmentScenario: FragmentScenario<AboutFragment>

  @Before
  fun setup() {
    fragmentScenario = FragmentScenario.launchInContainer(AboutFragment::class.java)
  }

  @Test
  fun `should create without any errors`() {
    fragmentScenario
      .moveToState(Lifecycle.State.CREATED)
      .onFragment { fragment ->
        assert(fragment.view != null)
      }
  }
}
