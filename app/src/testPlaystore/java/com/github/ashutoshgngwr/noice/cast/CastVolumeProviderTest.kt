package com.github.ashutoshgngwr.noice.cast

import com.google.android.gms.cast.framework.CastSession
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class CastVolumeProviderTest {

  @RelaxedMockK
  private lateinit var session: CastSession

  @OverrideMockKs(lookupType = InjectionLookupType.BY_NAME)
  private lateinit var volumeProvider: CastVolumeProvider

  @Before
  fun setup() {
    MockKAnnotations.init(this)
  }

  @Test
  fun testOnSetVolumeTo() {
    val volume = Random.nextInt(0, 1 + CastVolumeProvider.MAX_VOLUME)
    volumeProvider.onSetVolumeTo(volume)
    verify(exactly = 1) { session.volume = volume.toDouble() / CastVolumeProvider.MAX_VOLUME }
  }

  @Test
  fun testOnAdjustVolume() {
    volumeProvider.onAdjustVolume(1)
    volumeProvider.onAdjustVolume(-1)
    verifyOrder {
      session.volume = 1.0 / CastVolumeProvider.MAX_VOLUME
      session.volume = 0.0
    }
  }
}
