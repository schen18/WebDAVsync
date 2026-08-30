package com.dissonance.webdav.client

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.dissonance.webdav.provider.SyncHubContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Data model for a Sync Pair exposed to client applications.
 */
data class ClientSyncPair(
  val id: Long,
  val serverId: Long,
  val name: String,

  /** Display name of the granted device folder (SAF tree). */
  val localFolderName: String,

  /** Same as [localFolderName]; the legacy vault-path column now carries the folder display name. */
  val localRelativePath: String,
  val remoteRelativePath: String,
  val syncDirection: String,
  val lastSyncStatus: String,
  val fileCount: Int,
  val totalBytes: Long
)

/**
 * Data model for a file record: a file in one of the pair's granted device folders.
 */
data class ClientFileRecord(
  val id: Long,
  val syncPairId: Long,

  /** Path relative to the pair's granted folder, leading slash, e.g. "/sub/file.md". */
  val relativePath: String,
  val fileName: String,
  val isDirectory: Boolean,
  val fileSize: Long,
  val localLastModified: Long,
  val isOfflineAvailable: Boolean,
  val isDirty: Boolean,
  val dirtyAction: String,
  val syncState: String,
  val previewSnippet: String
)

/**
 * Hub statistics.
 */
data class ClientHubStats(
  val dirtyCount: Int,
  val pairCount: Int
)

/**
 * Lightweight, reusable client library for companion Android applications.
 *
 * Provides a clean, asynchronous Kotlin API for reading and writing files in the
 * device folders the WebDAV Sync Hub has SAF grants for, querying metadata, and
 * triggering cloud syncs — all through the signature-secured Sync Hub provider.
 * Written files are flagged dirty and pushed to WebDAV automatically.
 *
 * ### Setup in Client App:
 * 1. Add signature permission in client `AndroidManifest.xml`:
 *    `<uses-permission android:name="com.dissonance.webdav.permission.ACCESS_SYNC_HUB" />`
 * 2. Sign client app with the same signing key as the WebDAV Sync Hub app.
 * 3. Instantiate `val syncHub = SyncHubClient(context)` and invoke methods.
 *
 * Paths passed to the read/write APIs are relative to the pair's granted device
 * folder (e.g. "notes/idea.md" or "/notes/idea.md"). Folder grants are managed
 * by the Hub app; if a pair's grant is missing, write operations fail and
 * read/write streams return null.
 */
class SyncHubClient(private val context: Context) {

  private val contentResolver: ContentResolver
    get() = context.contentResolver

  /**
   * Checks if the Sync Hub ContentProvider is available and accessible on this device.
   */
  fun isHubAvailable(): Boolean {
    return try {
      val cursor = contentResolver.query(
        SyncHubContract.PAIRS_URI,
        arrayOf(SyncHubContract.PairsColumns._ID),
        null,
        null,
        null
      )
      cursor?.use { true } ?: false
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Retrieves all configured Sync Pairs from the Hub.
   */
  suspend fun getSyncPairs(): List<ClientSyncPair> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ClientSyncPair>()
    try {
      val cursor = contentResolver.query(SyncHubContract.PAIRS_URI, null, null, null, null)
      cursor?.use { c ->
        val idIdx = c.getColumnIndex(SyncHubContract.PairsColumns._ID)
        val serverIdIdx = c.getColumnIndex(SyncHubContract.PairsColumns.SERVER_ID)
        val nameIdx = c.getColumnIndex(SyncHubContract.PairsColumns.NAME)
        val localFolderIdx = c.getColumnIndex(SyncHubContract.PairsColumns.LOCAL_FOLDER_NAME)
        val localPathIdx = c.getColumnIndex(SyncHubContract.PairsColumns.LOCAL_RELATIVE_PATH)
        val remotePathIdx = c.getColumnIndex(SyncHubContract.PairsColumns.REMOTE_RELATIVE_PATH)
        val directionIdx = c.getColumnIndex(SyncHubContract.PairsColumns.SYNC_DIRECTION)
        val statusIdx = c.getColumnIndex(SyncHubContract.PairsColumns.LAST_SYNC_STATUS)
        val fileCountIdx = c.getColumnIndex(SyncHubContract.PairsColumns.FILE_COUNT)
        val totalBytesIdx = c.getColumnIndex(SyncHubContract.PairsColumns.TOTAL_BYTES)

        while (c.moveToNext()) {
          results.add(
            ClientSyncPair(
              id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
              serverId = if (serverIdIdx >= 0) c.getLong(serverIdIdx) else 0L,
              name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "",
              localFolderName = if (localFolderIdx >= 0) c.getString(localFolderIdx) ?: "" else "",
              localRelativePath = if (localPathIdx >= 0) c.getString(localPathIdx) ?: "" else "",
              remoteRelativePath = if (remotePathIdx >= 0) c.getString(remotePathIdx) ?: "" else "",
              syncDirection = if (directionIdx >= 0) c.getString(directionIdx) ?: "TWO_WAY" else "TWO_WAY",
              lastSyncStatus = if (statusIdx >= 0) c.getString(statusIdx) ?: "IDLE" else "IDLE",
              fileCount = if (fileCountIdx >= 0) c.getInt(fileCountIdx) else 0,
              totalBytes = if (totalBytesIdx >= 0) c.getLong(totalBytesIdx) else 0L
            )
          )
        }
      }
    } catch (e: Exception) {
      // Security or provider exception
    }
    results
  }

  /**
   * Retrieves cached file records for a specific Sync Pair.
   */
  suspend fun listFiles(pairId: Long): List<ClientFileRecord> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ClientFileRecord>()
    try {
      val uri = SyncHubContract.getFilesForPairUri(pairId)
      val cursor = contentResolver.query(uri, null, null, null, null)
      cursor?.use { c ->
        val idIdx = c.getColumnIndex(SyncHubContract.FilesColumns._ID)
        val pairIdIdx = c.getColumnIndex(SyncHubContract.FilesColumns.SYNC_PAIR_ID)
        val pathIdx = c.getColumnIndex(SyncHubContract.FilesColumns.RELATIVE_PATH)
        val nameIdx = c.getColumnIndex(SyncHubContract.FilesColumns.FILE_NAME)
        val isDirIdx = c.getColumnIndex(SyncHubContract.FilesColumns.IS_DIRECTORY)
        val sizeIdx = c.getColumnIndex(SyncHubContract.FilesColumns.FILE_SIZE)
        val modifiedIdx = c.getColumnIndex(SyncHubContract.FilesColumns.LOCAL_LAST_MODIFIED)
        val offlineIdx = c.getColumnIndex(SyncHubContract.FilesColumns.IS_OFFLINE_AVAILABLE)
        val dirtyIdx = c.getColumnIndex(SyncHubContract.FilesColumns.IS_DIRTY)
        val dirtyActionIdx = c.getColumnIndex(SyncHubContract.FilesColumns.DIRTY_ACTION)
        val syncStateIdx = c.getColumnIndex(SyncHubContract.FilesColumns.SYNC_STATE)
        val previewIdx = c.getColumnIndex(SyncHubContract.FilesColumns.PREVIEW_SNIPPET)

        while (c.moveToNext()) {
          results.add(
            ClientFileRecord(
              id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
              syncPairId = if (pairIdIdx >= 0) c.getLong(pairIdIdx) else pairId,
              relativePath = if (pathIdx >= 0) c.getString(pathIdx) ?: "" else "",
              fileName = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "",
              isDirectory = if (isDirIdx >= 0) c.getInt(isDirIdx) == 1 else false,
              fileSize = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L,
              localLastModified = if (modifiedIdx >= 0) c.getLong(modifiedIdx) else 0L,
              isOfflineAvailable = if (offlineIdx >= 0) c.getInt(offlineIdx) == 1 else true,
              isDirty = if (dirtyIdx >= 0) c.getInt(dirtyIdx) == 1 else false,
              dirtyAction = if (dirtyActionIdx >= 0) c.getString(dirtyActionIdx) ?: "" else "",
              syncState = if (syncStateIdx >= 0) c.getString(syncStateIdx) ?: "SYNCED" else "SYNCED",
              previewSnippet = if (previewIdx >= 0) c.getString(previewIdx) ?: "" else ""
            )
          )
        }
      }
    } catch (e: Exception) {
      // Error handling
    }
    results
  }

  /**
   * Opens an [InputStream] to read bytes directly from the pair's granted device folder.
   */
  suspend fun openInputStream(pairId: Long, relativePath: String): InputStream? = withContext(Dispatchers.IO) {
    try {
      val uri = SyncHubContract.getFileItemUri(pairId, relativePath)
      contentResolver.openInputStream(uri)
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Reads the text content of a file in the pair's granted folder.
   * Returns null when the file has never been synced (no record) or is unreadable.
   */
  suspend fun readText(pairId: Long, relativePath: String): String? = withContext(Dispatchers.IO) {
    try {
      openInputStream(pairId, relativePath)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Reads raw bytes from a file in the pair's granted folder.
   * Returns null when the file has never been synced (no record) or is unreadable.
   */
  suspend fun readBytes(pairId: Long, relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
      openInputStream(pairId, relativePath)?.use { it.readBytes() }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Opens an [OutputStream] to write bytes into the pair's granted device folder,
   * creating the file (and missing parent folders) as needed. Closing the stream
   * flags the record as dirty and schedules a background push to WebDAV.
   * Returns null when the pair or its folder grant is missing.
   */
  suspend fun openOutputStream(pairId: Long, relativePath: String, mode: String = "wt"): OutputStream? = withContext(Dispatchers.IO) {
    try {
      val uri = SyncHubContract.getFileItemUri(pairId, relativePath)
      contentResolver.openOutputStream(uri, mode)
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Writes text into a file in the pair's granted folder, flags it dirty, and
   * enqueues a background push to WebDAV. Returns null on failure (e.g. missing
   * folder grant).
   */
  suspend fun writeText(pairId: Long, relativePath: String, text: String): Uri? = withContext(Dispatchers.IO) {
    writeBytes(pairId, relativePath, text.toByteArray(Charsets.UTF_8))
  }

  /**
   * Writes a byte array into a file in the pair's granted folder, flags it dirty,
   * and enqueues a background push to WebDAV. Returns null on failure.
   */
  suspend fun writeBytes(
    pairId: Long,
    relativePath: String,
    bytes: ByteArray,
    mimeType: String = "application/octet-stream"
  ): Uri? = withContext(Dispatchers.IO) {
    try {
      val uri = SyncHubContract.getFileItemUri(pairId, relativePath)
      val outputStream = contentResolver.openOutputStream(uri, "wt")
      if (outputStream != null) {
        outputStream.use { it.write(bytes) }
        uri
      } else {
        // Try insert if the write stream could not be opened
        val insertUri = SyncHubContract.getFilesForPairUri(pairId)
        val values = ContentValues().apply {
          put(SyncHubContract.FilesColumns.SYNC_PAIR_ID, pairId)
          put(SyncHubContract.FilesColumns.RELATIVE_PATH, relativePath)
          put(SyncHubContract.FilesColumns.FILE_NAME, relativePath.substringAfterLast('/'))
          put(SyncHubContract.FilesColumns.IS_DIRECTORY, false)
          put(SyncHubContract.FilesColumns.MIME_TYPE, mimeType)
          put("initial_bytes", bytes)
        }
        contentResolver.insert(insertUri, values)
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Deletes a file from the pair's granted folder, marks it for remote deletion,
   * and enqueues a background push.
   */
  suspend fun deleteFile(pairId: Long, relativePath: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val uri = SyncHubContract.getFileItemUri(pairId, relativePath)
      val count = contentResolver.delete(uri, null, null)
      count > 0
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Triggers an immediate cloud sync via IPC. With a [pairId], a full delta sync
   * of that pair runs (uploads, downloads, deletions, conflict policy); without
   * one, only pending dirty files are pushed. Returns true when the Hub accepted
   * the request.
   */
  suspend fun triggerSync(pairId: Long? = null): Boolean = withContext(Dispatchers.IO) {
    try {
      val extras = Bundle().apply {
        if (pairId != null) putLong(SyncHubContract.EXTRA_PAIR_ID, pairId)
      }
      val result = contentResolver.call(
        SyncHubContract.AUTHORITY_URI,
        SyncHubContract.METHOD_TRIGGER_SYNC,
        null,
        extras
      )
      result?.getBoolean(SyncHubContract.RESULT_SUCCESS, false) ?: false
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Retrieves Hub statistics (dirty files count, active pair count).
   */
  suspend fun getHubStats(): ClientHubStats = withContext(Dispatchers.IO) {
    try {
      val result = contentResolver.call(
        SyncHubContract.AUTHORITY_URI,
        SyncHubContract.METHOD_GET_STATS,
        null,
        null
      )
      val dirty = result?.getInt(SyncHubContract.RESULT_DIRTY_COUNT, 0) ?: 0
      val pairs = result?.getInt("pair_count", 0) ?: 0
      ClientHubStats(dirty, pairs)
    } catch (e: Exception) {
      ClientHubStats(0, 0)
    }
  }

  /**
   * Observes live changes to cached files for a specific Sync Pair using [ContentObserver].
   */
  fun observeFiles(pairId: Long): Flow<List<ClientFileRecord>> = callbackFlow {
    val uri = SyncHubContract.getFilesForPairUri(pairId)
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean, uri: Uri?) {
        trySend(Unit)
      }
    }

    contentResolver.registerContentObserver(uri, true, observer)
    trySend(Unit) // Emit initial

    awaitClose {
      contentResolver.unregisterContentObserver(observer)
    }
  }.map {
    listFiles(pairId)
  }.flowOn(Dispatchers.IO)
}
