package com.dissonance.webdav

import android.app.Application
import android.util.Log
import com.dissonance.webdav.sync.work.SyncWorkScheduler

/**
 * Application entry point: registers the periodic background sync so folder pairs
 * keep syncing automatically even when the app UI is not open.
 */
class WebDavSyncApp : Application() {

  private val TAG = "WebDavSyncApp"

  override fun onCreate() {
    super.onCreate()
    try {
      SyncWorkScheduler.schedulePeriodicSync(this)
    } catch (e: Exception) {
      // WorkManager may be unavailable in rare instrumentation environments.
      Log.w(TAG, "Could not schedule periodic sync", e)
    }
  }
}
