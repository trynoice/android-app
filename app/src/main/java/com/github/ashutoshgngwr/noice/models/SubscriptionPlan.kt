package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import java.io.Serializable
import java.text.NumberFormat
import java.util.*
import com.trynoice.api.client.models.SubscriptionPlan as ApiSubscriptionPlan

data class SubscriptionPlan(
  val id: Int,
  val provider: String,
  val billingPeriodMonths: Int,
  val trialPeriodDays: Int,
  val priceInIndianPaise: Int,
  val priceInRequestedCurrency: Double? = null,
  val requestedCurrencyCode: String? = null,
  val googlePlaySubscriptionId: String? = null,
) : Serializable {

  /**
   * A formatted string representing total price of this plan.
   */
  val totalPrice
    get(): String = if (priceInRequestedCurrency != null && requestedCurrencyCode != null) {
      formatPrice(priceInRequestedCurrency, requestedCurrencyCode)
    } else {
      formatPrice(priceInIndianPaise / 100.0, "INR")
    }

  /**
   * A formatted string representing monthly price of this plan.
   */
  val monthlyPrice
    get(): String? = when {
      billingPeriodMonths < 1 -> null
      priceInRequestedCurrency != null && requestedCurrencyCode != null -> {
        formatPrice(priceInRequestedCurrency / billingPeriodMonths, requestedCurrencyCode)
      }
      else -> formatPrice(priceInIndianPaise / (billingPeriodMonths * 100.0), "INR")
    }

  private fun formatPrice(price: Double, currencyCode: String): String {
    return NumberFormat.getCurrencyInstance()
      .apply {
        currency = Currency.getInstance(currencyCode)
        minimumFractionDigits = if (price % 1 == 0.0) 0 else minimumFractionDigits
      }
      .format(price)
  }

  companion object {
    const val PROVIDER_GOOGLE_PLAY = ApiSubscriptionPlan.PROVIDER_GOOGLE_PLAY
    const val PROVIDER_STRIPE = ApiSubscriptionPlan.PROVIDER_STRIPE
    const val PROVIDER_GIFT_CARD = ApiSubscriptionPlan.PROVIDER_GIFT_CARD
  }
}

fun SubscriptionPlanDto.toDomainEntity(): SubscriptionPlan {
  return SubscriptionPlan(
    id = id,
    provider = provider,
    billingPeriodMonths = billingPeriodMonths,
    trialPeriodDays = trialPeriodDays,
    priceInIndianPaise = priceInIndianPaise,
    priceInRequestedCurrency = priceInRequestedCurrency,
    requestedCurrencyCode = requestedCurrencyCode,
    googlePlaySubscriptionId = googlePlaySubscriptionId,
  )
}

@JvmName("toDomainEntitySubscriptionPlanDto")
fun List<SubscriptionPlanDto>.toDomainEntity(): List<SubscriptionPlan> {
  return map { it.toDomainEntity() }
}

fun ApiSubscriptionPlan.toDomainEntity(): SubscriptionPlan {
  return SubscriptionPlan(
    id = id,
    provider = provider,
    billingPeriodMonths = billingPeriodMonths,
    trialPeriodDays = trialPeriodDays,
    priceInIndianPaise = priceInIndianPaise,
    priceInRequestedCurrency = priceInRequestedCurrency,
    requestedCurrencyCode = requestedCurrencyCode,
    googlePlaySubscriptionId = googlePlaySubscriptionId,
  )
}

@JvmName("toDomainEntityApiSubscriptionPlan")
fun List<ApiSubscriptionPlan>.toDomainEntity(): List<SubscriptionPlan> {
  return map { it.toDomainEntity() }
}

fun SubscriptionPlan.toRoomDto(): SubscriptionPlanDto {
  return SubscriptionPlanDto(
    id = id,
    provider = provider,
    billingPeriodMonths = billingPeriodMonths,
    trialPeriodDays = trialPeriodDays,
    priceInIndianPaise = priceInIndianPaise,
    priceInRequestedCurrency = priceInRequestedCurrency,
    requestedCurrencyCode = requestedCurrencyCode,
    googlePlaySubscriptionId = googlePlaySubscriptionId,
  )
}

fun List<SubscriptionPlan>.toRoomDto(): List<SubscriptionPlanDto> {
  return map { it.toRoomDto() }
}

fun ApiSubscriptionPlan.toRoomDto(): SubscriptionPlanDto {
  return SubscriptionPlanDto(
    id = id,
    provider = provider,
    billingPeriodMonths = billingPeriodMonths,
    trialPeriodDays = trialPeriodDays,
    priceInIndianPaise = priceInIndianPaise,
    priceInRequestedCurrency = priceInRequestedCurrency,
    requestedCurrencyCode = requestedCurrencyCode,
    googlePlaySubscriptionId = googlePlaySubscriptionId,
  )
}
