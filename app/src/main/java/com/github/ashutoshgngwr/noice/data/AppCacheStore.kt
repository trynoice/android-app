package com.github.ashutoshgngwr.noice.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheStore @Inject constructor(appDb: AppDatabase) {

  private val profileDao = appDb.profile()

  fun profile(): ProfileDao = profileDao

  /**
   * Removes all cache entries from the [AppDatabase].
   */
  suspend fun clear() {
    profileDao.remove()
  }
}
