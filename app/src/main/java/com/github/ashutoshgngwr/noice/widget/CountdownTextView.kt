package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.text.Spanned
import android.util.AttributeSet
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.textview.MaterialTextView
import java.util.concurrent.TimeUnit

class CountdownTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : MaterialTextView(context, attrs, defStyleAttr) {

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

  /**
   * In Robolectric 4.4 onwards, the looper model has changed. Earlier, all tasks were posted on the
   * main looper and could be executed during tests by (main looper related) ShadowLooper APIs. With
   * PAUSED looper mode set as default now, this is no longer true and each looper has its own thread
   * and task queue. On top of that, this view used to post its tasks to handler exposed by the its
   * parent class using [getHandler]. This handler isn't initialized unless the view is attached to
   * a window. Personally, I tried building a dummy activity using Robolectric and setting this view
   * as its content during the tests but it didn't work.
   *
   * The only way around was to initialize a new [Handler] instance and keeps it visibility open
   * for tests so that we'd be able to use it with ShadowLooper APIs.
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val countdownHandler = Handler(Looper.getMainLooper())

  /**
   * countdownUntilMillis denotes the time until which the countdown should run. It is
   * countdown duration (millis) added to [SystemClock.uptimeMillis]
   */
  private var countdownUntilMillis = SystemClock.uptimeMillis()

  /**
   * Updates the view content and registers a delayed callback to itself for indefinitely refreshing
   * the view content. Use [android.view.View.removeCallbacks] to remove the callback. If won't
   * register itself as a delayed callback if countdown timer has finished or if the view is not
   * attached to a window anymore.
   */
  private val updateCallback = object : Runnable {
    override fun run() {
      updateCountdown()
      if (SystemClock.uptimeMillis() < countdownUntilMillis) {
        countdownHandler.postDelayed(this, UPDATE_INTERVAL)
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    countdownHandler.post(updateCallback)
  }

  override fun onDetachedFromWindow() {
    countdownHandler.removeCallbacks(updateCallback)
    super.onDetachedFromWindow()
  }

  /**
   * Starts the countdown from given time period in millis
   *
   * @param millis countdown period in millis
   */
  fun startCountdown(millis: Long) {
    // remove any pre-registered callbacks. startCountdown() may be called multiple times.
    countdownHandler.removeCallbacks(updateCallback)
    countdownUntilMillis = SystemClock.uptimeMillis() + millis
    countdownHandler.post(updateCallback)
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
