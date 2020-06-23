package com.github.ashutoshgngwr.noice.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.DonateActivity
import kotlinx.android.synthetic.playstore.view_donate.view.*
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
    mapOf(
      view.button_usd1 to DonateActivity.DONATE_AMOUNT_1USD,
      view.button_usd2 to DonateActivity.DONATE_AMOUNT_2USD,
      view.button_usd5 to DonateActivity.DONATE_AMOUNT_5USD,
      view.button_usd10 to DonateActivity.DONATE_AMOUNT_10USD,
      view.button_usd15 to DonateActivity.DONATE_AMOUNT_15USD,
      view.button_usd25 to DonateActivity.DONATE_AMOUNT_25USD
    ).forEach { testCase ->
      testCase.key.performClick()
      shadowOf(application).nextStartedActivity.also {
        assertEquals(DonateActivity::class.qualifiedName, it.component?.className)
        assertEquals(testCase.value, it.getStringExtra(DonateActivity.EXTRA_DONATE_AMOUNT))
      }
    }
  }
}
