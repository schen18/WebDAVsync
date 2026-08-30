package com.dissonance.webdav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.ui.AppNavigationTab
import com.dissonance.webdav.ui.MainViewModel
import com.dissonance.webdav.ui.screens.ConflictsScreen
import com.dissonance.webdav.ui.screens.DashboardScreen
import com.dissonance.webdav.ui.screens.ExplorerScreen
import com.dissonance.webdav.ui.screens.LogsScreen
import com.dissonance.webdav.ui.screens.SettingsScreen
import com.dissonance.webdav.ui.screens.SyncPairsScreen
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.EnergyEmerald
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.MyApplicationTheme
import com.dissonance.webdav.ui.theme.OutlineLight
import com.dissonance.webdav.ui.theme.OutlineVariantLight
import com.dissonance.webdav.ui.theme.TextMuted
import com.dissonance.webdav.ui.theme.TextPrimary
import com.dissonance.webdav.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        WebdavSyncApp(viewModel = viewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavSyncApp(viewModel: MainViewModel) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val syncPairs by viewModel.syncPairs.collectAsState()
  val servers by viewModel.servers.collectAsState()
  val recentLogs by viewModel.recentLogs.collectAsState()
  val pendingConflicts by viewModel.pendingConflicts.collectAsState()
  val allConflicts by viewModel.allConflicts.collectAsState()
  val syncProgress by viewModel.syncProgress.collectAsState()
  val energyState by viewModel.energyState.collectAsState()
  val dirtyCount by viewModel.dirtyCount.collectAsState()

  val selectedPairForFiles by viewModel.selectedPairForFiles.collectAsState()
  val currentPairFiles by viewModel.currentPairFiles.collectAsState()
  val isCreatingSyncPair by viewModel.isCreatingSyncPair.collectAsState()
  val editingSyncPair by viewModel.editingSyncPair.collectAsState()

  val isCreatingServer by viewModel.isCreatingServer.collectAsState()
  val editingServer by viewModel.editingServer.collectAsState()
  val serverTestResult by viewModel.serverTestResult.collectAsState()

  val inspectingFileContent by viewModel.inspectingFileContent.collectAsState()
  val snackbarMessage by viewModel.snackbarMessage.collectAsState()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearMessage()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BrandPrimary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.CloudSync,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "SyncVault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "WebDAV Synchronization",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          // Battery / Power Safeguard Pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = BrandPrimaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.padding(end = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = if (energyState.batteryLevelPercent >= 0) "${energyState.batteryLevelPercent}%" else "?",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandOnPrimaryContainer
              )
            }
          }

          IconButton(
            onClick = { viewModel.syncAllNow() },
            enabled = !syncProgress.isRunning,
            modifier = Modifier.testTag("appbar_sync_button")
          ) {
            if (syncProgress.isRunning) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandPrimary)
            } else {
              Icon(Icons.Default.Refresh, contentDescription = "Sync All Folders", tint = BrandPrimary)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.testTag("main_bottom_nav")
      ) {
        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.DASHBOARD,
          onClick = { viewModel.selectTab(AppNavigationTab.DASHBOARD) },
          icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
          label = {
            Text(
              text = "Home",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.DASHBOARD) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.SYNC_PAIRS,
          onClick = { viewModel.selectTab(AppNavigationTab.SYNC_PAIRS) },
          icon = {
            BadgedBox(
              badge = {
                if (syncPairs.isNotEmpty()) {
                  Badge(containerColor = BrandPrimary, contentColor = Color.White) { Text("${syncPairs.size}", fontSize = 9.sp) }
                }
              }
            ) {
              Icon(Icons.Default.Folder, contentDescription = "Pairs")
            }
          },
          label = {
            Text(
              text = "Pairs",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.SYNC_PAIRS) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_sync_pairs")
        )

        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.CONFLICTS,
          onClick = { viewModel.selectTab(AppNavigationTab.CONFLICTS) },
          icon = {
            BadgedBox(
              badge = {
                if (pendingConflicts.isNotEmpty()) {
                  Badge(containerColor = ErrorRose, contentColor = Color.White) { Text("${pendingConflicts.size}", fontSize = 9.sp) }
                }
              }
            ) {
              Icon(Icons.Default.Warning, contentDescription = "Conflicts")
            }
          },
          label = {
            Text(
              text = "Conflicts",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.CONFLICTS) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_conflicts")
        )

        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.EXPLORER,
          onClick = { viewModel.selectTab(AppNavigationTab.EXPLORER) },
          icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Files") },
          label = {
            Text(
              text = "Files",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.EXPLORER) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_explorer")
        )

        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.LOGS,
          onClick = { viewModel.selectTab(AppNavigationTab.LOGS) },
          icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
          label = {
            Text(
              text = "Logs",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.LOGS) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_logs")
        )

        NavigationBarItem(
          selected = selectedTab == AppNavigationTab.SERVERS_SETTINGS,
          onClick = { viewModel.selectTab(AppNavigationTab.SERVERS_SETTINGS) },
          icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
          label = {
            Text(
              text = "Settings",
              fontSize = 9.sp,
              fontWeight = if (selectedTab == AppNavigationTab.SERVERS_SETTINGS) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandPrimary,
            selectedTextColor = BrandPrimary,
            indicatorColor = BrandPrimaryContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
          ),
          modifier = Modifier.testTag("nav_settings")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (selectedTab) {
        AppNavigationTab.DASHBOARD -> {
          DashboardScreen(
            syncPairs = syncPairs,
            pendingConflicts = pendingConflicts,
            recentLogs = recentLogs,
            syncProgress = syncProgress,
            energyState = energyState,
            dirtyCount = dirtyCount,
            onTriggerWorkManagerPush = { viewModel.triggerWorkManagerPush() },
            onSyncAll = { viewModel.syncAllNow() },
            onSyncPair = { viewModel.syncSinglePair(it) },
            onOpenConflict = {
              viewModel.inspectConflict(it)
              viewModel.selectTab(AppNavigationTab.CONFLICTS)
            },
            onNavigateTab = { viewModel.selectTab(it) },
            onAddPair = {
              viewModel.openCreateSyncPair()
              viewModel.selectTab(AppNavigationTab.SYNC_PAIRS)
            }
          )
        }

        AppNavigationTab.SYNC_PAIRS -> {
          SyncPairsScreen(
            syncPairs = syncPairs,
            servers = servers,
            syncProgress = syncProgress,
            onSyncPair = { viewModel.syncSinglePair(it) },
            onToggleEnabled = { viewModel.togglePairEnabled(it) },
            onEditPair = { viewModel.openEditSyncPair(it) },
            onDeletePair = { viewModel.deleteSyncPair(it) },
            onSelectPairForFiles = {
              viewModel.selectPairForFileView(it)
              viewModel.selectTab(AppNavigationTab.EXPLORER)
            },
            isCreating = isCreatingSyncPair,
            editingPair = editingSyncPair,
            onOpenCreate = { viewModel.openCreateSyncPair() },
            onCloseEditor = { viewModel.closeSyncPairEditor() },
            onSavePair = { id, sId, name, lName, lTreeUri, rPath, dir, pol, interval, wOnly, cOnly, bThresh, excl, en ->
              viewModel.saveSyncPair(id, sId, name, lName, lTreeUri, rPath, dir, pol, interval, wOnly, cOnly, bThresh, excl, en)
            }
          )
        }

        AppNavigationTab.CONFLICTS -> {
          ConflictsScreen(
            pendingConflicts = pendingConflicts,
            allConflicts = allConflicts,
            onResolveConflict = { id, res -> viewModel.resolveConflict(id, res) }
          )
        }

        AppNavigationTab.EXPLORER -> {
          ExplorerScreen(
            syncPairs = syncPairs,
            selectedPair = selectedPairForFiles,
            files = currentPairFiles,
            inspectingFileContent = inspectingFileContent,
            onSelectPair = { viewModel.selectPairForFileView(it) },
            onOpenFile = { pair, path -> viewModel.openFileViewer(pair, path) },
            onCloseFileViewer = { viewModel.closeFileViewer() },
            onSaveFileContent = { pair, path, content -> viewModel.saveFileEdits(pair, path, content) },
            onCreateFile = { pair, name, content -> viewModel.createNewFileInFolder(pair, name, content) },
            onSyncPair = { viewModel.syncSinglePair(it) }
          )
        }

        AppNavigationTab.LOGS -> {
          LogsScreen(
            logs = recentLogs,
            onClearLogs = { viewModel.clearAllLogs() }
          )
        }

        AppNavigationTab.SERVERS_SETTINGS -> {
          SettingsScreen(
            servers = servers,
            energyState = energyState,
            isCreatingServer = isCreatingServer,
            editingServer = editingServer,
            serverTestResult = serverTestResult,
            onOpenCreateServer = { viewModel.openCreateServer() },
            onOpenEditServer = { viewModel.openEditServer(it) },
            onCloseServerEditor = { viewModel.closeServerEditor() },
            onSaveServer = { id, name, url, u, p, trust -> viewModel.saveServer(id, name, url, u, p, trust) },
            onDeleteServer = { viewModel.deleteServer(it) },
            onTestServer = { viewModel.testServer(it) },
            onResetAllData = { viewModel.resetAllData() }
          )
        }
      }
    }
  }
}
