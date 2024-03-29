package com.github.ashutoshgngwr.noice.data

import androidx.paging.PagingSource
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
  open suspend fun saveAll(subscriptions: List<SubscriptionWithPlanDto>) {
    subscriptions.forEach { save(it) }
  }

  @Transaction
  open suspend fun save(subscription: SubscriptionWithPlanDto) {
    savePlan(subscription.plan)
    save(subscription.subscription)
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun save(subscriptionDto: SubscriptionDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun savePlan(plan: SubscriptionPlanDto)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun savePlans(plan: List<SubscriptionPlanDto>)

  @Transaction
  @Query("SELECT * FROM subscription WHERE id = :id")
  abstract suspend fun get(id: Long): SubscriptionWithPlanDto?

  @Transaction
  @Query("SELECT * FROM subscription WHERE renewsAt > :after ORDER BY renewsAt LIMIT 1")
  abstract suspend fun getByRenewsAfter(after: Long): SubscriptionWithPlanDto?

  @Transaction
  @Query("SELECT * FROM subscription WHERE startedAt IS NOT NULL ORDER BY startedAt DESC")
  abstract fun pagingSource(): PagingSource<Int, SubscriptionWithPlanDto>

  @Query("SELECT * FROM subscription_plan WHERE (:provider IS NULL OR provider = :provider)")
  abstract suspend fun listPlans(provider: String? = null): List<SubscriptionPlanDto>

  /**
   * Removes all subscription entities from the database.
   */
  @Query("DELETE FROM subscription")
  abstract suspend fun removeAll()
}
