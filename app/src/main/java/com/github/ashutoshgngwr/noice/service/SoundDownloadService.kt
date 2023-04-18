package com.github.ashutoshgngwr.noice.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.engine.media.CdnSoundDataSource
import com.github.ashutoshgngwr.noice.engine.media.SoundDownloadNotificationManager
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

  @set:Inject
  internal lateinit var notificationManager: SoundDownloadNotificationManager

  private val notificationContentIntent: PendingIntent by lazy {
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags = flags or PendingIntent.FLAG_IMMUTABLE
    }

    PendingIntent.getActivity(this, 0x03, Intent(this, MainActivity::class.java), flags)
  }

  override fun getDownloadManager(): DownloadManager {
    return CacheDataSource.Factory()
      .setCache(downloadCache)
      .setUpstreamDataSourceFactory(CdnSoundDataSource.Factory(apiClient))
      .let { DefaultDownloaderFactory(it, Executors.newFixedThreadPool(2)) }
      .let { DownloadManager(this, downloadIndex, it) }
      .also { it.maxParallelDownloads = 2 }
  }

  override fun getScheduler(): Scheduler {
    return WorkManagerScheduler(this, "SoundDownloadWork")
  }

  override fun getForegroundNotification(
    downloads: MutableList<Download>,
    notMetRequirements: Int,
  ): Notification {
    return notificationManager.exoPlayerNotificationHelper.buildProgressNotification(
      this,
      android.R.drawable.stat_sys_download,
      notificationContentIntent,
      null,
      downloads,
      notMetRequirements,
    )
  }
}
