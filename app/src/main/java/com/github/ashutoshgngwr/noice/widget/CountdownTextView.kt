package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.Html
import android.text.Spanned
import android.util.AttributeSet
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.textview.MaterialTextView
import java.util.concurrent.TimeUnit

class CountdownTextView : MaterialTextView {
  companion object {
    /**
     * HTML template to use on the TextView. Countdown is set using [String.format] and [Html.fromHtml].
     */
    private val COUNTDOWN_TEMPLATE = """
      <strong>%02d</strong><small><small>h</small></small>
      <strong>%02d</strong><small><small>m</small></small>
      <strong>%02d</strong><small><small>s</small></small>
    """.trimIndent()

    /**
     * time interval between each update to the view's content
     */
    private const val UPDATE_INTERVAL = 500L
  }

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  /**
   * countdownUntilMillis denotes the time until which the countdown should run. It is
   * countdown duration (millis) added to [SystemClock.uptimeMillis]
   */
  private var countdownUntilMillis = SystemClock.uptimeMillis()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateCountdownWithCallbacks()
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(this::updateCountdownWithCallbacks)
    super.onDetachedFromWindow()
  }

  /**
   * Starts the countdown from given time period in millis
   *
   * @param millis countdown period in millis
   */
  fun startCountdown(millis: Long) {
    // remove any pre-registered callbacks. startCountdown() may be called multiple times.
    removeCallbacks(this::updateCountdownWithCallbacks)
    countdownUntilMillis = SystemClock.uptimeMillis() + millis
    updateCountdownWithCallbacks()
  }

  /**
   * Updates the view content and registers a delayed callback to itself for indefinitely refreshing
   * the view content. Use [android.view.View.removeCallbacks] to remove the callback. If won't
   * register itself as a delayed callback if countdown timer has finished or if the view is not
   * attached to a window anymore.
   */
  private fun updateCountdownWithCallbacks() {
    updateCountdown()
    if (SystemClock.uptimeMillis() < countdownUntilMillis) {
      postDelayed(this::updateCountdownWithCallbacks, UPDATE_INTERVAL)
    }
  }

  /**
   * Updates the timer based on [countdownUntilMillis]. It also sets the text style to `disabled`
   * if countdown is not running.
   */
  private fun updateCountdown() {
    var hours = 0L
    var minutes = 0L
    var seconds = 0L
    val remainingMillis = countdownUntilMillis - SystemClock.uptimeMillis()

    if (remainingMillis > 0) {
      hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
      minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60
      seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
    }

    text = COUNTDOWN_TEMPLATE.format(hours, minutes, seconds).toSpanned()
    updateTextAppearance(remainingMillis <= 0)
  }

  /**
   * Sets text appearance to one of [R.style.TextAppearance_App_CountdownTextView_Disabled] or
   * [R.style.TextAppearance_App_CountdownTextView] based on `disabled` flag.
   * Note: This doesn't disable the view itself.
   * @param disabled whether the view should be disabled.
   */
  private fun updateTextAppearance(disabled: Boolean) {
    val style = if (disabled) {
      R.style.TextAppearance_App_CountdownTextView_Disabled
    } else {
      R.style.TextAppearance_App_CountdownTextView
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      setTextAppearance(style)
    } else {
      @Suppress("DEPRECATION")
      setTextAppearance(context, style)
    }
  }

  /**
   * Converts the HTML string to [Spanned]
   */
  private fun String.toSpanned(): Spanned {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    } else {
      @Suppress("DEPRECATION")
      Html.fromHtml(this)
    }
  }
}
