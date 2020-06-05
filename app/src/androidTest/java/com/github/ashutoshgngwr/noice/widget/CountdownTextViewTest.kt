package com.github.ashutoshgngwr.noice.widget

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.MainActivity
import com.github.ashutoshgngwr.noice.RetryTestRule
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep

@RunWith(AndroidJUnit4::class)
class CountdownTextViewTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var view: CountdownTextView
  private val activityScenario = ActivityScenario.launch(MainActivity::class.java)

  @Before
  fun setup() {
    activityScenario.onActivity {
      view = CountdownTextView(it)
      view.id = android.R.id.text1
      it.setContentView(view)
    }
  }

  @Test
  fun testWithZeroCountdown() {
    activityScenario.onActivity {
      view.startCountdown(0L)
    }

    onView(withId(view.id)).check(matches(withText("00h 00m 00s")))
  }

  @Test
  fun testWithNonZeroCountdown_textOnStart() {
    activityScenario.onActivity {
      view.startCountdown(3661000L)
    }

    onView(withId(view.id)).check(matches(not(withText("00h 00m 00s"))))
  }

  @Test
  fun testWithNonZeroCountdown_textAfterExpiry() {
    activityScenario.onActivity {
      view.startCountdown(2000L)
    }

    sleep(2500L)
    onView(withId(view.id)).check(matches(withText("00h 00m 00s")))
  }
}
