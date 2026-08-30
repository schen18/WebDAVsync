package com.dissonance.webdav.provider

import android.net.Uri
import android.provider.BaseColumns

/**
 * Public IPC Contract for the WebDAV Sync Hub ContentProvider and DocumentsProvider.
 *
 * The Hub operates on real device folders granted through SAF document trees
 * (one per sync pair): relative paths in URIs and columns are relative to the
 * pair's granted folder root, e.g. "/notes/idea.md". There is no app-private
 * copy of the files.
 *
 * Client apps signed with the same developer certificate can use these URIs and
 * column definitions with [android.content.ContentResolver] or the
 * [com.dissonance.webdav.client.SyncHubClient] SDK.
 */
object SyncHubContract {

  const val AUTHORITY = "com.dissonance.webdav.provider.synchub"
  val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

  const val DOCUMENTS_AUTHORITY = "com.dissonance.webdav.provider.documents"

  /** Signature-level permission required to access the Hub Provider */
  const val PERMISSION_ACCESS = "com.dissonance.webdav.permission.ACCESS_SYNC_HUB"

  // Base URIs
  /** All sync pairs. */
  val PAIRS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "pairs")

  /** All file records across every pair (not only dirty ones; see [DIRTY_FILES_URI]). */
  val FILES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "files")

  /** Records with pending local changes queued for upload/deletion. */
  val DIRTY_FILES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "dirty")
  val SYNC_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "sync")
  val STATUS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "status")

  /** Build URI for files in a specific Sync Pair */
  fun getFilesForPairUri(pairId: Long): Uri =
    Uri.withAppendedPath(AUTHORITY_URI, "files/$pairId")

  /**
   * Build URI for a specific file by Sync Pair and relative path. The path is
   * percent-encoded into a single segment; providers also accept manually built
   * URIs with real separators (`file/{pairId}/sub/file.md`).
   */
  fun getFileItemUri(pairId: Long, relativePath: String): Uri {
    val cleanPath = relativePath.trim().trimStart('/')
    return Uri.withAppendedPath(AUTHORITY_URI, "file/$pairId/${Uri.encode(cleanPath)}")
  }

  /** Build URI to trigger sync for a specific pair */
  fun getTriggerSyncUri(pairId: Long): Uri =
    Uri.withAppendedPath(AUTHORITY_URI, "sync/$pairId")

  // MIME Types
  const val MIME_DIR_SYNC_PAIRS = "vnd.android.cursor.dir/vnd.com.dissonance.webdav.sync_pair"
  const val MIME_ITEM_SYNC_PAIR = "vnd.android.cursor.item/vnd.com.dissonance.webdav.sync_pair"
  const val MIME_DIR_FILE_RECORDS = "vnd.android.cursor.dir/vnd.com.dissonance.webdav.file_record"
  const val MIME_ITEM_FILE_RECORD = "vnd.android.cursor.item/vnd.com.dissonance.webdav.file_record"

  // Custom Call Methods
  /**
   * `trigger_sync`: run a full delta sync for the pair given in [EXTRA_PAIR_ID]
   * (uploads, downloads, deletions, conflict policy). Without a pair id, only
   * the dirty-file push worker is enqueued.
   */
  const val METHOD_TRIGGER_SYNC = "trigger_sync"

  /** `push_dirty`: enqueue the WorkManager job that pushes dirty files upstream. */
  const val METHOD_PUSH_DIRTY = "push_dirty"
  const val METHOD_GET_STATS = "get_stats"
  const val EXTRA_PAIR_ID = "extra_pair_id"
  const val EXTRA_FORCE = "extra_force"
  const val RESULT_SUCCESS = "result_success"
  const val RESULT_DIRTY_COUNT = "result_dirty_count"
  const val RESULT_MESSAGE = "result_message"

  // Columns for Sync Pairs
  object PairsColumns : BaseColumns {
    const val _ID = BaseColumns._ID
    const val SERVER_ID = "serverId"
    const val NAME = "name"

    /** Display name of the granted device folder (SAF tree). */
    const val LOCAL_FOLDER_NAME = "localFolderName"

    /** Same display name as [LOCAL_FOLDER_NAME]; kept for API compatibility. The underlying vault-path semantics were removed with the SAF migration. */
    const val LOCAL_RELATIVE_PATH = "localRelativePath"
    const val REMOTE_RELATIVE_PATH = "remoteRelativePath"
    const val SYNC_DIRECTION = "syncDirection"
    const val CONFLICT_POLICY = "conflictPolicy"
    const val SYNC_INTERVAL_MINUTES = "syncIntervalMinutes"
    const val IS_ENABLED = "isEnabled"
    const val LAST_SYNC_TIMESTAMP = "lastSyncTimestamp"
    const val LAST_SYNC_STATUS = "lastSyncStatus"
    const val LAST_SYNC_MESSAGE = "lastSyncMessage"
    const val FILE_COUNT = "fileCount"
    const val TOTAL_BYTES = "totalBytes"
  }

  // Columns for File Records
  object FilesColumns : BaseColumns {
    const val _ID = BaseColumns._ID
    const val SYNC_PAIR_ID = "syncPairId"

    /** Path relative to the pair's granted device folder, leading slash, e.g. "/sub/file.md". */
    const val RELATIVE_PATH = "relativePath"
    const val FILE_NAME = "fileName"
    const val IS_DIRECTORY = "isDirectory"
    const val LOCAL_LAST_MODIFIED = "localLastModified"
    const val REMOTE_LAST_MODIFIED = "remoteLastModified"
    const val FILE_SIZE = "fileSize"
    const val ETAG = "etag"
    const val IS_OFFLINE_AVAILABLE = "isOfflineAvailable"
    const val SYNC_STATE = "syncState"
    const val IS_DIRTY = "isDirty"
    const val DIRTY_ACTION = "dirtyAction"
    const val MIME_TYPE = "mimeType"
    const val PREVIEW_SNIPPET = "previewSnippet"
  }
}
