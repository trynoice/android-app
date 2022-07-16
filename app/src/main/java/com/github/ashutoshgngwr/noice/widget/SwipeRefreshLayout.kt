package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.github.ashutoshgngwr.noice.R


/**
 * A wrapper around [AndroidX
 * SwipeRefreshLayout][androidx.swiperefreshlayout.widget.SwipeRefreshLayout] that supports
 * styleable attributes in XML. Use [swipeRefreshLayoutStyle][R.attr.swipeRefreshLayoutStyle]
 * attribute in app themes to declare a global style.
 */
class SwipeRefreshLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context, attrs) {

  init {
    val ta = context.theme.obtainStyledAttributes(
      attrs,
      R.styleable.SwipeRefreshLayout,
      R.attr.swipeRefreshLayoutStyle,
      ResourcesCompat.ID_NULL,
    )

    val arrayId = ta.getResourceId(
      R.styleable.SwipeRefreshLayout_progressSpinnerColors,
      ResourcesCompat.ID_NULL,
    )

    if (arrayId != ResourcesCompat.ID_NULL) {
      val colorArray = context.resources.obtainTypedArray(arrayId)
      val colors = IntArray(colorArray.length())
      val colorValue = TypedValue()
      for (i in 0 until colorArray.length()) {
        colorArray.getValue(i, colorValue)
        colors[i] = resolveColorValue(colorValue)
          ?: throw IllegalArgumentException("progressSpinnerColors: found null color value at index $i")
      }

      setColorSchemeColors(*colors)
      colorArray.recycle()
    }

    TypedValue()
      .also { ta.getValue(R.styleable.SwipeRefreshLayout_progressBackgroundColor, it) }
      .let { resolveColorValue(it) }
      ?.also { setProgressBackgroundColorSchemeColor(it) }

    ta.recycle()
  }

  @ColorInt
  private fun resolveColorValue(value: TypedValue): Int? {
    return when {
      value.type == TypedValue.TYPE_NULL -> null
      value.isColorTypeX -> return value.data
      value.type == TypedValue.TYPE_REFERENCE -> ContextCompat.getColor(context, value.data)
      value.type == TypedValue.TYPE_ATTRIBUTE -> {
        val attr = value.data
        if (!context.theme.resolveAttribute(attr, value, true)) {
          throw Resources.NotFoundException("'${context.resources.getResourceName(attr)}' is not set")
        }

        value.data
      }
      else -> throw Resources.NotFoundException("color value must be a hex string, a reference or a theme attribute")
    }
  }

  private val TypedValue.isColorTypeX: Boolean
    get() = type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT
}
