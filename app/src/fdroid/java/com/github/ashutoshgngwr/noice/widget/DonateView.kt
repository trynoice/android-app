package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatImageButton
import com.github.ashutoshgngwr.noice.R

class DonateView : AppCompatImageButton {

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  init {
    val background = TypedValue()
    context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, background, true)

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
