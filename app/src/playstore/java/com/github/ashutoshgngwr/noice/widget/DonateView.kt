package com.github.ashutoshgngwr.noice.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.LinearLayoutCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.DonateViewBinding
import com.github.ashutoshgngwr.noice.provider.RealBillingProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DonateView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

  private lateinit var defaultScope: CoroutineScope

  private val binding: DonateViewBinding =
    DonateViewBinding.inflate(LayoutInflater.from(context), this)

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
    binding.progressCircle.visibility = View.VISIBLE
    binding.error.visibility = View.GONE
    binding.buttonContainer.visibility = View.GONE

    defaultScope = CoroutineScope(Job() + CoroutineName("DonateViewScope"))
    defaultScope.launch(Dispatchers.IO) { loadSkuDetails() }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal suspend fun loadSkuDetails() {
    try {
      val detailsList = RealBillingProvider.queryDetails(
        BillingClient.SkuType.INAPP, listOf(
          "donate_usd1", "donate_usd2", "donate_usd5",
          "donate_usd10", "donate_usd15", "donate_usd25",
        )
      )

      defaultScope.launch(Dispatchers.Main) {
        setDetails(detailsList.sortedBy { it.priceAmountMicros })
      }
    } catch (e: RealBillingProvider.QueryDetailsException) {
      Log.w(this::class.simpleName, "failed to load sku details", e)
      defaultScope.launch(Dispatchers.Main) { setQueryDetailsFailedError() }
    }
  }

  private fun setDetails(detailsList: List<SkuDetails>) {
    binding.progressCircle.visibility = View.GONE
    binding.error.visibility = View.GONE
    binding.buttonContainer.visibility = View.VISIBLE
    binding.buttonContainer.removeAllViews()

    detailsList.forEach { details ->
      val b = MaterialButton(context, null, R.attr.materialButtonOutlinedStyle)
      b.text = details.price
      b.setOnClickListener {
        if (!RealBillingProvider.purchase(getActivity(), details)) {
          Snackbar.make(this, R.string.failed_to_purchase, Snackbar.LENGTH_LONG).show()
        }
      }
      binding.buttonContainer.addView(b)
    }
  }

  private fun setQueryDetailsFailedError() {
    binding.progressCircle.visibility = View.GONE
    binding.buttonContainer.visibility = View.GONE
    binding.error.visibility = View.VISIBLE
    binding.error.setText(R.string.failed_to_load_inapp_purchases)
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
