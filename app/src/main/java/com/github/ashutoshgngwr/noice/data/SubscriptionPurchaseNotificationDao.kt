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

  suspend fun listUnconsumed(): List<SubscriptionPurchaseNotificationDto> {
    return list(false)
  }

  @Query("SELECT * FROM subscription_purchase_notification WHERE isConsumed = :isConsumed")
  protected abstract suspend fun list(isConsumed: Boolean): List<SubscriptionPurchaseNotificationDto>

  suspend fun removeConsumed() {
    remove(true)
  }

  @Query("DELETE FROM subscription_purchase_notification WHERE isConsumed = :isConsumed")
  protected abstract suspend fun remove(isConsumed: Boolean)
}
