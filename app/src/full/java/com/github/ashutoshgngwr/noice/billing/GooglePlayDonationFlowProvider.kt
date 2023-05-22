package com.github.ashutoshgngwr.noice.billing

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.Purchase

class GooglePlayDonationFlowProvider(
  private val billingProvider: GooglePlayBillingProvider,
) : DonationFlowProvider, GooglePlayPurchaseListener, DefaultLifecycleObserver {

  private var callbackFragmentHostActivity: AppCompatActivity? = null

  override fun addButtons(fragmentManager: FragmentManager, containerId: Int) {
    fragmentManager.commit { replace(containerId, GooglePlayDonateButtonsFragment()) }
  }

  override fun setCallbackFragmentHost(hostActivity: AppCompatActivity) {
    callbackFragmentHostActivity = hostActivity
    hostActivity.lifecycle.addObserver(this)
  }

  override fun onInAppPurchasePending(purchase: Purchase): Boolean {
    val fragmentManager = callbackFragmentHostActivity?.supportFragmentManager ?: return false
    if (purchase.products.any { it in GooglePlayDonateButtonsFragment.IN_APP_DONATION_PRODUCT_SKUS }) {
      GooglePlayDonationPurchasePendingFragment()
        .show(fragmentManager, GooglePlayDonationPurchasePendingFragment::class.simpleName)
      return true
    }
    return false
  }

  override fun onInAppPurchaseComplete(purchase: Purchase): Boolean {
    val fragmentManager = callbackFragmentHostActivity?.supportFragmentManager ?: return false
    if (purchase.products.any { it in GooglePlayDonateButtonsFragment.IN_APP_DONATION_PRODUCT_SKUS }) {
      GooglePlayDonationPurchaseCompleteFragment.newInstance(purchase)
        .show(fragmentManager, GooglePlayDonationPurchaseCompleteFragment::class.simpleName)
      return true
    }
    return false
  }

  override fun onPause(owner: LifecycleOwner) {
    billingProvider.removePurchaseListener(this)
  }

  override fun onResume(owner: LifecycleOwner) {
    billingProvider.addPurchaseListener(this)
  }

  override fun onDestroy(owner: LifecycleOwner) {
    callbackFragmentHostActivity = null
  }
}
