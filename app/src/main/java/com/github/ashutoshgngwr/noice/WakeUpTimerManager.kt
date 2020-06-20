package com.github.ashutoshgngwr.noice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import com.github.ashutoshgngwr.noice.Utils.withDefaultSharedPreferences
import com.github.ashutoshgngwr.noice.Utils.withGson
import com.google.gson.annotations.Expose

object WakeUpTimerManager {

  /**
   * [Timer] declares fields necessary to schedule a Wake-up timer.
   */
  data class Timer(@Expose var presetName: String, @Expose var atMillis: Long)

  private const val PREF_WAKE_UP_TIMER = "wake_up_timer"
  private const val RC_WAKE_UP_TIMER = 0x39

  private inline fun <T> withAlarmManager(ctx: Context, crossinline block: (AlarmManager) -> T): T {
    return block.invoke(
      requireNotNull(ContextCompat.getSystemService(ctx, AlarmManager::class.java))
    )
  }

  private fun getPendingIntentForService(context: Context, intent: Intent) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(
        context, RC_WAKE_UP_TIMER, intent, PendingIntent.FLAG_UPDATE_CURRENT
      )
    } else {
      PendingIntent.getService(context, RC_WAKE_UP_TIMER, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

  private fun getPendingIntent(context: Context, timer: Timer) =
    Intent(context, MediaPlayerService::class.java)
      .let {
        it.action = MediaPlayerService.ACTION_PLAY_PRESET
        it.putExtra(MediaPlayerService.EXTRA_PRESET_NAME, timer.presetName)
        getPendingIntentForService(context, it)
      }

  /**
   * [set] schedules the given [Timer] using Android's [AlarmManager]. It also persists the [Timer]
   * instance onto device storage to be able return it later upon querying.
   */
  fun set(context: Context, timer: Timer) {
    if (timer.atMillis < System.currentTimeMillis()) {
      return
    }

    withDefaultSharedPreferences(context) { prefs ->
      withGson {
        prefs.edit().putString(PREF_WAKE_UP_TIMER, it.toJson(timer)).apply()
      }

      withAlarmManager(context) {
        AlarmManagerCompat.setExactAndAllowWhileIdle(
          it, AlarmManager.RTC_WAKEUP, timer.atMillis, getPendingIntent(context, timer)
        )
      }
    }
  }

  /**
   * [cancel] cancels the last scheduled timer.
   */
  fun cancel(context: Context) {
    withDefaultSharedPreferences(context) {
      it.edit().remove(PREF_WAKE_UP_TIMER).apply()
    }

    withAlarmManager(context) {
      // don't need concrete timer value for cancelling the alarm.
      it.cancel(getPendingIntent(context, Timer("", 0)))
    }
  }

  /**
   * [get] returns the last scheduled timer if found in the persisted state. Returns null otherwise.
   */
  fun get(context: Context): Timer? {
    return withGson {
      withDefaultSharedPreferences(context) { prefs ->
        it.fromJson(prefs.getString(PREF_WAKE_UP_TIMER, null), Timer::class.java)
      }
    }
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
