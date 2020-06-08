@file:Suppress("unused", "TestFunctionName")

package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.session.MediaSession
import android.os.Build.VERSION_CODES
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow.invokeConstructor
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * The framework's original [ShadowMediaSession][org.robolectric.shadows.ShadowMediaSession]
 * implementation throws NPEs.
 */
@Implements(value = MediaSession::class, minSdk = VERSION_CODES.LOLLIPOP)
class ShadowMediaSession {

  @RealObject
  lateinit var realMediaSession: MediaSession

  @Implementation
  fun __constructor__(context: Context, tag: String) {
    invokeConstructor(
      MediaSession::class.java,
      realMediaSession,
      ClassParameter.from(Context::class.java, context),
      ClassParameter.from(String::class.java, tag)
    )
  }
}
