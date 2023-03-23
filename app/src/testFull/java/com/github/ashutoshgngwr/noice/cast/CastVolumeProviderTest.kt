package com.github.ashutoshgngwr.noice.cast

import com.google.android.gms.cast.framework.CastSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class CastVolumeProviderTest {

  private lateinit var sessionMock: CastSession
  private lateinit var volumeProvider: CastVolumeProvider

  @Before
  fun setup() {
    sessionMock = mockk(relaxed = true)
    volumeProvider = CastVolumeProvider(mockk {
      every { sessionManager } returns mockk {
        every { currentCastSession } returns sessionMock
      }
    })
  }

  @Test
  fun setVolumeTo() {
    val volume = Random.nextInt(0, 1 + CastVolumeProvider.MAX_VOLUME)
    volumeProvider.onSetVolumeTo(volume)
    verify(exactly = 1) { sessionMock.volume = volume.toDouble() / CastVolumeProvider.MAX_VOLUME }
  }

  @Test
  fun onAdjustVolume() {
    volumeProvider.onAdjustVolume(1)
    volumeProvider.onAdjustVolume(-1)
    verifyOrder {
      sessionMock.volume = 1.0 / CastVolumeProvider.MAX_VOLUME
      sessionMock.volume = 0.0
    }
  }
}
