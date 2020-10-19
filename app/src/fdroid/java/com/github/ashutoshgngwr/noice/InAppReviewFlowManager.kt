package com.github.ashutoshgngwr.noice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import kotlin.random.Random

/**
 * [InAppReviewFlowManager] shows a confirmation dialog to the user asking for feedback.
 *
 * It doesn't need to be initialized via [InAppReviewFlowManager.init] before the flow can be shown
 * to the user but a stub definition is kept to make the API consistent with the Play Store variant.
 * If the user has opted for 'don't show again', the flow will never be shown again. Otherwise,
 * it'll be shown 1/20 calls to [InAppReviewFlowManager.maybeAskForReview].
 */
object InAppReviewFlowManager {

  private val TAG = this::class.simpleName

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  const val PREF_FLOW_SUCCESSFULLY_COMPLETED = "flow_successfully_completed"

  /**
   * [init] is a stub method kept for maintaining consistent [InAppReviewFlowManager] APIs among
   * F-Droid and Play Store app variants.
   */
  @Suppress("UNUSED_PARAMETER")
  fun init(context: Context) = Unit

  /**
   * [maybeAskForReview] checks whether user has completed the InAppReview flow, i.e. whether user
   * has potentially submitted a feedback or opted not to the see the flow again. If the checks
   * satisfy, it displays the review dialog with a pseudo-random probability of 1/20.
   */
  fun maybeAskForReview(activity: FragmentActivity) {
    if (hasUserCompletedReviewFlow(activity)) {
      Log.d(TAG, "user has already completed the in-app review flow. abandoning request!")
      return
    }

    if (Random.nextInt(20) != 0) {
      Log.d(TAG, "abandoning request due to bad luck!")
      return
    }

    DialogFragment().show(activity.supportFragmentManager) {
      title(R.string.in_app_feedback__title)
      message(R.string.in_app_feedback__description)
      negativeButton(R.string.later)
      neutralButton(R.string.never) { markReviewFlowCompleted(activity) }
      positiveButton(R.string.okay) {
        markReviewFlowCompleted(activity)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.app_github_url))))
      }
    }
  }

  private fun hasUserCompletedReviewFlow(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
      .getBoolean(PREF_FLOW_SUCCESSFULLY_COMPLETED, false)
  }

  private fun markReviewFlowCompleted(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putBoolean(PREF_FLOW_SUCCESSFULLY_COMPLETED, true)
    }
  }
}
