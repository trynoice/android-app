@file:Suppress("unused", "TestFunctionName")

package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.session.MediaSession
import android.support.v4.media.session.MediaSessionCompat
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadow.api.Shadow.invokeConstructor
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Shadow implementation for [MediaSessionCompat].
 */
@Implements(value = MediaSessionCompat::class)
class ShadowMediaSessionCompat {

  @RealObject
  lateinit var realMediaSessionCompat: MediaSessionCompat

  companion object {
    /**
     * Not sure about the following implementation. The original implementation of
     * [MediaSessionCompat.fromMediaSession] seems to suggest that [mediaSession] argument should
     * be of type [MediaSession]. But somehow, we are receiving [MediaSessionCompat] and that too
     * from [MediaRouter][androidx.mediarouter.media.MediaRouter]. The following works. Not at all
     * sure about its correctness.
     */
    @Implementation
    @JvmStatic
    fun fromMediaSession(context: Context, mediaSession: Any): MediaSessionCompat {
      val mediaSessionImpl = when (mediaSession) {
        is ShadowMediaSession -> {
          mediaSession.realMediaSession
        }
        is MediaSessionCompat -> {
          mediaSession.mediaSession as MediaSession
        }
        else -> {
          throw IllegalArgumentException("Couldn't recognize mediaSession arg of type ${mediaSession::class.simpleName}")
        }
      }

      return Shadow.directlyOn(
        MediaSessionCompat::class.java, "fromMediaSession",
        ClassParameter.from(Context::class.java, context),
        ClassParameter.from(Object::class.java, mediaSessionImpl)
      )
    }
  }

  @Implementation
  fun __constructor__(context: Context, tag: String) {
    invokeConstructor(
      MediaSessionCompat::class.java,
      realMediaSessionCompat,
      ClassParameter.from(Context::class.java, context),
      ClassParameter.from(String::class.java, tag)
    )
  }
}
