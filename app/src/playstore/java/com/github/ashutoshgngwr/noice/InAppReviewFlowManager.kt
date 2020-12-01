package com.github.ashutoshgngwr.noice

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit

/**
 * [InAppReviewFlowManager] manages the complete lifecycle of in-app review API from the Google Play
 * Core API group.
 *
 * It needs to be initialized via [InAppReviewFlowManager.init] before the review flow can be shown
 * to the user using [InAppReviewFlowManager.maybeAskForReview]. The review flow will not always be
 * shown. All original quota restrictions apply from the API. In addition to this,
 * [InAppReviewFlowManager] drops all request to display review flow within a week of a successful
 * launch review flow API call.
 */
object InAppReviewFlowManager {

  private val TAG = this::class.simpleName

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  const val PREF_LAST_SHOWN_ON = "last_shown_on"

  private lateinit var reviewManager: ReviewManager
  private lateinit var reviewInfo: ReviewInfo

  /**
   * [init] initializes the [ReviewManager] and requests the [ReviewInfo] from the [ReviewManager].
   */
  fun init(context: Context) {
    reviewManager = ReviewManagerFactory.create(context)
    reviewManager.requestReviewFlow()
      .addOnFailureListener {
        Log.w(TAG, "review flow request failed!", it)
      }.addOnSuccessListener {
        Log.d(TAG, "review flow request successful!")
        reviewInfo = it
      }
  }

  /**
   * [maybeAskForReview] checks whether [ReviewInfo] has been initialized and whether user has
   * successfully completed the review flow within past 7 days. If both conditions are false,
   * it requests the Google Play API to launch the review flow.
   */
  fun maybeAskForReview(activity: FragmentActivity) {
    if (!this::reviewInfo.isInitialized) {
      Log.d(TAG, "review info isn't initialized. can not launch review flow!")
      return
    }

    if (isShownWithinLastWeek(activity)) {
      Log.d(TAG, "review flow was shown within last week. abandoning request!")
      return
    }

    reviewManager.launchReviewFlow(activity, reviewInfo)
      .addOnFailureListener {
        Log.w(TAG, "failed to complete review flow!", it)
      }
      .addOnSuccessListener {
        Log.d(TAG, "review flow successfully completed!")
        updateLastSeenTimestamp(activity)
      }
  }

  private fun updateLastSeenTimestamp(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putLong(PREF_LAST_SHOWN_ON, System.currentTimeMillis())
    }
  }

  private fun isShownWithinLastWeek(context: Context): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var timestamp = prefs.getLong(PREF_LAST_SHOWN_ON, 0L)

    // refrain from showing review flow to the users newer than a day.
    if (timestamp == 0L) {
      timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6L)
      prefs.edit { putLong(PREF_LAST_SHOWN_ON, timestamp) }
    }

    return TimeUnit.DAYS.toMillis(7L) + timestamp > System.currentTimeMillis()
  }
}
