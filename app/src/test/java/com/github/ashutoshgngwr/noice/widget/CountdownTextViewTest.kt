package com.github.ashutoshgngwr.noice.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class CountdownTextViewTest {

  private lateinit var view: CountdownTextView

  @Before
  fun setup() {
    view = CountdownTextView(RuntimeEnvironment.systemContext)
    shadowOf(view).callOnAttachedToWindow()
  }

  @Test
  fun testWithZeroCountdown() {
    view.startCountdown(0L)
    assertEquals("00h 00m 00s", view.text.toString())
  }

  @Test
  fun testWithNonZeroCountdown_textOnStart() {
    view.startCountdown(3661000L)
    assertNotEquals("00h 00m 0s", view.text.toString())
  }

  @Test
  fun testWithNonZeroCountdown_textUntilExpiry() {
    // this test case may fail in future if View's update interval changes from 1000L
    view.startCountdown(2000L)
    assertEquals("00h 00m 02s", view.text.toString())

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals("00h 00m 01s", view.text.toString())

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals("00h 00m 00s", view.text.toString())
  }
}
