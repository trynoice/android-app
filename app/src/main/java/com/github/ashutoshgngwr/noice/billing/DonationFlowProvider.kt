package com.github.ashutoshgngwr.noice.billing

import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager

/**
 * [DonationFlowProvider] provides an interface to add different variants of donate buttons in
 * support development fragment.
 */
interface DonationFlowProvider {

  /**
   * Adds donation buttons to the container with the given [containerId].
   */
  fun addButtons(fragmentManager: FragmentManager, @IdRes containerId: Int)

  /**
   * A host activity for displaying relevant UI fragments on receiving purchase callbacks from the
   * billing provider. The provider internally handles host activity's lifecycle changes.
   */
  fun setCallbackFragmentHost(hostActivity: AppCompatActivity)
}
