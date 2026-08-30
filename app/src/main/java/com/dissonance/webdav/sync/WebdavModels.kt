package com.dissonance.webdav.sync

data class RemoteFileItem(
  val path: String,
  val name: String,
  val size: Long,
  val lastModified: Long,
  val isDirectory: Boolean,
  val etag: String = "",
  val contentType: String = "application/octet-stream"
)

data class LocalFileItem(
  val relativePath: String,
  val name: String,
  val size: Long,
  val lastModified: Long,
  val isDirectory: Boolean,
  val absolutePath: String = ""
)

sealed class SyncResult {
  data class Success(val filesUploaded: Int, val filesDownloaded: Int, val bytesTransferred: Long) : SyncResult()
  data class ConflictFound(val count: Int) : SyncResult()
  data class Paused(val reason: String) : SyncResult()
  data class Failure(val errorMessage: String) : SyncResult()
}

data class SyncProgress(
  val isRunning: Boolean = false,
  val currentPairName: String = "",
  val currentFileName: String = "",
  val progressPercent: Float = 0f,
  val filesProcessed: Int = 0,
  val totalFiles: Int = 0,
  val bytesTransferred: Long = 0L,
  val transferRateSpeed: String = "0 KB/s",
  val currentPhase: String = "Idle"
)

data class EnergyState(
  val batteryLevelPercent: Int = -1, // -1 = unknown (no battery hardware or not reported)
  val isCharging: Boolean = false,
  val isWifiConnected: Boolean = true,
  val isPowerSaveMode: Boolean = false,
  val energyEfficiencyRating: String = "Battery unknown"
)
