package com.dissonance.webdav.sync

import com.dissonance.webdav.data.dao.FileConflictDao
import com.dissonance.webdav.data.dao.SyncLogDao
import com.dissonance.webdav.data.dao.SyncPairDao
import com.dissonance.webdav.data.dao.SyncedFileRecordDao
import com.dissonance.webdav.data.dao.WebdavServerDao
import com.dissonance.webdav.data.model.ConflictPolicy
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.LogActionType
import com.dissonance.webdav.data.model.LogStatus
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncStatus
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.model.WebdavServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncEngine(
  private val syncPairDao: SyncPairDao,
  private val serverDao: WebdavServerDao,
  private val logDao: SyncLogDao,
  private val conflictDao: FileConflictDao,
  private val fileRecordDao: SyncedFileRecordDao,
  private val remoteStore: RemoteStore,
  private val folderFor: (SyncPair) -> LocalFolderAccess?,
  val energyMonitor: EnergyMonitor,
  private val scope: CoroutineScope,
  autoStartScheduler: Boolean = true
) {

  private val _syncProgress = MutableStateFlow(SyncProgress())
  val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

  private var backgroundSyncJob: Job? = null

  // Guards against manual + background syncs interleaving and double-transferring files.
  private val syncMutex = Mutex()

  init {
    if (autoStartScheduler) {
      startBackgroundScheduler()
    }
  }

  fun startBackgroundScheduler() {
    backgroundSyncJob?.cancel()
    backgroundSyncJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        delay(60_000) // check every minute
        try {
          energyMonitor.refreshEnergyState()
          syncDuePairs()
        } catch (_: Exception) {}
      }
    }
  }

  suspend fun syncAll(): List<SyncResult> = withContext(Dispatchers.IO) {
    val enabledPairs = syncPairDao.getEnabledSyncPairs().first()
    enabledPairs.map { syncPair(it.id) }
  }

  /**
   * Sync only pairs whose configured interval has elapsed since the last run.
   * Used by the in-app scheduler and the periodic background worker.
   */
  suspend fun syncDuePairs(): List<SyncResult> = withContext(Dispatchers.IO) {
    val enabledPairs = syncPairDao.getEnabledSyncPairs().first()
    val now = System.currentTimeMillis()
    enabledPairs.mapNotNull { pair ->
      val intervalMillis = pair.syncIntervalMinutes * 60 * 1000L
      if (intervalMillis > 0 && (now - pair.lastSyncTimestamp) >= intervalMillis) {
        val (canRun, _) = energyMonitor.canSync(pair.wifiOnly, pair.chargingOnly, pair.batterySaverThreshold)
        if (canRun) syncPair(pair.id) else null
      } else {
        null
      }
    }
  }

  suspend fun syncPair(pairId: Long): SyncResult = syncMutex.withLock {
    withContext(Dispatchers.IO) {
      val pair = syncPairDao.getSyncPairById(pairId)
        ?: return@withContext SyncResult.Failure("Folder pair not found")

      energyMonitor.refreshEnergyState()
      val (canRun, reason) = energyMonitor.canSync(pair.wifiOnly, pair.chargingOnly, pair.batterySaverThreshold)
      if (!canRun) {
        syncPairDao.updateSyncPair(
          pair.copy(
            lastSyncStatus = if (reason.contains("Battery")) SyncStatus.PAUSED_BATTERY else SyncStatus.PAUSED_NETWORK,
            lastSyncMessage = reason
          )
        )
        logAction(pair, LogActionType.SKIP, "-", pair.localRelativePath, 0L, LogStatus.SKIPPED, reason)
        return@withContext SyncResult.Paused(reason)
      }

      val server = serverDao.getServerById(pair.serverId)
      if (server == null) {
        return@withContext failPair(pair, "Server for pair '${pair.name}' no longer exists (serverId=${pair.serverId}).")
      }

      val folder = folderFor(pair)
      if (folder == null) {
        return@withContext failPair(pair, "No device folder selected for '${pair.name}'. Pick a folder in the Pairs tab.")
      }
      if (!folder.hasAccess()) {
        return@withContext failPair(pair, "Access to the device folder of '${pair.name}' was revoked. Re-select the folder in the Pairs tab.")
      }

      _syncProgress.value = SyncProgress(
        isRunning = true,
        currentPairName = pair.name,
        currentPhase = "Scanning files...",
        progressPercent = 0.05f
      )
      syncPairDao.updateSyncPair(pair.copy(lastSyncStatus = SyncStatus.RUNNING, lastSyncMessage = "Scanning..."))

      try {
        syncPairInternal(pair, server, folder)
      } catch (e: Exception) {
        val message = "Sync failed: ${e.message}"
        syncPairDao.updateSyncPair(
          pair.copy(
            lastSyncTimestamp = System.currentTimeMillis(),
            lastSyncStatus = SyncStatus.ERROR,
            lastSyncMessage = "Error: ${e.message}"
          )
        )
        logAction(pair, LogActionType.ERROR, "-", pair.localRelativePath, 0L, LogStatus.FAILED, message)
        _syncProgress.value = SyncProgress(isRunning = false, currentPhase = "Sync error")
        SyncResult.Failure(e.message ?: "Unknown error")
      } finally {
        pruneOldLogs()
      }
    }
  }

  private suspend fun failPair(pair: SyncPair, message: String): SyncResult {
    syncPairDao.updateSyncPair(pair.copy(lastSyncStatus = SyncStatus.ERROR, lastSyncMessage = message))
    logAction(pair, LogActionType.ERROR, "-", pair.localRelativePath, 0L, LogStatus.FAILED, message)
    return SyncResult.Failure(message)
  }

  private suspend fun syncPairInternal(pair: SyncPair, server: WebdavServer, folder: LocalFolderAccess): SyncResult {
    // Step 1: scan the granted device folder (recursive). Keys are tree-relative paths.
    val localByPath = folder.listFiles()
      .filter { !it.isDirectory }
      .filter { !isExcluded(it.name, pair.excludePatterns) }
      .associateBy { it.relativePath }

    // Step 2: scan the remote tree, keyed by path relative to the pair's remote root.
    val remoteAll = remoteStore.listFolderDeep(server, pair.remoteRelativePath).getOrElse { e -> throw e }
    val remoteByPath = remoteAll
      .filter { !it.isDirectory }
      .filter { !isExcluded(it.name, pair.excludePatterns) }
      .associateBy { suffixOf(it.path, pair.remoteRelativePath) }

    // Step 3: snapshot from the last successful sync is the baseline for delta detection.
    val previousRecords = fileRecordDao.getFilesForPairSync(pair.id)
      .associateBy { it.relativePath }

    var uploadedCount = 0
    var downloadedCount = 0
    var deletedCount = 0
    var conflictsCount = 0
    var skippedCount = 0
    var totalBytesMoved = 0L

    val allPaths = (localByPath.keys + remoteByPath.keys + previousRecords.keys).distinct()
    val totalSteps = allPaths.size.coerceAtLeast(1)
    var filesProcessed = 0

    for (path in allPaths) {
      filesProcessed++
      val localItem = localByPath[path]
      val remoteItem = remoteByPath[path]
      val record = previousRecords[path]

      val remoteRelPath = joinRemote(pair.remoteRelativePath, path)

      _syncProgress.value = _syncProgress.value.copy(
        currentFileName = path.substringAfterLast('/'),
        progressPercent = 0.1f + (filesProcessed.toFloat() / totalSteps) * 0.85f,
        filesProcessed = filesProcessed,
        totalFiles = totalSteps,
        currentPhase = "Synchronizing ${path.substringAfterLast('/')}"
      )

      when {
        localItem != null && remoteItem != null -> {
          val localChanged = record == null || record.isDirty ||
            localItem.lastModified != record.localLastModified ||
            localItem.size != record.fileSize
          val remoteChanged = isRemoteChanged(record, remoteItem)

          if (record == null && localItem.size == remoteItem.size) {
            // First sight of this file on both sides with identical size: adopt as in-sync baseline.
            saveRecord(buildSyncedRecord(pair, path, localItem, remoteItem, record))
          } else when {
            !localChanged && !remoteChanged -> {
              saveRecord(buildSyncedRecord(pair, path, localItem, remoteItem, record))
            }

            localChanged && !remoteChanged -> {
              if (pair.syncDirection == SyncDirection.REMOTE_TO_LOCAL) {
                // Download mirror: the server is authoritative, restore it over local edits.
                if (downloadFile(pair, server, folder, path, remoteItem)) {
                  downloadedCount++; totalBytesMoved += remoteItem.size
                } else skippedCount++
              } else if (uploadFile(pair, server, folder, path, localItem, record)) {
                uploadedCount++; totalBytesMoved += localItem.size
              } else skippedCount++
            }

            !localChanged && remoteChanged -> {
              if (pair.syncDirection == SyncDirection.LOCAL_TO_REMOTE) {
                // Upload mirror: the device is authoritative, push local over the server copy.
                if (uploadFile(pair, server, folder, path, localItem, record)) {
                  uploadedCount++; totalBytesMoved += localItem.size
                } else skippedCount++
              } else if (downloadFile(pair, server, folder, path, remoteItem)) {
                downloadedCount++; totalBytesMoved += remoteItem.size
              } else skippedCount++
            }

            else -> when (resolveDivergence(pair, server, folder, path, localItem, remoteItem, record)) {
              DivergenceOutcome.CONFLICT_QUEUED -> conflictsCount++
              DivergenceOutcome.UPLOADED -> { uploadedCount++; totalBytesMoved += localItem.size }
              DivergenceOutcome.DOWNLOADED -> { downloadedCount++; totalBytesMoved += remoteItem.size }
              DivergenceOutcome.SKIPPED -> skippedCount++
            }
          }
        }

        localItem != null && remoteItem == null -> {
          if (record != null && record.etag.isNotEmpty() && !record.isDirty) {
            // Was synced to the server before; the server copy was deleted elsewhere.
            // (Dirty records carry pending local edits: push those instead of deleting.)
            if (pair.syncDirection == SyncDirection.LOCAL_TO_REMOTE) {
              if (uploadFile(pair, server, folder, path, localItem, record)) {
                uploadedCount++; totalBytesMoved += localItem.size
              } else skippedCount++
            } else {
              folder.deleteFile(path)
              fileRecordDao.deleteFile(pair.id, path)
              deletedCount++
              logAction(pair, LogActionType.DELETE, localItem.name, path, 0L, LogStatus.SUCCESS,
                "Deleted on device: file was removed on the WebDAV server.")
            }
          } else if (pair.syncDirection == SyncDirection.REMOTE_TO_LOCAL) {
            skippedCount++
            logAction(pair, LogActionType.SKIP, localItem.name, path, 0L, LogStatus.SKIPPED,
              "Skipped: device-only file on a download-only (server mirror) pair.")
          } else if (uploadFile(pair, server, folder, path, localItem, record)) {
            uploadedCount++; totalBytesMoved += localItem.size
          } else skippedCount++
        }

        localItem == null && remoteItem != null -> {
          if (record != null && record.etag.isNotEmpty()) {
            // Was synced before; the device copy was deleted.
            if (pair.syncDirection == SyncDirection.REMOTE_TO_LOCAL) {
              if (downloadFile(pair, server, folder, path, remoteItem)) {
                downloadedCount++; totalBytesMoved += remoteItem.size
              } else skippedCount++
            } else {
              remoteStore.deleteFile(server, remoteRelPath)
              fileRecordDao.deleteFile(pair.id, path)
              deletedCount++
              logAction(pair, LogActionType.DELETE, remoteItem.name, path, 0L, LogStatus.SUCCESS,
                "Deleted on WebDAV server: file was removed on the device.")
            }
          } else if (pair.syncDirection == SyncDirection.LOCAL_TO_REMOTE) {
            skippedCount++
            logAction(pair, LogActionType.SKIP, remoteItem.name, path, 0L, LogStatus.SKIPPED,
              "Skipped: remote-only file on an upload-only (device mirror) pair.")
          } else if (downloadFile(pair, server, folder, path, remoteItem)) {
            downloadedCount++; totalBytesMoved += remoteItem.size
          } else skippedCount++
        }

        else -> {
          // Stale record with no file on either side: drop it.
          fileRecordDao.deleteFile(pair.id, path)
        }
      }
    }

    val finalStatus = when {
      conflictsCount > 0 -> SyncStatus.CONFLICT_DETECTED
      skippedCount > 0 -> SyncStatus.WARNING
      else -> SyncStatus.SUCCESS
    }
    val finalMessage = if (conflictsCount > 0) {
      "$conflictsCount unresolved conflict(s) found"
    } else {
      "Sync completed: $uploadedCount up, $downloadedCount down, $deletedCount deleted" +
        (if (skippedCount > 0) ", $skippedCount skipped" else "") +
        " (${totalBytesMoved / 1024} KB)"
    }

    syncPairDao.updateSyncPair(
      pair.copy(
        lastSyncTimestamp = System.currentTimeMillis(),
        lastSyncStatus = finalStatus,
        lastSyncMessage = finalMessage,
        fileCount = localByPath.size,
        totalBytes = localByPath.values.sumOf { it.size }
      )
    )

    _syncProgress.value = SyncProgress(
      isRunning = false,
      currentPhase = "Sync complete",
      progressPercent = 1.0f,
      filesProcessed = totalSteps,
      totalFiles = totalSteps,
      bytesTransferred = totalBytesMoved
    )

    return if (conflictsCount > 0) {
      SyncResult.ConflictFound(conflictsCount)
    } else {
      SyncResult.Success(uploadedCount, downloadedCount, totalBytesMoved)
    }
  }

  private enum class DivergenceOutcome { CONFLICT_QUEUED, UPLOADED, DOWNLOADED, SKIPPED }

  /** Modified on both sides since the last sync: apply the pair's conflict policy. */
  private suspend fun resolveDivergence(
    pair: SyncPair,
    server: WebdavServer,
    folder: LocalFolderAccess,
    path: String,
    localItem: LocalFileItem,
    remoteItem: RemoteFileItem,
    record: SyncedFileRecord?
  ): DivergenceOutcome {
    val remoteRelPath = joinRemote(pair.remoteRelativePath, path)

    // One-way pairs never truly diverge: the authoritative side simply wins.
    val policy = when (pair.syncDirection) {
      SyncDirection.LOCAL_TO_REMOTE -> ConflictPolicy.DEVICE_WINS
      SyncDirection.REMOTE_TO_LOCAL -> ConflictPolicy.SERVER_WINS
      SyncDirection.TWO_WAY -> pair.conflictPolicy
    }

    return when (policy) {
      ConflictPolicy.ASK_USER -> {
        val localPreview = folder.readText(path).take(300)
        val remotePreview = remoteStore.downloadFile(server, remoteRelPath)
          .getOrNull()
          ?.toString(Charsets.UTF_8)
          ?.take(300) ?: ""
        conflictDao.insertConflict(
          FileConflict(
            syncPairId = pair.id,
            pairName = pair.name,
            fileName = localItem.name,
            relativePath = path,
            localLastModified = localItem.lastModified,
            remoteLastModified = remoteItem.lastModified,
            localSize = localItem.size,
            remoteSize = remoteItem.size,
            localPreview = localPreview,
            remotePreview = remotePreview,
            status = ConflictStatus.PENDING
          )
        )
        logAction(pair, LogActionType.CONFLICT, localItem.name, path, 0L, LogStatus.FAILED,
          "Version conflict detected between device and WebDAV server.")
        saveRecord(buildSyncedRecord(pair, path, localItem, remoteItem, record, FileSyncState.CONFLICT))
        DivergenceOutcome.CONFLICT_QUEUED
      }

      ConflictPolicy.DEVICE_WINS ->
        if (uploadFile(pair, server, folder, path, localItem, record)) {
          DivergenceOutcome.UPLOADED
        } else {
          DivergenceOutcome.SKIPPED
        }

      ConflictPolicy.SERVER_WINS ->
        if (downloadFile(pair, server, folder, path, remoteItem)) {
          DivergenceOutcome.DOWNLOADED
        } else {
          DivergenceOutcome.SKIPPED
        }

      ConflictPolicy.KEEP_BOTH -> {
        val bytes = remoteStore.downloadFile(server, remoteRelPath).getOrNull()
        if (bytes == null) {
          logAction(pair, LogActionType.ERROR, localItem.name, path, 0L, LogStatus.FAILED,
            "Conflict KEEP_BOTH failed: could not download the remote copy.")
          saveRecord(buildSyncedRecord(pair, path, localItem, remoteItem, record, FileSyncState.CONFLICT))
          return DivergenceOutcome.SKIPPED
        }
        val ext = localItem.name.substringAfterLast(".", "")
        val base = localItem.name.substringBeforeLast(".")
        val dir = path.substringBeforeLast('/')
        val renamedPath = if (dir.isNotEmpty()) "$dir/${base}_server_copy.${if (ext.isNotEmpty()) ext else "txt"}"
        else "/${base}_server_copy.${if (ext.isNotEmpty()) ext else "txt"}"
        folder.writeFile(renamedPath, bytes)
        logAction(pair, LogActionType.DOWNLOAD, localItem.name, renamedPath, bytes.size.toLong(), LogStatus.RESOLVED,
          "Conflict resolved: saved remote copy alongside device file.")
        // The device version stays authoritative for the original path.
        if (uploadFile(pair, server, folder, path, localItem, record)) {
          DivergenceOutcome.UPLOADED
        } else {
          DivergenceOutcome.SKIPPED
        }
      }

      ConflictPolicy.LATEST_WINS ->
        if (localItem.lastModified >= remoteItem.lastModified) {
          if (uploadFile(pair, server, folder, path, localItem, record)) {
            DivergenceOutcome.UPLOADED
          } else {
            DivergenceOutcome.SKIPPED
          }
        } else {
          if (downloadFile(pair, server, folder, path, remoteItem)) {
            DivergenceOutcome.DOWNLOADED
          } else {
            DivergenceOutcome.SKIPPED
          }
        }
    }
  }

  /** Upload one file, ensuring the remote parent directory exists. Persists the new baseline record. */
  private suspend fun uploadFile(
    pair: SyncPair,
    server: WebdavServer,
    folder: LocalFolderAccess,
    path: String,
    localItem: LocalFileItem,
    record: SyncedFileRecord?
  ): Boolean = withContext(Dispatchers.IO) {
    val remoteRelPath = joinRemote(pair.remoteRelativePath, path)
    val bytes = folder.readFile(path) ?: ByteArray(0)

    val parent = remoteRelPath.substringBeforeLast('/')
    val remoteRoot = pair.remoteRelativePath.trim().trimEnd('/')
    if (parent.isNotEmpty() && parent != remoteRoot) {
      remoteStore.ensureRemoteDirectory(server, parent)
    }

    val result = remoteStore.uploadFile(server, remoteRelPath, bytes)
    if (result.isSuccess) {
      saveRecord(
        SyncedFileRecord(
          id = record?.id ?: 0L,
          syncPairId = pair.id,
          relativePath = path,
          fileName = localItem.name,
          localLastModified = localItem.lastModified,
          remoteLastModified = System.currentTimeMillis(),
          fileSize = bytes.size.toLong(),
          etag = result.getOrDefault(""),
          syncState = FileSyncState.SYNCED,
          previewSnippet = folder.readText(path).take(120)
        )
      )
      logAction(pair, LogActionType.UPLOAD, localItem.name, path, bytes.size.toLong(), LogStatus.SUCCESS,
        "Uploaded to WebDAV folder.")
      true
    } else {
      logAction(pair, LogActionType.ERROR, localItem.name, path, 0L, LogStatus.FAILED,
        "Upload failed: ${result.exceptionOrNull()?.message}")
      false
    }
  }

  /** Download one file into the granted folder. Persists the new baseline record. */
  private suspend fun downloadFile(
    pair: SyncPair,
    server: WebdavServer,
    folder: LocalFolderAccess,
    path: String,
    remoteItem: RemoteFileItem
  ): Boolean = withContext(Dispatchers.IO) {
    val remoteRelPath = joinRemote(pair.remoteRelativePath, path)
    val existing = fileRecordDao.getFileByPath(pair.id, path)

    val result = remoteStore.downloadFile(server, remoteRelPath)
    val bytes = result.getOrNull()
    if (bytes == null) {
      logAction(pair, LogActionType.ERROR, remoteItem.name, path, 0L, LogStatus.FAILED,
        "Download failed: ${result.exceptionOrNull()?.message}")
      return@withContext false
    }

    if (!folder.writeFile(path, bytes)) {
      logAction(pair, LogActionType.ERROR, remoteItem.name, path, 0L, LogStatus.FAILED,
        "Download failed: could not write the file on the device.")
      return@withContext false
    }

    // Capture the storage-reported metadata (timestamps vary by provider) so the
    // next sync recognizes this as the baseline instead of a fresh local edit.
    val stored = folder.statFile(path)
    saveRecord(
      SyncedFileRecord(
        id = existing?.id ?: 0L,
        syncPairId = pair.id,
        relativePath = path,
        fileName = remoteItem.name,
        localLastModified = stored?.lastModified ?: System.currentTimeMillis(),
        remoteLastModified = remoteItem.lastModified,
        fileSize = bytes.size.toLong(),
        etag = remoteItem.etag,
        syncState = FileSyncState.SYNCED,
        previewSnippet = bytes.toString(Charsets.UTF_8).take(120)
      )
    )
    logAction(pair, LogActionType.DOWNLOAD, remoteItem.name, path, bytes.size.toLong(), LogStatus.SUCCESS,
      "Downloaded from WebDAV server.")
    true
  }

  suspend fun resolveConflict(conflictId: Long, resolution: ConflictStatus): Boolean = withContext(Dispatchers.IO) {
    val conflict = conflictDao.getConflictById(conflictId) ?: return@withContext false
    val pair = syncPairDao.getSyncPairById(conflict.syncPairId) ?: return@withContext false
    val server = serverDao.getServerById(pair.serverId)
      ?: return@withContext false.also {
        logAction(pair, LogActionType.ERROR, conflict.fileName, conflict.relativePath, 0L, LogStatus.FAILED,
          "Conflict resolution failed: server no longer exists.")
      }
    val folder = folderFor(pair)
      ?: return@withContext false.also {
        logAction(pair, LogActionType.ERROR, conflict.fileName, conflict.relativePath, 0L, LogStatus.FAILED,
          "Conflict resolution failed: no device folder selected.")
      }

    val path = conflict.relativePath
    val remoteRelPath = joinRemote(pair.remoteRelativePath, path)

    when (resolution) {
      ConflictStatus.RESOLVED_LOCAL -> {
        val bytes = folder.readFile(path) ?: ByteArray(0)
        val result = remoteStore.uploadFile(server, remoteRelPath, bytes)
        if (result.isSuccess) {
          conflictDao.updateConflict(
            conflict.copy(
              status = ConflictStatus.RESOLVED_LOCAL,
              resolvedAt = System.currentTimeMillis(),
              resolutionNotes = "Resolved: Device version uploaded to WebDAV"
            )
          )
          logAction(pair, LogActionType.CONFLICT, conflict.fileName, path, bytes.size.toLong(), LogStatus.RESOLVED,
            "Manual resolution: Device version preserved and sent to server.")
          // Reset the baseline; the next sync re-adopts the current state.
          fileRecordDao.deleteFile(pair.id, path)
        } else {
          logAction(pair, LogActionType.ERROR, conflict.fileName, path, 0L, LogStatus.FAILED,
            "Conflict resolution failed: ${result.exceptionOrNull()?.message}")
          return@withContext false
        }
      }

      ConflictStatus.RESOLVED_REMOTE -> {
        val result = remoteStore.downloadFile(server, remoteRelPath)
        val bytes = result.getOrNull()
        if (bytes != null && folder.writeFile(path, bytes)) {
          conflictDao.updateConflict(
            conflict.copy(
              status = ConflictStatus.RESOLVED_REMOTE,
              resolvedAt = System.currentTimeMillis(),
              resolutionNotes = "Resolved: WebDAV version downloaded to device"
            )
          )
          logAction(pair, LogActionType.CONFLICT, conflict.fileName, path, bytes.size.toLong(), LogStatus.RESOLVED,
            "Manual resolution: Server version accepted and written to device.")
          fileRecordDao.deleteFile(pair.id, path)
        } else {
          logAction(pair, LogActionType.ERROR, conflict.fileName, path, 0L, LogStatus.FAILED,
            "Conflict resolution failed: ${result.exceptionOrNull()?.message ?: "could not write the file on the device"}")
          return@withContext false
        }
      }

      ConflictStatus.RESOLVED_BOTH -> {
        val result = remoteStore.downloadFile(server, remoteRelPath)
        val bytes = result.getOrNull()
        if (bytes != null) {
          val ext = conflict.fileName.substringAfterLast(".", "")
          val base = conflict.fileName.substringBeforeLast(".")
          val dir = path.substringBeforeLast('/')
          val renamedPath = if (dir.isNotEmpty()) "$dir/${base}_server_backup.${if (ext.isNotEmpty()) ext else "txt"}"
          else "/${base}_server_backup.${if (ext.isNotEmpty()) ext else "txt"}"
          folder.writeFile(renamedPath, bytes)
          conflictDao.updateConflict(
            conflict.copy(
              status = ConflictStatus.RESOLVED_BOTH,
              resolvedAt = System.currentTimeMillis(),
              resolutionNotes = "Resolved: Both kept (Remote saved as $renamedPath)"
            )
          )
          logAction(pair, LogActionType.CONFLICT, conflict.fileName, renamedPath, bytes.size.toLong(), LogStatus.RESOLVED,
            "Manual resolution: Server version saved as duplicate copy.")
          fileRecordDao.deleteFile(pair.id, path)
        } else {
          logAction(pair, LogActionType.ERROR, conflict.fileName, path, 0L, LogStatus.FAILED,
            "Conflict resolution failed: ${result.exceptionOrNull()?.message}")
          return@withContext false
        }
      }

      else -> {}
    }

    // Refresh pair status when no pending conflicts remain for it.
    val pending = conflictDao.getConflictsByStatus(ConflictStatus.PENDING).first()
      .filter { it.syncPairId == pair.id }
    if (pending.isEmpty()) {
      syncPairDao.updateSyncPair(pair.copy(lastSyncStatus = SyncStatus.SUCCESS, lastSyncMessage = "All conflicts resolved."))
    }
    true
  }

  private suspend fun saveRecord(record: SyncedFileRecord) {
    fileRecordDao.insertOrUpdateFile(record)
  }

  private fun buildSyncedRecord(
    pair: SyncPair,
    path: String,
    localItem: LocalFileItem,
    remoteItem: RemoteFileItem,
    previous: SyncedFileRecord?,
    state: FileSyncState = FileSyncState.SYNCED
  ): SyncedFileRecord = SyncedFileRecord(
    id = previous?.id ?: 0L,
    syncPairId = pair.id,
    relativePath = path,
    fileName = localItem.name,
    localLastModified = localItem.lastModified,
    remoteLastModified = remoteItem.lastModified,
    fileSize = localItem.size,
    etag = remoteItem.etag,
    syncState = state,
    previewSnippet = previous?.previewSnippet ?: ""
  )

  /**
   * Detect remote changes by comparing against the baseline snapshot.
   * ETag is authoritative when both sides have one; falls back to lastModified otherwise.
   */
  private fun isRemoteChanged(record: SyncedFileRecord?, remoteItem: RemoteFileItem): Boolean {
    if (record == null) return true
    if (record.etag.isNotEmpty() && remoteItem.etag.isNotEmpty()) {
      return record.etag != remoteItem.etag
    }
    if (record.remoteLastModified > 0 && remoteItem.lastModified > 0) {
      return record.remoteLastModified != remoteItem.lastModified
    }
    return record.fileSize != remoteItem.size
  }

  private suspend fun logAction(
    pair: SyncPair,
    action: LogActionType,
    fileName: String,
    relativePath: String,
    bytes: Long,
    status: LogStatus,
    message: String
  ) {
    logDao.insertLog(
      SyncLog(
        syncPairId = pair.id,
        pairName = pair.name,
        actionType = action,
        fileName = fileName,
        relativePath = relativePath,
        bytesTransferred = bytes,
        status = status,
        message = message
      )
    )
  }

  /** Path of a remote item relative to the pair's remote root. */
  private fun suffixOf(vaultRelativePath: String, pairRoot: String): String {
    val p = normalizeVaultPath(vaultRelativePath)
    var root = pairRoot.trim().trim('/')
    return if (root.isEmpty()) {
      p
    } else {
      val prefix = "/$root"
      if (p == prefix) "" else if (p.startsWith("$prefix/")) p.removePrefix(prefix) else p
    }
  }

  private fun joinRemote(pairRoot: String, path: String): String {
    val root = pairRoot.trim().trimEnd('/')
    val s = path.trim().trimStart('/')
    return if (s.isEmpty()) root else "$root/$s"
  }

  private fun normalizeVaultPath(path: String): String {
    var p = path.trim().replace('\\', '/')
    if (!p.startsWith("/")) p = "/$p"
    return p
  }

  /** Exclude matching: "*.ext" and ".ext" match file extensions, other "*" patterns are globs, plain tokens match exactly. */
  private fun isExcluded(fileName: String, excludePatterns: String): Boolean {
    if (excludePatterns.isBlank()) return false
    val patterns = excludePatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (p in patterns) {
      when {
        p.startsWith("*.") -> if (fileName.endsWith(p.removePrefix("*"), ignoreCase = true)) return true
        p.startsWith(".") -> if (fileName.endsWith(p, ignoreCase = true)) return true
        p.contains('*') -> if (globToRegex(p).matches(fileName)) return true
        else -> if (fileName.equals(p, ignoreCase = true)) return true
      }
    }
    return false
  }

  private fun globToRegex(pattern: String): Regex {
    val sb = StringBuilder()
    for (c in pattern) {
      if (c == '*') sb.append(".*") else sb.append(Regex.escape(c.toString()))
    }
    return Regex("^${sb}$", RegexOption.IGNORE_CASE)
  }

  private suspend fun pruneOldLogs() {
    try {
      val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
      logDao.deleteLogsOlderThan(cutoff)
    } catch (_: Exception) {}
  }
}
