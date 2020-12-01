package com.github.ashutoshgngwr.noice.widget

import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.databinding.DurationPickerViewBinding
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationPickerTest {

  private lateinit var view: DurationPicker
  private lateinit var binding: DurationPickerViewBinding
  private lateinit var onDurationAddedListener: (Long) -> Unit

  @Before
  fun setup() {
    view = DurationPicker(ApplicationProvider.getApplicationContext())
    binding = DurationPickerViewBinding.bind(view.children.first())
    onDurationAddedListener = mockk()
    view.setOnDurationAddedListener(onDurationAddedListener)

    // following because https://github.com/mockk/mockk/issues/54
    every { onDurationAddedListener.invoke(any()) } just Runs
  }

  @Test
  fun testEnableDisableControls() {
    view.setResetButtonEnabled(true)
    assertTrue(binding.resetButton.isEnabled)

    view.setResetButtonEnabled(false)
    assertFalse(binding.resetButton.isEnabled)

    val controlButtons = arrayOf(
      binding.oneMinuteButton,
      binding.fiveMinuteButton,
      binding.thirtyMinuteButton,
      binding.oneHourButton,
      binding.fourHourButton,
      binding.eightHourButton,
      binding.resetButton
    )

    view.setControlsEnabled(false)
    controlButtons.forEach { assertFalse(it.isEnabled) }

    view.setControlsEnabled(true)
    controlButtons.forEach { assertTrue(it.isEnabled) }
  }

  @Test
  fun testResetButton() {
    binding.resetButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(less(0L)) }
  }

  @Test
  fun test1mButton() {
    binding.oneMinuteButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(60000L) }
  }

  @Test
  fun test5mButton() {
    binding.fiveMinuteButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(300000L) }
  }

  @Test
  fun test30mButton() {
    binding.thirtyMinuteButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(1800000L) }
  }

  @Test
  fun test1hButton() {
    binding.oneHourButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(3600000L) }
  }

  @Test
  fun test4hButton() {
    binding.fourHourButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(14400000L) }
  }

  @Test
  fun test8hButton() {
    binding.eightHourButton.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(28800000L) }
  }
}
