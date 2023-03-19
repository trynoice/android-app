package com.github.ashutoshgngwr.noice.ext

import android.os.Build
import android.os.Bundle
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

// TODO: remove this extension when a replacement method is available the Androidx Compat libraries.
fun <T : java.io.Serializable> Bundle.getSerializableCompat(
  key: String,
  kClass: KClass<T>
): T? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getSerializable(key, kClass.java)
  } else {
    @Suppress("DEPRECATION")
    getSerializable(key)?.let { kClass.safeCast(it) }
  }
}
