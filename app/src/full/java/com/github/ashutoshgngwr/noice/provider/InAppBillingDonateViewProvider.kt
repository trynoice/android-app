package com.github.ashutoshgngwr.noice.provider

import android.view.ViewGroup
import com.github.ashutoshgngwr.noice.widget.InAppBillingDonateView

object InAppBillingDonateViewProvider : DonateViewProvider {

  override fun addViewToParent(parent: ViewGroup) {
    parent.addView(InAppBillingDonateView(parent.context))
  }
}
