package com.dissonance.webdav.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dissonance.webdav.data.db.AppDatabase
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.repository.SyncRepository
import com.dissonance.webdav.sync.LocalFileManager
import com.dissonance.webdav.sync.work.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Custom secured ContentProvider exposing structured IPC for client companion apps.
 *
 * Enforces signature-level security permission `com.dissonance.webdav.permission.ACCESS_SYNC_HUB`.
 * Allows companion apps to perform CRUD operations on the real device folders behind
 * each pair's SAF grant, stream bytes directly through [ParcelFileDescriptor], flag
 * records as dirty, and trigger background synchronization.
 */
class SyncHubProvider : ContentProvider() {

  private val TAG = "SyncHubProvider"

  private lateinit var database: AppDatabase
  private lateinit var localFileManager: LocalFileManager

  companion object {
    private const val MATCH_PAIRS = 100
    private const val MATCH_PAIR_ID = 101
    private const val MATCH_FILES = 200
    private const val MATCH_FILES_PAIR = 201
    private const val MATCH_FILE_ITEM = 202
    private const val MATCH_DIRTY = 300
    private const val MATCH_SYNC = 400
    private const val MATCH_SYNC_PAIR = 401

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(SyncHubContract.AUTHORITY, "pairs", MATCH_PAIRS)
      addURI(SyncHubContract.AUTHORITY, "pairs/#", MATCH_PAIR_ID)
      addURI(SyncHubContract.AUTHORITY, "files", MATCH_FILES)
      addURI(SyncHubContract.AUTHORITY, "files/#", MATCH_FILES_PAIR)
      addURI(SyncHubContract.AUTHORITY, "file/#/*", MATCH_FILE_ITEM)
      addURI(SyncHubContract.AUTHORITY, "dirty", MATCH_DIRTY)
      addURI(SyncHubContract.AUTHORITY, "sync", MATCH_SYNC)
      addURI(SyncHubContract.AUTHORITY, "sync/#", MATCH_SYNC_PAIR)
    }
  }

  override fun onCreate(): Boolean {
    val ctx = context ?: return false
    database = AppDatabase.getInstance(ctx)
    localFileManager = LocalFileManager(ctx)
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor? {
    val ctx = context ?: return null
    return runBlocking {
      when (match(uri)) {
        MATCH_PAIRS -> {
          val cursor = MatrixCursor(
            arrayOf(
              SyncHubContract.PairsColumns._ID,
              SyncHubContract.PairsColumns.SERVER_ID,
              SyncHubContract.PairsColumns.NAME,
              SyncHubContract.PairsColumns.LOCAL_FOLDER_NAME,
              SyncHubContract.PairsColumns.LOCAL_RELATIVE_PATH,
              SyncHubContract.PairsColumns.REMOTE_RELATIVE_PATH,
              SyncHubContract.PairsColumns.SYNC_DIRECTION,
              SyncHubContract.PairsColumns.CONFLICT_POLICY,
              SyncHubContract.PairsColumns.SYNC_INTERVAL_MINUTES,
              SyncHubContract.PairsColumns.IS_ENABLED,
              SyncHubContract.PairsColumns.LAST_SYNC_TIMESTAMP,
              SyncHubContract.PairsColumns.LAST_SYNC_STATUS,
              SyncHubContract.PairsColumns.LAST_SYNC_MESSAGE,
              SyncHubContract.PairsColumns.FILE_COUNT,
              SyncHubContract.PairsColumns.TOTAL_BYTES
            )
          )

          val pairs = database.syncPairDao().getAllPairsSync()
          for (pair in pairs) {
            cursor.addRow(
              arrayOf<Any?>(
                pair.id,
                pair.serverId,
                pair.name,
                pair.localFolderName,
                pair.localFolderName,
                pair.remoteRelativePath,
                pair.syncDirection.name,
                pair.conflictPolicy.name,
                pair.syncIntervalMinutes,
                if (pair.isEnabled) 1 else 0,
                pair.lastSyncTimestamp,
                pair.lastSyncStatus.name,
                pair.lastSyncMessage,
                pair.fileCount,
                pair.totalBytes
              )
            )
          }
          cursor.setNotificationUri(ctx.contentResolver, uri)
          cursor
        }

        MATCH_PAIR_ID -> {
          val pairId = ContentUris.parseId(uri)
          val pair = database.syncPairDao().getPairById(pairId) ?: return@runBlocking null
          val cursor = MatrixCursor(
            arrayOf(
              SyncHubContract.PairsColumns._ID,
              SyncHubContract.PairsColumns.SERVER_ID,
              SyncHubContract.PairsColumns.NAME,
              SyncHubContract.PairsColumns.LOCAL_RELATIVE_PATH,
              SyncHubContract.PairsColumns.REMOTE_RELATIVE_PATH,
              SyncHubContract.PairsColumns.LAST_SYNC_STATUS
            )
          )
          cursor.addRow(
            arrayOf<Any?>(
              pair.id,
              pair.serverId,
              pair.name,
              pair.localFolderName,
              pair.remoteRelativePath,
              pair.lastSyncStatus.name
            )
          )
          cursor.setNotificationUri(ctx.contentResolver, uri)
          cursor
        }

        MATCH_FILES -> {
          val files = database.syncedFileRecordDao().getAllFiles()
          buildFileRecordsCursor(files, ctx, uri)
        }

        MATCH_FILES_PAIR -> {
          val pairId = ContentUris.parseId(uri)
          val files = database.syncedFileRecordDao().getFilesForPairSync(pairId)
          buildFileRecordsCursor(files, ctx, uri)
        }

        MATCH_FILE_ITEM -> {
          val (pairId, relativePath) = parseFileItemUri(uri) ?: return@runBlocking null
          val file = database.syncedFileRecordDao().getFileByPath(pairId, relativePath)
          val files = if (file != null) listOf(file) else emptyList()
          buildFileRecordsCursor(files, ctx, uri)
        }

        MATCH_DIRTY -> {
          val files = database.syncedFileRecordDao().getAllDirtyRecords()
          buildFileRecordsCursor(files, ctx, uri)
        }

        else -> null
      }
    }
  }

  private fun buildFileRecordsCursor(
    files: List<SyncedFileRecord>,
    ctx: android.content.Context,
    uri: Uri
  ): MatrixCursor {
    val cursor = MatrixCursor(
      arrayOf(
        SyncHubContract.FilesColumns._ID,
        SyncHubContract.FilesColumns.SYNC_PAIR_ID,
        SyncHubContract.FilesColumns.RELATIVE_PATH,
        SyncHubContract.FilesColumns.FILE_NAME,
        SyncHubContract.FilesColumns.IS_DIRECTORY,
        SyncHubContract.FilesColumns.LOCAL_LAST_MODIFIED,
        SyncHubContract.FilesColumns.REMOTE_LAST_MODIFIED,
        SyncHubContract.FilesColumns.FILE_SIZE,
        SyncHubContract.FilesColumns.ETAG,
        SyncHubContract.FilesColumns.IS_OFFLINE_AVAILABLE,
        SyncHubContract.FilesColumns.SYNC_STATE,
        SyncHubContract.FilesColumns.IS_DIRTY,
        SyncHubContract.FilesColumns.DIRTY_ACTION,
        SyncHubContract.FilesColumns.PREVIEW_SNIPPET
      )
    )

    for (file in files) {
      cursor.addRow(
        arrayOf<Any?>(
          file.id,
          file.syncPairId,
          file.relativePath,
          file.fileName,
          if (file.isDirectory) 1 else 0,
          file.localLastModified,
          file.remoteLastModified,
          file.fileSize,
          file.etag,
          if (file.isOfflineAvailable) 1 else 0,
          file.syncState.name,
          if (file.isDirty) 1 else 0,
          file.dirtyAction,
          file.previewSnippet
        )
      )
    }
    cursor.setNotificationUri(ctx.contentResolver, uri)
    return cursor
  }

  override fun getType(uri: Uri): String? {
    return when (match(uri)) {
      MATCH_PAIRS -> SyncHubContract.MIME_DIR_SYNC_PAIRS
      MATCH_PAIR_ID -> SyncHubContract.MIME_ITEM_SYNC_PAIR
      MATCH_FILES, MATCH_FILES_PAIR, MATCH_DIRTY -> SyncHubContract.MIME_DIR_FILE_RECORDS
      MATCH_FILE_ITEM -> SyncHubContract.MIME_ITEM_FILE_RECORD
      else -> null
    }
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    val ctx = context ?: return null
    if (values == null) return null

    return runBlocking {
      when (match(uri)) {
        MATCH_FILES, MATCH_FILES_PAIR -> {
          // A pair must be identified explicitly: by the URI or the SYNC_PAIR_ID value.
          val pairId = if (uriMatcher.match(uri) == MATCH_FILES_PAIR) {
            ContentUris.parseId(uri)
          } else {
            values.getAsLong(SyncHubContract.FilesColumns.SYNC_PAIR_ID) ?: return@runBlocking null
          }

          val pair = database.syncPairDao().getPairById(pairId) ?: return@runBlocking null
          val folder = localFileManager.folderFor(pair) ?: return@runBlocking null

          val relativePath = normalize(values.getAsString(SyncHubContract.FilesColumns.RELATIVE_PATH))
            ?: return@runBlocking null
          val fileName = values.getAsString(SyncHubContract.FilesColumns.FILE_NAME) ?: File(relativePath).name
          val isDirectory = values.getAsBoolean(SyncHubContract.FilesColumns.IS_DIRECTORY) ?: false
          val initialContent = values.getAsByteArray("initial_bytes")

          if (isDirectory) {
            // Directory records are materialized lazily on first file write inside them.
            val record = SyncedFileRecord(
              syncPairId = pairId,
              relativePath = relativePath,
              fileName = fileName,
              isDirectory = true,
              localLastModified = System.currentTimeMillis(),
              syncState = FileSyncState.LOCAL_MODIFIED
            )
            val recordId = database.syncedFileRecordDao().insertOrUpdateFile(record)
            notifyFilesChanged(ctx, pairId)
            return@runBlocking SyncHubContract.getFileItemUri(pairId, relativePath)
          }

          val payload = initialContent ?: ByteArray(0)
          if (!folder.writeFile(relativePath, payload)) return@runBlocking null
          val stat = folder.statFile(relativePath)

          val record = SyncedFileRecord(
            syncPairId = pairId,
            relativePath = relativePath,
            fileName = stat?.name ?: fileName,
            localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
            fileSize = stat?.size ?: payload.size.toLong(),
            isOfflineAvailable = true,
            syncState = FileSyncState.LOCAL_MODIFIED,
            isDirty = true,
            dirtyAction = "UPLOAD"
          )

          val recordId = database.syncedFileRecordDao().insertOrUpdateFile(record)

          notifyFilesChanged(ctx, pairId)
          ctx.contentResolver.notifyChange(uri, null)

          // Trigger background push worker
          SyncWorkScheduler.scheduleImmediatePush(ctx)

          SyncHubContract.getFileItemUri(pairId, relativePath)
        }

        else -> null
      }
    }
  }

  /** Notify the URIs clients observe for record changes (pair list, all files, dirty queue). */
  private fun notifyFilesChanged(ctx: android.content.Context, pairId: Long) {
    ctx.contentResolver.notifyChange(SyncHubContract.getFilesForPairUri(pairId), null)
    ctx.contentResolver.notifyChange(SyncHubContract.FILES_URI, null)
    ctx.contentResolver.notifyChange(SyncHubContract.DIRTY_FILES_URI, null)
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int {
    val ctx = context ?: return 0
    return runBlocking {
      when (match(uri)) {
        MATCH_FILE_ITEM -> {
          val (pairId, relativePath) = parseFileItemUri(uri) ?: return@runBlocking 0

          val pair = database.syncPairDao().getPairById(pairId) ?: return@runBlocking 0
          val folder = localFileManager.folderFor(pair)
          val stat = folder?.statFile(relativePath)
          val existing = database.syncedFileRecordDao().getFileByPath(pairId, relativePath)

          if (existing != null) {
            database.syncedFileRecordDao().insertOrUpdateFile(
              existing.copy(
                localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
                fileSize = stat?.size ?: existing.fileSize,
                isDirty = true,
                dirtyAction = "UPLOAD",
                syncState = FileSyncState.LOCAL_MODIFIED
              )
            )
          } else if (stat != null) {
            // Unknown file that exists in the granted folder: register it as dirty.
            database.syncedFileRecordDao().insertOrUpdateFile(
              SyncedFileRecord(
                syncPairId = pairId,
                relativePath = relativePath,
                fileName = stat.name,
                localLastModified = stat.lastModified,
                fileSize = stat.size,
                isOfflineAvailable = true,
                isDirty = true,
                dirtyAction = "UPLOAD",
                syncState = FileSyncState.LOCAL_MODIFIED
              )
            )
          }

          notifyFilesChanged(ctx, pairId)
          ctx.contentResolver.notifyChange(uri, null)

          SyncWorkScheduler.scheduleImmediatePush(ctx)
          1
        }

        else -> 0
      }
    }
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    val ctx = context ?: return 0
    return runBlocking {
      when (match(uri)) {
        MATCH_FILE_ITEM -> {
          val (pairId, relativePath) = parseFileItemUri(uri) ?: return@runBlocking 0

          val pair = database.syncPairDao().getPairById(pairId) ?: return@runBlocking 0
          val folder = localFileManager.folderFor(pair)

          // Delete on the device, then mark dirty as DELETE so WorkManager removes it upstream
          folder?.deleteFile(relativePath)

          val existing = database.syncedFileRecordDao().getFileByPath(pairId, relativePath)
          if (existing != null) {
            if (existing.etag.isNotEmpty() || existing.isDirty) {
              database.syncedFileRecordDao().insertOrUpdateFile(
                existing.copy(
                  isDirty = true,
                  dirtyAction = "DELETE",
                  syncState = FileSyncState.LOCAL_MODIFIED
                )
              )
            } else {
              database.syncedFileRecordDao().deleteFile(pairId, relativePath)
            }
          }

          notifyFilesChanged(ctx, pairId)
          ctx.contentResolver.notifyChange(uri, null)

          SyncWorkScheduler.scheduleImmediatePush(ctx)
          1
        }

        else -> 0
      }
    }
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val ctx = context ?: throw FileNotFoundException("Context is null")

    when (match(uri)) {
      MATCH_FILE_ITEM -> {
        val (pairId, relativePath) = parseFileItemUri(uri)
          ?: throw FileNotFoundException("Invalid file URI: $uri")

        val pair = runBlocking { database.syncPairDao().getPairById(pairId) }
          ?: throw FileNotFoundException("Unknown pair $pairId")
        val folder = localFileManager.folderFor(pair)
          ?: throw FileNotFoundException("No device folder granted for pair $pairId")

        val isWrite = mode.contains("w") || mode.contains("a")
        return if (isWrite) {
          val pfd = folder.openWritePipe(relativePath) {
            runBlocking { flagDirtyAfterWrite(ctx, pairId, relativePath) }
          }
          pfd ?: throw FileNotFoundException("Could not open file at $relativePath")
        } else {
          folder.openReadPipe(relativePath)
            ?: throw FileNotFoundException("Could not open file at $relativePath")
        }
      }

      else -> throw FileNotFoundException("Unsupported URI for openFile: $uri")
    }
  }

  private suspend fun flagDirtyAfterWrite(ctx: android.content.Context, pairId: Long, relativePath: String) {
    try {
      val folder = database.syncPairDao().getPairById(pairId)?.let { localFileManager.folderFor(it) }
      val stat = folder?.statFile(relativePath)
      val existing = database.syncedFileRecordDao().getFileByPath(pairId, relativePath)
      if (existing != null) {
        database.syncedFileRecordDao().insertOrUpdateFile(
          existing.copy(
            localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
            fileSize = stat?.size ?: existing.fileSize,
            isDirty = true,
            dirtyAction = "UPLOAD",
            syncState = FileSyncState.LOCAL_MODIFIED
          )
        )
      } else {
        database.syncedFileRecordDao().insertOrUpdateFile(
          SyncedFileRecord(
            syncPairId = pairId,
            relativePath = relativePath,
            fileName = File(relativePath).name,
            isDirectory = false,
            localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
            fileSize = stat?.size ?: 0L,
            isOfflineAvailable = true,
            isDirty = true,
            dirtyAction = "UPLOAD",
            syncState = FileSyncState.LOCAL_MODIFIED
          )
        )
      }
      notifyFilesChanged(ctx, pairId)
      ctx.contentResolver.notifyChange(SyncHubContract.getFileItemUri(pairId, relativePath), null)
      SyncWorkScheduler.scheduleImmediatePush(ctx)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to flag dirty after write", e)
    }
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val ctx = context ?: return Bundle()
    val result = Bundle()

    when (method) {
      SyncHubContract.METHOD_TRIGGER_SYNC -> {
        val pairId = extras?.getLong(SyncHubContract.EXTRA_PAIR_ID, -1L)?.takeIf { it > 0 }
        Log.d(TAG, "IPC call: trigger_sync requested for pair: $pairId")
        if (pairId != null) {
          // Real pair sync on a background scope; the scheduler stays off (one-shot engine).
          val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
          scope.launch {
            try {
              val repository = SyncRepository(ctx.applicationContext, scope, autoStartScheduler = false)
              val syncResult = repository.syncEngine.syncPair(pairId)
              Log.d(TAG, "IPC trigger_sync for pair $pairId finished: $syncResult")
            } catch (e: Exception) {
              Log.e(TAG, "IPC trigger_sync for pair $pairId failed", e)
            }
          }
          result.putBoolean(SyncHubContract.RESULT_SUCCESS, true)
          result.putString(SyncHubContract.RESULT_MESSAGE, "Pair sync started")
        } else {
          SyncWorkScheduler.scheduleImmediatePush(ctx)
          result.putBoolean(SyncHubContract.RESULT_SUCCESS, true)
          result.putString(SyncHubContract.RESULT_MESSAGE, "Dirty-file push enqueued (no pair id given)")
        }
      }

      SyncHubContract.METHOD_PUSH_DIRTY -> {
        Log.d(TAG, "IPC call: push_dirty requested")
        SyncWorkScheduler.scheduleImmediatePush(ctx)
        result.putBoolean(SyncHubContract.RESULT_SUCCESS, true)
      }

      SyncHubContract.METHOD_GET_STATS -> {
        runBlocking {
          val dirtyCount = database.syncedFileRecordDao().getDirtyCount()
          val pairs = database.syncPairDao().getAllPairsSync()
          result.putInt(SyncHubContract.RESULT_DIRTY_COUNT, dirtyCount)
          result.putInt("pair_count", pairs.size)
          result.putBoolean(SyncHubContract.RESULT_SUCCESS, true)
        }
      }

      else -> {
        result.putBoolean(SyncHubContract.RESULT_SUCCESS, false)
        result.putString(SyncHubContract.RESULT_MESSAGE, "Unknown method: $method")
      }
    }

    return result
  }

  /**
   * UriMatcher result, with a fallback that also recognizes manually built
   * "file/{id}/{a}/{b}" URIs whose path contains real separators (the matcher
   * pattern only covers the contract's single percent-encoded segment form).
   */
  private fun match(uri: Uri): Int {
    val m = uriMatcher.match(uri)
    if (m != UriMatcher.NO_MATCH) return m
    return if (parseFileItemUri(uri) != null) MATCH_FILE_ITEM else m
  }

  /**
   * Parse "file/{pairId}/{path...}" URIs. Accepts both the contract form
   * (path percent-encoded as a single segment) and manually built URIs with
   * real path separators, e.g. file/1/sub/notes.md.
   */
  private fun parseFileItemUri(uri: Uri): Pair<Long, String>? {
    val segments = uri.pathSegments
    if (segments.firstOrNull() != "file") return null
    val pairId = segments.getOrNull(1)?.toLongOrNull() ?: return null
    if (segments.size < 3) return null
    val relativePath = normalize("/" + segments.drop(2).joinToString("/")) ?: return null
    return Pair(pairId, relativePath)
  }

  private fun normalize(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val cleaned = path.trim().replace('\\', '/').trim('/')
    if (cleaned.split('/').any { it == ".." }) return null
    return "/$cleaned"
  }
}
