package com.trynoice.api.client.models

/**
 * Request parameters accepted by the
 * [create subscription][com.trynoice.api.client.apis.SubscriptionApi.create] operation.
 *
 * @property planId id of the subscription plan selected by the user.
 * @property cancelUrl redirect url when the user cancels the checkout session. it is only required
 * for Stripe plans.
 * @property successUrl redirect url when the user completes the checkout session. it is only
 * required for Stripe plans.
 */
data class SubscriptionFlowParams(
  val planId: Int,
  val cancelUrl: String? = null,
  val successUrl: String? = null
)
