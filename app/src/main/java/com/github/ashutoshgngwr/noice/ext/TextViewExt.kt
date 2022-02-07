package com.github.ashutoshgngwr.noice.ext

import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter

/**
 * Prevents NPEs when trying to set string resource IDs using a LiveData whose value may be `null`
 * at some point.
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
