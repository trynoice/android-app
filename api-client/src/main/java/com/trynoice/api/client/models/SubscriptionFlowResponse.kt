package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Result returned by the [create subscription][com.trynoice.api.client.apis.SubscriptionApi.create]
 * operation.
 *
 * @param subscriptionId id of the the newly created subscription.
 * @param stripeCheckoutSessionUrl Checkout url for billing this subscription. Only present if
 * provider is 'stripe'.
 */
data class SubscriptionFlowResponse(

  @Expose
  val subscriptionId: Long,

  @Expose
  val stripeCheckoutSessionUrl: String? = null
) : Serializable
