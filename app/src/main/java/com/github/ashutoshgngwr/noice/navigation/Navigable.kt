package com.github.ashutoshgngwr.noice.navigation

import androidx.annotation.IdRes
import androidx.navigation.fragment.NavHostFragment

/**
 * Fragments that host their own [NavHostFragment][androidx.navigation.fragment.NavHostFragment] can
 * implement [Navigable] to let their owner [Activity][android.app.Activity] pass them new
 * destinations if they're at the top of the backstack. This loosely mimics `singleInstance`
 * activity behaviour such that navigable fragments can accept new destinations without being
 * recreated.
 */
interface Navigable {
  /**
   * Invoked when a new destination is selected externally.
   *
   * @return `true` if this [Navigable] was able to set the selected destination. `false` otherwise.
   */
  fun onNavDestinationSelected(@IdRes destID: Int): Boolean

  companion object {

    /**
     * Checks the current fragment at the top of [NavHostFragment]'s backstack implements
     * [Navigable] and navigates it to the provided [destID].
     *
     * @return Returns `true` if navigation was successful, `false` otherwise.
     */
    fun navigate(navHostFragment: NavHostFragment?, @IdRes destID: Int): Boolean {
      navHostFragment ?: return false
      val fragments = navHostFragment.childFragmentManager.fragments
      if (fragments.size < 1) {
        return false
      }

      val top = fragments.first()
      if (top is Navigable) {
        return top.onNavDestinationSelected(destID)
      }

      return false
    }
  }
}
