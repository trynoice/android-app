package com.github.ashutoshgngwr.noice.widget

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CountdownTextViewTest {

  private lateinit var view: CountdownTextView
  private lateinit var shadowLooper: ShadowLooper

  @Before
  fun setup() {
    view = CountdownTextView(
      ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_App)
    )

    shadowLooper = shadowOf(view.countdownHandler.looper)
  }

  @Test
  fun testWithZeroCountdown() {
    view.startCountdown(0L)
    shadowLooper.idle()
    assertEquals("00h 00m 00s", view.text.toString())
  }

  @Test
  fun testWithNonZeroCountdown() {
    view.startCountdown(2100L)
    shadowLooper.idle()
    assertEquals("00h 00m 02s", view.text.toString())

    shadowLooper.idleFor(1, TimeUnit.SECONDS)
    assertEquals("00h 00m 01s", view.text.toString())

    shadowLooper.idleFor(1, TimeUnit.SECONDS)
    assertEquals("00h 00m 00s", view.text.toString())
  }
}
