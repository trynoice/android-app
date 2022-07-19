package com.github.ashutoshgngwr.noice.ext

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.widget.SnackBar

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(@StringRes msgResId: Int) {
  showInfoSnackBar(getString(msgResId))
}

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(msg: String) {
  SnackBar.info(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .show()
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(@StringRes msgResId: Int) {
  showSuccessSnackBar(getString(msgResId))
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(msg: String) {
  SnackBar.success(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .show()
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(@StringRes msgResId: Int) {
  showErrorSnackBar(getString(msgResId))
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(msg: String) {
  SnackBar.error(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .show()
}

private fun Fragment.snackBarView(): View {
  return activity?.findViewById(R.id.main_nav_host_fragment) ?: requireView()
}
