package com.github.ashutoshgngwr.noice

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom implementation of [AndroidJUnitRunner] to replace [NoiceApplication] with
 * [HiltTestApplication] when running instrumented tests.
 */
@Suppress("unused") // referenced in `app/build.gradle`
class InstrumentedTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
    return super.newApplication(cl, HiltTestApplication::class.java.name, context)
  }
}
