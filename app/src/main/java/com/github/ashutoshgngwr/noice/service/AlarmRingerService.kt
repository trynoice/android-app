package com.github.ashutoshgngwr.noice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.AudioFocusManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class AlarmRingerService : LifecycleService(), AudioFocusManager.Listener {

  @set:Inject
  internal lateinit var alarmRepository: AlarmRepository

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  @set:Inject
  internal lateinit var uiController: UiController

  private var defaultRingtonePlayer: MediaPlayer? = null
  private var volumeFadeJob: Job? = null
  private var autoDismissJob: Job? = null

  private val notificationManager: NotificationManager by lazy { requireNotNull(getSystemService()) }
  private val vibrator: Vibrator by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      requireNotNull(getSystemService<VibratorManager>()).defaultVibrator
    } else {
      requireNotNull(getSystemService())
    }
  }

  private val wakeLock: PowerManager.WakeLock by lazy {
    requireNotNull(getSystemService<PowerManager>())
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noice:AlarmRingerService")
      .apply { setReferenceCounted(false) }
  }

  private val audioFocusManager: AudioFocusManager by lazy {
    AudioFocusManager(this).also {
      it.setAudioAttributes(SoundPlayerManager.ALARM_AUDIO_ATTRIBUTES)
      it.setListener(this)
    }
  }

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Log.d(LOG_TAG, "onCreate: init notification channels")
      createNotificationChannel(
        channelId = CHANNEL_ID_RINGER,
        importance = NotificationManager.IMPORTANCE_HIGH,
        nameResId = R.string.notification_channel_alarm_primary__name,
        descriptionResId = R.string.notification_channel_alarm_primary__description,
      )

      createNotificationChannel(
        channelId = CHANNEL_ID_PRIMING,
        importance = NotificationManager.IMPORTANCE_LOW,
        nameResId = R.string.notification_channel_alarm_priming__name,
        descriptionResId = R.string.notification_channel_alarm_priming__description,
      )

      createNotificationChannel(
        channelId = CHANNEL_ID_MISSED,
        importance = NotificationManager.IMPORTANCE_LOW,
        nameResId = R.string.notification_channel_missed_alarms__name,
        descriptionResId = R.string.notification_channel_missed_alarms__description,
      )
    }
  }

  override fun onDestroy() {
    if (wakeLock.isHeld) wakeLock.release()
    super.onDestroy()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    intent ?: return super.onStartCommand(null, flags, startId)
    Log.d(LOG_TAG, "onStartCommand: received new intent")
    val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
    when (intent.action) {
      ACTION_RING -> {
        val wakeLockTimeout = settingsRepository.getAlarmRingerMaxDuration() + 15.seconds
        wakeLock.acquire(wakeLockTimeout.inWholeMilliseconds)
        lifecycleScope.launch { startRinger(alarmId) }
      }
      ACTION_SNOOZE -> lifecycleScope.launch { dismiss(alarmId, isSnoozed = true) }
      ACTION_DISMISS -> lifecycleScope.launch { dismiss(alarmId, isSnoozed = false) }
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onAudioFocusGained() {
    defaultRingtonePlayer?.start()
  }

  override fun onAudioFocusLost(transient: Boolean) {
    defaultRingtonePlayer?.pause()
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createNotificationChannel(
    channelId: String,
    importance: Int,
    @StringRes nameResId: Int,
    @StringRes descriptionResId: Int,
  ) {
    NotificationChannel(channelId, getString(nameResId), importance)
      .apply {
        description = getString(descriptionResId)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
        enableVibration(false)
        setSound(null, null)
      }
      .also { notificationManager.createNotificationChannel(it) }
  }

  private suspend fun startRinger(alarmId: Int) {
    val alarm = alarmRepository.get(alarmId)
    if (alarm == null) {
      Log.w(LOG_TAG, "startRinger: alarm [id=${alarmId}] not found, stopping service")
      stopService()
      return
    }

    val timeFmtFlags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME
    val alarmTriggerTime = DateUtils.formatDateTime(this, System.currentTimeMillis(), timeFmtFlags)

    // since loading the preset may take some time, show a notification.
    startForeground(NOTIFICATION_ID_ALARM, buildLoadingNotification(alarmTriggerTime))

    val preset: Preset? = alarm.preset // use the alarm's selected preset if it has one
      ?: presetRepository.getRandom() // or pick one at random from the saved presets
      ?: presetRepository.generate(emptySet(), Random.nextInt(2, 6))
        .lastOrNull()?.data // or attempt to generate one

    val fadeDurationMillis = min(
      settingsRepository.getAlarmVolumeRampDuration().inWholeMilliseconds,
      (settingsRepository.getAlarmRingerMaxDuration() - 1.minutes).inWholeMilliseconds,
    )

    if (preset != null) {
      Log.d(LOG_TAG, "startRinger: starting preset: $preset")
      playbackServiceController.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_ALARM)
      if (fadeDurationMillis > 0) playbackServiceController.setVolume(0F)
      playbackServiceController.playPreset(preset)
    } else {
      Log.d(LOG_TAG, "startRinger: starting default ringtone")
      playDefaultRingtone(if (fadeDurationMillis > 0) 0.2f else 1f)
      buildPresetLoadFailedNotification(alarmTriggerTime)
        .also { notificationManager.notify(NOTIFICATION_ID_ALARM, it) }
    }

    volumeFadeJob?.cancelAndJoin()
    volumeFadeJob = if (fadeDurationMillis > 0) {
      lifecycleScope.launch {
        val startTime = System.currentTimeMillis()
        while (isActive) {
          val v = min(1F, (System.currentTimeMillis() - startTime).toFloat() / fadeDurationMillis)
          playbackServiceController.setVolume(v)
          defaultRingtonePlayer?.setVolume(v, v)
          if (v == 1F) {
            break
          }

          delay(250)
        }
      }
    } else {
      null
    }

    if (alarm.vibrate) {
      startVibrating()
    }

    buildRingerNotification(alarm, alarmTriggerTime)
      .also { notificationManager.notify(NOTIFICATION_ID_ALARM, it) }

    autoDismissJob?.cancel()
    autoDismissJob = lifecycleScope.launch {
      Log.d(LOG_TAG, "startRinger: start delayed auto dismiss job")
      delay(settingsRepository.getAlarmRingerMaxDuration())
      buildMissedAlarmNotification(alarmTriggerTime)
        .also { notificationManager.notify(NOTIFICATION_ID_MISSED, it) }

      dismiss(alarm.id, isSnoozed = false)
    }
  }

  private fun buildLoadingNotification(contentText: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID_PRIMING)
      .setSmallIcon(R.drawable.ic_baseline_alarm_24)
      .setContentTitle(getString(R.string.alarm))
      .setContentText(contentText)
      .setProgress(0, 0, true)
      .setOngoing(true)
      .setShowWhen(false)
      .build()
  }

  private fun startVibrating() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      VibrationAttributes.Builder()
        .setUsage(VibrationAttributes.USAGE_ALARM)
        .build()
        .also { vibrator.vibrate(VibrationEffect.createWaveform(DEFAULT_VIBRATION_PATTERN, 0), it) }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
        .also {
          @Suppress("DEPRECATION")
          vibrator.vibrate(VibrationEffect.createWaveform(DEFAULT_VIBRATION_PATTERN, 0), it)
        }
    } else {
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
        .also { @Suppress("DEPRECATION") vibrator.vibrate(DEFAULT_VIBRATION_PATTERN, 0, it) }
    }
  }

  private fun buildRingerNotification(alarm: Alarm, alarmTriggerTime: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID_RINGER)
      .setSmallIcon(R.drawable.ic_baseline_alarm_24)
      .setContentTitle(getString(R.string.alarm))
      .setContentText(alarmTriggerTime)
      .addAction(
        R.drawable.ic_round_snooze_24,
        getString(R.string.snooze),
        buildPendingServiceIntent(buildSnoozeIntent(this, alarm.id), 0x61),
      )
      .addAction(
        R.drawable.ic_round_close_24,
        getString(R.string.dismiss),
        buildPendingServiceIntent(buildDismissIntent(this, alarm.id), 0x62),
      )
      .setFullScreenIntent(
        uiController.buildShowIntent(alarm.id, alarm.label, alarmTriggerTime)
          .let { PendingIntent.getActivity(this, 0x63, it, PI_FLAGS) },
        true,
      )
      .setShowWhen(false)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setSound(null)
      .setVibrate(null)
      .build()
  }

  private fun buildPendingServiceIntent(intent: Intent, requestCode: Int): PendingIntent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(this, requestCode, intent, PI_FLAGS)
    } else {
      PendingIntent.getService(this, requestCode, intent, PI_FLAGS)
    }
  }

  private fun buildPresetLoadFailedNotification(alarmTriggerTime: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID_PRIMING)
      .setSmallIcon(R.drawable.ic_baseline_alarm_24)
      .setContentTitle(getString(R.string.alarm))
      .setContentText(getString(R.string.alarm_ringer_preset_load_error))
      .setSubText(alarmTriggerTime)
      .setShowWhen(true)
      .setAutoCancel(true)
      .build()
  }

  private suspend fun dismiss(alarmId: Int, isSnoozed: Boolean) {
    Log.d(LOG_TAG, "dismiss: reporting alarm trigger")
    alarmRepository.reportTrigger(alarmId, isSnoozed)

    vibrator.cancel()
    volumeFadeJob?.cancelAndJoin()
    playbackServiceController.pause(true)
    playbackServiceController.setAudioUsage(SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA)
    playbackServiceController.setVolume(1F)
    audioFocusManager.abandonFocus()
    defaultRingtonePlayer?.release()

    uiController.dismiss()
    stopService()
  }

  private fun buildMissedAlarmNotification(alarmTriggerTime: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID_MISSED)
      .setSmallIcon(R.drawable.ic_baseline_alarm_24)
      .setContentTitle(getString(R.string.alarm_missed))
      .setContentText(alarmTriggerTime)
      .setShowWhen(true)
      .setAutoCancel(true)
      .build()
  }

  private fun stopService() {
    if (wakeLock.isHeld) wakeLock.release()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun playDefaultRingtone(initialVolume: Float) {
    defaultRingtonePlayer?.release()
    val ringtoneUri = getDefaultAlarmRingtoneUri() ?: return
    defaultRingtonePlayer = MediaPlayer().apply {
      isLooping = true
      setDataSource(this@AlarmRingerService, ringtoneUri)
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setLegacyStreamType(AudioManager.STREAM_ALARM)
          .build()
      )
      setVolume(initialVolume, initialVolume)
      prepare()
    }

    audioFocusManager.requestFocus()
  }

  private fun getDefaultAlarmRingtoneUri(): Uri? {
    return RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
  }

  companion object {
    private const val LOG_TAG = "AlarmRingerService"
    private const val ACTION_RING = "ring"
    private const val ACTION_SNOOZE = "snooze"
    private const val ACTION_DISMISS = "dismiss"
    private const val EXTRA_ALARM_ID = "alarmId"

    private const val CHANNEL_ID_RINGER = "com.github.ashutoshgngwr.noice.alarms"
    private const val CHANNEL_ID_PRIMING = "com.github.ashutoshgngwr.noice.alarmPriming"
    private const val CHANNEL_ID_MISSED = "com.github.ashutoshgngwr.noice.missedAlarms"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val NOTIFICATION_ID_ALARM = 0x4

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val NOTIFICATION_ID_MISSED = 0x5

    private val DEFAULT_VIBRATION_PATTERN = longArrayOf(500, 500, 500, 500, 500)
    private val PI_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else PendingIntent.FLAG_UPDATE_CURRENT

    fun buildRingIntent(context: Context, alarmId: Int): Intent {
      return Intent(context, AlarmRingerService::class.java)
        .setAction(ACTION_RING)
        .putExtra(EXTRA_ALARM_ID, alarmId)
    }

    fun buildSnoozeIntent(context: Context, alarmId: Int): Intent {
      return Intent(context, AlarmRingerService::class.java)
        .setAction(ACTION_SNOOZE)
        .putExtra(EXTRA_ALARM_ID, alarmId)
    }

    fun buildDismissIntent(context: Context, alarmId: Int): Intent {
      return Intent(context, AlarmRingerService::class.java)
        .setAction(ACTION_DISMISS)
        .putExtra(EXTRA_ALARM_ID, alarmId)
    }
  }

  interface UiController {
    fun buildShowIntent(alarmId: Int, alarmLabel: String?, alarmTriggerTime: String): Intent
    fun dismiss()
  }
}
