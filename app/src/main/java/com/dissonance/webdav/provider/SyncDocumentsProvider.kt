package com.dissonance.webdav.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.dissonance.webdav.R
import com.dissonance.webdav.data.db.AppDatabase
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.sync.LocalFileManager
import com.dissonance.webdav.sync.work.SyncWorkScheduler
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Storage Access Framework (SAF) DocumentsProvider for WebDAV Sync.
 *
 * Exposes every configured sync pair (and its synchronized file records) as a
 * unified tree in Android's Files app and document pickers. Bytes are streamed
 * straight from/to the real device folders behind each pair's SAF grant; writes
 * are flagged dirty and pushed to WebDAV by the background worker.
 */
class SyncDocumentsProvider : DocumentsProvider() {

  private lateinit var database: AppDatabase
  private lateinit var localFileManager: LocalFileManager

  companion object {
    private const val ROOT_ID = "webdav_root"
    private const val DOC_ID_ROOT = "doc_root"

    private val DEFAULT_ROOT_PROJECTION = arrayOf(
      Root.COLUMN_ROOT_ID,
      Root.COLUMN_MIME_TYPES,
      Root.COLUMN_FLAGS,
      Root.COLUMN_ICON,
      Root.COLUMN_TITLE,
      Root.COLUMN_SUMMARY,
      Root.COLUMN_DOCUMENT_ID,
      Root.COLUMN_AVAILABLE_BYTES
    )

    private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_LAST_MODIFIED,
      Document.COLUMN_FLAGS,
      Document.COLUMN_SIZE
    )
  }

  override fun onCreate(): Boolean {
    val ctx = context ?: return false
    database = AppDatabase.getInstance(ctx)
    localFileManager = LocalFileManager(ctx)
    return true
  }

  override fun queryRoots(projection: Array<out String>?): Cursor {
    val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

    val flags = Root.FLAG_SUPPORTS_RECENTS or Root.FLAG_SUPPORTS_SEARCH

    val row = result.newRow()
    row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
    row.add(Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
    row.add(Root.COLUMN_TITLE, context?.getString(R.string.documents_provider_root_title) ?: "WebDAV Sync")
    row.add(Root.COLUMN_SUMMARY, "Device folders mirrored to WebDAV")
    row.add(Root.COLUMN_FLAGS, flags)
    row.add(Root.COLUMN_MIME_TYPES, "*/*")
    row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)

    return result
  }

  override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
    val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
    includeDocument(result, documentId)
    return result
  }

  override fun queryChildDocuments(
    parentDocumentId: String,
    projection: Array<out String>?,
    sortOrder: String?
  ): Cursor {
    val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

    when {
      parentDocumentId == DOC_ID_ROOT -> {
        val pairs = runBlocking { database.syncPairDao().getAllPairsSync() }
        for (pair in pairs) {
          val row = result.newRow()
          row.add(Document.COLUMN_DOCUMENT_ID, "pair_${pair.id}")
          row.add(Document.COLUMN_DISPLAY_NAME, pair.name)
          row.add(Document.COLUMN_SIZE, pair.totalBytes)
          row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
          row.add(Document.COLUMN_LAST_MODIFIED, pair.lastSyncTimestamp)
          row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
        }
      }

      parentDocumentId.startsWith("pair_") -> {
        val pairId = parentDocumentId.removePrefix("pair_").toLongOrNull() ?: return result
        val files = runBlocking { database.syncedFileRecordDao().getFilesForPairSync(pairId) }
        for (file in files) {
          // Top-level entries only: deeper records appear when browsing their parent directory.
          if (!file.relativePath.removePrefix("/").contains('/')) {
            includeFileRecord(result, file)
          }
        }
      }

      parentDocumentId.startsWith("file_") -> {
        val fileId = parentDocumentId.removePrefix("file_").toLongOrNull()
        if (fileId != null) {
          val parentRecord = runBlocking { database.syncedFileRecordDao().getFileById(fileId) }
          if (parentRecord != null && parentRecord.isDirectory) {
            val prefix = "${parentRecord.relativePath.trimEnd('/')}/"
            val files = runBlocking { database.syncedFileRecordDao().getFilesForPairSync(parentRecord.syncPairId) }
            for (file in files) {
              if (file.relativePath.startsWith(prefix) &&
                !file.relativePath.removePrefix(prefix).contains('/') &&
                file.id != parentRecord.id
              ) {
                includeFileRecord(result, file)
              }
            }
          }
        }
      }
    }

    return result
  }

  override fun openDocument(
    documentId: String,
    mode: String,
    signal: CancellationSignal?
  ): ParcelFileDescriptor {
    val ctx = context ?: throw FileNotFoundException("Context is null")
    val record = recordFor(documentId)
    if (record.isDirectory) throw FileNotFoundException("$documentId is a directory")
    val folder = folderFor(record) ?: throw FileNotFoundException("No device folder for $documentId")

    val isWrite = mode.contains("w") || mode.contains("a")
    return if (isWrite) {
      val pfd = folder.openWritePipe(record.relativePath) {
        runBlocking {
          markDirty(record, ctx)
          SyncWorkScheduler.scheduleImmediatePush(ctx)
        }
      }
      pfd ?: throw FileNotFoundException("Cannot open document $documentId for writing")
    } else {
      folder.openReadPipe(record.relativePath)
        ?: throw FileNotFoundException("Cannot open document $documentId")
    }
  }

  override fun createDocument(
    parentDocumentId: String,
    mimeType: String,
    displayName: String
  ): String {
    val ctx = context ?: throw FileNotFoundException("Context is null")
    val isDir = mimeType == Document.MIME_TYPE_DIR

    val (pairId, parentPath) = resolveParentInfo(parentDocumentId)
    val relativePath = if (parentPath.isEmpty()) "/$displayName" else "$parentPath/$displayName"

    val folder = runBlocking {
      database.syncPairDao().getPairById(pairId)?.let { localFileManager.folderFor(it) }
    } ?: throw FileNotFoundException("No device folder for $parentDocumentId")

    if (isDir) {
      // Represented lazily: create a directory record so it appears in the tree.
      // The physical folder is created on first file write inside it.
      val record = SyncedFileRecord(
        syncPairId = pairId,
        relativePath = relativePath,
        fileName = displayName,
        isDirectory = true,
        localLastModified = System.currentTimeMillis(),
        syncState = FileSyncState.LOCAL_MODIFIED
      )
      val recordId = runBlocking { database.syncedFileRecordDao().insertOrUpdateFile(record) }
      notifyPairChanged(pairId, ctx)
      return "file_$recordId"
    }

    if (!runBlocking { folder.writeFile(relativePath, ByteArray(0)) }) {
      throw FileNotFoundException("Could not create $displayName")
    }

    val stat = runBlocking { folder.statFile(relativePath) }
    val record = SyncedFileRecord(
      syncPairId = pairId,
      relativePath = relativePath,
      fileName = stat?.name ?: displayName,
      isDirectory = false,
      localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
      fileSize = stat?.size ?: 0L,
      isOfflineAvailable = true,
      syncState = FileSyncState.LOCAL_MODIFIED,
      isDirty = true,
      dirtyAction = "UPLOAD"
    )
    val recordId = runBlocking { database.syncedFileRecordDao().insertOrUpdateFile(record) }
    SyncWorkScheduler.scheduleImmediatePush(ctx)
    notifyPairChanged(pairId, ctx)
    return "file_$recordId"
  }

  override fun deleteDocument(documentId: String) {
    val ctx = context ?: return
    val record = recordFor(documentId)
    val folder = folderFor(record)

    runBlocking {
      if (folder != null && folder.hasAccess()) {
        folder.deleteFile(record.relativePath)
      }
      if (record.etag.isNotEmpty() || record.isDirty) {
        // Known upstream: mark for remote deletion; otherwise drop the record outright.
        database.syncedFileRecordDao().insertOrUpdateFile(
          record.copy(isDirty = true, dirtyAction = "DELETE", syncState = FileSyncState.LOCAL_MODIFIED)
        )
      } else {
        database.syncedFileRecordDao().deleteFile(record.syncPairId, record.relativePath)
      }
      SyncWorkScheduler.scheduleImmediatePush(ctx)
    }
    notifyPairChanged(record.syncPairId, ctx)
  }

  override fun renameDocument(documentId: String, displayName: String): String {
    val ctx = context ?: throw FileNotFoundException("Context is null")
    val record = recordFor(documentId)
    val folder = folderFor(record) ?: throw FileNotFoundException("No device folder for $documentId")
    val dir = record.relativePath.substringBeforeLast('/')
    val newPath = if (dir.isNotEmpty() && dir != record.relativePath) "$dir/$displayName" else "/$displayName"

    val bytes = runBlocking { folder.readFile(record.relativePath) }
      ?: throw FileNotFoundException("Cannot read ${record.fileName}")

    runBlocking {
      if (!folder.writeFile(newPath, bytes)) throw FileNotFoundException("Cannot create $displayName")
      folder.deleteFile(record.relativePath)
    }

    runBlocking {
      // Old path disappears upstream, new path is pushed.
      database.syncedFileRecordDao().insertOrUpdateFile(
        record.copy(isDirty = true, dirtyAction = "DELETE", syncState = FileSyncState.LOCAL_MODIFIED)
      )
      database.syncedFileRecordDao().insertOrUpdateFile(
        record.copy(
          id = 0L,
          relativePath = newPath,
          fileName = displayName,
          isDirty = true,
          dirtyAction = "UPLOAD",
          syncState = FileSyncState.LOCAL_MODIFIED
        )
      )
      SyncWorkScheduler.scheduleImmediatePush(ctx)
    }
    notifyPairChanged(record.syncPairId, ctx)
    return documentId
  }

  // ---- helpers ----------------------------------------------------------

  private fun folderFor(record: SyncedFileRecord) = runBlocking {
    database.syncPairDao().getPairById(record.syncPairId)?.let { localFileManager.folderFor(it) }
  }

  private fun recordFor(documentId: String): SyncedFileRecord {
    val fileId = documentId.removePrefix("file_").toLongOrNull()
      ?: throw FileNotFoundException("Unknown document $documentId")
    return runBlocking { database.syncedFileRecordDao().getFileById(fileId) }
      ?: throw FileNotFoundException("Unknown document $documentId")
  }

  private suspend fun markDirty(record: SyncedFileRecord, ctx: android.content.Context) {
    val stat = folderFor(record)?.statFile(record.relativePath)
    database.syncedFileRecordDao().insertOrUpdateFile(
      record.copy(
        localLastModified = stat?.lastModified ?: System.currentTimeMillis(),
        fileSize = stat?.size ?: record.fileSize,
        isDirty = true,
        dirtyAction = "UPLOAD",
        syncState = FileSyncState.LOCAL_MODIFIED
      )
    )
    notifyPairChanged(record.syncPairId, ctx)
  }

  private fun notifyPairChanged(pairId: Long, ctx: android.content.Context) {
    ctx.contentResolver.notifyChange(
      com.dissonance.webdav.provider.SyncHubContract.getFilesForPairUri(pairId), null
    )
  }

  private fun includeDocument(result: MatrixCursor, documentId: String) {
    if (documentId == DOC_ID_ROOT) {
      // The root only lists pair folders; file creation happens inside a pair.
      val row = result.newRow()
      row.add(Document.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
      row.add(Document.COLUMN_DISPLAY_NAME, "WebDAV Sync")
      row.add(Document.COLUMN_SIZE, 0L)
      row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
      row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
      row.add(Document.COLUMN_FLAGS, 0)
      return
    }

    if (documentId.startsWith("pair_")) {
      val pairId = documentId.removePrefix("pair_").toLongOrNull() ?: return
      val pair = runBlocking { database.syncPairDao().getPairById(pairId) }
      if (pair != null) {
        val row = result.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(Document.COLUMN_DISPLAY_NAME, pair.name)
        row.add(Document.COLUMN_SIZE, pair.totalBytes)
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_LAST_MODIFIED, pair.lastSyncTimestamp)
        row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
      }
      return
    }

    if (documentId.startsWith("file_")) {
      val fileId = documentId.removePrefix("file_").toLongOrNull() ?: return
      val record = runBlocking { database.syncedFileRecordDao().getFileById(fileId) }
      if (record != null) {
        includeFileRecord(result, record)
      }
    }
  }

  private fun includeFileRecord(result: MatrixCursor, record: SyncedFileRecord) {
    val mimeType = if (record.isDirectory) {
      Document.MIME_TYPE_DIR
    } else {
      getMimeTypeForName(record.fileName)
    }

    val flags = if (record.isDirectory) {
      Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
    } else {
      Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
    }

    val row = result.newRow()
    row.add(Document.COLUMN_DOCUMENT_ID, "file_${record.id}")
    row.add(Document.COLUMN_DISPLAY_NAME, record.fileName)
    row.add(Document.COLUMN_SIZE, record.fileSize)
    row.add(Document.COLUMN_MIME_TYPE, mimeType)
    row.add(Document.COLUMN_LAST_MODIFIED, record.localLastModified)
    row.add(Document.COLUMN_FLAGS, flags)
  }

  private fun resolveParentInfo(parentDocId: String): Pair<Long, String> {
    if (parentDocId == DOC_ID_ROOT) return Pair(1L, "")
    if (parentDocId.startsWith("pair_")) {
      val pairId = parentDocId.removePrefix("pair_").toLongOrNull() ?: 1L
      return Pair(pairId, "")
    }
    if (parentDocId.startsWith("file_")) {
      val fileId = parentDocId.removePrefix("file_").toLongOrNull() ?: 1L
      val record = runBlocking { database.syncedFileRecordDao().getFileById(fileId) }
      return Pair(record?.syncPairId ?: 1L, record?.relativePath ?: "")
    }
    return Pair(1L, "")
  }

  private fun getMimeTypeForName(name: String): String {
    val ext = MimeTypeMap.getFileExtensionFromUrl(name)
    return if (ext.isNotEmpty()) {
      MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
    } else {
      "application/octet-stream"
    }
  }
}
