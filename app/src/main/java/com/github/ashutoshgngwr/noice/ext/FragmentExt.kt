package com.github.ashutoshgngwr.noice.ext

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Shows a [Snackbar] anchored to [R.id.bottom_nav] (if present) and returns it.
 */
fun Fragment.showSnackbar(
  @StringRes msgRes: Int,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  return showSnackbar(getString(msgRes), length)
}

/**
 * Shows a [Snackbar] anchored to [R.id.bottom_nav] (if present) and returns it.
 */
fun Fragment.showSnackbar(
  msg: String,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  // pass Snackbar a view that is likely to be available even if current fragment is currently being
  // destroyed. This happens frequently in case of bottom sheet dialog fragments.
  val view = activity?.findViewById(R.id.main_nav_host_fragment) ?: requireView()
  return Snackbar.make(view, msg, length).apply {
    anchorView = activity?.findViewById(R.id.bottom_nav)
    show()
  }
}
