package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable
import java.util.*

/**
 * Represents an issued gift card.
 *
 * @param code code of the gift card.
 * @param hourCredits duration (in hours) of the subscription that the gift card provides on
 * redemption.
 * @param isRedeemed whether the gift card has been redeemed.
 * @param expiresAt optional timestamp when the gift card expires.
 */
data class GiftCard(
  @Expose
  val code: String,

  @Expose
  val hourCredits: Int,

  @Expose
  val isRedeemed: Boolean,

  @Expose
  val expiresAt: Date? = null,
) : Serializable
