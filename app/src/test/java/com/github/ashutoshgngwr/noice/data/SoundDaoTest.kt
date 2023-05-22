package com.github.ashutoshgngwr.noice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.LibraryUpdateTimeDto
import com.github.ashutoshgngwr.noice.data.models.SoundGroupDto
import com.github.ashutoshgngwr.noice.data.models.SoundMetadataDto
import com.github.ashutoshgngwr.noice.data.models.SoundSegmentDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto
import com.github.ashutoshgngwr.noice.data.models.SoundTagCrossRef
import com.github.ashutoshgngwr.noice.data.models.SoundTagDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.*

@RunWith(RobolectricTestRunner::class)
class SoundDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var soundDao: SoundDao

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java,
    ).build()

    soundDao = appDb.sounds()
  }

  @After
  @Throws(IOException::class)
  fun teardown() {
    appDb.close()
  }

  @Test
  fun saveAndListTags() = runTest {
    val tags = listOf(SoundTagDto("test-1", "Test 1"), SoundTagDto("test-2", "Test 2"))
    soundDao.saveTags(tags)
    assertEquals(tags, soundDao.listTags())
  }

  @Test
  fun saveAndGetLibraryUpdateTime() = runTest {
    assertNull(soundDao.getLibraryUpdateTime())

    val now = Date()
    soundDao.saveLibraryUpdateTime(LibraryUpdateTimeDto(now))
    assertEquals(now, soundDao.getLibraryUpdateTime())
  }

  @Test
  fun saveAndListInfo() = runTest {
    val group = SoundGroupDto("test-group-1", "Test Group 1")
    soundDao.saveGroups(listOf(group))

    val tag = SoundTagDto("test-tag-1", "Test Tag 1")
    soundDao.saveTags(listOf(tag))

    val metadata1 = buildSoundMetadata("test-1", groupId = group.id)
    soundDao.saveMetadata(metadata1)
    soundDao.saveSoundTagCrossRefs(listOf(SoundTagCrossRef(metadata1.id, tag.id)))

    val source = SoundSourceDto(
      soundId = metadata1.id,
      name = "test",
      url = "https://android-app.test",
      license = "test-license",
      authorName = null,
      authorUrl = null,
    )

    soundDao.saveSources(listOf(source))

    val metadata2 = buildSoundMetadata("test-2", groupId = group.id)
    soundDao.saveMetadata(metadata2)

    val infos = soundDao.listInfo()
    assertEquals(2, infos.size)
    assertEquals(listOf(metadata1, metadata2), infos.map { it.metadata })
    infos.forEach { assertEquals(group, it.group) }
    assertEquals(tag, infos[0].tags.firstOrNull())
    assertTrue(infos[1].tags.isEmpty())
    assertEquals(source, infos[0].sources.firstOrNull())
    assertTrue(infos[1].sources.isEmpty())
  }

  @Test
  fun saveThenGetSoundAndRemoveAll() = runTest {
    assertNull(soundDao.get("test-2"))

    val group = SoundGroupDto("test-group-1", "Test Group 1")
    soundDao.saveGroups(listOf(group))

    val metadata1 = buildSoundMetadata("test-1", groupId = group.id)
    soundDao.saveMetadata(metadata1)

    val segments = listOf(
      buildSoundSegment(metadata1.id, "test-1"),
      buildSoundSegment(metadata1.id, "test-2"),
      buildSoundSegment(metadata1.id, "test-3"),
    )

    segments.forEach { soundDao.saveSegment(it) }
    val sound = soundDao.get(metadata1.id)
    assertNotNull(sound)
    assertEquals(metadata1, sound?.info?.metadata)
    assertEquals(segments, sound?.segments)

    soundDao.removeAll()
    assertNull(soundDao.get(metadata1.id))
    assertEquals(0, soundDao.listInfo().size)
    assertEquals(0, soundDao.listTags().size)
  }

  @Test
  fun saveMetadataAndCountPremium() = runTest {
    soundDao.saveMetadata(buildSoundMetadata("test-1", isPremium = false))
    soundDao.saveMetadata(buildSoundMetadata("test-2", isPremium = true))
    soundDao.saveMetadata(buildSoundMetadata("test-3", isPremium = true))
    assertEquals(2, soundDao.countPremium())
  }

  private fun buildSoundMetadata(
    id: String,
    groupId: String = "test",
    isPremium: Boolean = false,
  ): SoundMetadataDto {
    return SoundMetadataDto(
      id = id,
      groupId = groupId,
      name = "test",
      iconSvg = "test",
      maxSilence = 0,
      isPremium = isPremium,
      hasPremiumSegments = true,
    )
  }

  private fun buildSoundSegment(soundId: String, name: String): SoundSegmentDto {
    return SoundSegmentDto(
      soundId = soundId,
      name = name,
      basePath = "/",
      isFree = true,
      isBridgeSegment = false,
      from = null,
      to = null,
    )
  }
}
