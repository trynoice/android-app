package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.util.*

/**
 * Represents a subscription purchase that users attempted to buy in the past. All subscription
 * purchases are linked to a subscription plan.
 *
 * @param id id of the subscription purchase.
 * @param plan subscription plan for this purchase.
 * @param status the current status of the subscription. it must be one of
 * [Subscription.STATUS_INACTIVE], [Subscription.STATUS_PENDING] or [Subscription.STATUS_ACTIVE].
 * @param endedAt subscription end timestamp (ISO-8601 format) if the subscription has ended
 * (status = ended)
 * @param startedAt subscription start timestamp (ISO-8601 format) if the subscription is active
 * (status != inactive)
 * @param stripeCustomerPortalUrl Stripe customer portal URL to manage subscriptions (only present
 * if provider = stripe and status != inactive)
 */
data class Subscription(

  @Expose
  val id: Long,

  @Expose
  val plan: SubscriptionPlan,

  @Expose
  val status: String,

  @Expose
  val endedAt: Date? = null,

  @Expose
  val startedAt: Date? = null,

  @Expose
  val stripeCustomerPortalUrl: String? = null
) {

  companion object {
    /**
     * The subscription is currently inactive. It may be due to a pending payment after the
     * subscription flow was started. Otherwise, it indicates the subscription has expired.
     */
    const val STATUS_INACTIVE = "inactive"

    /**
     * The payment for the subscription is pending, but the user has access to its entitlements.
     */
    const val STATUS_PENDING = "pending"

    /**
     * The subscription is currently active, and the user have access to all its entitlements.
     */
    const val STATUS_ACTIVE = "active"
  }
}
