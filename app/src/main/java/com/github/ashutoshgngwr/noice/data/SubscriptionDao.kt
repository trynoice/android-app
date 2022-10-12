package com.github.ashutoshgngwr.noice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.ashutoshgngwr.noice.data.models.SubscriptionDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionWithPlanDto

@Dao
abstract class SubscriptionDao {

  @Transaction
  open fun save(subscription: SubscriptionWithPlanDto) {
    _savePlan(subscription.plan)
    _save(subscription.subscription)
  }

  @Transaction
  open fun saveAll(subscriptions: List<SubscriptionWithPlanDto>) {
    subscriptions.forEach(this::save)
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun _save(subscriptionDto: SubscriptionDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun _savePlan(plan: SubscriptionPlanDto)

  @Transaction
  @Query("SELECT * FROM subscription WHERE id = :id")
  abstract suspend fun get(id: Long): SubscriptionWithPlanDto?

  @Transaction
  @Query("SELECT * FROM subscription WHERE renewsAt > :after")
  abstract suspend fun getByExpiresAfter(after: Long): SubscriptionWithPlanDto

  @Transaction
  @Query("SELECT * FROM subscription WHERE startedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :count OFFSET :offset")
  abstract suspend fun listStarted(offset: Int, count: Int): List<SubscriptionWithPlanDto>

  @Query("DELETE FROM subscription")
  abstract suspend fun removeAll()
}
