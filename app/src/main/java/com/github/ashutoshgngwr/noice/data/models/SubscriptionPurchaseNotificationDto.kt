package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_purchase_notification")
data class SubscriptionPurchaseNotificationDto(
  @PrimaryKey val subscriptionId: Long,
  val isPurchasePending: Boolean,
  val isConsumed: Boolean,
)
