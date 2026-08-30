package com.dissonance.webdav.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncDirection {
  TWO_WAY,
  LOCAL_TO_REMOTE, // Upload mirror
  REMOTE_TO_LOCAL  // Download mirror
}

enum class ConflictPolicy {
  ASK_USER,
  DEVICE_WINS,
  SERVER_WINS,
  KEEP_BOTH,
  LATEST_WINS
}

enum class SyncStatus {
  IDLE,
  RUNNING,
  SUCCESS,
  WARNING,
  ERROR,
  CONFLICT_DETECTED,
  PAUSED_BATTERY,
  PAUSED_NETWORK
}

enum class LogActionType {
  UPLOAD,
  DOWNLOAD,
  DELETE,
  MKCOL,
  CONFLICT,
  ERROR,
  SCAN,
  SKIP
}

enum class LogStatus {
  SUCCESS,
  FAILED,
  RESOLVED,
  SKIPPED
}

enum class ConflictStatus {
  PENDING,
  RESOLVED_LOCAL,
  RESOLVED_REMOTE,
  RESOLVED_BOTH
}

enum class FileSyncState {
  SYNCED,
  LOCAL_MODIFIED,
  REMOTE_MODIFIED,
  CONFLICT,
  OFFLINE_AVAILABLE
}

@Entity(tableName = "webdav_servers")
data class WebdavServer(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val url: String,
  val username: String = "",
  val password: String = "",
  val trustSelfSigned: Boolean = true,
  val lastConnectedAt: Long = 0L,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_pairs")
data class SyncPair(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val serverId: Long,
  val name: String,
  val localFolderName: String,
  // Legacy vault path, kept only as a display fallback for pre-SAF installs.
  val localRelativePath: String,
  // Persisted SAF document tree Uri ("content://.../tree/...") granted by the user.
  val localTreeUri: String = "",
  val remoteRelativePath: String,
  val syncDirection: SyncDirection = SyncDirection.TWO_WAY,
  val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK_USER,
  val syncIntervalMinutes: Int = 30,
  val wifiOnly: Boolean = true,
  val chargingOnly: Boolean = false,
  val batterySaverThreshold: Int = 20, // Pause if battery <= threshold
  val excludePatterns: String = ".tmp, .bak, .DS_Store, .thumbnails",
  val isEnabled: Boolean = true,
  val lastSyncTimestamp: Long = 0L,
  val lastSyncStatus: SyncStatus = SyncStatus.IDLE,
  val lastSyncMessage: String = "",
  val fileCount: Int = 0,
  val totalBytes: Long = 0L,
  val isOfflineCached: Boolean = true
)

@Entity(tableName = "sync_logs")
data class SyncLog(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val syncPairId: Long,
  val pairName: String,
  val timestamp: Long = System.currentTimeMillis(),
  val actionType: LogActionType,
  val fileName: String,
  val relativePath: String = "",
  val bytesTransferred: Long = 0L,
  val status: LogStatus = LogStatus.SUCCESS,
  val message: String = ""
)

@Entity(tableName = "file_conflicts")
data class FileConflict(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val syncPairId: Long,
  val pairName: String,
  val fileName: String,
  val relativePath: String,
  val localLastModified: Long,
  val remoteLastModified: Long,
  val localSize: Long,
  val remoteSize: Long,
  val localPreview: String = "",
  val remotePreview: String = "",
  val status: ConflictStatus = ConflictStatus.PENDING,
  val detectedAt: Long = System.currentTimeMillis(),
  val resolvedAt: Long? = null,
  val resolutionNotes: String = ""
)

@Entity(tableName = "file_records")
data class SyncedFileRecord(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val syncPairId: Long,
  val relativePath: String,
  val fileName: String,
  val isDirectory: Boolean = false,
  val localLastModified: Long = 0L,
  val remoteLastModified: Long = 0L,
  val fileSize: Long = 0L,
  val etag: String = "",
  val localHash: String = "",
  val isOfflineAvailable: Boolean = true,
  val syncState: FileSyncState = FileSyncState.SYNCED,
  val previewSnippet: String = "",
  val isDirty: Boolean = false,
  val dirtyAction: String = ""
)
