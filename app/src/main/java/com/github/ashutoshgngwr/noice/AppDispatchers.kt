package com.github.ashutoshgngwr.noice

import kotlinx.coroutines.CoroutineDispatcher

data class AppDispatchers(
  val main: CoroutineDispatcher,
  val io: CoroutineDispatcher,
)
