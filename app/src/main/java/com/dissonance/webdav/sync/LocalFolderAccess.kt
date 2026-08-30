package com.dissonance.webdav.sync

/**
 * Access to a sync pair's local folder. Paths are tree-relative with a leading
 * slash, e.g. "/notes/idea.md". The production implementation is backed by a
 * SAF document-tree grant ([SafFolderAccess]); tests substitute in-memory fakes.
 */
interface LocalFolderAccess {

  /** Display name of the granted root folder (e.g. "DCIM"). */
  val rootName: String

  /** Whether the underlying grant is still usable. */
  suspend fun hasAccess(): Boolean

  /** Recursively list all files (and directories) under the root. */
  suspend fun listFiles(): List<LocalFileItem>

  /** Current metadata for a single file, or null when absent. */
  suspend fun statFile(relativePath: String): LocalFileItem?

  suspend fun readFile(relativePath: String): ByteArray?

  suspend fun readText(relativePath: String): String =
    readFile(relativePath)?.toString(Charsets.UTF_8) ?: ""

  /** Write a file, creating parent folders and the file itself as needed. */
  suspend fun writeFile(relativePath: String, data: ByteArray): Boolean

  suspend fun writeText(relativePath: String, text: String): Boolean =
    writeFile(relativePath, text.toByteArray(Charsets.UTF_8))

  suspend fun deleteFile(relativePath: String): Boolean

  /** Serve reads from other apps through a pipe fd (used by the providers). */
  fun openReadPipe(relativePath: String): android.os.ParcelFileDescriptor?

  /**
   * Accept writes from other apps through a pipe fd (used by the providers).
   * [onWriteFinished] runs after the caller's bytes have been flushed to storage.
   */
  fun openWritePipe(relativePath: String, onWriteFinished: () -> Unit = {}): android.os.ParcelFileDescriptor?
}
