package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.google.android.exoplayer2.ext.workmanager.WorkManagerScheduler
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.offline.WritableDownloadIndex
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class SoundDownloadService : DownloadService(0x2, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL) {

  @set:Inject
  internal lateinit var downloadCache: Cache

  @set:Inject
  internal lateinit var downloadIndex: WritableDownloadIndex

  @set:Inject
  internal lateinit var apiClient: NoiceApiClient

  private val downloadExecutor = Executors.newFixedThreadPool(2)
  private val notificationHelper by lazy {
    SoundDownloadsNotificationChannelHelper.initChannel(this)
    DownloadNotificationHelper(this, SoundDownloadsNotificationChannelHelper.CHANNEL_ID)
  }

  override fun getDownloadManager(): DownloadManager {
    return CacheDataSource.Factory()
      .setCache(downloadCache)
      .setUpstreamDataSourceFactory(CdnSoundDataSource.Factory(apiClient))
      .let { DefaultDownloaderFactory(it, downloadExecutor) }
      .let { DownloadManager(this, downloadIndex, it) }
      .also { it.maxParallelDownloads = 2 }
  }

  override fun getScheduler(): Scheduler {
    return WorkManagerScheduler(this, "SoundDownloadWork")
  }

  override fun getForegroundNotification(
    downloads: MutableList<Download>,
    notMetRequirements: Int
  ): Notification {
    var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
    }

    val contentIntent = Intent(this, MainActivity::class.java)
      .let { PendingIntent.getActivity(this, 0x03, it, piFlags) }

    return notificationHelper.buildProgressNotification(
      this,
      R.drawable.ic_launcher_24dp,
      contentIntent,
      null,
      downloads,
      notMetRequirements,
    )
  }

  override fun onDestroy() {
    downloadExecutor.shutdown()
    super.onDestroy()
  }
}
