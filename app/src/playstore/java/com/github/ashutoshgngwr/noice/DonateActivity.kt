package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.github.ashutoshgngwr.noice.fragment.DialogFragment

class DonateActivity : AppCompatActivity(), BillingProcessor.IBillingHandler {

  companion object {
    const val EXTRA_DONATE_AMOUNT = "donate_amount"
    const val DONATE_AMOUNT_1USD = "donate_usd1"
    const val DONATE_AMOUNT_2USD = "donate_usd2"
    const val DONATE_AMOUNT_5USD = "donate_usd5"
    const val DONATE_AMOUNT_10USD = "donate_usd10"
    const val DONATE_AMOUNT_15USD = "donate_usd15"
    const val DONATE_AMOUNT_25USD = "donate_usd25"
  }

  private lateinit var billingProcessor: BillingProcessor

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!intent.hasExtra(EXTRA_DONATE_AMOUNT)) {
      finish()
      return
    }

    val licenseKey = getString(R.string.google_play_license_key)
    billingProcessor = BillingProcessor.newBillingProcessor(this, licenseKey, this)
    billingProcessor.initialize()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (billingProcessor.handleActivityResult(requestCode, resultCode, data)) {
      return
    }

    super.onActivityResult(requestCode, resultCode, data)
  }

  /**
   * Exit animations are still happening after setting windowAnimationStyle to null.
   * Manual override.
   */
  override fun finish() {
    super.finish()
    overridePendingTransition(0, 0)
  }

  override fun onDestroy() {
    if (billingProcessor.isInitialized) {
      billingProcessor.release()
    }

    super.onDestroy()
  }

  override fun onBillingInitialized() {
    billingProcessor.purchase(this, intent.getStringExtra(EXTRA_DONATE_AMOUNT) ?: null)
  }

  override fun onProductPurchased(productId: String, details: TransactionDetails?) {
    billingProcessor.consumePurchase(productId)
    DialogFragment.show(supportFragmentManager) {
      title(R.string.support_development__donate_thank_you)
      message(R.string.support_development__donate_thank_you_description)
      positiveButton(android.R.string.ok) {
        // finish activity with a delay to finish dialog close animation since activity has no anim.
        Handler().postDelayed({ finish() }, 200)
      }
    }
  }

  override fun onBillingError(errorCode: Int, error: Throwable?) {
    finish()
  }

  override fun onPurchaseHistoryRestored() = Unit
}
