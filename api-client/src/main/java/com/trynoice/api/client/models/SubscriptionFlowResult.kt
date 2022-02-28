package com.trynoice.api.client.models

import com.google.gson.annotations.Expose

/**
 * Result returned by the [create subscription][com.trynoice.api.client.apis.SubscriptionApi.create]
 * operation.
 *
 * @param subscription the newly created subscription.
 * @param stripeCheckoutSessionUrl Checkout url for billing this subscription. Only present if
 * provider is 'stripe'.
 */
data class SubscriptionFlowResult(

  @Expose
  val subscription: Subscription,

  @Expose
  val stripeCheckoutSessionUrl: String? = null
)
