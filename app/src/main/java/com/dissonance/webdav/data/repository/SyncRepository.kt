package com.dissonance.webdav.data.repository

import android.content.Context
import com.dissonance.webdav.data.db.AppDatabase
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.model.WebdavServer
import com.dissonance.webdav.sync.EnergyMonitor
import com.dissonance.webdav.sync.EnergyState
import com.dissonance.webdav.sync.LocalFileManager
import com.dissonance.webdav.sync.LocalFolderAccess
import com.dissonance.webdav.sync.RemoteFileItem
import com.dissonance.webdav.sync.SyncEngine
import com.dissonance.webdav.sync.SyncProgress
import com.dissonance.webdav.sync.SyncResult
import com.dissonance.webdav.sync.WebdavClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncRepository(
  private val context: Context,
  private val scope: CoroutineScope,
  autoStartScheduler: Boolean = true
) {

  private val database = AppDatabase.getInstance(context)
  private val serverDao = database.webdavServerDao()
  private val syncPairDao = database.syncPairDao()
  private val syncLogDao = database.syncLogDao()
  private val conflictDao = database.fileConflictDao()
  private val fileRecordDao = database.syncedFileRecordDao()

  val webdavClient = WebdavClient()
  val localFileManager = LocalFileManager(context)
  val energyMonitor = EnergyMonitor(context)

  val syncEngine = SyncEngine(
    syncPairDao = syncPairDao,
    serverDao = serverDao,
    logDao = syncLogDao,
    conflictDao = conflictDao,
    fileRecordDao = fileRecordDao,
    remoteStore = webdavClient,
    folderFor = localFileManager::folderFor,
    energyMonitor = energyMonitor,
    scope = scope,
    autoStartScheduler = autoStartScheduler
  )

  val allServers: Flow<List<WebdavServer>> = serverDao.getAllServers()
  val allSyncPairs: Flow<List<SyncPair>> = syncPairDao.getAllSyncPairs()
  val recentLogs: Flow<List<SyncLog>> = syncLogDao.getRecentLogs()
  val pendingConflicts: Flow<List<FileConflict>> = conflictDao.getConflictsByStatus(ConflictStatus.PENDING)
  val allConflicts: Flow<List<FileConflict>> = conflictDao.getAllConflicts()
  val dirtyCount: Flow<Int> = fileRecordDao.getDirtyCountFlow()
  val syncProgress: StateFlow<SyncProgress> = syncEngine.syncProgress
  val energyState: StateFlow<EnergyState> = energyMonitor.energyState

  suspend fun resetAllData() = withContext(Dispatchers.IO) {
    // Release the persisted SAF grants of every pair before wiping the database.
    for (pair in syncPairDao.getAllPairsSync()) {
      if (pair.localTreeUri.isNotEmpty()) localFileManager.releaseGrant(pair.localTreeUri)
    }
    serverDao.clearAllServers()
    syncPairDao.clearAllPairs()
    syncLogDao.clearAllLogs()
    conflictDao.clearAllConflicts()
    fileRecordDao.clearAllFiles()
  }

  fun getFilesForPair(pairId: Long): Flow<List<SyncedFileRecord>> =
    fileRecordDao.getFilesForPair(pairId)

  suspend fun syncAllNow(): List<SyncResult> = syncEngine.syncAll()

  suspend fun syncSinglePair(pairId: Long): SyncResult = syncEngine.syncPair(pairId)

  suspend fun resolveConflict(conflictId: Long, resolution: ConflictStatus): Boolean =
    syncEngine.resolveConflict(conflictId, resolution)

  suspend fun saveSyncPair(pair: SyncPair): Long = withContext(Dispatchers.IO) {
    if (pair.id == 0L) {
      syncPairDao.insertSyncPair(pair)
    } else {
      // Release the old grant when the pair is re-linked to a different folder.
      val old = syncPairDao.getSyncPairById(pair.id)
      if (old != null && old.localTreeUri.isNotEmpty() && old.localTreeUri != pair.localTreeUri) {
        localFileManager.releaseGrant(old.localTreeUri)
      }
      syncPairDao.updateSyncPair(pair)
      pair.id
    }
  }

  suspend fun deleteSyncPair(pair: SyncPair) = withContext(Dispatchers.IO) {
    syncPairDao.deleteSyncPair(pair)
    fileRecordDao.deleteFilesForPair(pair.id)
    if (pair.localTreeUri.isNotEmpty()) localFileManager.releaseGrant(pair.localTreeUri)
    // Remove conflicts belonging to this pair.
    conflictDao.getAllConflicts().first()
      .filter { it.syncPairId == pair.id }
      .forEach { conflictDao.deleteConflictById(it.id) }
  }

  suspend fun saveServer(server: WebdavServer): Long = withContext(Dispatchers.IO) {
    if (server.id == 0L) {
      serverDao.insertServer(server)
    } else {
      serverDao.updateServer(server)
      server.id
    }
  }

  suspend fun deleteServer(server: WebdavServer) = withContext(Dispatchers.IO) {
    // Cascade: remove pairs (and their file records/conflicts/grants) that pointed at
    // this server, otherwise they would fail on every subsequent sync attempt.
    val orphanPairs = syncPairDao.getAllPairsSync().filter { it.serverId == server.id }
    for (pair in orphanPairs) {
      deleteSyncPair(pair)
    }
    serverDao.deleteServer(server)
  }

  suspend fun clearLogs() = withContext(Dispatchers.IO) {
    syncLogDao.clearAllLogs()
  }

  suspend fun testServerConnection(server: WebdavServer): Result<String> =
    webdavClient.testConnection(server)

  suspend fun listRemoteFolder(server: WebdavServer, path: String): Result<List<RemoteFileItem>> =
    webdavClient.listFolder(server, path)

  /** Folder handle for a pair, or null when no grant is configured. */
  fun folderFor(pair: SyncPair): LocalFolderAccess? = localFileManager.folderFor(pair)

  suspend fun readLocalFileContent(pair: SyncPair, relativePath: String): String =
    folderFor(pair)?.readText(relativePath) ?: ""

  suspend fun saveLocalFileContent(pair: SyncPair, relativePath: String, text: String): Boolean =
    folderFor(pair)?.writeText(relativePath, text) ?: false

  suspend fun createNewLocalFile(pair: SyncPair, fileName: String, content: String): Boolean =
    withContext(Dispatchers.IO) {
      val folder = folderFor(pair) ?: return@withContext false
      val clean = fileName.trim().trimStart('/')
      if (clean.isEmpty()) return@withContext false
      folder.writeText("/$clean", content)
    }
}
