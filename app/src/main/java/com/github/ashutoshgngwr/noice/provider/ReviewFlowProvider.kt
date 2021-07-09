package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.github.ashutoshgngwr.noice.NoiceApplication

/**
 * [ReviewFlowProvider] provides a flow to prompt users, asking them for feedback. Its
 * implementation may defer based on build variants.
 */
interface ReviewFlowProvider {

  companion object {
    /**
     * [of] is a convenience method that returns the default [ReviewFlowProvider] of the
     * [NoiceApplication].
     */
    fun of(context: Context): ReviewFlowProvider {
      return (context.applicationContext as NoiceApplication).getReviewFlowProvider()
    }
  }

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
