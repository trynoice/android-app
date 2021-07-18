package com.github.ashutoshgngwr.noice.ext

import android.content.SharedPreferences

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
