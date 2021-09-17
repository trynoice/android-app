package com.github.ashutoshgngwr.noice.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DonateViewTest {

  private lateinit var application: Application
  private lateinit var view: DonateView

  @Before
  fun setup() {
    application = ApplicationProvider.getApplicationContext()
    view = DonateView(application)
  }

  @Test
  fun testOnClick() {
    view.performClick()
    shadowOf(application).nextStartedActivity.also {
      val expectedURL = application.getString(R.string.support_development__donate_url)
      assertEquals(expectedURL, it.data?.toString())
    }
  }
}
