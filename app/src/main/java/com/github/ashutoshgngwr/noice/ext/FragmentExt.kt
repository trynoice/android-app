package com.github.ashutoshgngwr.noice.ext

import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Shows the [Snackbar] with [R.drawable.ic_baseline_check_circle_24] icon and [R.color.accent]
 * iconTint, anchored to [R.id.network_indicator] or [R.id.bottom_nav] (if present) and returns it.
 */
fun Fragment.showSuccessSnackbar(
  @StringRes msgRes: Int,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  return showSnackbar(msgRes, R.drawable.ic_baseline_check_circle_24, R.color.accent, length)
}

/**
 * Shows the [Snackbar] with [R.drawable.ic_baseline_error_24] icon and [R.color.error] iconTint,
 * anchored to [R.id.network_indicator] or [R.id.bottom_nav] (if present) and returns it.
 */
fun Fragment.showErrorSnackbar(
  @StringRes msgRes: Int,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  return showErrorSnackbar(getString(msgRes), length)
}

/**
 * Shows the [Snackbar] with [R.drawable.ic_baseline_error_24] icon and [R.color.error] iconTint,
 * anchored to [R.id.network_indicator] or [R.id.bottom_nav] (if present) and returns it.
 */
fun Fragment.showErrorSnackbar(
  msg: String,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  return showSnackbar(msg, R.drawable.ic_baseline_error_24, R.color.error, length)
}

/**
 * Shows a [Snackbar] anchored to [R.id.network_indicator] or [R.id.bottom_nav] (if present) and
 * returns it.
 *
 * @param msgRes id of the string to show on [Snackbar].
 * @param icon icon to display at the start of text.
 * @param iconTint tint of the icon.
 * @param length length of the [Snackbar].
 */
fun Fragment.showSnackbar(
  @StringRes msgRes: Int,
  @DrawableRes icon: Int = ResourcesCompat.ID_NULL,
  @ColorRes iconTint: Int = R.color.accent,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  return showSnackbar(getString(msgRes), icon, iconTint, length)
}

/**
 * Shows a [Snackbar] anchored to [R.id.network_indicator] or [R.id.bottom_nav] (if present) and
 * returns it.
 *
 * @param msg text to show on [Snackbar].
 * @param icon icon to display at the start of text.
 * @param iconTint tint of the icon.
 * @param length length of the [Snackbar].
 */
fun Fragment.showSnackbar(
  msg: String,
  @DrawableRes icon: Int = ResourcesCompat.ID_NULL,
  @ColorRes iconTint: Int = R.color.accent,
  @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
): Snackbar {
  // pass Snackbar a view that is likely to be available even if current fragment is currently being
  // destroyed. This happens frequently in case of bottom sheet dialog fragments.
  val treeNode = activity?.findViewById(R.id.main_nav_host_fragment) ?: requireView()
  val networkIndicator = activity?.findViewById<View>(R.id.network_indicator)
  return Snackbar.make(treeNode, msg, length).apply {
    anchorView = activity?.findViewById(R.id.bottom_nav)
    if (networkIndicator?.isVisible == true && anchorView == null) {
      anchorView = networkIndicator
    }

    this.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.also { tv ->
      tv.gravity = Gravity.CENTER_VERTICAL or Gravity.START
      tv.compoundDrawablePadding = context.resources
        .getDimensionPixelSize(R.dimen.snackbar_icon_padding)

      TextViewCompat.setCompoundDrawableTintList(
        tv, ResourcesCompat.getColorStateList(context.resources, iconTint, context.theme)
      )

      tv.setCompoundDrawablesWithIntrinsicBounds(
        icon,
        ResourcesCompat.ID_NULL,
        ResourcesCompat.ID_NULL,
        ResourcesCompat.ID_NULL
      )
    }

    show()
  }
}
