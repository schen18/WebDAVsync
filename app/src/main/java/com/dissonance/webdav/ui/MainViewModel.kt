package com.dissonance.webdav.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dissonance.webdav.data.model.ConflictPolicy
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.data.model.WebdavServer
import com.dissonance.webdav.data.repository.SyncRepository
import com.dissonance.webdav.sync.EnergyState
import com.dissonance.webdav.sync.RemoteFileItem
import com.dissonance.webdav.sync.SyncProgress
import com.dissonance.webdav.sync.SyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppNavigationTab {
  DASHBOARD,
  SYNC_PAIRS,
  CONFLICTS,
  EXPLORER,
  LOGS,
  SERVERS_SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val repository = SyncRepository(application, viewModelScope)

  val syncPairs: StateFlow<List<SyncPair>> = repository.allSyncPairs
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val servers: StateFlow<List<WebdavServer>> = repository.allServers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentLogs: StateFlow<List<SyncLog>> = repository.recentLogs
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val pendingConflicts: StateFlow<List<FileConflict>> = repository.pendingConflicts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allConflicts: StateFlow<List<FileConflict>> = repository.allConflicts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val dirtyCount: StateFlow<Int> = repository.dirtyCount
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val syncProgress: StateFlow<SyncProgress> = repository.syncProgress
  val energyState: StateFlow<EnergyState> = repository.energyState

  private val _selectedTab = MutableStateFlow(AppNavigationTab.DASHBOARD)
  val selectedTab: StateFlow<AppNavigationTab> = _selectedTab.asStateFlow()

  private val _selectedPairForFiles = MutableStateFlow<SyncPair?>(null)
  val selectedPairForFiles: StateFlow<SyncPair?> = _selectedPairForFiles.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentPairFiles: StateFlow<List<SyncedFileRecord>> =
    combine(_selectedPairForFiles, syncPairs) { selected, pairs ->
      selected ?: pairs.firstOrNull()
    }.flatMapLatest { pair ->
      if (pair != null) repository.getFilesForPair(pair.id) else flowOf(emptyList())
    }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Modals / Sheet States
  private val _editingSyncPair = MutableStateFlow<SyncPair?>(null)
  val editingSyncPair: StateFlow<SyncPair?> = _editingSyncPair.asStateFlow()

  private val _isCreatingSyncPair = MutableStateFlow(false)
  val isCreatingSyncPair: StateFlow<Boolean> = _isCreatingSyncPair.asStateFlow()

  private val _editingServer = MutableStateFlow<WebdavServer?>(null)
  val editingServer: StateFlow<WebdavServer?> = _editingServer.asStateFlow()

  private val _isCreatingServer = MutableStateFlow(false)
  val isCreatingServer: StateFlow<Boolean> = _isCreatingServer.asStateFlow()

  private val _inspectingConflict = MutableStateFlow<FileConflict?>(null)
  val inspectingConflict: StateFlow<FileConflict?> = _inspectingConflict.asStateFlow()

  private val _inspectingFileContent = MutableStateFlow<Pair<String, String>?>(null) // path to content
  val inspectingFileContent: StateFlow<Pair<String, String>?> = _inspectingFileContent.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _serverTestResult = MutableStateFlow<String?>(null)
  val serverTestResult: StateFlow<String?> = _serverTestResult.asStateFlow()

  // Remote live browsing
  private val _remoteBrowserItems = MutableStateFlow<List<RemoteFileItem>>(emptyList())
  val remoteBrowserItems: StateFlow<List<RemoteFileItem>> = _remoteBrowserItems.asStateFlow()

  fun selectTab(tab: AppNavigationTab) {
    _selectedTab.value = tab
  }

  fun showMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  fun clearMessage() {
    _snackbarMessage.value = null
  }

  fun syncAllNow() {
    viewModelScope.launch {
      val results = repository.syncAllNow()
      val conflicts = results.filterIsInstance<SyncResult.ConflictFound>().sumOf { it.count }
      val failures = results.filterIsInstance<SyncResult.Failure>()
      val pauses = results.filterIsInstance<SyncResult.Paused>()
      when {
        failures.isNotEmpty() ->
          showMessage("Sync finished with ${failures.size} error(s): ${failures.first().errorMessage}")
        conflicts > 0 ->
          showMessage("Sync finished with $conflicts version conflict(s). Review in Conflicts tab.")
        pauses.isNotEmpty() -> showMessage(pauses.first().reason)
        else -> showMessage("All enabled folder pairs synchronized successfully.")
      }
    }
  }

  fun triggerWorkManagerPush() {
    com.dissonance.webdav.sync.work.SyncWorkScheduler.scheduleImmediatePush(getApplication())
    showMessage("WorkManager dirty push job enqueued (Connected network constraints).")
  }

  fun syncSinglePair(pairId: Long) {
    viewModelScope.launch {
      val result = repository.syncSinglePair(pairId)
      when (result) {
        is SyncResult.Success -> showMessage("Synchronized: ${result.filesUploaded} uploaded, ${result.filesDownloaded} downloaded")
        is SyncResult.ConflictFound -> showMessage("Sync found ${result.count} conflict(s). Review in Conflicts tab.")
        is SyncResult.Paused -> showMessage(result.reason)
        is SyncResult.Failure -> showMessage("Sync error: ${result.errorMessage}")
      }
    }
  }

  fun resolveConflict(conflictId: Long, resolution: ConflictStatus) {
    viewModelScope.launch {
      val ok = repository.resolveConflict(conflictId, resolution)
      if (ok) {
        _inspectingConflict.value = null
        val label = when (resolution) {
          ConflictStatus.RESOLVED_LOCAL -> "Kept Device version (Uploaded to Server)"
          ConflictStatus.RESOLVED_REMOTE -> "Kept Server version (Downloaded to Device)"
          ConflictStatus.RESOLVED_BOTH -> "Kept Both versions (Duplicate saved)"
          else -> "Resolved"
        }
        showMessage("Conflict resolved: $label")
      }
    }
  }

  fun togglePairEnabled(pair: SyncPair) {
    viewModelScope.launch {
      repository.saveSyncPair(pair.copy(isEnabled = !pair.isEnabled))
      showMessage(if (!pair.isEnabled) "${pair.name} auto-sync enabled" else "${pair.name} sync disabled")
    }
  }

  fun openCreateSyncPair() {
    _isCreatingSyncPair.value = true
    _editingSyncPair.value = null
  }

  fun openEditSyncPair(pair: SyncPair) {
    _editingSyncPair.value = pair
    _isCreatingSyncPair.value = false
  }

  fun closeSyncPairEditor() {
    _editingSyncPair.value = null
    _isCreatingSyncPair.value = false
  }

  fun saveSyncPair(
    id: Long,
    serverId: Long,
    name: String,
    localFolderName: String,
    localTreeUri: String,
    remoteRelativePath: String,
    direction: SyncDirection,
    conflictPolicy: ConflictPolicy,
    intervalMins: Int,
    wifiOnly: Boolean,
    chargingOnly: Boolean,
    batteryThreshold: Int,
    excludePatterns: String,
    isEnabled: Boolean
  ) {
    viewModelScope.launch {
      val pair = SyncPair(
        id = id,
        serverId = serverId,
        name = name.ifBlank { "Folder Sync Pair" },
        localFolderName = localFolderName.ifBlank { "Folder" },
        localRelativePath = localFolderName,
        localTreeUri = localTreeUri,
        remoteRelativePath = if (remoteRelativePath.startsWith("/")) remoteRelativePath else "/$remoteRelativePath",
        syncDirection = direction,
        conflictPolicy = conflictPolicy,
        syncIntervalMinutes = intervalMins,
        wifiOnly = wifiOnly,
        chargingOnly = chargingOnly,
        batterySaverThreshold = batteryThreshold,
        excludePatterns = excludePatterns,
        isEnabled = isEnabled
      )
      repository.saveSyncPair(pair)
      closeSyncPairEditor()
      showMessage("Saved folder sync pair '${pair.name}'")
    }
  }

  fun deleteSyncPair(pair: SyncPair) {
    viewModelScope.launch {
      repository.deleteSyncPair(pair)
      if (_selectedPairForFiles.value?.id == pair.id) {
        _selectedPairForFiles.value = null
      }
      showMessage("Deleted sync pair '${pair.name}'")
    }
  }

  fun openCreateServer() {
    _isCreatingServer.value = true
    _editingServer.value = null
    _serverTestResult.value = null
  }

  fun openEditServer(server: WebdavServer) {
    _editingServer.value = server
    _isCreatingServer.value = false
    _serverTestResult.value = null
  }

  fun closeServerEditor() {
    _editingServer.value = null
    _isCreatingServer.value = false
    _serverTestResult.value = null
  }

  fun saveServer(
    id: Long,
    name: String,
    url: String,
    username: String,
    password: String,
    trustSelfSigned: Boolean
  ) {
    viewModelScope.launch {
      if (url.isBlank()) {
        showMessage("Server URL is required.")
        return@launch
      }
      val server = WebdavServer(
        id = id,
        name = name.ifBlank { "WebDAV Server" },
        url = url.trim(),
        username = username.trim(),
        password = password,
        trustSelfSigned = trustSelfSigned
      )
      repository.saveServer(server)
      closeServerEditor()
      showMessage("Saved server configuration '${server.name}'")
    }
  }

  fun deleteServer(server: WebdavServer) {
    viewModelScope.launch {
      repository.deleteServer(server)
      showMessage("Removed server '${server.name}'")
    }
  }

  fun testServer(server: WebdavServer) {
    viewModelScope.launch {
      _serverTestResult.value = "Connecting to ${server.url}..."
      val result = repository.testServerConnection(server)
      result.fold(
        onSuccess = { _serverTestResult.value = "✓ $it" },
        onFailure = { _serverTestResult.value = "✗ Connection failed: ${it.message}" }
      )
    }
  }

  fun inspectConflict(conflict: FileConflict) {
    _inspectingConflict.value = conflict
  }

  fun closeConflictInspector() {
    _inspectingConflict.value = null
  }

  fun selectPairForFileView(pair: SyncPair?) {
    _selectedPairForFiles.value = pair
  }

  fun openFileViewer(pair: SyncPair, relativePath: String) {
    viewModelScope.launch {
      val content = repository.readLocalFileContent(pair, relativePath)
      _inspectingFileContent.value = Pair(relativePath, content)
    }
  }

  fun closeFileViewer() {
    _inspectingFileContent.value = null
  }

  fun saveFileEdits(pair: SyncPair, relativePath: String, newContent: String) {
    viewModelScope.launch {
      val ok = repository.saveLocalFileContent(pair, relativePath, newContent)
      if (ok) {
        _inspectingFileContent.value = null
        showMessage("Saved changes to $relativePath. Run sync to push updates.")
      } else {
        showMessage("Could not save $relativePath (folder access missing).")
      }
    }
  }

  fun createNewFileInFolder(pair: SyncPair, fileName: String, content: String) {
    viewModelScope.launch {
      val ok = repository.createNewLocalFile(pair, fileName, content)
      if (ok) {
        showMessage("Created $fileName in ${pair.localFolderName}. Triggering sync...")
        syncSinglePair(pair.id)
      } else {
        showMessage("Could not create $fileName (folder access missing).")
      }
    }
  }

  fun clearAllLogs() {
    viewModelScope.launch {
      repository.clearLogs()
      showMessage("Sync logs cleared")
    }
  }

  fun resetAllData() {
    viewModelScope.launch {
      repository.resetAllData()
      _selectedPairForFiles.value = null
      _inspectingConflict.value = null
      _inspectingFileContent.value = null
      showMessage("All servers, sync pairs, file records, and logs have been cleared. Folder grants released.")
    }
  }
}
