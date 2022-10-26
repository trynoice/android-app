package com.github.ashutoshgngwr.noice.ext

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.widget.SnackBar
import com.google.android.material.snackbar.Snackbar

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(@StringRes msgResId: Int): Snackbar {
  return showInfoSnackBar(getString(msgResId))
}

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(msg: String): Snackbar {
  return SnackBar.info(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(@StringRes msgResId: Int): Snackbar {
  return showSuccessSnackBar(getString(msgResId))
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(msg: String): Snackbar {
  return SnackBar.success(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(@StringRes msgResId: Int): Snackbar {
  return showErrorSnackBar(getString(msgResId))
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(msg: String): Snackbar {
  return SnackBar.error(snackBarView(), msg)
    .setAnchorView((activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

private fun Fragment.snackBarView(): View {
  return activity?.findViewById(R.id.main_nav_host_fragment) ?: requireView()
}
