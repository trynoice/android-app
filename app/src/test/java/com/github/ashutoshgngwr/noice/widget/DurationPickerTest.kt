package com.github.ashutoshgngwr.noice.widget

import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.android.synthetic.main.view_duration_picker.view.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationPickerTest {

  private lateinit var view: DurationPicker
  private lateinit var onDurationAddedListener: (Long) -> Unit

  @Before
  fun setup() {
    view = DurationPicker(ApplicationProvider.getApplicationContext())
    onDurationAddedListener = mockk()
    view.setOnDurationAddedListener(onDurationAddedListener)

    // following because https://github.com/mockk/mockk/issues/54
    every { onDurationAddedListener.invoke(any()) } just Runs
  }

  @Test
  fun testEnableDisableControls() {
    view.setResetButtonEnabled(true)
    assertTrue(view.button_reset.isEnabled)

    view.setResetButtonEnabled(false)
    assertFalse(view.button_reset.isEnabled)

    val controlButtons = arrayOf(
      view.button_1m,
      view.button_5m,
      view.button_30m,
      view.button_1h,
      view.button_4h,
      view.button_8h,
      view.button_reset
    )

    view.setControlsEnabled(false)
    controlButtons.forEach { assertFalse(it.isEnabled) }

    view.setControlsEnabled(true)
    controlButtons.forEach { assertTrue(it.isEnabled) }
  }

  @Test
  fun testResetButton() {
    view.button_reset.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(less(0L)) }
  }

  @Test
  fun test1mButton() {
    view.button_1m.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(60000L) }
  }

  @Test
  fun test5mButton() {
    view.button_5m.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(300000L) }
  }

  @Test
  fun test30mButton() {
    view.button_30m.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(1800000L) }
  }

  @Test
  fun test1hButton() {
    view.button_1h.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(3600000L) }
  }

  @Test
  fun test4hButton() {
    view.button_4h.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(14400000L) }
  }

  @Test
  fun test8hButton() {
    view.button_8h.performClick()
    verify(exactly = 1) { onDurationAddedListener.invoke(28800000L) }
  }
}
