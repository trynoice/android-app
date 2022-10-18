package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto

@Dao
abstract class SubscriptionPlanDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveAll(plan: List<SubscriptionPlanDto>)

  @Query("SELECT * FROM subscription_plan WHERE (:provider IS NULL OR provider = :provider)")
  abstract suspend fun list(provider: String? = null): List<SubscriptionPlanDto>
}
