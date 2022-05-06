package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSourceException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.util.Util
import com.trynoice.api.client.NoiceApiClient
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * An ExoPlayer [DataSource] that fetches sound data from the [CDN][NoiceApiClient.cdn]. Although it
 * doesn't implement [HttpDataSource][com.google.android.exoplayer2.upstream.HttpDataSource], it
 * throws relevant [HttpDataSourceException] and [InvalidResponseCodeException] instances to exhibit
 * a similar behaviour. It is also compatible with ExoPlayer's default load error handling policy
 * (works with [InvalidResponseCodeException] instances).
 */
class CdnSoundDataSource private constructor(
  private val apiClient: NoiceApiClient,
) : BaseDataSource(true) {

  private var opened = false
  private var response: Response<ResponseBody>? = null
  private lateinit var dataSpec: DataSpec

  override fun open(dataSpec: DataSpec): Long {
    // mostly copied from the okhttp data source from the okhttp extension for ExoPlayer.
    // https://github.com/google/ExoPlayer/blob/release-v2/extensions/okhttp/

    this.dataSpec = dataSpec
    transferInitializing(dataSpec)
    val rangeEnd = if (dataSpec.length == LENGTH_UNSET) "" else dataSpec.length.toString()
    val response = try {
      apiClient.cdn()
        .resource(requireNotNull(dataSpec.uri.path), "bytes=${dataSpec.position}-${rangeEnd}")
        .execute()
    } catch (e: IOException) {
      throw HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSourceException.TYPE_OPEN
      )
    }

    this.response = response
    if (!response.isSuccessful) { // handle unsuccessful response
      val cause = if (response.code() == 416) {
        DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
      } else {
        IOException(HttpException(response))
      }

      try {
        // must throw InvalidResponseCodeException here as our load error policy depends on it.
        throw InvalidResponseCodeException(
          response.code(),
          response.message(),
          cause,
          response.headers().toMultimap(),
          dataSpec,
          Util.EMPTY_BYTE_ARRAY,
        )
      } finally {
        closeQuietly()
      }
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    val toSkip = if (response.code() == 200 && dataSpec.position != 0L) dataSpec.position else 0
    // Determine the length of the data to be read, after skipping.
    val toRead = if (dataSpec.length != LENGTH_UNSET) {
      dataSpec.length
    } else {
      val contentLength = response?.body()?.contentLength() ?: -1L
      if (contentLength != -1L) contentLength - toSkip else LENGTH_UNSET
    }

    opened = true
    transferStarted(dataSpec)
    try {
      val skipped = requireNotNull(response.body())
        .byteStream()
        .skip(toSkip)

      if (skipped != toSkip) {
        closeQuietly()
        throw HttpDataSourceException(
          dataSpec,
          PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
          HttpDataSourceException.TYPE_OPEN,
        )
      }
    } catch (e: IOException) {
      closeQuietly()
      throw HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSourceException.TYPE_OPEN
      )
    }

    return toRead
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) {
      return 0
    }

    try {
      val read = requireNotNull(response?.body())
        .byteStream()
        .read(buffer, offset, length)

      if (read == -1) {
        return C.RESULT_END_OF_INPUT
      }

      bytesTransferred(read)
      return read
    } catch (e: IOException) {
      throw HttpDataSourceException.createForIOException(
        e, dataSpec, HttpDataSourceException.TYPE_READ
      )
    }
  }

  override fun getUri(): Uri? {
    return response?.raw()?.request?.url?.toString()?.let { Uri.parse(it) }
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
