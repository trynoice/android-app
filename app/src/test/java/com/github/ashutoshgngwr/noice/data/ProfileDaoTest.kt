package com.github.ashutoshgngwr.noice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.ProfileDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ProfileDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var profileDao: ProfileDao

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java,
    ).build()

    profileDao = appDb.profile()
  }

  @After
  @Throws(IOException::class)
  fun teardown() {
    appDb.close()
  }

  @Test
  fun saveAndGet() = runTest {
    val profile = ProfileDto(10, "user@app.test", "test user 1")
    profileDao.save(profile)
    assertEquals(profile, profileDao.get())

    val newProfile = profile.copy(name = "test user 2")
    profileDao.save(newProfile)
    assertEquals(newProfile, profileDao.get())
  }

  @Test
  fun saveAndRemove() = runTest {
    val profile = ProfileDto(10, "user@app.test", "test user 1")
    profileDao.save(profile)
    assertEquals(profile, profileDao.get())

    profileDao.remove()
    assertNull(profileDao.get())
  }
}
