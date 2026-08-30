package com.dissonance.webdav.sync.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dissonance.webdav.data.db.AppDatabase
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.LogActionType
import com.dissonance.webdav.data.model.LogStatus
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.provider.SyncHubContract
import com.dissonance.webdav.sync.EnergyMonitor
import com.dissonance.webdav.sync.LocalFileManager
import com.dissonance.webdav.sync.WebdavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager Worker responsible for pushing locally modified "dirty" files
 * to WebDAV remote servers with network constraints and exponential backoff retry.
 */
class PushDirtyFilesWorker(
  private val appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  private val TAG = "PushDirtyFilesWorker"

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    Log.d(TAG, "Starting PushDirtyFilesWorker job...")
    val database = AppDatabase.getInstance(appContext)
    val localFileManager = LocalFileManager(appContext)
    val webdavClient = WebdavClient()
    val energyMonitor = EnergyMonitor(appContext)

    val fileDao = database.syncedFileRecordDao()
    val pairDao = database.syncPairDao()
    val serverDao = database.webdavServerDao()
    val logDao = database.syncLogDao()

    val dirtyRecords = try {
      fileDao.getAllDirtyRecords()
    } catch (e: Exception) {
      Log.e(TAG, "Error querying dirty records", e)
      return@withContext Result.retry()
    }

    if (dirtyRecords.isEmpty()) {
      Log.d(TAG, "No dirty files pending push. Finished.")
      return@withContext Result.success()
    }

    Log.d(TAG, "Found ${dirtyRecords.size} dirty files to push upstream.")
    var failureCount = 0
    var deferredCount = 0

    for (record in dirtyRecords) {
      val pair = pairDao.getPairById(record.syncPairId)
      if (pair == null || !pair.isEnabled) {
        continue
      }
      val server = serverDao.getServerById(pair.serverId)
      if (server == null) {
        continue
      }
      val folder = localFileManager.folderFor(pair)
      if (folder == null || !folder.hasAccess()) {
        Log.i(TAG, "Pair ${pair.name} has no usable folder grant; deferring ${record.fileName}.")
        deferredCount++
        continue
      }

      // Honor the pair's energy/network safeguards; leave files dirty for a later run.
      energyMonitor.refreshEnergyState()
      val (canRun, reason) = energyMonitor.canSync(pair.wifiOnly, pair.chargingOnly, pair.batterySaverThreshold)
      if (!canRun) {
        Log.i(TAG, "Pair ${pair.name} not eligible for push: $reason")
        deferredCount++
        continue
      }

      // Records store tree-relative paths; the remote path mirrors the pair folder structure.
      val remotePath = "${pair.remoteRelativePath.trim().trimEnd('/')}/${record.relativePath.trim().trimStart('/')}"

      val isDelete = record.dirtyAction.equals("DELETE", ignoreCase = true)

      // Upload pushes only make sense for pairs that send to the server.
      if (!isDelete && pair.syncDirection == SyncDirection.REMOTE_TO_LOCAL) {
        fileDao.clearDirtyById(record.id)
        continue
      }

      try {
        if (isDelete) {
          val deleteResult = webdavClient.deleteFile(server, remotePath)
          if (deleteResult.isSuccess) {
            fileDao.deleteFile(record.syncPairId, record.relativePath)
            logDao.insertLog(
              SyncLog(
                syncPairId = pair.id,
                pairName = pair.name,
                actionType = LogActionType.DELETE,
                fileName = record.fileName,
                relativePath = record.relativePath,
                status = LogStatus.SUCCESS,
                message = "Pushed remote delete for ${record.fileName}"
              )
            )
          } else {
            failureCount++
            logDao.insertLog(
              SyncLog(
                syncPairId = pair.id,
                pairName = pair.name,
                actionType = LogActionType.ERROR,
                fileName = record.fileName,
                relativePath = record.relativePath,
                status = LogStatus.FAILED,
                message = "Remote delete failed: ${deleteResult.exceptionOrNull()?.message}"
              )
            )
          }
        } else {
          val fileBytes = folder.readFile(record.relativePath)
          if (fileBytes != null) {
            val parentDir = remotePath.substringBeforeLast('/')
            if (parentDir.isNotEmpty() && parentDir != pair.remoteRelativePath.trim().trimEnd('/')) {
              webdavClient.ensureRemoteDirectory(server, parentDir)
            }
            val uploadResult = webdavClient.uploadFile(server, remotePath, fileBytes)
            if (uploadResult.isSuccess) {
              fileDao.insertOrUpdateFile(
                record.copy(
                  isDirty = false,
                  dirtyAction = "",
                  syncState = FileSyncState.SYNCED,
                  etag = uploadResult.getOrDefault(record.etag),
                  remoteLastModified = System.currentTimeMillis(),
                  localLastModified = System.currentTimeMillis(),
                  fileSize = fileBytes.size.toLong()
                )
              )
              logDao.insertLog(
                SyncLog(
                  syncPairId = pair.id,
                  pairName = pair.name,
                  actionType = LogActionType.UPLOAD,
                  fileName = record.fileName,
                  relativePath = record.relativePath,
                  bytesTransferred = fileBytes.size.toLong(),
                  status = LogStatus.SUCCESS,
                  message = "Upstream push complete (${fileBytes.size} bytes)"
                )
              )
            } else {
              failureCount++
              logDao.insertLog(
                SyncLog(
                  syncPairId = pair.id,
                  pairName = pair.name,
                  actionType = LogActionType.ERROR,
                  fileName = record.fileName,
                  relativePath = record.relativePath,
                  status = LogStatus.FAILED,
                  message = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                )
              )
            }
          } else {
            // File was removed locally after being flagged: drop the stale record.
            fileDao.clearDirtyById(record.id)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error pushing record ${record.relativePath}", e)
        failureCount++
      }
    }

    // Notify observers
    try {
      appContext.contentResolver.notifyChange(SyncHubContract.DIRTY_FILES_URI, null)
      appContext.contentResolver.notifyChange(SyncHubContract.FILES_URI, null)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to notify change", e)
    }

    when {
      failureCount > 0 -> {
        Log.w(TAG, "$failureCount file pushes failed. Scheduling retry with exponential backoff.")
        Result.retry()
      }
      deferredCount > 0 -> {
        // Constraint-blocked files stay dirty; no point burning retries.
        Log.i(TAG, "$deferredCount pushes deferred by pair constraints.")
        Result.success()
      }
      else -> {
        Log.d(TAG, "All dirty files pushed successfully.")
        Result.success()
      }
    }
  }
}
