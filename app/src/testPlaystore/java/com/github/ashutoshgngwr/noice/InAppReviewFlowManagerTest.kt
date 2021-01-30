package com.github.ashutoshgngwr.noice

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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InAppReviewFlowManagerTest {

  private lateinit var fragmentActivity: FragmentActivity
  private lateinit var fakeReviewManager: ReviewManager
  private lateinit var mockPrefs: SharedPreferences

  @Before
  fun setup() {
    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    fakeReviewManager = FakeReviewManager(fragmentActivity)
    mockPrefs = mockk(relaxed = true)

    mockkStatic(PreferenceManager::class)
    mockkStatic(ReviewManagerFactory::class)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { ReviewManagerFactory.create(any()) } returns fakeReviewManager
    InAppReviewFlowManager.init(fragmentActivity)
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

    every {
      mockPrefs.getLong(InAppReviewFlowManager.PREF_LAST_SHOWN_ON, any())
    } returns System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7L)

    // try to show review flow for the first time, should work
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)
    ShadowLooper.idleMainLooper()

    // should update the last shown timestamp in shared preferences
    val timestampSlot = slot<Long>()
    verify(exactly = 1) {
      mockPrefsEditor.putLong(InAppReviewFlowManager.PREF_LAST_SHOWN_ON, capture(timestampSlot))
    }

    assertNotEquals(0, timestampSlot.captured)
  }

  @Test
  fun testMaybeAskForReview_whenShownWithinLastWeek() {
    every {
      mockPrefs.getLong(InAppReviewFlowManager.PREF_LAST_SHOWN_ON, any())
    } returns System.currentTimeMillis()

    // try to show review flow again, should not work since it was shown less than a week ago
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // should call launchReviewFlow on reviewManager. since FakeReviewManager is supposed to invoke
    // success listener if launchReviewFlow was invoked, the timestamp will change
    verify(exactly = 0) { mockPrefs.edit() }
  }
}
