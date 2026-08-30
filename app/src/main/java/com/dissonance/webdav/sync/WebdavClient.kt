package com.dissonance.webdav.sync

import com.dissonance.webdav.data.model.WebdavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** WebDAV (RFC 4918) client: PROPFIND/PUT/GET/DELETE/MKCOL over OkHttp. */
class WebdavClient : RemoteStore {

  // Reuse OkHttp clients (connection pools + threads) instead of building one per request.
  private val clientCache = ConcurrentHashMap<Boolean, OkHttpClient>()

  private fun obtainClient(trustSelfSigned: Boolean): OkHttpClient =
    clientCache.getOrPut(trustSelfSigned) { buildOkHttpClient(trustSelfSigned) }

  private fun normalizePath(path: String): String {
    var p = path.trim()
    if (!p.startsWith("/")) p = "/$p"
    if (p.endsWith("/") && p.length > 1) p = p.dropLast(1)
    return p
  }

  private fun getContentTypeForFileName(name: String): String {
    return when {
      name.endsWith(".md") || name.endsWith(".txt") -> "text/markdown; charset=utf-8"
      name.endsWith(".csv") -> "text/csv"
      name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json") -> "application/json"
      name.endsWith(".pdf") -> "application/pdf"
      name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
      name.endsWith(".png") -> "image/png"
      else -> "application/octet-stream"
    }
  }

  private fun buildOkHttpClient(trustSelfSigned: Boolean): OkHttpClient {
    val builder = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)

    if (trustSelfSigned) {
      try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
          override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
          override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
          override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }
      } catch (_: Exception) {}
    }
    return builder.build()
  }

  private fun Request.Builder.withAuth(server: WebdavServer): Request.Builder {
    if (server.username.isNotEmpty()) {
      header("Authorization", Credentials.basic(server.username, server.password))
    }
    return this
  }

  override suspend fun testConnection(server: WebdavServer): Result<String> = withContext(Dispatchers.IO) {
    if (server.url.isBlank()) {
      return@withContext Result.failure(Exception("Server URL is required."))
    }
    try {
      val client = obtainClient(server.trustSelfSigned)
      val request = Request.Builder()
        .url(server.url)
        .method(
          "PROPFIND",
          "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"
            .toRequestBody("application/xml".toMediaTypeOrNull())
        )
        .header("Depth", "0")
        .withAuth(server)
        .build()

      client.newCall(request).execute().use { response ->
        when {
          response.code == 401 -> Result.failure(Exception("Authentication required. Please verify username and password."))
          response.isSuccessful -> Result.success("Connected to ${server.url} (HTTP ${response.code})")
          else -> Result.failure(Exception("Server returned HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Could not reach ${server.url}: ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun listFolder(server: WebdavServer, remotePath: String): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    try {
      val client = obtainClient(server.trustSelfSigned)
      val fullUrl = buildFullUrl(server.url, normalized)
      val body = """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:getlastmodified/>
            <D:getcontentlength/>
            <D:resourcetype/>
            <D:getetag/>
            <D:displayname/>
          </D:prop>
        </D:propfind>
      """.trimIndent().toRequestBody("application/xml".toMediaTypeOrNull())

      val request = Request.Builder()
        .url(fullUrl)
        .method("PROPFIND", body)
        .header("Depth", "1")
        .withAuth(server)
        .build()

      client.newCall(request).execute().use { response ->
        val responseXml = response.body?.string() ?: ""
        when {
          response.code == 404 ->
            // A folder that does not exist yet is equivalent to an empty folder for sync purposes.
            Result.success(emptyList())
          response.isSuccessful || response.code == 207 ->
            Result.success(parsePropfindXml(responseXml, serverPathOf(fullUrl), normalized))
          response.code == 401 ->
            Result.failure(Exception("Authentication failed for ${server.name} (HTTP 401)."))
          else ->
            Result.failure(Exception("PROPFIND failed with HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Could not reach ${server.url}: ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun listFolderDeep(
    server: WebdavServer,
    remotePath: String,
    maxDepth: Int
  ): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    val all = mutableListOf<RemoteFileItem>()
    val queue = ArrayDeque<Pair<String, Int>>() // path to depth relative to root
    queue.add(normalized to 0)

    while (queue.isNotEmpty()) {
      val (dir, depth) = queue.removeFirst()
      val children = listFolder(server, dir).getOrElse { e ->
        return@withContext Result.failure(e)
      }
      for (child in children) {
        all.add(child)
        if (child.isDirectory && depth < maxDepth) {
          queue.add(child.path to depth + 1)
        }
      }
    }
    Result.success(all)
  }

  override suspend fun ensureRemoteDirectory(server: WebdavServer, remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    val segments = normalized.trim('/').split('/').filter { it.isNotEmpty() }
    var current = ""
    try {
      val client = obtainClient(server.trustSelfSigned)
      for (segment in segments) {
        current = "$current/$segment"
        val request = Request.Builder()
          .url(buildFullUrl(server.url, current))
          .method("MKCOL", null)
          .withAuth(server)
          .build()
        client.newCall(request).execute().use { response ->
          // 405 = already exists; anything else outside 2xx is a failure.
          if (!response.isSuccessful && response.code != 405) {
            return@withContext Result.failure(Exception("MKCOL $current failed with HTTP ${response.code}"))
          }
        }
      }
      Result.success(true)
    } catch (e: IOException) {
      Result.failure(Exception("Could not reach ${server.url}: ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun uploadFile(
    server: WebdavServer,
    remotePath: String,
    content: ByteArray,
    contentType: String
  ): Result<String> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    try {
      val client = obtainClient(server.trustSelfSigned)
      val fullUrl = buildFullUrl(server.url, normalized)
      val requestBody = content.toRequestBody(contentType.toMediaTypeOrNull())

      val request = Request.Builder()
        .url(fullUrl)
        .put(requestBody)
        .withAuth(server)
        .build()

      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          Result.success(response.header("ETag") ?: "\"w-${content.size}\"")
        } else {
          Result.failure(Exception("Upload failed with HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Upload failed (network): ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun downloadFile(server: WebdavServer, remotePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    try {
      val client = obtainClient(server.trustSelfSigned)
      val fullUrl = buildFullUrl(server.url, normalized)

      val request = Request.Builder()
        .url(fullUrl)
        .get()
        .withAuth(server)
        .build()

      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          Result.success(response.body?.bytes() ?: ByteArray(0))
        } else {
          Result.failure(Exception("Download failed with HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Download failed (network): ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deleteFile(server: WebdavServer, remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
    val normalized = normalizePath(remotePath)
    try {
      val client = obtainClient(server.trustSelfSigned)
      val fullUrl = buildFullUrl(server.url, normalized)

      val request = Request.Builder()
        .url(fullUrl)
        .delete()
        .withAuth(server)
        .build()

      client.newCall(request).execute().use { response ->
        if (response.isSuccessful || response.code == 404) {
          Result.success(true)
        } else {
          Result.failure(Exception("Delete failed with HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Delete failed (network): ${e.message}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /** The decoded path portion of a full URL, e.g. "https://h/dav/files/u/Vault" -> "/dav/files/u/Vault". */
  private fun serverPathOf(fullUrl: String): String = try {
    val raw = java.net.URI(fullUrl).rawPath ?: ""
    percentDecode(raw)
  } catch (_: Exception) {
    ""
  }

  private fun buildFullUrl(baseUrl: String, remotePath: String): String {
    var base = baseUrl.trim()
    if (base.endsWith("/")) base = base.dropLast(1)
    val encodedPath = remotePath.trim().split('/')
      .filter { it.isNotEmpty() }
      .joinToString("/") { encodeSegment(it) }
    return if (encodedPath.isEmpty()) base else "$base/$encodedPath"
  }

  /** Encode a single path segment, keeping it valid for both URLs and WebDAV servers. */
  private fun encodeSegment(segment: String): String =
    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

  /**
   * Parse a multistatus PROPFIND response. Hrefs are server-absolute paths (or full URLs);
   * [requestPathPrefix] (the path portion of the request URL) and [normalized] (the requested
   * collection path relative to the server base URL) are used to map each child back to a
   * path relative to the server base, matching how upload/download URLs are built.
   */
  private fun parsePropfindXml(xml: String, requestPathPrefix: String, normalized: String): List<RemoteFileItem> {
    val items = mutableListOf<RemoteFileItem>()
    try {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      val parser = factory.newPullParser()
      parser.setInput(StringReader(xml))

      var eventType = parser.eventType
      var currentHref = ""
      var contentLength = 0L
      var lastModified = 0L
      var isCollection = false
      var etag = ""
      var currentTag = ""

      val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
      }

      val rootSegment = normalized.substringAfterLast('/')

      while (eventType != XmlPullParser.END_DOCUMENT) {
        val name = parser.name?.lowercase() ?: ""
        when (eventType) {
          XmlPullParser.START_TAG -> {
            currentTag = name
            if (name == "response") {
              currentHref = ""
              contentLength = 0
              lastModified = 0
              isCollection = false
              etag = ""
            } else if (name == "collection") {
              isCollection = true
            }
          }
          XmlPullParser.TEXT -> {
            val text = parser.text?.trim() ?: ""
            if (text.isNotEmpty()) {
              when (currentTag) {
                "href" -> currentHref = text
                "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0
                "getlastmodified" -> {
                  try {
                    val date = httpDateFormat.parse(text)
                    if (date != null) lastModified = date.time
                  } catch (_: Exception) {}
                }
                "getetag" -> etag = text
              }
            }
          }
          XmlPullParser.END_TAG -> {
            if (name == "response" && currentHref.isNotEmpty()) {
              val decodedHref = percentDecode(currentHref)
              // Hrefs may be absolute URLs or server-absolute paths; keep only the path part.
              val pathOnly = if (decodedHref.contains("://")) {
                decodedHref.substringAfter("://").substringAfter("/")
              } else {
                decodedHref.trimStart('/')
              }
              val path = if (pathOnly.startsWith("/")) pathOnly else "/$pathOnly"

              // Resolve the child's path relative to the server base URL.
              val relativePath: String? = when {
                requestPathPrefix.isNotEmpty() && path.startsWith(requestPathPrefix) ->
                  normalized + path.removePrefix(requestPathPrefix)
                path.endsWith(normalized) -> normalized
                rootSegment.isNotEmpty() -> {
                  val idx = path.lastIndexOf("/$rootSegment")
                  if (idx >= 0) normalized + path.substring(idx + 1 + rootSegment.length) else null
                }
                else -> null
              }

              if (relativePath != null) {
                val trimmed = relativePath.trimEnd('/')
                // Skip the requested collection itself; only children are of interest.
                if (trimmed.isNotEmpty() && trimmed != normalized) {
                  val fileName = trimmed.substringAfterLast('/')
                  if (fileName.isNotEmpty()) {
                    items.add(
                      RemoteFileItem(
                        path = trimmed,
                        name = fileName,
                        size = contentLength,
                        lastModified = lastModified,
                        isDirectory = isCollection,
                        etag = etag,
                        contentType = getContentTypeForFileName(fileName)
                      )
                    )
                  }
                }
              }
            }
            currentTag = ""
          }
        }
        eventType = parser.next()
      }
    } catch (_: Exception) {}

    return items
  }

  /** Decode %XX escapes without treating '+' as space (WebDAV hrefs may contain literal '+'). */
  private fun percentDecode(value: String): String {
    if (!value.contains('%')) return value
    return try {
      val out = StringBuilder(value.length)
      var i = 0
      while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
          val hex = value.substring(i + 1, i + 3)
          val byte = hex.toIntOrNull(16)
          if (byte != null) {
            out.append(byte.toChar())
            i += 3
            continue
          }
        }
        out.append(c)
        i++
      }
      // Re-interpret the decoded chars (which may form UTF-8 sequences) correctly.
      URLDecoder.decode(out.toString(), "UTF-8")
    } catch (_: Exception) {
      value
    }
  }
}
