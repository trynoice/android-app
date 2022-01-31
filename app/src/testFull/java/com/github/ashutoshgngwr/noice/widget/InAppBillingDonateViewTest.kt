package com.github.ashutoshgngwr.noice.widget

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.github.ashutoshgngwr.noice.HiltTestActivity
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class InAppBillingDonateViewTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var activity: Activity
  private lateinit var view: InAppBillingDonateView

  @set:Inject
  internal lateinit var mockBillingProvider: BillingProvider

  @Before
  fun setup() {
    activity = Robolectric.buildActivity(HiltTestActivity::class.java).create().get()
    view = InAppBillingDonateView(activity, TestCoroutineScope())
    hiltRule.inject()
  }

  @Test
  fun testQueryDetailsFailed() = runBlockingTest {
    coEvery {
      mockBillingProvider.queryDetails(any(), any())
    } throws BillingProvider.QueryDetailsException("test-error")

    view.loadSkuDetails()
    ShadowLooper.idleMainLooper()
    assertEquals(
      activity.getString(R.string.failed_to_load_inapp_purchases),
      view.findViewById<TextView>(R.id.error).text,
    )
  }

  @Test
  fun testOnInAppItemClick() = runBlockingTest {
    val skuDetailsList = listOf<BillingProvider.SkuDetails>(
      mockk(relaxed = true) { every { price } returns "price-1" },
      mockk(relaxed = true) { every { price } returns "price-2" }
    )

    coEvery { mockBillingProvider.queryDetails(any(), any()) } answers { skuDetailsList }
    view.loadSkuDetails()
    ShadowLooper.idleMainLooper()

    every { mockBillingProvider.purchase(any(), any()) } returns true
    val container = view.findViewById<ViewGroup>(R.id.button_container)
    container.children.forEach { it.performClick() }
    skuDetailsList.forEach {
      verify { mockBillingProvider.purchase(any(), it) }
    }
  }
}
