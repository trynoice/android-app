package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.*

@Entity(tableName = "subscription")
data class SubscriptionDto(
  @PrimaryKey val id: Long,
  val planId: Int,
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

data class SubscriptionWithPlanDto(
  @Embedded val subscription: SubscriptionDto,
  @Relation(parentColumn = "planId", entityColumn = "id") val plan: SubscriptionPlanDto
)
