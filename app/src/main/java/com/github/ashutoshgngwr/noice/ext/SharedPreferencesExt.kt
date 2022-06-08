package com.github.ashutoshgngwr.noice.ext

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart

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
 * Returns a [Flow] that emits the given [key] every time its value changes in this
 * [SharedPreferences]. It also emits the key as soon as a collector starts collecting.
 */
fun SharedPreferences.keyFlow(key: String): Flow<String> {
  return keysFlow()
    .filter { it == key }
    .onStart { emit(key) }
}

/**
 * Returns a [callback Flow][callbackFlow] that emits a key from this [SharedPreferences] every time
 * its value changes.
 */
private fun SharedPreferences.keysFlow(): Flow<String> = callbackFlow {
  val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
    key?.also { trySend(it) }
  }
  registerOnSharedPreferenceChangeListener(listener)
  awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
