package com.trynoice.api.client.models

import java.util.*

/**
 * Represents a subscription purchase that users attempted to buy in the past. All subscription
 * purchases are linked to a subscription plan.
 *
 * @property id id of the subscription purchase.
 * @property plan subscription plan for this purchase.
 * @property isActive whether this subscription purchase is currently active.
 * @property isPaymentPending whether a payment for this subscription purchase is currently pending.
 * @property isAutoRenewing whether the subscription will renew at the end of this billing cycle. if
 * `false`, it implies that the subscription will end at the end of current billing cycle.
 * @property isRefunded whether the subscription was cancelled and its amount refunded.
 * @property startedAt epoch millis when the subscription started.
 * @property endedAt epoch millis when the subscription has ended.
 * @property renewsAt epoch millis when the current billing cycle ends and the next one starts.
 * always present unless the subscription is inactive.
 * @property googlePlayPurchaseToken Google Play purchase token corresponding to this subscription
 * purchase. only present when the subscription is active and provided by Google Play.
 * @property giftCardCode the gift card code if this subscription was activated using a gift card.
 */
data class Subscription(
  val id: Long,
  val plan: SubscriptionPlan,
  val isActive: Boolean,
  val isPaymentPending: Boolean,
  val isAutoRenewing: Boolean,
  val isRefunded: Boolean? = null,
  val startedAt: Date? = null,
  val endedAt: Date? = null,
  val renewsAt: Date? = null,
  val googlePlayPurchaseToken: String? = null,
  val giftCardCode: String? = null,
)
