package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlinx.android.synthetic.main.dialog_save_preset.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavePresetDialogFragmentTest {

  private lateinit var mFragmentScenario: FragmentScenario<SavePresetDialogFragment>

  @Before
  fun setup() {
    mFragmentScenario = launchFragmentInContainer<SavePresetDialogFragment>(null, R.style.AppTheme)
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
  fun `should dismiss on clicking cancel`() {
    onView(withId(R.id.button_cancel)).perform(click())
    // assert that parent of button does not exist in view hierarchy anymore
    onView(withChild(withId(R.id.layout_edit_name))).check(doesNotExist())
  }

  @Test
  fun `should not call on activity result of target fragment on clicking cancel`() {
    var notCalled = true
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        notCalled = false
      }
    }

    mFragmentScenario.onFragment { fragment ->
      fragment.setTargetFragment(dummyFragment, SoundLibraryFragment.RC_SAVE_PRESET_DIALOG)
    }

    onView(withId(R.id.button_cancel)).perform(click())

    // assert that parent of button does not exist in view hierarchy anymore
    onView(withChild(withId(R.id.layout_edit_name))).check(doesNotExist())
    // assert onActivityResult is not called
    assert(notCalled)
  }

  @Test
  fun `should call on activity result of target fragment if result was not saved`() {
    var called = false
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        called = requestCode == SoundLibraryFragment.RC_SAVE_PRESET_DIALOG && resultCode == Activity.RESULT_OK
      }
    }

    mFragmentScenario.onFragment { fragment ->
      fragment.preset = PresetFragment.Preset(
        "",
        arrayOf(PresetFragment.Preset.PresetPlaybackState(LIBRARY[1].key, 0.4f, 123))
      )
      fragment.setTargetFragment(dummyFragment, SoundLibraryFragment.RC_SAVE_PRESET_DIALOG)
      fragment.edit_name.setText("foo bar")
    }

    onView(withId(R.id.button_save)).perform(click())

    // assert that fragment does not exist in view hierarchy anymore
    onView(withChild(withId(R.id.layout_edit_name))).check(doesNotExist())
    // assert onActivityResult called
    assert(called)
  }

  @Test
  fun `should display error if name is black when save is clicked`() {
    var called = false
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        called = requestCode == SoundLibraryFragment.RC_SAVE_PRESET_DIALOG && resultCode == Activity.RESULT_OK
      }
    }

    mFragmentScenario.onFragment { fragment ->
      fragment.preset = PresetFragment.Preset(
        "",
        arrayOf(PresetFragment.Preset.PresetPlaybackState(LIBRARY[1].key, 0.4f, 123))
      )
      fragment.setTargetFragment(dummyFragment, SoundLibraryFragment.RC_SAVE_PRESET_DIALOG)
    }

    onView(withId(R.id.button_save)).perform(click())

    // assert that error view exists
    onView(withChild(withId(R.id.layout_edit_name)))
      .check(matches(hasDescendant(withText(R.string.preset_name_cannot_be_empty))))
    // assert onActivityResult is not called
    assert(!called)
  }
}
