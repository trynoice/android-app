package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch

class MaterialSwitchFix @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = com.google.android.material.R.attr.materialSwitchStyle,
) : MaterialSwitch(context, attrs, defStyleAttr) {

  private var checkedChangeListener: OnCheckedChangeListener? = null

  fun setChecked(checked: Boolean, notifyListener: Boolean) {
    if (!notifyListener) super.setOnCheckedChangeListener(null)
    super.setChecked(checked)
    if (!notifyListener) super.setOnCheckedChangeListener(checkedChangeListener)
  }

  override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
    checkedChangeListener = listener
    super.setOnCheckedChangeListener(listener)
  }
}
