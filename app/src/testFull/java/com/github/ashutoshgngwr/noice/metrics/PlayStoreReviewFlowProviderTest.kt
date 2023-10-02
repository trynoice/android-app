package com.github.ashutoshgngwr.noice.metrics

import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class PlayStoreReviewFlowProviderTest {

  private lateinit var fragmentActivity: FragmentActivity
  private lateinit var fakeReviewManager: ReviewManager
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var provider: PlayStoreReviewFlowProvider

  @Before
  fun setup() {
    mockkStatic(PreferenceManager::class)
    mockkStatic(ReviewManagerFactory::class)

    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    fakeReviewManager = FakeReviewManager(fragmentActivity)
    provider = PlayStoreReviewFlowProvider()
    mockPrefs = mockk(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { ReviewManagerFactory.create(any()) } returns fakeReviewManager
    provider.init(fragmentActivity)
    ShadowLooper.idleMainLooper() // to let the fake review manager return its ReviewInfo object
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testMaybeAskForReview_whenNotShownWithinLastWeek() {
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { mockPrefs.edit() } returns this
      every { putLong(any(), any()) } returns this
    }

    // try to show review flow for the first time, should work
    provider.maybeAskForReview(fragmentActivity)
    ShadowLooper.idleMainLooper()

    // should update the last shown timestamp in shared preferences
    val timestampSlot = slot<Long>()
    verify(exactly = 1) {
      mockPrefsEditor.putLong(
        PlayStoreReviewFlowProvider.PREF_LAST_SHOWN_ON,
        capture(timestampSlot)
      )
    }

    assertNotEquals(0, timestampSlot.captured)
  }

  @Test
  fun testMaybeAskForReview_whenShownWithinLastWeek() {
    every {
      mockPrefs.getLong(PlayStoreReviewFlowProvider.PREF_LAST_SHOWN_ON, any())
    } returns System.currentTimeMillis()

    // try to show review flow again, should not work since it was shown less than a week ago
    provider.maybeAskForReview(fragmentActivity)

    // should call launchReviewFlow on reviewManager. since FakeReviewManager is supposed to invoke
    // success listener if launchReviewFlow was invoked, the timestamp will change
    verify(exactly = 0) { mockPrefs.edit() }
  }
}
