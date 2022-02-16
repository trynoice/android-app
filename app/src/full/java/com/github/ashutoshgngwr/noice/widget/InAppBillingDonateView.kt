package com.github.ashutoshgngwr.noice.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.DonateViewBinding
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InAppBillingDonateView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

  private lateinit var defaultScope: CoroutineScope

  @set:Inject
  internal lateinit var billingProvider: BillingProvider

  private val binding = DonateViewBinding.inflate(LayoutInflater.from(context), this)

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal constructor(context: Context, defaultScope: CoroutineScope) : this(context) {
    this.defaultScope = defaultScope
  }

  init {
    orientation = VERTICAL
    gravity = Gravity.CENTER
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    binding.progressCircle.isVisible = true
    binding.error.isVisible = false
    binding.buttonContainer.isVisible = false

    defaultScope = CoroutineScope(Job() + CoroutineName("DonateViewScope"))
    defaultScope.launch(Dispatchers.IO) { loadSkuDetails() }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal suspend fun loadSkuDetails() {
    try {
      val detailsList = billingProvider.queryDetails(
        BillingProvider.SkuType.INAPP, listOf(
          "donate_usd1", "donate_usd2", "donate_usd5",
          "donate_usd10", "donate_usd15", "donate_usd25",
        )
      )

      defaultScope.launch(Dispatchers.Main) {
        setDetails(detailsList.sortedBy { it.priceAmountMicros })
      }
    } catch (e: BillingProvider.QueryDetailsException) {
      Log.w(this::class.simpleName, "failed to load sku details", e)
      defaultScope.launch(Dispatchers.Main) {
        binding.progressCircle.isVisible = false
        binding.buttonContainer.isVisible = false
        binding.error.isVisible = true
      }
    }
  }

  private fun setDetails(detailsList: List<BillingProvider.SkuDetails>) {
    binding.progressCircle.isVisible = false
    binding.error.isVisible = false
    binding.buttonContainer.isVisible = true
    binding.buttonContainer.removeAllViews()

    detailsList.forEach { details ->
      val b = MaterialButton(context, null, R.attr.materialButtonOutlinedStyle)
      b.text = details.price
      b.setOnClickListener {
        if (!billingProvider.purchase(getActivity(), details)) {
          Snackbar.make(this, R.string.failed_to_purchase, Snackbar.LENGTH_LONG).show()
        }
      }
      binding.buttonContainer.addView(b)
    }
  }

  // https://android.googlesource.com/platform/frameworks/support/+/refs/heads/marshmallow-release/v7/mediarouter/src/android/support/v7/app/MediaRouteButton.java#262
  private fun getActivity(): Activity {
    // Gross way of unwrapping the Activity
    var context = context
    while (context is ContextWrapper) {
      if (context is Activity) {
        return context
      }

      context = context.baseContext
    }

    throw IllegalStateException("unable to find activity that owns this donate view")
  }

  override fun onDetachedFromWindow() {
    defaultScope.cancel()
    super.onDetachedFromWindow()
  }
}
