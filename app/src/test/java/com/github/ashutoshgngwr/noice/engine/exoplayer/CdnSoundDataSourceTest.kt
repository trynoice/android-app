package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.trynoice.api.client.apis.CdnApi
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.IOException
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class CdnSoundDataSourceTest {

  private val testUri = Uri.parse("cdn://test-uri")

  private lateinit var cdnApiMock: CdnApi
  private lateinit var dataSource: DataSource

  @Before
  fun setUp() {
    cdnApiMock = mockk(relaxed = true)
    dataSource = CdnSoundDataSource.Factory(mockk {
      every { cdn() } returns cdnApiMock
    }).createDataSource()
  }

  @Test
  fun open_withNetworkError() {
    every { cdnApiMock.resource(any()) } returns mockk {
      every { execute() } throws IOException("test-network error")
    }

    assertThrows(HttpDataSource.HttpDataSourceException::class.java) {
      dataSource.open(DataSpec(testUri))
    }
  }

  @Test
  fun open_withHttpError() {
    every { cdnApiMock.resource(any()) } returns mockk {
      every { execute() } returns Response.error(404, "".toResponseBody(null))
    }

    assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) {
      dataSource.open(DataSpec(testUri))
    }
  }

  @Test
  fun open_withSkipError() {
    every { cdnApiMock.resource(any()) } returns mockk {
      every { execute() } returns Response.success(ByteArray(256).toResponseBody("audio/mpeg".toMediaType()))
    }

    assertThrows(HttpDataSource.HttpDataSourceException::class.java) {
      dataSource.open(DataSpec(testUri, 300, C.LENGTH_UNSET.toLong()))
    }
  }

  @Test
  fun open_read_close_withSetDataSpecLength() {
    val data = Random.Default.nextBytes(256)
    every { cdnApiMock.resource(any()) } returns mockk {
      every { execute() } returns Response.success(data.toResponseBody("audio/mpeg".toMediaType()))
    }

    val toRead = dataSource.open(DataSpec(testUri, 64, C.LENGTH_UNSET.toLong()))
    assertEquals(192L, toRead)

    val buff = ByteArray(8)
    var offset = 64
    repeat(24) {
      val read = dataSource.read(buff, 0, 8)
      repeat(read) { i ->
        assertEquals(data[offset++], buff[i])
      }
    }

    assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(buff, 0, 8))
    dataSource.close()
  }

  @Test
  fun open_read_close_withUnsetDataSpecLength() {
    val data = Random.Default.nextBytes(256)
    every { cdnApiMock.resource(any()) } returns mockk {
      every { execute() } returns Response.success(data.toResponseBody("audio/mpeg".toMediaType()))
    }

    val toRead = dataSource.open(DataSpec(testUri, 0, 192L))
    assertEquals(192L, toRead)

    val buff = ByteArray(8)
    var offset = 0
    repeat(24) {
      val read = dataSource.read(buff, 0, 8)
      repeat(read) { i ->
        assertEquals(data[offset++], buff[i])
      }
    }

    assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(buff, 0, 8))
    dataSource.close()
  }
}
