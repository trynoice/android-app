package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.R
import kotlinx.android.synthetic.main.view_duration_picker.view.*

/**
 * [DurationPicker] creates a compound [View] with 7 buttons to manipulate the duration in a larger
 * context. Each button invokes the [onDurationAddedListener] with its corresponding duration
 * in milliseconds. If reset button is clicked, it invokes [onDurationAddedListener] with a negative
 * duration value.
 */
class DurationPicker : FrameLayout {

  private var onDurationAddedListener: ((Long) -> Unit)? = null

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  init {
    View.inflate(context, R.layout.view_duration_picker, this)
    OnClickListener {
      val timeToAdd = 1000L * 60L * when (it.id) {
        R.id.button_1m -> 1
        R.id.button_5m -> 5
        R.id.button_30m -> 30
        R.id.button_1h -> 60
        R.id.button_4h -> 240
        R.id.button_8h -> 480
        R.id.button_reset -> -1
        else -> 0
      }

      onDurationAddedListener?.invoke(timeToAdd)
    }.also {
      button_1m.setOnClickListener(it)
      button_5m.setOnClickListener(it)
      button_30m.setOnClickListener(it)
      button_1h.setOnClickListener(it)
      button_4h.setOnClickListener(it)
      button_8h.setOnClickListener(it)
      button_reset.setOnClickListener(it)
    }
  }

  /**
   * [setOnDurationAddedListener] attaches a listener that is invoked with every button click in the
   * view.
   */
  fun setOnDurationAddedListener(listener: ((Long) -> Unit)?) {
    this.onDurationAddedListener = listener
  }

  /**
   * [setResetButtonEnabled] enables or disables the reset button for user interactions.
   */
  fun setResetButtonEnabled(enabled: Boolean) {
    button_reset.isEnabled = enabled
  }

  /**
   * [invokeOnDurationAddedListener] is utility function for testing. This allows tests to bypass
   * clicking buttons inside the [DurationPicker] and invoke the [onDurationAddedListener] directly.
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun invokeOnDurationAddedListener(durationMillis: Long) {
    onDurationAddedListener?.invoke(durationMillis)
  }
}
