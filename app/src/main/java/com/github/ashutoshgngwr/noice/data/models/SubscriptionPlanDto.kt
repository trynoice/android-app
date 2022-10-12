package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_plan")
data class SubscriptionPlanDto(
  @PrimaryKey val id: Int,
  val provider: String,
  val billingPeriodMonths: Int,
  val trialPeriodDays: Int,
  val priceInIndianPaise: Int,
  val priceInRequestedCurrency: Double? = null,
  val requestedCurrencyCode: String? = null,
  val googlePlaySubscriptionId: String?,
)
