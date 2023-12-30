package com.github.ashutoshgngwr.noice.metrics

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import io.mockk.every
import io.mockk.mockk
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
import java.util.Random

@RunWith(RobolectricTestRunner::class)
class GitHubReviewFlowProviderTest {

  private lateinit var fragmentActivity: FragmentActivity
  private lateinit var mockRandom: Random
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var provider: GitHubReviewFlowProvider

  @Before
  fun setup() {
    mockkStatic(PreferenceManager::class)
    mockRandom = mockk(relaxed = true)
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    provider = GitHubReviewFlowProvider(mockRandom)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testMaybeAskForReview_whenUnlucky() {
    every { mockRandom.nextInt(any()) } returns 1 // expecting 0 but return something else
    every {
      mockPrefs.getBoolean(GitHubReviewFlowProvider.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns false

    provider.maybeAskForReview(fragmentActivity)

    // dialog fragment should not be shown
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenLucky() {
    every { mockRandom.nextInt(any()) } returns 0 // returns the expected value
    every {
      mockPrefs.getBoolean(GitHubReviewFlowProvider.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns false

    provider.maybeAskForReview(fragmentActivity)
    ShadowLooper.idleMainLooper()

    // dialog fragment should be shown
    assertEquals(1, fragmentActivity.supportFragmentManager.fragments.size)
  }

  @Test
  fun testMaybeAskForReview_whenUserHasCompletedReviewFlow() {
    every { mockRandom.nextInt(any()) } returns 0 // returns the expected value
    every {
      mockPrefs.getBoolean(GitHubReviewFlowProvider.PREF_FLOW_SUCCESSFULLY_COMPLETED, any())
    } returns true

    PreferenceManager.getDefaultSharedPreferences(fragmentActivity).edit {
      putBoolean(GitHubReviewFlowProvider.PREF_FLOW_SUCCESSFULLY_COMPLETED, true)
    }

    provider.maybeAskForReview(fragmentActivity)

    // dialog fragment should not be shown
    assertEquals(0, fragmentActivity.supportFragmentManager.fragments.size)
  }
}
