package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.FragmentScenario.launchInContainer
import androidx.lifecycle.Lifecycle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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

  @Test
  fun `should refresh recycler view on playback state change`() {
    fragmentScenario
      .moveToState(Lifecycle.State.CREATED)
      .onFragment { fragment ->
        shadowOf(fragment.mRecyclerView).setDidRequestLayout(false)
        fragment.onPlaybackStateChanged()
        assert(shadowOf(fragment.mRecyclerView).didRequestLayout())
      }
  }

  @Test
  fun `should destroy nice and quiet`() {
    fragmentScenario
      .moveToState(Lifecycle.State.DESTROYED)
  }

  // not sure how to write more cases..?
  // UPDATE:
  // okay! this is the second time I am having trouble writing unit tests with Robolectric.
  // Maybe UI tests are better off with an instrumented framework like Espresso..?
}
