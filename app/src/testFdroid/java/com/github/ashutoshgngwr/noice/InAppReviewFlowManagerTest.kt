package com.github.ashutoshgngwr.noice

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class InAppReviewFlowManagerTest {

  private lateinit var fragmentActivity: FragmentActivity
  private lateinit var mockPrefs: SharedPreferences

  @Before
  fun setup() {
    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    mockPrefs = mockk(relaxed = true)

    mockkObject(Random.Default)
    mockkStatic(PreferenceManager::class)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  // These are rather shallow tests. These don't check the correctness of the review dialog but only
  // check if it was shown. This is me being lazy because interacting with dialog would require me
  // to add another dependency to this source set. For that I'd have to open 'app/build.gradle' and
  // stuff so just let it go. okay? besides, I have manually tested the shit out of it.
  // For future me: https://imgur.com/a/ApXEmpZ

  @Test
  fun testMaybeAskForReview_whenUnlucky() {
    every { Random.Default.nextInt(any()) } returns 1 // expecting 0 but return something else
    every {
      mockPrefs.getBoolean(InAppReviewFlowManager.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns false

    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // dialog fragment should not be shown
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenLucky() {
    every { Random.Default.nextInt(any()) } returns 0 // returns the expected value
    every {
      mockPrefs.getBoolean(InAppReviewFlowManager.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns false

    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)
    ShadowLooper.idleMainLooper()

    // dialog fragment should be shown
    assertEquals(1, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenUserHasCompletedReviewFlow() {
    every { Random.Default.nextInt(any()) } returns 0 // returns the expected value
    every {
      mockPrefs.getBoolean(InAppReviewFlowManager.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns true

    PreferenceManager.getDefaultSharedPreferences(fragmentActivity).edit(commit = true) {
      putBoolean(InAppReviewFlowManager.PREF_FLOW_SUCCESSFULLY_COMPLETED, true)
    }

    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // dialog fragment should not be shown
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }
}
