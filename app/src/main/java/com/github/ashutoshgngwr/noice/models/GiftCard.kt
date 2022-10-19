package com.github.ashutoshgngwr.noice.models

import java.io.Serializable
import java.util.*
import com.trynoice.api.client.models.GiftCard as ApiGiftCard

data class GiftCard(
  val code: String,
  val hourCredits: Int,
  val isRedeemed: Boolean,
  val expiresAt: Date? = null,
) : Serializable {

  /**
   * Whether the gift card has not expired and redeemed, i.e. is available to redeem.
   */
  val isRedeemable: Boolean
    get() = !(isRedeemed || expiresAt?.before(Date()) == true)
}

fun ApiGiftCard.toDomainEntity(): GiftCard {
  return GiftCard(
    code = code,
    hourCredits = hourCredits,
    isRedeemed = isRedeemed,
    expiresAt = expiresAt,
  )
}
