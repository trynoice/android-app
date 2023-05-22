package com.github.ashutoshgngwr.noice.metrics

import android.content.Context
import androidx.fragment.app.FragmentActivity

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

