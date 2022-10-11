package com.github.ashutoshgngwr.noice.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.ashutoshgngwr.noice.data.models.ProfileDto

@Database(entities = [ProfileDto::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

  abstract fun profile(): ProfileDao
}
