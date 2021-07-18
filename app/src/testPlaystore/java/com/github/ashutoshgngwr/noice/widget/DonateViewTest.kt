package com.github.ashutoshgngwr.noice.widget

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.android.billingclient.api.SkuDetails
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.provider.RealBillingProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class DonateViewTest {

  private lateinit var activity: Activity
  private lateinit var view: DonateView
  private lateinit var testScope: CoroutineScope

  @Before
  fun setup() {
    mockkObject(RealBillingProvider)
    activity = Robolectric.buildActivity(Activity::class.java).create().get()
    testScope = TestCoroutineScope()
    view = DonateView(activity, testScope)
  }

  @Test
  fun testQueryDetailsFailed() = runBlockingTest {
    coEvery {
      RealBillingProvider.queryDetails(any(), any())
    } throws RealBillingProvider.QueryDetailsException("test-error")

    view.loadSkuDetails()
    ShadowLooper.idleMainLooper()
    assertEquals(
      activity.getString(R.string.failed_to_load_inapp_purchases),
      view.findViewById<TextView>(R.id.error).text,
    )
  }

  @Test
  fun testOnInAppItemClick() = runBlockingTest {
    val skuDetailsList = listOf<SkuDetails>(
      mockk(relaxed = true) { every { price } returns "price-1" },
      mockk(relaxed = true) { every { price } returns "price-2" }
    )

    coEvery { RealBillingProvider.queryDetails(any(), any()) } answers { skuDetailsList }
    view.loadSkuDetails()
    ShadowLooper.idleMainLooper()

    every { RealBillingProvider.purchase(any(), any()) } returns true
    val container = view.findViewById<ViewGroup>(R.id.button_container)
    container.children.forEach { it.performClick() }
    skuDetailsList.forEach {
      verify { RealBillingProvider.purchase(any(), it) }
    }
  }
}
