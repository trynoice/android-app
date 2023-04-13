package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.trynoice.api.client.NoiceApiClient
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.math.min

/**
 * An ExoPlayer [DataSource] that fetches sound data from the [CDN][NoiceApiClient.cdn]. Although it
 * doesn't implement [HttpDataSource], it throws relevant [HttpDataSource.HttpDataSourceException]
 * and [HttpDataSource.InvalidResponseCodeException] instances to exhibit a similar behaviour.
 */
@OptIn(UnstableApi::class)
class CdnSoundDataSource private constructor(
  private val apiClient: NoiceApiClient,
) : BaseDataSource(true) {

  private var opened = false
  private var response: Response<ResponseBody>? = null
  private lateinit var dataSpec: DataSpec
  private var bytesToRead = LENGTH_UNSET
  private var bytesRead = 0

  override fun open(dataSpec: DataSpec): Long {
    // mostly copied from the okhttp data source from the okhttp extension for ExoPlayer.
    // https://github.com/google/ExoPlayer/blob/release-v2/extensions/okhttp/

    // We're not using range headers since OkHttp won't cache partial content responses. Requesting
    // for a full resource should be a more economical solution since CDN always responds with
    // `Cache-Control` and `ETag` headers and the API Client is configured to cache responses for
    // CDN requests.

    this.dataSpec = dataSpec
    transferInitializing(dataSpec)
    val response = try {
      apiClient.cdn()
        .resource(requireNotNull(dataSpec.uri.path))
        .execute()
    } catch (e: IOException) {
      throw HttpDataSource.HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN
      )
    }

    this.response = response
    if (!response.isSuccessful) { // handle unsuccessful response
      closeQuietly()
      throw HttpDataSource.InvalidResponseCodeException(
        response.code(),
        response.message(),
        IOException(HttpException(response)),
        response.headers().toMultimap(),
        dataSpec,
        Util.EMPTY_BYTE_ARRAY,
      )
    }

    // Determine the length of the data to be read, after skipping.
    bytesToRead = if (dataSpec.length != LENGTH_UNSET) {
      dataSpec.length
    } else {
      val contentLength = response?.body()?.contentLength() ?: -1L
      if (contentLength != -1L) contentLength - dataSpec.position else LENGTH_UNSET
    }

    opened = true
    transferStarted(dataSpec)
    val skipped = try {
      requireNotNull(response.body())
        .byteStream()
        .skip(dataSpec.position)
    } catch (e: IOException) {
      closeQuietly()
      throw HttpDataSource.HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN
      )
    }

    if (skipped != dataSpec.position) {
      closeQuietly()
      throw HttpDataSource.HttpDataSourceException(
        dataSpec,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        HttpDataSource.HttpDataSourceException.TYPE_OPEN,
      )
    }

    bytesTransferred(skipped.toInt())
    return bytesToRead
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length < 1) {
      return 0
    }

    val readLength = if (bytesToRead == LENGTH_UNSET) length else {
      val bytesRemaining = (bytesToRead - bytesRead).toInt()
      if (bytesRemaining < 1) {
        return C.RESULT_END_OF_INPUT
      }

      min(length, bytesRemaining)
    }

    try {
      val read = requireNotNull(response?.body())
        .byteStream()
        .read(buffer, offset, readLength)

      if (read < 0) {
        return C.RESULT_END_OF_INPUT
      }

      bytesRead += read
      bytesTransferred(read)
      return read
    } catch (e: IOException) {
      throw HttpDataSource.HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_READ
      )
    }
  }

  override fun getUri(): Uri? {
    return if (opened) dataSpec.uri else null
  }

  override fun close() {
    if (opened) {
      opened = false
      transferEnded()
      closeQuietly()
    }
  }

  private fun closeQuietly() {
    response?.body()?.closeQuietly()
    response = null
  }

  companion object {
    private const val LENGTH_UNSET = C.LENGTH_UNSET.toLong()
  }

  /**
   * A factory for [CdnSoundDataSource] instances.
   */
  class Factory(private val apiClient: NoiceApiClient) : DataSource.Factory {

    override fun createDataSource(): DataSource {
      return CdnSoundDataSource(apiClient)
    }
  }
}
