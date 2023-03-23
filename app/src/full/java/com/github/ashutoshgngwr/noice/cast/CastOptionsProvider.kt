package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import com.github.ashutoshgngwr.noice.BuildConfig
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

@Suppress("unused") // referenced in `full/AndroidManifest.xml`
class CastOptionsProvider : OptionsProvider {

  override fun getCastOptions(context: Context): CastOptions {
    return CastOptions.Builder()
      .setReceiverApplicationId(RECEIVER_APP_ID)
      .setStopReceiverApplicationWhenEndingSession(true)
      .setCastMediaOptions(
        CastMediaOptions.Builder()
          .setMediaSessionEnabled(false)
          .build()
      )
      .build()
  }

  override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null

  companion object {
    private val RECEIVER_APP_ID = if (BuildConfig.DEBUG) "E9762721" else "" // TODO:
  }
}
