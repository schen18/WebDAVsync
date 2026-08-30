package com.dissonance.webdav.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Produces [LocalFolderAccess] handles for sync pairs, backed by SAF
 * document-tree grants on real device storage (photos, downloads, any
 * provider folder). There is no app-private storage area anymore: all synced
 * files live in folders the user explicitly granted through the system
 * folder picker.
 */
class LocalFileManager(private val context: Context) {

  private val resolver = context.applicationContext.contentResolver

  /** Folder handle for a pair, or null when no SAF tree grant is configured. */
  fun folderFor(pair: com.dissonance.webdav.data.model.SyncPair): LocalFolderAccess? {
    val uri = pair.localTreeUri.toUriSafe() ?: return null
    return SafFolderAccess(context, uri)
  }

  /** Human-readable name of a granted tree (e.g. "DCIM"), or null. */
  fun folderDisplayName(treeUriString: String): String? {
    val uri = treeUriString.toUriSafe() ?: return null
    return try {
      val rootDocId = DocumentsContract.getTreeDocumentId(uri)
      resolver.query(
        DocumentsContract.buildDocumentUriUsingTree(uri, rootDocId),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null
      )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
      null
    }
  }

  /** Persist read/write access to a freshly granted tree Uri. */
  fun persistGrant(treeUri: Uri): Boolean {
    return try {
      resolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
      true
    } catch (_: Exception) {
      false
    }
  }

  /** Release a previously persisted grant (pair deleted / app reset). */
  fun releaseGrant(treeUriString: String) {
    val uri = treeUriString.toUriSafe() ?: return
    try {
      resolver.releasePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      )
    } catch (_: Exception) {}
  }

  private fun String.toUriSafe(): Uri? {
    if (isBlank()) return null
    return try {
      val uri = Uri.parse(this)
      if (uri.scheme == "content" && uri.pathSegments.firstOrNull() == "tree") uri else null
    } catch (_: Exception) {
      null
    }
  }
}
