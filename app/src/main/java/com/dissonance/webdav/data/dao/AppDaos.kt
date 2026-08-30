package com.dissonance.webdav.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.model.WebdavServer
import kotlinx.coroutines.flow.Flow

@Dao
interface WebdavServerDao {
  @Query("SELECT * FROM webdav_servers ORDER BY id ASC")
  fun getAllServers(): Flow<List<WebdavServer>>

  @Query("SELECT * FROM webdav_servers WHERE id = :id")
  suspend fun getServerById(id: Long): WebdavServer?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertServer(server: WebdavServer): Long

  @Update
  suspend fun updateServer(server: WebdavServer)

  @Delete
  suspend fun deleteServer(server: WebdavServer)

  @Query("SELECT COUNT(*) FROM webdav_servers")
  suspend fun getServerCount(): Int

  @Query("DELETE FROM webdav_servers")
  suspend fun clearAllServers()
}

@Dao
interface SyncPairDao {
  @Query("SELECT * FROM sync_pairs ORDER BY id ASC")
  fun getAllSyncPairs(): Flow<List<SyncPair>>

  @Query("SELECT * FROM sync_pairs ORDER BY id ASC")
  suspend fun getAllPairsSync(): List<SyncPair>

  @Query("SELECT * FROM sync_pairs WHERE isEnabled = 1")
  fun getEnabledSyncPairs(): Flow<List<SyncPair>>

  @Query("SELECT * FROM sync_pairs WHERE id = :id")
  suspend fun getSyncPairById(id: Long): SyncPair?

  @Query("SELECT * FROM sync_pairs WHERE id = :id")
  suspend fun getPairById(id: Long): SyncPair?

  @Query("SELECT * FROM sync_pairs WHERE id = :id")
  fun observeSyncPairById(id: Long): Flow<SyncPair?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSyncPair(pair: SyncPair): Long

  @Update
  suspend fun updateSyncPair(pair: SyncPair)

  @Delete
  suspend fun deleteSyncPair(pair: SyncPair)

  @Query("SELECT COUNT(*) FROM sync_pairs")
  suspend fun getSyncPairCount(): Int

  @Query("DELETE FROM sync_pairs")
  suspend fun clearAllPairs()
}

@Dao
interface SyncLogDao {
  @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 200")
  fun getRecentLogs(): Flow<List<SyncLog>>

  @Query("SELECT * FROM sync_logs WHERE syncPairId = :pairId ORDER BY timestamp DESC LIMIT 100")
  fun getLogsForPair(pairId: Long): Flow<List<SyncLog>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: SyncLog): Long

  @Query("DELETE FROM sync_logs")
  suspend fun clearAllLogs()

  @Query("DELETE FROM sync_logs WHERE syncPairId = :pairId")
  suspend fun clearLogsForPair(pairId: Long)

  @Query("DELETE FROM sync_logs WHERE timestamp < :cutoff")
  suspend fun deleteLogsOlderThan(cutoff: Long): Int
}

@Dao
interface FileConflictDao {
  @Query("SELECT * FROM file_conflicts WHERE status = :status ORDER BY detectedAt DESC")
  fun getConflictsByStatus(status: ConflictStatus = ConflictStatus.PENDING): Flow<List<FileConflict>>

  @Query("SELECT * FROM file_conflicts ORDER BY detectedAt DESC")
  fun getAllConflicts(): Flow<List<FileConflict>>

  @Query("SELECT * FROM file_conflicts WHERE id = :id")
  suspend fun getConflictById(id: Long): FileConflict?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConflict(conflict: FileConflict): Long

  @Update
  suspend fun updateConflict(conflict: FileConflict)

  @Query("DELETE FROM file_conflicts WHERE id = :id")
  suspend fun deleteConflictById(id: Long)

  @Query("DELETE FROM file_conflicts WHERE status != 'PENDING'")
  suspend fun clearResolvedConflicts()

  @Query("DELETE FROM file_conflicts")
  suspend fun clearAllConflicts()
}

@Dao
interface SyncedFileRecordDao {
  @Query("SELECT * FROM file_records WHERE syncPairId = :pairId ORDER BY isDirectory DESC, fileName ASC")
  fun getFilesForPair(pairId: Long): Flow<List<SyncedFileRecord>>

  @Query("SELECT * FROM file_records WHERE syncPairId = :pairId ORDER BY isDirectory DESC, fileName ASC")
  suspend fun getFilesForPairSync(pairId: Long): List<SyncedFileRecord>

  @Query("SELECT * FROM file_records WHERE syncPairId = :pairId AND relativePath = :path LIMIT 1")
  suspend fun getFileByPath(pairId: Long, path: String): SyncedFileRecord?

  @Query("SELECT * FROM file_records ORDER BY syncPairId ASC, fileName ASC")
  suspend fun getAllFiles(): List<SyncedFileRecord>

  @Query("SELECT * FROM file_records WHERE id = :id LIMIT 1")
  suspend fun getFileById(id: Long): SyncedFileRecord?

  @Query("SELECT * FROM file_records WHERE isDirty = 1 AND syncPairId = :pairId")
  suspend fun getDirtyRecordsForPair(pairId: Long): List<SyncedFileRecord>

  @Query("SELECT * FROM file_records WHERE isDirty = 1")
  suspend fun getAllDirtyRecords(): List<SyncedFileRecord>

  @Query("SELECT COUNT(*) FROM file_records WHERE isDirty = 1")
  fun getDirtyCountFlow(): Flow<Int>

  @Query("SELECT COUNT(*) FROM file_records WHERE isDirty = 1")
  suspend fun getDirtyCount(): Int

  @Query("UPDATE file_records SET isDirty = :isDirty, dirtyAction = :dirtyAction, syncState = 'LOCAL_MODIFIED' WHERE syncPairId = :pairId AND relativePath = :path")
  suspend fun markFileDirty(pairId: Long, path: String, isDirty: Boolean = true, dirtyAction: String = "UPLOAD")

  @Query("UPDATE file_records SET isDirty = 0, dirtyAction = '', syncState = 'SYNCED' WHERE id = :id")
  suspend fun clearDirtyById(id: Long)

  @Query("UPDATE file_records SET isDirty = 0, dirtyAction = '', syncState = 'SYNCED' WHERE syncPairId = :pairId AND relativePath = :path")
  suspend fun clearDirtyByPath(pairId: Long, path: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateFile(file: SyncedFileRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(files: List<SyncedFileRecord>)

  @Query("DELETE FROM file_records WHERE syncPairId = :pairId")
  suspend fun deleteFilesForPair(pairId: Long)

  @Query("DELETE FROM file_records WHERE syncPairId = :pairId AND relativePath = :path")
  suspend fun deleteFile(pairId: Long, path: String)

  @Query("DELETE FROM file_records")
  suspend fun clearAllFiles()
}
