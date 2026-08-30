package com.dissonance.webdav.sync.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dissonance.webdav.data.repository.SyncRepository
import com.dissonance.webdav.sync.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/**
 * Periodic background worker performing a full delta sync of all folder pairs whose
 * sync interval has elapsed. Each pair's Wi-Fi/charging/battery constraints are
 * enforced by the SyncEngine at sync time.
 */
class FullSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  private val TAG = "FullSyncWorker"

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    Log.d(TAG, "Starting periodic full sync...")
    // Own scope, scheduler disabled: this engine lives only for the duration of the run.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val repository = SyncRepository(
        context = applicationContext,
        scope = scope,
        autoStartScheduler = false
      )
      val results = repository.syncEngine.syncDuePairs()
      val errors = results.count { it is SyncResult.Failure }
      Log.d(TAG, "Periodic sync finished: ${results.size} pair(s) synced, $errors error(s).")
      if (errors > 0) Result.retry() else Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Periodic sync failed", e)
      Result.retry()
    } finally {
      scope.cancel()
    }
  }
}
