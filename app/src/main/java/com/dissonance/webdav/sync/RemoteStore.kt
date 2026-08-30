package com.dissonance.webdav.sync

import com.dissonance.webdav.data.model.WebdavServer

/**
 * The remote WebDAV side of synchronization. Production code always talks to a
 * real server through [WebdavClient]; tests substitute in-memory fakes.
 */
interface RemoteStore {
  suspend fun testConnection(server: WebdavServer): Result<String>

  suspend fun listFolder(server: WebdavServer, remotePath: String): Result<List<RemoteFileItem>>

  /** Recursively list a remote folder (bounded depth). */
  suspend fun listFolderDeep(
    server: WebdavServer,
    remotePath: String,
    maxDepth: Int = 6
  ): Result<List<RemoteFileItem>>

  /** Recursively create the remote directory (mkdir -p); succeeds when it exists. */
  suspend fun ensureRemoteDirectory(server: WebdavServer, remotePath: String): Result<Boolean>

  /** Upload (PUT); returns the server ETag on success (empty when none provided). */
  suspend fun uploadFile(
    server: WebdavServer,
    remotePath: String,
    content: ByteArray,
    contentType: String = "application/octet-stream"
  ): Result<String>

  suspend fun downloadFile(server: WebdavServer, remotePath: String): Result<ByteArray>

  suspend fun deleteFile(server: WebdavServer, remotePath: String): Result<Boolean>
}
