package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RandomPresetFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<RandomPresetFragment>

  @Before
  fun setup() {
    mockkObject(InAppReviewFlowManager, MediaPlayerService.Companion)
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testRandomPresetButton_onClick() {
    val intensityExpectations = mapOf(
      R.id.preset_intensity__any to SoundLibraryFragment.RANGE_INTENSITY_ANY,
      R.id.preset_intensity__dense to SoundLibraryFragment.RANGE_INTENSITY_DENSE,
      R.id.preset_intensity__light to SoundLibraryFragment.RANGE_INTENSITY_LIGHT
    )

    val typeExpectations = mapOf(
      R.id.preset_type__any to null,
      R.id.preset_type__focus to Sound.Tag.FOCUS,
      R.id.preset_type__relax to Sound.Tag.RELAX
    )

    for ((typeID, tag) in typeExpectations) {
      for ((intensityID, intensityRange) in intensityExpectations) {
        Espresso.onView(withId(typeID)).perform(ViewActions.click())
        Espresso.onView(withId(intensityID)).perform(ViewActions.click())

        Espresso.onView(withId(R.id.play_preset_button))
          .perform(ViewActions.click())

        verify(exactly = 1) { MediaPlayerService.playRandomPreset(any(), tag, intensityRange) }
        clearMocks(MediaPlayerService.Companion)
      }
    }
  }
}
