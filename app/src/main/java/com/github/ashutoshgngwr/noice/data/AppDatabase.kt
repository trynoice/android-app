package com.github.ashutoshgngwr.noice.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.github.ashutoshgngwr.noice.data.models.AlarmDto
import com.github.ashutoshgngwr.noice.data.models.DefaultPresetsSyncVersionDto
import com.github.ashutoshgngwr.noice.data.models.LibraryUpdateTimeDto
import com.github.ashutoshgngwr.noice.data.models.PresetDto
import com.github.ashutoshgngwr.noice.data.models.ProfileDto
import com.github.ashutoshgngwr.noice.data.models.SoundGroupDto
import com.github.ashutoshgngwr.noice.data.models.SoundMetadataDto
import com.github.ashutoshgngwr.noice.data.models.SoundSegmentDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto
import com.github.ashutoshgngwr.noice.data.models.SoundTagCrossRef
import com.github.ashutoshgngwr.noice.data.models.SoundTagDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import java.util.*

@Database(
  entities = [
    ProfileDto::class,
    SubscriptionPlanDto::class,
    SubscriptionDto::class,
    SoundGroupDto::class,
    SoundTagDto::class,
    SoundMetadataDto::class,
    SoundSegmentDto::class,
    SoundTagCrossRef::class,
    SoundSourceDto::class,
    LibraryUpdateTimeDto::class,
    AlarmDto::class,
    PresetDto::class,
    DefaultPresetsSyncVersionDto::class,
  ],
  version = 2,
  autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@TypeConverters(AppDatabaseTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

  abstract fun profile(): ProfileDao

  abstract fun subscriptions(): SubscriptionDao

  abstract fun sounds(): SoundDao

  abstract fun alarms(): AlarmDao

  abstract fun presets(): PresetDao
}

class AppDatabaseTypeConverters {

  @TypeConverter
  fun fromTimestamp(value: Long?): Date? {
    return value?.let { Date(it) }
  }

  @TypeConverter
  fun dateToTimestamp(date: Date?): Long? {
    return date?.time
  }
}
