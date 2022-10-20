package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.activity.AlarmRingerActivity
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeUpTimerManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val presetRepository: PresetRepository,
  private val gson: Gson,
) {

  private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  private inline fun <T> withAlarmManager(ctx: Context, crossinline block: (AlarmManager) -> T): T {
    return with(requireNotNull(ctx.getSystemService()), block)
  }

  private fun getPendingIntentForActivity() = Intent(context, MainActivity::class.java).let {
    it.putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.home_wake_up_timer)
    var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
    }

    PendingIntent.getActivity(context, RC_MAIN_ACTIVITY, it, piFlags)
  }

  /**
   * [set] schedules the given [Timer] using Android's [AlarmManager]. It also persists the [Timer]
   * instance onto device storage to be able return it later upon querying.
   */
  fun set(timer: Timer) {
    if (timer.atMillis < System.currentTimeMillis()) {
      return
    }

    if (presetRepository.get(timer.presetID) == null) {
      return
    }

    prefs.edit { putString(PREF_WAKE_UP_TIMER, gson.toJson(timer)) }
    withAlarmManager(context) {
      it.setAlarmClock(
        AlarmManager.AlarmClockInfo(timer.atMillis, getPendingIntentForActivity()),
        AlarmRingerActivity.getPendingIntent(context, timer.presetID)
      )
    }
  }

  /**
   * [cancel] cancels the last scheduled timer.
   */
  fun cancel() {
    prefs.edit { remove(PREF_WAKE_UP_TIMER) }
    withAlarmManager(context) {
      // don't need concrete timer value for cancelling the alarm.
      it.cancel(AlarmRingerActivity.getPendingIntent(context, null))
    }
  }

  /**
   * [get] returns the last scheduled timer if found in the persisted state. Returns null otherwise.
   */
  fun get(): Timer? {
    val timer = prefs.getString(PREF_WAKE_UP_TIMER, null)
    return gson.fromJson(timer, Timer::class.java)
  }

  /**
   * [saveLastUsedPresetID] saves the ID of the preset last used by the user in a Wake-up timer.
   */
  fun saveLastUsedPresetID(selectedPresetID: String?) {
    prefs.edit { putString(PREF_LAST_USED_PRESET_ID, selectedPresetID) }
  }

  /**
   * [getLastUsedPresetID] return the saved ID of the last selected preset. Returns null otherwise.
   */
  fun getLastUsedPresetID(): String? {
    val lastSelectedPresetID = prefs.getString(PREF_LAST_USED_PRESET_ID, null)
    // ensure that preset with given ID exists in preferences.
    return presetRepository.get(lastSelectedPresetID)?.id
  }

  /**
   * reschedules existing timer when invoked, e.g. on device reboot.
   */
  fun rescheduleExistingTimer() {
    val timer = get() ?: return
    if (timer.atMillis > System.currentTimeMillis()) {
      set(timer)
    }
  }

  /**
   * [Timer] declares fields necessary to schedule a Wake-up timer.
   */
  data class Timer(var presetID: String, var atMillis: Long)

  /**
   * [BootReceiver] ensures that a scheduled [Timer] is able to persist across device restarts.
   */
  @AndroidEntryPoint
  class BootReceiver : BroadcastReceiver() {

    @set:Inject
    internal lateinit var wakeUpTimerManager: WakeUpTimerManager

    override fun onReceive(context: Context, intent: Intent?) {
      if (Intent.ACTION_BOOT_COMPLETED != intent?.action) {
        return
      }

      wakeUpTimerManager.rescheduleExistingTimer()
    }
  }

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_WAKE_UP_TIMER = "wake_up_timer"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_LAST_USED_PRESET_ID = "last_used_preset_id"

    private const val RC_MAIN_ACTIVITY = 0x40
  }
}
