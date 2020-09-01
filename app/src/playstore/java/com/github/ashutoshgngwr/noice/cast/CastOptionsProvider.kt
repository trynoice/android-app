package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

@Suppress("unused")
class CastOptionsProvider : OptionsProvider {

  override fun getCastOptions(context: Context): CastOptions {
    return CastOptions.Builder().run {
      setReceiverApplicationId(
        context.getString(
          @Suppress("ConstantConditionIf")
          if (BuildConfig.DEBUG) {
            R.string.cast_app_id__debug
          } else {
            R.string.cast_app_id__release
          }
        )
      )

      setStopReceiverApplicationWhenEndingSession(true)
      setCastMediaOptions(
        CastMediaOptions.Builder()
          .setMediaSessionEnabled(false)
          .build()
      )

      build()
    }
  }

  override fun getAdditionalSessionProviders(context: Context?): List<SessionProvider>? = null
}
