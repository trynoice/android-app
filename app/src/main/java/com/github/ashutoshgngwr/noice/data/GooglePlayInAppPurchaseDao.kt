package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.GooglePlayInAppPurchaseDto

@Dao
abstract class GooglePlayInAppPurchaseDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun save(purchase: GooglePlayInAppPurchaseDto)

  @Query("SELECT * FROM google_play_in_app_purchase WHERE purchaseToken = :token")
  abstract suspend fun getByPurchaseToken(token: String): GooglePlayInAppPurchaseDto?

  @Query("SELECT * FROM google_play_in_app_purchase WHERE isNotificationConsumed = false")
  abstract suspend fun listWithUnconsumedNotifications(): List<GooglePlayInAppPurchaseDto>

  @Query("SELECT purchaseToken FROM google_play_in_app_purchase WHERE isNotificationConsumed = true")
  abstract suspend fun listPurchaseTokensWithConsumedNotifications(): List<String>

  @Query("DELETE FROM google_play_in_app_purchase WHERE purchaseToken = :token")
  abstract suspend fun deleteByPurchaseToken(token: String)
}
