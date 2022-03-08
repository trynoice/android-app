package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable
import java.util.*

/**
 * Represents a subscription purchase that users attempted to buy in the past. All subscription
 * purchases are linked to a subscription plan.
 *
 * @param id id of the subscription purchase.
 * @param plan subscription plan for this purchase.
 * @param isActive whether this subscription purchase is currently active.
 * @param isPaymentPending whether a payment for this subscription purchase is currently pending.
 * @param startedAt epoch millis when the subscription started.
 * @param endedAt epoch millis when the subscription has ended.
 * @param renewsAt epoch millis when the next billing cycle starts, if the subscription is active.
 * @param stripeCustomerPortalUrl Stripe customer portal URL to manage subscriptions (only present
 * if subscription is active and the plan provider is Stripe).
 */
data class Subscription(

  @Expose
  val id: Long,

  @Expose
  val plan: SubscriptionPlan,

  @Expose
  val isActive: Boolean,

  @Expose
  val isPaymentPending: Boolean,

  @Expose
  val startedAt: Date? = null,

  @Expose
  val endedAt: Date? = null,

  @Expose
  val renewsAt: Date? = null,

  @Expose
  val stripeCustomerPortalUrl: String? = null
) : Serializable
