package com.github.ashutoshgngwr.noice.ext

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import kotlin.math.roundToInt

/**
 * Prevents NPEs when trying to set string resource IDs using a LiveData/StateFlow whose value may
 * be `null` at some point.
 */
@BindingAdapter("android:text")
fun TextView.setText(@StringRes resId: Int?) {
  if (resId == null || resId == ResourcesCompat.ID_NULL) {
    text = null
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

@BindingAdapter("android:layout_marginBottom")
fun View.setLayoutMarginBottom(margin: Float) {
  (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = margin.roundToInt()
}
