package com.github.ashutoshgngwr.noice.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.github.ashutoshgngwr.noice.data.models.ProfileDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import java.util.*

@Database(
  entities = [ProfileDto::class, SubscriptionPlanDto::class, SubscriptionDto::class],
  version = 1,
  exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

  abstract fun profile(): ProfileDao

  abstract fun subscriptionPlans(): SubscriptionPlanDao

  abstract fun subscriptions(): SubscriptionDao
}

class AppTypeConverters {

  @TypeConverter
  fun fromTimestamp(value: Long?): Date? {
    return value?.let { Date(it) }
  }

  @TypeConverter
  fun dateToTimestamp(date: Date?): Long? {
    return date?.time
  }
}
