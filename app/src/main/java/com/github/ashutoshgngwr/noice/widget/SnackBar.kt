package com.github.ashutoshgngwr.noice.widget

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Contains builder functions to build a [Snackbar] with different styles.
 */
object SnackBar {

  /**
   * Builds a [Snackbar] with [R.drawable.ic_outline_info_24] icon and default [Snackbar] style.
   */
  fun info(
    view: View,
    @StringRes msgResId: Int,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return info(view, view.context.getString(msgResId), length)
  }

  /**
   * Builds a [Snackbar] with [R.drawable.ic_outline_info_24] icon and default [Snackbar] style.
   */
  fun info(
    view: View,
    msg: String,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return make(
      view,
      msg,
      length,
      R.style.Widget_App_Snackbar,
      R.drawable.ic_outline_info_24,
    )
  }

  /**
   * Builds a [Snackbar] with [R.drawable.ic_baseline_check_circle_24] icon and
   * [R.style.Widget_App_Snackbar_Success] style.
   */
  fun success(
    view: View,
    @StringRes msgResId: Int,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return success(view, view.context.getString(msgResId), length)
  }

  /**
   * Builds a [Snackbar] with [R.drawable.ic_baseline_check_circle_24] icon and
   * [R.style.Widget_App_Snackbar_Success] style.
   */
  fun success(
    view: View,
    msg: String,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return make(
      view,
      msg,
      length,
      R.style.Widget_App_Snackbar_Success,
      R.drawable.ic_baseline_check_circle_24,
    )
  }

  /**
   * Builds a [Snackbar] with [R.drawable.ic_baseline_error_24] icon and
   * [R.style.Widget_App_Snackbar_Error] style.
   */
  fun error(
    view: View,
    @StringRes msgResId: Int,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return error(view, view.context.getString(msgResId), length)
  }

  /**
   * Builds a [Snackbar] with [R.drawable.ic_baseline_error_24] icon and
   * [R.style.Widget_App_Snackbar_Error] style.
   */
  fun error(
    view: View,
    msg: String,
    @BaseTransientBottomBar.Duration length: Int = Snackbar.LENGTH_LONG,
  ): Snackbar {
    return make(
      view,
      msg,
      length,
      R.style.Widget_App_Snackbar_Error,
      R.drawable.ic_baseline_error_24,
    )
  }

  private fun make(
    view: View,
    msg: String,
    @BaseTransientBottomBar.Duration length: Int,
    @StyleRes theme: Int,
    @DrawableRes icon: Int? = null,
  ): Snackbar {
    val snackBar = Snackbar.make(ContextThemeWrapper(view.context, theme), view, msg, length)
    icon?.also {
      snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        .setCompoundDrawablesRelativeWithIntrinsicBounds(it, 0, 0, 0)
    }

    return snackBar
  }
}
