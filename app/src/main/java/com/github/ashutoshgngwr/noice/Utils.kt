package com.github.ashutoshgngwr.noice

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * [Utils] contains all the convenience APIs
 */
object Utils {

  /**
   * convenience method to add a fresh [Gson] instance to the context using a lambda.
   */
  inline fun <T> withGson(crossinline f: (Gson) -> T) =
    GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().let { f(it) }

  /**
   * convenience method to add a default [SharedPreferences] to the context using a lambda.
   */
  inline fun <T> withDefaultSharedPreferences(
    ctx: Context,
    crossinline block: (SharedPreferences) -> T
  ): T {
    return block.invoke(PreferenceManager.getDefaultSharedPreferences(ctx))
  }
}
