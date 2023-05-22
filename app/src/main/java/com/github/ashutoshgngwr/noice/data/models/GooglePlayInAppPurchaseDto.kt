package com.github.ashutoshgngwr.noice.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "google_play_in_app_purchase")
data class GooglePlayInAppPurchaseDto(
  @PrimaryKey val purchaseToken: String,
  val purchaseInfoJson: String,
  val signature: String,
  val isNotificationConsumed: Boolean,
)
