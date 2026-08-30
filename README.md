# WebDAV Sync

An Android app that synchronizes folders on real device storage (photos, downloads,
any provider folder) with WebDAV servers — self-hosted (Nextcloud, ownCloud, Synology,
Apache `mod_dav`, lighttpd, TrueNAS) or any RFC 4918 server.

Storage access uses Android's Storage Access Framework: each sync pair is linked to a
folder you grant through the system picker, so the app needs **no storage permissions**
and keeps **no private copy** of your files. All transfers go straight between your
device folders and your server. There is no demo or simulated mode — a reachable
WebDAV server is required.

## Features

- **Real device folders via SAF** — pair any granted folder with a remote WebDAV path;
  the grant persists across reboots and is released when the pair is deleted.
- **Folder pair sync** — two-way, upload-only (device mirror), or download-only
  (server mirror); per-pair exclude patterns (`.tmp`, `*.bak`, exact names).
- **Delta sync** — Room-persisted snapshots (ETag / size / mtime) drive change
  detection; only changed files transfer. Nested directory structures are preserved
  (remote `MKCOL` for new folders), and deletions propagate in both directions.
- **Conflict handling** — detect concurrent edits on both sides and resolve by policy:
  ask-user (side-by-side preview in the Conflicts tab), device-wins, server-wins,
  keep-both, or latest-timestamp-wins.
- **Energy guards** — per-pair constraints (Wi-Fi only, charging only, battery
  threshold); sync also pauses while Android Battery Saver is on.
- **Background sync** — a periodic WorkManager job plus per-pair intervals keep pairs
  in sync with the app closed; local edits made from any app are picked up on the
  next run.
- **Files app integration** — all pairs are exposed through a `DocumentsProvider`
  as one tree; open, edit, create, rename, and delete from the Android Files app or
  any SAF-compatible app. Writes are flagged dirty and pushed automatically.
- **Sync Hub IPC** — a signature-permission-protected `ContentProvider` lets
  companion apps read/write files in granted folders, observe changes, and trigger
  syncs (see the client SDK below).
- **Audit logs** — every upload/download/delete/conflict/skip is recorded, searchable
  in the Logs tab, and pruned automatically after 7 days.

## Requirements

- Device/emulator running Android 7.0+ (minSdk 24, targetSdk 36)
- A WebDAV server reachable from the device (HTTP or HTTPS, Basic auth)
- Building: JDK 21 and Android SDK platform 36 (Gradle 9.4.1 wrapper included —
  run `./gradlew`, not a system Gradle)

## Building

```bash
./gradlew assembleDebug          # debug APK (uses the standard debug key)
./gradlew testDebugUnitTest      # Robolectric + screenshot unit tests
```

Release builds only sign when `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`
point at an existing keystore; otherwise they fall back to the debug key.

## Usage

1. **Settings → Add Server** — enter the WebDAV base URL of a folder, e.g.
   `https://cloud.example.com/remote.php/dav/files/username` (Nextcloud) or
   `http://192.168.1.50:5005/dav` (Synology), with username and app password.
   Use **Test Probe** to verify connectivity and credentials. Self-signed
   certificates can be trusted per server.
2. **Pairs → New Folder Sync Pair** — pick the server, tap **Choose** and grant a
   device folder in the system picker, set the remote path, direction, conflict
   policy, interval, energy constraints, and exclusions.
3. Tap **Sync** on the pair (or the toolbar sync-all button). Conflicts (if any)
   appear in the Conflicts tab for resolution.

Notes:

- On some Android versions the top-level `Download` folder itself cannot be granted
  by the OS; grant a subfolder or another folder instead.
- HTTP (cleartext) servers are supported for LAN use; HTTPS with Basic auth is
  recommended for anything beyond your local network.

## Companion app integration (client SDK)

`client/SyncHubClient.kt` is a drop-in Kotlin SDK for apps signed with the **same
signing key**:

1. Declare the permission in the client manifest:
   `<uses-permission android:name="com.dissonance.webdav.permission.ACCESS_SYNC_HUB" />`
2. Use `SyncHubClient(context)` to list pairs, read/write files by pair + relative
   path (`writeText`/`writeBytes` create missing parents and auto-push), delete,
   observe per-pair changes with `observeFiles`, and `triggerSync(pairId)` to run a
   full delta sync of that pair on demand.

URI and column documentation lives in `provider/SyncHubContract.kt`.

## Architecture

```
ui/            Compose screens (dashboard, pairs, conflicts, explorer, logs, settings)
sync/          SyncEngine      — delta/deletion/conflict logic, snapshot baselines
               WebdavClient    — RFC 4918 client (PROPFIND/PUT/GET/DELETE/MKCOL, OkHttp)
               RemoteStore     — interface the engine depends on (tests fake it)
               LocalFolderAccess / SafFolderAccess — SAF document-tree storage
               LocalFileManager — grant lifecycle + per-pair folder factory
               EnergyMonitor   — real battery/network/power state
sync/work/     PushDirtyFilesWorker (edit push), FullSyncWorker (periodic delta sync)
data/          Room database v4: servers, pairs (with SAF tree URIs), file records,
               conflicts, logs — migrations preserve data across upgrades
provider/      SyncDocumentsProvider (Files app view) + SyncHubProvider (secured IPC)
client/        SyncHubClient SDK for companion apps
```

Local storage notes: file paths recorded in the database are relative to each pair's
granted folder (leading slash, e.g. `/notes/idea.md`). Credentials are stored in the
app's private Room database (not encrypted — standard for WebDAV clients, but worth
knowing).

## Testing

`./gradlew testDebugUnitTest` runs 26 unit tests on the JVM via Robolectric,
including end-to-end engine tests against in-memory fakes (`FakeFolderAccess` stands
in for a granted device folder, `FakeRemoteStore` for a WebDAV server with realistic
ETag behavior) and IPC conformance tests that exercise the Sync Hub provider through
the real ContentResolver. SAF interaction against real providers is verified on an
emulator manually (picker → grant → upload → remote edit → download).
