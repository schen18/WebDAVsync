package com.dissonance.webdav.sync

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * [LocalFolderAccess] backed by a Storage Access Framework document-tree grant.
 * All device storage locations (photos, downloads, any provider folder) are
 * reachable this way without storage permissions; the app holds a persistable
 * read/write grant on the tree Uri.
 *
 * Document IDs are opaque per provider, so paths are resolved by walking child
 * queries; resolved IDs are cached for the lifetime of the instance.
 */
class SafFolderAccess(
  context: Context,
  private val treeUri: Uri
) : LocalFolderAccess {

  private val resolver = context.applicationContext.contentResolver
  private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

  // tree-relative path (no leading slash) -> document ID
  private val docIds = ConcurrentHashMap<String, String>()

  @Volatile
  private var cachedRootName: String? = null

  override val rootName: String
    get() {
      cachedRootName?.let { return it }
      val name = try {
        queryDocumentName(rootDocId) ?: "Folder"
      } catch (_: Exception) {
        "Folder"
      }
      cachedRootName = name
      return name
    }

  override suspend fun hasAccess(): Boolean = withContext(Dispatchers.IO) {
    try {
      resolver.query(
        DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId),
        arrayOf(Document.COLUMN_DOCUMENT_ID),
        null, null, null
      )?.use { true } ?: false
    } catch (_: SecurityException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  override suspend fun listFiles(): List<LocalFileItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<LocalFileItem>()
    val queue = ArrayDeque<Pair<String, String>>() // document ID to tree-relative path ("" = root)
    queue.add(rootDocId to "")

    while (queue.isNotEmpty()) {
      val (dirDocId, dirPath) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
      val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE
      )
      try {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          while (cursor.moveToNext()) {
            val childDocId = cursor.getString(0) ?: continue
            val name = cursor.getString(1) ?: continue
            val size = cursor.getLong(2)
            val lastModified = cursor.getLong(3)
            val mime = cursor.getString(4) ?: ""
            val isDirectory = mime == Document.MIME_TYPE_DIR
            val childPath = if (dirPath.isEmpty()) name else "$dirPath/$name"
            docIds[childPath] = childDocId
            items.add(
              LocalFileItem(
                relativePath = "/$childPath",
                name = name,
                size = if (isDirectory) 0L else size,
                lastModified = lastModified,
                isDirectory = isDirectory
              )
            )
            if (isDirectory) {
              queue.add(childDocId to childPath)
            }
          }
        }
      } catch (_: SecurityException) {
        // Grant lost mid-walk: report what was collected; hasAccess() gates syncs up front.
      }
    }
    items
  }

  override suspend fun statFile(relativePath: String): LocalFileItem? = withContext(Dispatchers.IO) {
    val docUri = try {
      resolveDocumentUri(relativePath)
    } catch (_: FileNotFoundException) {
      return@withContext null
    }
    try {
      resolver.query(
        docUri,
        arrayOf(
          Document.COLUMN_DISPLAY_NAME,
          Document.COLUMN_SIZE,
          Document.COLUMN_LAST_MODIFIED,
          Document.COLUMN_MIME_TYPE
        ),
        null, null, null
      )?.use { cursor ->
        if (!cursor.moveToFirst()) return@withContext null
        val name = cursor.getString(0) ?: return@withContext null
        val isDirectory = (cursor.getString(3) ?: "") == Document.MIME_TYPE_DIR
        LocalFileItem(
          relativePath = "/" + normalizeKey(relativePath),
          name = name,
          size = if (isDirectory) 0L else cursor.getLong(1),
          lastModified = cursor.getLong(2),
          isDirectory = isDirectory
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun readFile(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
    val docUri = try {
      resolveDocumentUri(relativePath)
    } catch (_: FileNotFoundException) {
      return@withContext null
    }
    try {
      resolver.openInputStream(docUri)?.use { it.readBytes() }
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun writeFile(relativePath: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
    try {
      val docUri = ensureDocumentUri(relativePath)
      resolver.openOutputStream(docUri, "wt")?.use { it.write(data); it.flush() } ?: return@withContext false
      true
    } catch (_: Exception) {
      false
    }
  }

  override suspend fun deleteFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
    val key = normalizeKey(relativePath)
    try {
      val docId = resolveDocId(key)
        ?: return@withContext true // already gone
      val deleted = DocumentsContract.deleteDocument(
        resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
      )
      if (deleted) docIds.remove(key)
      deleted
    } catch (_: FileNotFoundException) {
      true
    } catch (_: Exception) {
      false
    }
  }

  override fun openReadPipe(relativePath: String): ParcelFileDescriptor? {
    val docUri = try {
      resolveDocumentUri(relativePath)
    } catch (_: Exception) {
      return null
    }
    val input = try {
      resolver.openInputStream(docUri) ?: return null
    } catch (_: Exception) {
      return null
    }
    val pipe = ParcelFileDescriptor.createReliablePipe()
    val readEnd = pipe[0]
    val sink = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
    return try {
      pump(after = { runCatching { sink.close() }; runCatching { input.close() } }) {
        sink.use { dst -> input.use { src -> src.copyTo(dst) } }
      }
      readEnd
    } catch (e: Exception) {
      runCatching { readEnd.close() }
      runCatching { pipe[1].close() }
      runCatching { input.close() }
      null
    }
  }

  override fun openWritePipe(relativePath: String, onWriteFinished: () -> Unit): ParcelFileDescriptor? {
    val pipe = ParcelFileDescriptor.createReliablePipe()
    val writeEnd = pipe[1]
    return try {
      val source = ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
      pump(after = onWriteFinished) {
        val docUri = ensureDocumentUri(relativePath)
        resolver.openOutputStream(docUri, "wt")?.use { out ->
          source.use { src -> src.copyTo(out); out.flush() }
        } ?: throw FileNotFoundException("Cannot open $relativePath for writing")
      }
      writeEnd
    } catch (e: Exception) {
      runCatching { pipe[0].close() }
      runCatching { writeEnd.close() }
      null
    }
  }

  /** Run [body] on a daemon thread, then [after] (even when [body] throws). */
  private fun pump(after: () -> Unit, body: () -> Unit) {
    Thread {
      try {
        body()
      } catch (_: Exception) {
      } finally {
        runCatching { after() }
      }
    }.apply { isDaemon = true }.start()
  }

  // ---- path resolution -------------------------------------------------

  private fun normalizeKey(relativePath: String): String =
    relativePath.trim().replace('\\', '/').trim('/')

  private fun resolveDocId(key: String): String? {
    if (key.isEmpty()) return rootDocId
    docIds[key]?.let { return it }
    // Not cached (e.g. file created outside this instance): walk from the root,
    // resolving and caching each ancestor directory.
    val segments = key.split('/')
    var currentDocId = rootDocId
    var currentPath = StringBuilder()
    for (segment in segments) {
      if (segment.isEmpty()) continue
      if (currentPath.isNotEmpty()) currentPath.append('/')
      currentPath.append(segment)
      val childPath = currentPath.toString()
      val childDocId = docIds[childPath] ?: queryChildDocumentId(currentDocId, segment) ?: return null
      docIds[childPath] = childDocId
      currentDocId = childDocId
    }
    return currentDocId
  }

  private fun queryChildDocumentId(parentDocId: String, displayName: String): String? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    return try {
      resolver.query(
        childrenUri,
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
        null, null, null
      )?.use { cursor ->
        while (cursor.moveToNext()) {
          if (cursor.getString(1) == displayName) return cursor.getString(0)
        }
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  @Throws(FileNotFoundException::class)
  private fun resolveDocumentUri(relativePath: String): Uri {
    val key = normalizeKey(relativePath)
    val docId = resolveDocId(key) ?: throw FileNotFoundException("$relativePath not found")
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
  }

  /**
   * Resolve the document for [relativePath], creating missing parent folders and
   * the file itself where necessary.
   */
  @Throws(Exception::class)
  private fun ensureDocumentUri(relativePath: String): Uri {
    val key = normalizeKey(relativePath)
    if (key.isEmpty()) throw FileNotFoundException("Empty path")
    resolveDocId(key)?.let { return DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }

    val segments = key.split('/')
    val fileName = segments.last()
    val dirKey = segments.dropLast(1).joinToString("/")
    val dirDocId = ensureDirectoryDocId(dirKey)
    val created = DocumentsContract.createDocument(
      resolver,
      DocumentsContract.buildDocumentUriUsingTree(treeUri, dirDocId),
      mimeFor(fileName),
      fileName
    ) ?: throw FileNotFoundException("Could not create $fileName")
    val createdDocId = DocumentsContract.getDocumentId(created)
    // Some providers rewrite the display name on create ("photo.jpg" -> "photo (1).jpg"):
    // cache under both the actual and the requested name so later lookups hit.
    val createdName = queryDocumentName(createdDocId) ?: fileName
    docIds[joinKey(dirKey, createdName)] = createdDocId
    docIds[key] = createdDocId
    return created
  }

  private fun ensureDirectoryDocId(dirKey: String): String {
    if (dirKey.isEmpty()) return rootDocId
    resolveDocId(dirKey)?.let { return it }
    val segments = dirKey.split('/')
    var parentDocId = rootDocId
    var currentPath = StringBuilder()
    for (segment in segments) {
      if (segment.isEmpty()) continue
      if (currentPath.isNotEmpty()) currentPath.append('/')
      currentPath.append(segment)
      val childPath = currentPath.toString()
      var childId = docIds[childPath] ?: queryChildDocumentId(parentDocId, segment)
      if (childId == null) {
        val created = DocumentsContract.createDocument(
          resolver,
          DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId),
          Document.MIME_TYPE_DIR,
          segment
        ) ?: throw FileNotFoundException("Could not create folder $segment")
        childId = DocumentsContract.getDocumentId(created)
      }
      docIds[childPath] = childId
      parentDocId = childId
    }
    return parentDocId
  }

  private fun joinKey(dir: String, name: String): String =
    if (dir.isEmpty()) name else "$dir/$name"

  private fun queryDocumentName(docId: String): String? {
    return try {
      resolver.query(
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
        arrayOf(Document.COLUMN_DISPLAY_NAME),
        null, null, null
      )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
      null
    }
  }

  private fun mimeFor(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "")
    if (ext.isEmpty()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
      ?: "application/octet-stream"
  }
}
