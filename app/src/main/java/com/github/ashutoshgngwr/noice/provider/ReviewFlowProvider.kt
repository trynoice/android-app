package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import kotlin.random.Random

/**
 * [ReviewFlowProvider] provides a flow to prompt users, asking them for feedback. Its
 * implementation may defer based on build variants.
 */
interface ReviewFlowProvider {

  /**
   * [init] may be used to perform any initialisation tasks before [maybeAskForReview] can be called.
   */
  fun init(context: Context)

  /**
   * [maybeAskForReview] should check whether user has completed the review flow. If the check
   * satisfies, it may display the review flow to the user.
   */
  fun maybeAskForReview(activity: FragmentActivity)
}

/**
 * [GitHubReviewFlowProvider] provides a simple review flow that prompts user for feedback through
 * GitHub stars and issues.
 *
 * If the user has opted for 'don't show again', the flow will never be shown again. Otherwise,
 * it'll be shown every 1/20 calls to [GitHubReviewFlowProvider.maybeAskForReview].
 */
object GitHubReviewFlowProvider : ReviewFlowProvider {

  private val TAG = this::class.simpleName

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  const val PREF_FLOW_SUCCESSFULLY_COMPLETED = "flow_successfully_completed"

  override fun init(context: Context) = Unit

  /**
   * [maybeAskForReview] checks whether user has completed the InAppReview flow, i.e. whether user
   * has potentially submitted a feedback or opted not to the see the flow again. If the checks
   * satisfy, it displays the review dialog with a pseudo-random probability of 1/20.
   */
  override fun maybeAskForReview(activity: FragmentActivity) {
    if (hasUserCompletedReviewFlow(activity)) {
      Log.d(TAG, "user has already completed the in-app review flow. abandoning request!")
      return
    }

    if (Random.nextInt(20) != 0) {
      Log.d(TAG, "abandoning request due to bad luck!")
      return
    }

    DialogFragment.show(activity.supportFragmentManager) {
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
