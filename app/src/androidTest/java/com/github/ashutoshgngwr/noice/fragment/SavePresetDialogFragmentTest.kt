package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.media.AudioAttributesCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.Sound
import kotlinx.android.synthetic.main.dialog_save_preset.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavePresetDialogFragmentTest {

  private lateinit var fragmentScenario: FragmentScenario<SavePresetDialogFragment>

  @Before
  fun setup() {
    fragmentScenario = launchFragmentInContainer<SavePresetDialogFragment>(null, R.style.AppTheme)
  }

  @Test
  fun testCancelButton() {
    // should not call target fragment's onActivityResult() on cancel
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        fail()
      }
    }

    fragmentScenario.onFragment { fragment ->
      fragment.setTargetFragment(dummyFragment, 0x0)
    }

    onView(withId(R.id.button_cancel)).perform(click())
    onView(withId(R.id.layout_main)).check(doesNotExist())
  }

  @Test
  fun testSaveButton_onValidUserInput() {
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        assertEquals(0xff, requestCode)
        assertEquals(Activity.RESULT_OK, resultCode)
      }
    }

    fragmentScenario.onFragment { fragment ->
      fragment.setTargetFragment(dummyFragment, 0xff)
      fragment.edit_name.setText("foo bar")
      fragment.preset = PresetFragment.Preset(
        "",
        arrayOf(
          Playback(
            InstrumentationRegistry.getInstrumentation().targetContext,
            requireNotNull(Sound.LIBRARY["birds"]),
            AudioAttributesCompat.Builder().build()
          )
        )
      )
    }

    onView(withId(R.id.button_save)).perform(click())
    // assert that fragment does not exist in view hierarchy anymore
    onView(withChild(withId(R.id.layout_main))).check(doesNotExist())
  }

  @Test
  fun testSaveButton_onInvalidUserInput() {
    val dummyFragment = object : Fragment() {
      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        fail()
      }
    }

    fragmentScenario.onFragment { fragment ->
      fragment.setTargetFragment(dummyFragment, 0xff)
      fragment.edit_name.setText("")
      fragment.preset = PresetFragment.Preset(
        "",
        arrayOf(
          Playback(
            InstrumentationRegistry.getInstrumentation().targetContext,
            requireNotNull(Sound.LIBRARY["birds"]),
            AudioAttributesCompat.Builder().build()
          )
        )
      )
    }

    onView(withId(R.id.button_save)).perform(click())
    // assert that error view exists
    onView(withChild(withId(R.id.layout_edit_name)))
      .check(matches(hasDescendant(withText(R.string.preset_name_cannot_be_empty))))
  }
}
