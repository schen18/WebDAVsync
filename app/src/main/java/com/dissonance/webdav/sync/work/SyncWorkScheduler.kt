package com.dissonance.webdav.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Utility for enqueuing WorkManager jobs with appropriate network constraints,
 * battery awareness, and exponential backoff retry.
 */
object SyncWorkScheduler {

  private const val UNIQUE_WORK_PUSH_DIRTY = "work_push_dirty_files"
  private const val UNIQUE_WORK_PERIODIC_SYNC = "work_periodic_sync"

  /**
   * Immediately schedule a background push for dirty files with network connectivity constraint.
   */
  fun scheduleImmediatePush(context: Context) {
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val pushRequest = OneTimeWorkRequestBuilder<PushDirtyFilesWorker>()
      .setConstraints(constraints)
      .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        15,
        TimeUnit.SECONDS
      )
      .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      UNIQUE_WORK_PUSH_DIRTY,
      ExistingWorkPolicy.REPLACE,
      pushRequest
    )
  }

  /**
   * Schedule periodic background full sync of all due folder pairs.
   * Runs every 15 minutes; the engine itself honors each pair's interval,
   * Wi-Fi/charging/battery constraints and skip windows.
   */
  fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 15) {
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val periodicRequest = PeriodicWorkRequestBuilder<FullSyncWorker>(
      intervalMinutes.coerceAtLeast(15),
      TimeUnit.MINUTES
    )
      .setConstraints(constraints)
      .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        30,
        TimeUnit.SECONDS
      )
      .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      UNIQUE_WORK_PERIODIC_SYNC,
      ExistingPeriodicWorkPolicy.KEEP,
      periodicRequest
    )
  }

  /**
   * Cancel all pending work.
   */
  fun cancelAllWork(context: Context) {
    WorkManager.getInstance(context.applicationContext).cancelAllWork()
  }
}
