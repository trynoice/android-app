package com.github.ashutoshgngwr.noice.widget

import android.app.Application
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.activity.DonateActivity
import com.github.ashutoshgngwr.noice.databinding.DonateViewBinding
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
  private lateinit var binding: DonateViewBinding

  @Before
  fun setup() {
    application = ApplicationProvider.getApplicationContext()
    view = DonateView(application)
    binding = DonateViewBinding.bind(view.children.first())
  }

  @Test
  fun testOnClick() {
    mapOf(
      binding.oneUsdButton to DonateActivity.DONATE_AMOUNT_1USD,
      binding.twoUsdButton to DonateActivity.DONATE_AMOUNT_2USD,
      binding.fiveUsdButton to DonateActivity.DONATE_AMOUNT_5USD,
      binding.tenUsdButton to DonateActivity.DONATE_AMOUNT_10USD,
      binding.fifteenUsdButton to DonateActivity.DONATE_AMOUNT_15USD,
      binding.twentyfiveUsdButton to DonateActivity.DONATE_AMOUNT_25USD
    ).forEach { testCase ->
      testCase.key.performClick()
      shadowOf(application).nextStartedActivity.also {
        assertEquals(DonateActivity::class.qualifiedName, it.component?.className)
        assertEquals(testCase.value, it.getStringExtra(DonateActivity.EXTRA_DONATE_AMOUNT))
      }
    }
  }
}
