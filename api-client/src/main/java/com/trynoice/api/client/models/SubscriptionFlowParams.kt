package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Request parameters accepted by the
 * [create subscription][com.trynoice.api.client.apis.SubscriptionApi.create] operation.
 *
 * @param planId id of the subscription plan selected by the user.
 * @param cancelUrl redirect url when the user cancels the checkout session (required only for Stripe plans).
 * @param successUrl redirect url when the user completes the checkout session (required only for Stripe plans).
 */
data class SubscriptionFlowParams(

  @Expose
  val planId: Int,

  @Expose
  val cancelUrl: String? = null,

  @Expose
  val successUrl: String? = null
) : Serializable
