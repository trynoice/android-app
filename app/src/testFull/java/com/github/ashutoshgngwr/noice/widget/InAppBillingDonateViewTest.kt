package com.github.ashutoshgngwr.noice.widget

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.github.ashutoshgngwr.noice.BillingProviderModule
import com.github.ashutoshgngwr.noice.HiltTestActivity
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@UninstallModules(BillingProviderModule::class)
@RunWith(RobolectricTestRunner::class)
class InAppBillingDonateViewTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var activity: Activity
  private lateinit var view: InAppBillingDonateView

  @BindValue
  internal lateinit var mockBillingProvider: BillingProvider

  @Before
  fun setup() {
    mockBillingProvider = mockk(relaxed = true)
    activity = Robolectric.buildActivity(HiltTestActivity::class.java).create().get()
    view = InAppBillingDonateView(activity, TestScope())
  }

  @Test
  fun testQueryDetailsFailed() = runTest {
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
  fun testOnInAppItemClick() = runTest {
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
