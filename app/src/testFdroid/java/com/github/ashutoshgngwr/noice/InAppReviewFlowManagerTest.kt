package com.github.ashutoshgngwr.noice

import androidx.fragment.app.FragmentActivity
import io.mockk.every
import io.mockk.mockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class InAppReviewFlowManagerTest {

  private lateinit var fragmentActivity: FragmentActivity

  @Before
  fun setup() {
    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    mockkObject(Random.Default)
  }

  @After
  fun teardown() {
    Utils.withDefaultSharedPreferences(fragmentActivity) {
      it.edit().clear().commit()
    }
  }

  // These are a rather shallow tests. These don't check the correctness of the review dialog but only
  // check if it was shown. This is me being lazy because interacting with dialog would require me
  // to add another dependency to this source set. For that I'd have to open 'app/build.gradle' and
  // stuff so just let it go. okay? besides, I have manually tested the shit out of it.
  // For future me: https://imgur.com/a/ApXEmpZ

  @Test
  fun testMaybeAskForReview_whenUnlucky() {
    every { Random.Default.nextInt(any()) } returns 1 // expecting 0 but return something else
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // no new fragment should be created
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenLucky() {
    every { Random.Default.nextInt(any()) } returns 0 // returns the expected value
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // dialog fragment should be created
    assertEquals(1, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenUserHasCompletedReviewFlow() {
    every { Random.Default.nextInt(any()) } returns 0 // returns the expected value
    Utils.withDefaultSharedPreferences(fragmentActivity) {
      it.edit().putBoolean(InAppReviewFlowManager.PREF_FLOW_SUCCESSFULLY_COMPLETED, true).commit()
    }

    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // dialog fragment should not be created
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }
}
