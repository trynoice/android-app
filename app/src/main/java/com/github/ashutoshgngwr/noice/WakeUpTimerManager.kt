package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.Utils.withGson
import com.github.ashutoshgngwr.noice.sound.Preset
import com.google.gson.annotations.Expose

object WakeUpTimerManager {

  private const val SELECTED_PRESET_ID = "selectedPresetID"

  /**
   * [Timer] declares fields necessary to schedule a Wake-up timer.
   */
  data class Timer(
    @Expose var presetID: String,
    @Expose var atMillis: Long,
    @Expose var shouldUpdateMediaVolume: Boolean,
    @Expose var mediaVolume: Int
  )

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  const val PREF_WAKE_UP_TIMER = "wake_up_timer"

  private const val RC_WAKE_UP_TIMER = 0x39
  private const val RC_MAIN_ACTIVITY = 0x40

  private inline fun <T> withAlarmManager(ctx: Context, crossinline block: (AlarmManager) -> T): T {
    return block.invoke(
      requireNotNull(ContextCompat.getSystemService(ctx, AlarmManager::class.java))
    )
  }

  private fun getPendingIntentForService(context: Context, timer: Timer?): PendingIntent {
    val intent = Intent(context, MediaPlayerService::class.java)
    intent.action = MediaPlayerService.ACTION_PLAY_PRESET
    timer?.also {
      intent.putExtra(MediaPlayerService.EXTRA_PRESET_ID, it.presetID)

      if (it.shouldUpdateMediaVolume) {
        intent.putExtra(MediaPlayerService.EXTRA_DEVICE_MEDIA_VOLUME, it.mediaVolume)
      }
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(
        context, RC_WAKE_UP_TIMER, intent, PendingIntent.FLAG_UPDATE_CURRENT
      )
    } else {
      PendingIntent.getService(context, RC_WAKE_UP_TIMER, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
  }

  private fun getPendingIntentForActivity(context: Context) =
    Intent(context, MainActivity::class.java).let {
      it.putExtra(MainActivity.EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.wake_up_timer)
      PendingIntent.getActivity(context, RC_MAIN_ACTIVITY, it, PendingIntent.FLAG_UPDATE_CURRENT)
    }

  /**
   * [set] schedules the given [Timer] using Android's [AlarmManager]. It also persists the [Timer]
   * instance onto device storage to be able return it later upon querying.
   */
  fun set(context: Context, timer: Timer) {
    if (timer.atMillis < System.currentTimeMillis()) {
      return
    }

    if (Preset.findByID(context, timer.presetID) == null) {
      return
    }

    withGson {
      PreferenceManager.getDefaultSharedPreferences(context).edit {
        putString(PREF_WAKE_UP_TIMER, it.toJson(timer))
      }
    }

    withAlarmManager(context) {
      it.setAlarmClock(
        AlarmManager.AlarmClockInfo(timer.atMillis, getPendingIntentForActivity(context)),
        getPendingIntentForService(context, timer)
      )
    }
  }

  /**
   * [cancel] cancels the last scheduled timer.
   */
  fun cancel(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      remove(PREF_WAKE_UP_TIMER)
    }

    withAlarmManager(context) {
      // don't need concrete timer value for cancelling the alarm.
      it.cancel(getPendingIntentForService(context, null))
    }
  }

  /**
   * [get] returns the last scheduled timer if found in the persisted state. Returns null otherwise.
   */
  fun get(context: Context): Timer? {
    val timer = PreferenceManager.getDefaultSharedPreferences(context)
      .getString(PREF_WAKE_UP_TIMER, null)

    return withGson { it.fromJson(timer, Timer::class.java) }
  }

  /**
   * [savePresetID] saves the ID of the selected preset by the user
   */
  fun savePresetID(context: Context, selectedPresetID: String) {
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    with(sharedPrefs.edit()) {
      putString(SELECTED_PRESET_ID, selectedPresetID)
      apply()
    }
  }

  /**
   * [removePresetIDFromSharedPrefs] remove the ID of the selected preset from Shared Preferences if exists.
   */
  fun removePresetIDFromSharedPrefs(context: Context, removePresetID: String) {
    if (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("selectedPresetID", null) == removePresetID
    ) {
      with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
        clear()
        apply()
      }
    }
  }

  /**
   * [getSharedPrefsSelectedPresetID] return the saved ID of the selected preset. Returns null otherwise.
   */
  fun getSharedPrefsSelectedPresetID(context: Context): String? {
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPrefs.getString(SELECTED_PRESET_ID, null)
  }

  /**
   * [BootReceiver] ensures that a scheduled [Timer] is able to persist across device restarts.
   */
  class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
      if (Intent.ACTION_BOOT_COMPLETED != intent?.action) {
        return
      }

      val timer = get(context) ?: return
      if (timer.atMillis > System.currentTimeMillis()) {
        set(context, timer)
      }
    }
  }
}
