package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPurchaseNotificationDto

@Dao
abstract class SubscriptionPurchaseNotificationDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(purchase: SubscriptionPurchaseNotificationDto)

  @Query("SELECT * FROM subscription_purchase_notification WHERE isConsumed = false")
  abstract suspend fun listUnconsumed(): List<SubscriptionPurchaseNotificationDto>

  @Query("DELETE FROM subscription_purchase_notification WHERE isConsumed = true")
  abstract suspend fun removeConsumed()
}
