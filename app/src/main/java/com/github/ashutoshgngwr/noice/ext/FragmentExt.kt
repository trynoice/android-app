package com.github.ashutoshgngwr.noice.ext

import android.text.format.DateFormat
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.widget.SnackBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(@StringRes msgResId: Int, anchorView: View? = null): Snackbar {
  return showInfoSnackBar(getString(msgResId), anchorView)
}

/**
 * Shows info level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showInfoSnackBar(msg: String, anchorView: View? = null): Snackbar {
  return SnackBar.info(snackBarView(), msg)
    .setAnchorView(anchorView ?: (activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(@StringRes msgResId: Int, anchorView: View? = null): Snackbar {
  return showSuccessSnackBar(getString(msgResId), anchorView)
}

/**
 * Shows success level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showSuccessSnackBar(msg: String, anchorView: View? = null): Snackbar {
  return SnackBar.success(snackBarView(), msg)
    .setAnchorView(anchorView ?: (activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(@StringRes msgResId: Int, anchorView: View? = null): Snackbar {
  return showErrorSnackBar(getString(msgResId), anchorView)
}

/**
 * Shows error level [SnackBar]. It anchors the snack bar to bottom navigation view or network
 * indicator if this fragment is hosted by the MainActivity.
 */
fun Fragment.showErrorSnackBar(msg: String, anchorView: View? = null): Snackbar {
  return SnackBar.error(snackBarView(), msg)
    .setAnchorView(anchorView ?: (activity as? MainActivity)?.findSnackBarAnchorView())
    .apply { show() }
}

private fun Fragment.snackBarView(): View {
  return activity?.findViewById(R.id.main_nav_host_fragment) ?: requireView()
}

inline fun Fragment.showTimePicker(
  hour: Int? = null,
  minute: Int? = null,
  crossinline callback: (hour: Int, minute: Int) -> Unit,
) {
  val calendar = Calendar.getInstance()
  MaterialTimePicker.Builder()
    .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
    .also { builder -> builder.setHour(hour ?: calendar.get(Calendar.HOUR_OF_DAY)) }
    .also { builder -> builder.setMinute(minute ?: calendar.get(Calendar.MINUTE)) }
    .build()
    .also { picker ->
      picker.addOnPositiveButtonClickListener { callback.invoke(picker.hour, picker.minute) }
    }
    .show(childFragmentManager, "MaterialTimePicker")
}
