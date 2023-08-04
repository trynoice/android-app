package com.github.ashutoshgngwr.noice.billing

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StripeCheckoutSessionCallbackActivity : AppCompatActivity() {

  @set:Inject
  internal lateinit var stripeSubscriptionBillingProvider: StripeSubscriptionBillingProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val uri = intent.data
    if (Intent.ACTION_VIEW == intent?.action && uri != null) {
      stripeSubscriptionBillingProvider.handleStripeCheckoutSessionCallbackUri(this, uri)
    }

    finish()
  }
}
