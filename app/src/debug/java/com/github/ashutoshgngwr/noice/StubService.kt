package com.github.ashutoshgngwr.noice

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * A stub service for testing components need a [Service] dependency.
 */
class StubService : Service() {

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
