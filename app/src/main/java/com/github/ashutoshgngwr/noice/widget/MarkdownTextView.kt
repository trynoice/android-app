package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatTextView
import io.noties.markwon.Markwon

class MarkdownTextView : AppCompatTextView {

  private lateinit var markwon: Markwon

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  init {
    movementMethod = LinkMovementMethod.getInstance()
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    text ?: return
    if (!this::markwon.isInitialized) {
      markwon = Markwon.create(context)
    }

    val spannedText = markwon.toMarkdown(text.toString())
    super.setText(spannedText, type)
  }
}
