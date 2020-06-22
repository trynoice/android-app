package com.github.ashutoshgngwr.noice

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anjlab.android.iab.v3.BillingProcessor
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DonateActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var mockBillingProcessor: BillingProcessor
  private lateinit var activityScenario: ActivityScenario<DonateActivity>

  @Before
  fun setup() {
    mockkStatic(BillingProcessor::class)
    mockBillingProcessor = mockk(relaxed = true)
    every { BillingProcessor.newBillingProcessor(any(), any(), any()) } returns mockBillingProcessor

    Intent(ApplicationProvider.getApplicationContext(), DonateActivity::class.java).also {
      it.putExtra(DonateActivity.EXTRA_DONATE_AMOUNT, "test")
      activityScenario = ActivityScenario.launch(it)
    }
  }

  @Test
  fun testBillingProcessorInitialization() {
    verify(exactly = 1) { mockBillingProcessor.initialize() }
    clearMocks(mockBillingProcessor)

    // should release BillingProcessor on destroy
    every { mockBillingProcessor.isInitialized } returns true
    activityScenario.moveToState(Lifecycle.State.DESTROYED)
    verify(exactly = 1) { mockBillingProcessor.release() }
  }

  @Test
  fun testOnBillingProcessorInitialized() {
    activityScenario.onActivity {
      it.onBillingInitialized()
      verify(exactly = 1) { mockBillingProcessor.purchase(it, "test") }
    }
  }

  @Test
  fun testOnBillingFailed() {
    activityScenario.onActivity {
      it.onBillingError(0, mockk())
    }

    assertEquals(Lifecycle.State.DESTROYED, activityScenario.state)
  }

  @Test
  fun testOnBillingSuccess() {
    activityScenario.onActivity {
      it.onProductPurchased("test", mockk())
      verify(exactly = 1) { mockBillingProcessor.consumePurchase("test") }
    }

    EspressoX.waitForView(withText(R.string.support_development__donate_thank_you), 100, 5)
      .check(matches(isDisplayed()))

    onView(withText(android.R.string.ok)).perform(click())
    assertEquals(Lifecycle.State.DESTROYED, activityScenario.state)
  }
}
