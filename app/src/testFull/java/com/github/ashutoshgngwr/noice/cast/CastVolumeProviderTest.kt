package com.github.ashutoshgngwr.noice.cast

import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class CastVolumeProviderTest {

  private lateinit var castContextMock: CastContext
  private lateinit var castSessionMock: CastSession

  @Before
  fun setUp() {
    castSessionMock = mockk(relaxed = true)
    castContextMock = mockk {
      every { sessionManager } returns mockk {
        every { currentCastSession } returns castSessionMock
      }
    }
  }

  @Test
  fun getVolume_setVolume_increaseVolume_decreaseVolume() {
    every { castSessionMock.volume } returns 0.5
    val provider = CastVolumeProvider(castContextMock)
    assertEquals((0.5 * CastVolumeProvider.MAX_VOLUME).roundToInt(), provider.getVolume())

    provider.setVolume(15)
    assertEquals(15, provider.getVolume())
    verify(exactly = 1) { castSessionMock.volume = 15 / CastVolumeProvider.MAX_VOLUME.toDouble() }

    provider.increaseVolume()
    assertEquals(16, provider.getVolume())
    verify(exactly = 1) { castSessionMock.volume = 16 / CastVolumeProvider.MAX_VOLUME.toDouble() }

    provider.decreaseVolume()
    assertEquals(15, provider.getVolume())
    verify(exactly = 2) { castSessionMock.volume = 15 / CastVolumeProvider.MAX_VOLUME.toDouble() }
  }

  @Test
  fun isMute_setMute() {
    every { castSessionMock.isMute } returns true
    val provider = CastVolumeProvider(castContextMock)
    assertTrue(provider.isMute())

    provider.setMute(false)
    assertFalse(provider.isMute())
    verify(exactly = 1) { castSessionMock.isMute = false }
  }
}
