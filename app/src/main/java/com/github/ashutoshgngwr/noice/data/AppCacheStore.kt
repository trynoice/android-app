package com.github.ashutoshgngwr.noice.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheStore @Inject constructor(private val appDb: AppDatabase) {

  private val profileDao = appDb.profile()
  private val subscriptionPlanDao = appDb.subscriptionPlans()
  private val subscriptionDao = appDb.subscriptions()
  private val soundDao = appDb.sounds()

  fun profile(): ProfileDao = profileDao
  fun subscriptionPlans(): SubscriptionPlanDao = subscriptionPlanDao
  fun subscriptions(): SubscriptionDao = subscriptionDao
  fun sounds(): SoundDao = soundDao

  suspend fun <R> withTransaction(block: suspend () -> R): R {
    return appDb.withTransaction(block)
  }

  /**
   * Removes all cache entries from the [AppDatabase].
   */
  suspend fun clear() {
    appDb.withTransaction {
      profileDao.remove()
      subscriptionDao.removeAll()
      subscriptionPlanDao.removeAll()
    }
  }
}
