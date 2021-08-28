package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.DurationPickerViewBinding

/**
 * [DurationPicker] creates a compound [View] with 7 buttons to manipulate the duration in a larger
 * context. Each button invokes the [onDurationAddedListener] with its corresponding duration
 * in milliseconds. If reset button is clicked, it invokes [onDurationAddedListener] with a negative
 * duration value.
 */
class DurationPicker @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val binding: DurationPickerViewBinding
  private val buttons: Array<View>

  private var onDurationAddedListener: ((Long) -> Unit)? = null

  init {
    LayoutInflater.from(context).also {
      binding = DurationPickerViewBinding.inflate(it, this, true)
    }

    buttons = arrayOf(
      binding.oneMinuteButton,
      binding.fiveMinuteButton,
      binding.thirtyMinuteButton,
      binding.oneHourButton,
      binding.fourHourButton,
      binding.eightHourButton,
      binding.resetButton
    )

    OnClickListener {
      val timeToAdd = 1000L * 60L * when (it.id) {
        R.id.one_minute_button -> 1
        R.id.five_minute_button -> 5
        R.id.thirty_minute_button -> 30
        R.id.one_hour_button -> 60
        R.id.four_hour_button -> 240
        R.id.eight_hour_button -> 480
        R.id.reset_button -> -1
        else -> 0
      }

      onDurationAddedListener?.invoke(timeToAdd)
    }.also { listener ->
      buttons.forEach { it.setOnClickListener(listener) }
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
   * [setControlsEnabled] enables or disables all the buttons in the [DurationPicker] view.
   * This includes enabling/disabling reset button.
   *
   * @see setResetButtonEnabled
   */
  fun setControlsEnabled(enabled: Boolean) {
    isEnabled = enabled
    buttons.forEach { it.isEnabled = enabled }
  }

  /**
   * [setResetButtonEnabled] enables or disables the reset button for user interactions.
   */
  fun setResetButtonEnabled(enabled: Boolean) {
    binding.resetButton.isEnabled = enabled
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
