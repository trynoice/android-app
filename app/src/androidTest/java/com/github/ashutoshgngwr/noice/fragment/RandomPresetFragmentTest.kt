package com.github.ashutoshgngwr.noice.fragment

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RandomPresetFragmentTest {

  @Before
  fun setup() {
    mockkObject(PlaybackController)
    every { PlaybackController.playRandomPreset(any(), any(), any()) } returns Unit
    ApplicationProvider.getApplicationContext<NoiceApplication>()
      .reviewFlowProvider = mockk(relaxed = true)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testRandomPresetButton_onClick() {
    val intensities = mapOf(
      R.id.preset_intensity__any to RandomPresetFragment.RANGE_INTENSITY_ANY,
      R.id.preset_intensity__dense to RandomPresetFragment.RANGE_INTENSITY_DENSE,
      R.id.preset_intensity__light to RandomPresetFragment.RANGE_INTENSITY_LIGHT
    )

    val types = mapOf(
      R.id.preset_type__any to null,
      R.id.preset_type__focus to Sound.Tag.FOCUS,
      R.id.preset_type__relax to Sound.Tag.RELAX
    )

    launchFragmentInContainer<RandomPresetFragment>(null, R.style.Theme_App)

    val intensityID = intensities.keys.random()
    val typeID = types.keys.random()

    onView(withId(typeID)).perform(ViewActions.click())
    onView(withId(intensityID)).perform(ViewActions.click())

    onView(withId(R.id.play_button))
      .perform(ViewActions.click())

    verify(exactly = 1) {
      PlaybackController.playRandomPreset(
        any(), types[typeID],
        intensities[intensityID] ?: IntRange.EMPTY
      )
    }
  }
}
