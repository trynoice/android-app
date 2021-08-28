package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatImageButton
import com.github.ashutoshgngwr.noice.R

class DonateView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

  init {
    val background = TypedValue()
    context.theme.resolveAttribute(R.attr.selectableItemBackground, background, true)

    setBackgroundResource(background.resourceId)
    setImageResource(R.drawable.ic_donate_opencollective)

    setOnClickListener {
      val targetURL = context.getString(R.string.support_development__donate_url)
      Intent(Intent.ACTION_VIEW, Uri.parse(targetURL)).also {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
      }
    }
  }
}
