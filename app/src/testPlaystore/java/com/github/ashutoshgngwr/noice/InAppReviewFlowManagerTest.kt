package com.github.ashutoshgngwr.noice

import androidx.fragment.app.FragmentActivity
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.OnSuccessListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InAppReviewFlowManagerTest {

  private lateinit var fragmentActivity: FragmentActivity
  private lateinit var mockReviewManager: ReviewManager
  private lateinit var mockReviewInfo: ReviewInfo

  @Before
  fun setup() {
    fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    mockReviewManager = mockk(relaxed = true)
    mockReviewInfo = mockk(relaxed = true)
    val successListenerSlot = slot<OnSuccessListener<ReviewInfo>>()

    mockkStatic(ReviewManagerFactory::class)
    every { ReviewManagerFactory.create(any()) } returns mockReviewManager
    every { mockReviewManager.requestReviewFlow() } returns mockk(relaxed = true) {
      every { addOnFailureListener(any()) } returns this
      every { addOnSuccessListener(capture(successListenerSlot)) } returns this
    }

    InAppReviewFlowManager.init(fragmentActivity)
    successListenerSlot.captured.onSuccess(mockReviewInfo)
  }

  @After
  fun teardown() {
    Utils.withDefaultSharedPreferences(fragmentActivity) {
      it.edit().clear().commit()
    }
  }

  @Test
  fun testMaybeAskForReview_whenNotShownWithinLastWeek() {
    // capture the success listener
    val successListenerSlot = slot<OnSuccessListener<Void>>()
    every { mockReviewManager.launchReviewFlow(any(), any()) } returns mockk(relaxed = true) {
      every { addOnFailureListener(any()) } returns this
      every { addOnSuccessListener(capture(successListenerSlot)) } returns this
    }

    // try to show review flow for the first time, should work
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // invoke the success listener
    successListenerSlot.captured.onSuccess(null)

    // should call launchReviewFlow on reviewManager
    verify(exactly = 1) { mockReviewManager.launchReviewFlow(any(), mockReviewInfo) }
  }

  @Test
  fun testMaybeAskForReview_whenShownWithinLastWeek() {
    Utils.withDefaultSharedPreferences(fragmentActivity) {
      it.edit()
        .putLong(InAppReviewFlowManager.PREF_LAST_SHOWN_ON, System.currentTimeMillis())
        .commit()
    }

    // try to show review flow again, should not work since it was shown less than a week ago
    InAppReviewFlowManager.maybeAskForReview(fragmentActivity)

    // should call launchReviewFlow on reviewManager
    verify(exactly = 0) { mockReviewManager.launchReviewFlow(any(), mockReviewInfo) }
  }
}
