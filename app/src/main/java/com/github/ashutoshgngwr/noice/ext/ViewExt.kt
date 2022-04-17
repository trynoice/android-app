package com.github.ashutoshgngwr.noice.ext

import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Prevents NPEs when trying to set string resource IDs using a LiveData/StateFlow whose value may
 * be `null` at some point.
 */
@BindingAdapter("android:text")
fun TextView.setText(@StringRes resId: Int?) {
  resId ?: return
  if (resId == ResourcesCompat.ID_NULL) {
    text = ""
  } else {
    setText(resId)
  }
}

/**
 * Binding adapter for [View.isVisible] extension property.
 */
@BindingAdapter("isVisible")
fun View.isVisibleAdapter(enabled: Boolean) {
  this.isVisible = enabled
}

/**
 * Binding adapter to set [SwipeRefreshLayout]'s progress circle color from XML.
 */
@BindingAdapter("progressCircleColor")
fun SwipeRefreshLayout.setProgressCircleColor(@ColorInt color: Int) {
  setColorSchemeColors(color)
}
