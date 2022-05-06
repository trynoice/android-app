package com.github.ashutoshgngwr.noice.engine.exoplayer

import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource
import java.io.IOException

/**
 * An extension to ExoPlayer's [DefaultLoadErrorHandlingPolicy] that makes `HTTP 401` load error
 * responses eligible for fallback. `HTTP 401` needs to be eligible for fallback, in addition to
 * `HTTP 403`, because on requesting premium bitrate tracks, the CDN server will respond `HTTP 401`
 * and `HTTP 403` to unauthenticated and unsubscribed users respectively.
 */
class CdnSoundLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

  override fun isEligibleForFallback(exception: IOException): Boolean {
    return (exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 401
      || super.isEligibleForFallback(exception)
  }
}
