package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.FragmentScenario.launchInContainer
import androidx.lifecycle.Lifecycle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundLibraryFragmentTest {

  private lateinit var fragmentScenario: FragmentScenario<SoundLibraryFragment>

  @Before
  fun setup() {
    fragmentScenario = launchInContainer(SoundLibraryFragment::class.java)
  }

  @Test
  fun `should list sound library on create`() {
    fragmentScenario
      .moveToState(Lifecycle.State.CREATED)
      .onFragment { fragment ->
        assert(fragment.mRecyclerView != null)
        assert(fragment.mRecyclerView!!.adapter != null)
        assert(fragment.mRecyclerView!!.adapter!!.itemCount == SoundLibraryFragment.Sound.LIBRARY.size())
      }
  }

  // not sure how to write more cases..?
}
