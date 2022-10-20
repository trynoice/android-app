package com.trynoice.api.client.models

import java.util.*

/**
 * Represents an issued gift card.
 *
 * @property code code of the gift card.
 * @property hourCredits duration (in hours) of the subscription that the gift card provides on
 * redemption.
 * @property isRedeemed whether the gift card has been redeemed.
 * @property expiresAt optional timestamp when the gift card expires.
 */
data class GiftCard(
  val code: String,
  val hourCredits: Int,
  val isRedeemed: Boolean,
  val expiresAt: Date? = null,
)
