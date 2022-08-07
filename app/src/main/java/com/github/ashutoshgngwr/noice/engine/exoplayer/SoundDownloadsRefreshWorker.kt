package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.getMutableStringSet
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.model.SoundDownloadMetadata
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.google.android.exoplayer2.database.DatabaseIOException
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * A worker that implements a facade to download sounds using ExoPlayer's [DownloadService]. Use
 * [SoundDownloadsRefreshWorker.addSoundDownload] and
 * [SoundDownloadsRefreshWorker.removeSoundDownload] to add and remove sound downloads. Use
 * [SoundDownloadsRefreshWorker.refreshDownloads] to ensure that the local copies of downloaded
 * sounds are in sync with the CDN server.
 *
 * Internally, it maintains a list of ids for downloaded sounds in the default shared preferences.
 * Whenever a sound is added to or removed from downloads, it updates this list. The worker then
 * performs a check on ExoPlayer downloads ensuring that ExoPlayer download list are in sync with
 * its own list.
 */
@HiltWorker
class SoundDownloadsRefreshWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val subscriptionRepository: SubscriptionRepository,
  private val soundRepository: SoundRepository,
  private val settingsRepository: SettingsRepository,
  private val downloadIndex: DownloadIndex,
  private val gson: Gson,
) : CoroutineWorker(context, params) {

  private val notification: Notification by lazy {
    SoundDownloadsNotificationChannelHelper.initChannel(context)
    NotificationCompat.Builder(context, SoundDownloadsNotificationChannelHelper.CHANNEL_ID)
      .setContentTitle(context.getString(R.string.checking_sound_downloads))
      .setTicker(context.getString(R.string.checking_sound_downloads))
      .setProgress(0, 0, true)
      .setSmallIcon(R.drawable.ic_launcher_24dp)
      .setOngoing(true)
      .setShowWhen(false)
      .setSilent(true)
      .build()
  }

  override suspend fun doWork(): Result {
    try {
      // ensure that user has an active subscription or return with success.
      if (!withContext(Dispatchers.IO) { hasSubscription() }) {
        Log.i(LOG_TAG, "doWork: user doesn't have an active subscription")
        return Result.success()
      }
    } catch (e: Throwable) {
      Log.e(LOG_TAG, "doWork: failed to check user's subscription status", e)
      return Result.retry()
    }

    try {
      setForeground(getForegroundInfo())
    } catch (e: Throwable) {
      // since the work is not long running, it is not a fatal error. The intent behind starting the
      // worker in foreground is to update user about app's activity.
      Log.w(LOG_TAG, "doWork: failed to bring worker to the foreground", e)
    }

    val soundIds = PreferenceManager.getDefaultSharedPreferences(context)
      .getStringSet(PREF_DOWNLOADED_SOUND_IDS, emptySet()) ?: return Result.failure()

    val segmentPathsToSoundIds = mutableMapOf<String, String>()
    val audioBitrate = settingsRepository.getAudioQuality().bitrate
    soundIds.forEach { soundId ->
      try {
        withContext(Dispatchers.IO) { getSound(soundId) }
          .segments
          .forEach { segmentPathsToSoundIds[it.path(audioBitrate)] = soundId }
      } catch (e: Throwable) {
        Log.e(LOG_TAG, "doWork: failed to retrieve sound $soundId", e)
        return Result.retry()
      }
    }

    val md5sums: Map<String, String> = try {
      if (soundIds.isEmpty()) emptyMap() else withContext(Dispatchers.IO) { getMd5sums() }
    } catch (e: Throwable) {
      Log.e(LOG_TAG, "doWork: failed to get md5sums for CDN resources", e)
      return Result.retry()
    }

    try {
      val downloadCursor = withContext(Dispatchers.IO) { downloadIndex.getDownloads() }
      while (downloadCursor.moveToNext()) {
        // check if this download is still needed and is up to date with the CDN server.
        val request = downloadCursor.download.request
        if (request.id !in segmentPathsToSoundIds) {
          Log.d(LOG_TAG, "doWork: ${request.id} has been removed")
          removeExoPlayerDownload(request.id)
          continue
        }

        val metadataJson = request.data.decodeToString()
        val metadata = gson.fromJson(metadataJson, SoundDownloadMetadata::class.java)
        if (metadata.md5sum != md5sums[request.id]) {
          Log.d(LOG_TAG, "doWork: ${request.id} has changed")
          removeExoPlayerDownload(request.id)
          continue
        }

        Log.d(LOG_TAG, "doWork: ${request.id} is unchanged")
        segmentPathsToSoundIds.remove(request.id)
      }

      downloadCursor.close()
    } catch (e: DatabaseIOException) {
      Log.e(LOG_TAG, "doWork: failed to query ExoPlayer's download index", e)
      return Result.retry()
    }

    // schedule remaining downloads
    segmentPathsToSoundIds.forEach { (path, soundId) ->
      Log.d(LOG_TAG, "doWork: adding exoplayer download request for $path")
      addExoPlayerDownload(path, SoundDownloadMetadata(md5sums.getValue(path), soundId))
    }

    DownloadService.sendResumeDownloads(context, SoundDownloadService::class.java, true)
    return Result.success()
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    return ForegroundInfo(0x03, notification)
  }

  private suspend fun hasSubscription(): Boolean {
    val resource = subscriptionRepository.isSubscribed().lastOrNull()
    if (resource !is Resource.Success || resource.data == null) {
      throw resource?.error ?: Exception("Resource is not Success and error was null")
    }

    return resource.data
  }

  private suspend fun getSound(soundId: String): Sound {
    val resource = soundRepository.get(soundId).lastOrNull()
    if (resource !is Resource.Success || resource.data == null) {
      throw resource?.error ?: Exception("Resource is not Success and error was null")
    }

    return resource.data
  }

  private suspend fun getMd5sums(): Map<String, String> {
    val resource = soundRepository.getMd5sums().lastOrNull()
    if (resource !is Resource.Success || resource.data == null) {
      throw resource?.error ?: Exception("Resource is not Success and error was null")
    }

    return resource.data
  }

  private fun addExoPlayerDownload(segmentPath: String, metadata: SoundDownloadMetadata) {
    DownloadRequest.Builder(segmentPath, "noice://cdn/library/${segmentPath}".toUri())
      .setData(gson.toJson(metadata).encodeToByteArray())
      .build()
      .also { DownloadService.sendAddDownload(context, SoundDownloadService::class.java, it, true) }
  }

  private fun removeExoPlayerDownload(contentId: String) {
    DownloadService.sendRemoveDownload(context, SoundDownloadService::class.java, contentId, true)
  }

  companion object {
    private const val LOG_TAG = "SoundDownloadsRefreshWo"
    private const val PREF_DOWNLOADED_SOUND_IDS = "downloaded_sound_ids"

    /**
     * Adds an expedited request to download the sound with the given [soundId].
     */
    fun addSoundDownload(context: Context, soundId: String) {
      var isAdded = false
      editDownloadedSoundIds(context) { isAdded = it.add(soundId) }
      if (isAdded) {
        refreshDownloads(context, true)
      }
    }

    /**
     * Adds an expedited request to remove the downloads for the sound with the given [soundId].
     */
    fun removeSoundDownload(context: Context, soundId: String) {
      var isRemoved = false
      editDownloadedSoundIds(context) { isRemoved = it.remove(soundId) }
      if (isRemoved) {
        refreshDownloads(context, true)
      }
    }

    /**
     * Adds a request to check if all previously requested sounds have been downloaded and are in
     * sync with the CDN server.
     *
     * @param expedited whether to make the work manager request expedited.
     */
    fun refreshDownloads(context: Context, expedited: Boolean = false) {
      OneTimeWorkRequestBuilder<SoundDownloadsRefreshWorker>()
        .also { if (expedited) it.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(10))
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        )
        .build()
        .also {
          WorkManager.getInstance(context)
            .enqueueUniqueWork("SoundDownloadsRefreshWork", ExistingWorkPolicy.REPLACE, it)
        }
    }

    /**
     * Adds an expedited request to remove all sound downloads.
     */
    fun removeAllSoundDownloads(context: Context) {
      var removedCount = 0
      editDownloadedSoundIds(context) { soundIds ->
        removedCount = soundIds.size
        soundIds.clear()
      }

      if (removedCount > 0) {
        refreshDownloads(context, true)
      }
    }

    private inline fun editDownloadedSoundIds(
      context: Context,
      block: (MutableSet<String>) -> Unit,
    ) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(context)
      val soundIds = prefs.getMutableStringSet(PREF_DOWNLOADED_SOUND_IDS)
      block.invoke(soundIds)
      prefs.edit { putStringSet(PREF_DOWNLOADED_SOUND_IDS, soundIds) }
    }
  }
}
