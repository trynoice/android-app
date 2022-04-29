package com.github.ashutoshgngwr.noice.ext

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [getMutableStringSet] returns a mutable copy of the original [Set]<[String]> returned by
 * [SharedPreferences.getStringSet]. If the [Set] isn't present in the [SharedPreferences], it
 * returns an empty [MutableSet].
 *
 * @return [MutableSet]<[String]>
 */
fun SharedPreferences.getMutableStringSet(key: String): MutableSet<String> {
  return getStringSet(key, null)?.toMutableSet() ?: mutableSetOf()
}

/**
 * Returns a [callback Flow][callbackFlow] that emits a key from [this] [SharedPreferences] every
 * time its value changes.
 */
fun SharedPreferences.keysFlow(): Flow<String> = callbackFlow {
  val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
    key?.also { trySend(it) }
  }
  registerOnSharedPreferenceChangeListener(listener)
  awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
