package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import com.github.ashutoshgngwr.noice.R
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider

@Suppress("unused")
class CastOptionsProvider : OptionsProvider {

  override fun getCastOptions(context: Context): CastOptions {
    return CastOptions.Builder().run {
      // `cast_app_id` is declared in 'app/build.gradle'
      setReceiverApplicationId(context.getString(R.string.cast_app_id))
      setStopReceiverApplicationWhenEndingSession(true)
      build()
    }
  }

  override fun getAdditionalSessionProviders(context: Context?) = null
}
