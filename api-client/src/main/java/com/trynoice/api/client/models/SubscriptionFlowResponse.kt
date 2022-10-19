package com.trynoice.api.client.models

/**
 * Result returned by the [create subscription][com.trynoice.api.client.apis.SubscriptionApi.create]
 * operation.
 *
 * @property subscriptionId id of the the newly created subscription.
 * @property stripeCheckoutSessionUrl Checkout url for billing this subscription. Only present if
 * provider is 'stripe'.
 */
data class SubscriptionFlowResponse(
  val subscriptionId: Long,
  val stripeCheckoutSessionUrl: String? = null,
)
