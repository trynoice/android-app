package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import com.github.ashutoshgngwr.noice.ext.launchInCustomTab
import com.google.android.material.textview.MaterialTextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration

class MarkdownTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : MaterialTextView(context, attrs, defStyleAttr) {

  private val markwon = Markwon.builder(context)
    .usePlugin(
      object : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
          builder.linkResolver { _, link -> Uri.parse(link).launchInCustomTab(context) }
        }
      }
    )
    .build()

  init {
    setMarkdown(text.toString()) // if text is set via XML
  }

  fun setMarkdown(source: String) {
    markwon.setMarkdown(this, source)
  }
}
