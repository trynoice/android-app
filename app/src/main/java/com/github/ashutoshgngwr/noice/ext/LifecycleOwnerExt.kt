package com.github.ashutoshgngwr.noice.ext

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Convenience method to do the following:
 *
 * ```kt
 * lifecycleOwner.lifecycleScope.launch {
 *   lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
 *     // stuff
 *   }
 * }
 * ```
 */
fun LifecycleOwner.launchAndRepeatOnStarted(block: suspend CoroutineScope.() -> Unit): Job {
  return lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}
