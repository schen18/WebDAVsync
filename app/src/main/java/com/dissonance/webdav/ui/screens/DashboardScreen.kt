package com.dissonance.webdav.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncStatus
import com.dissonance.webdav.sync.EnergyState
import com.dissonance.webdav.sync.SyncProgress
import com.dissonance.webdav.ui.AppNavigationTab
import com.dissonance.webdav.ui.components.DirectionBadge
import com.dissonance.webdav.ui.components.LogActionChip
import com.dissonance.webdav.ui.components.StatusBadge
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.EnergyEmerald
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.ErrorRoseBg
import com.dissonance.webdav.ui.theme.ErrorRoseDark
import com.dissonance.webdav.ui.theme.ErrorRoseSub
import com.dissonance.webdav.ui.theme.OutlineLight
import com.dissonance.webdav.ui.theme.OutlineVariantLight
import com.dissonance.webdav.ui.theme.TextMuted
import com.dissonance.webdav.ui.theme.TextPrimary
import com.dissonance.webdav.ui.theme.TextSecondary
import com.dissonance.webdav.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  syncPairs: List<SyncPair>,
  pendingConflicts: List<FileConflict>,
  recentLogs: List<SyncLog>,
  syncProgress: SyncProgress,
  energyState: EnergyState,
  dirtyCount: Int = 0,
  onTriggerWorkManagerPush: () -> Unit = {},
  onSyncAll: () -> Unit,
  onSyncPair: (Long) -> Unit,
  onOpenConflict: (FileConflict) -> Unit,
  onNavigateTab: (AppNavigationTab) -> Unit,
  onAddPair: () -> Unit
) {
  val animatedProgress by animateFloatAsState(targetValue = syncProgress.progressPercent, label = "progress")

  // Real system status derived from pair states, not a hardcoded label.
  val systemStatus = when {
    syncProgress.isRunning -> "Synchronizing..."
    syncPairs.any { it.lastSyncStatus == SyncStatus.CONFLICT_DETECTED } -> "Conflicts Pending"
    syncPairs.any { it.lastSyncStatus == SyncStatus.ERROR } -> "Sync Errors"
    syncPairs.isNotEmpty() -> "Up to Date"
    else -> "No Pairs Yet"
  }
  // Share of enabled pairs whose last sync finished clean.
  val enabledPairs = syncPairs.filter { it.isEnabled }
  val syncHealthPercent = if (enabledPairs.isEmpty()) {
    0
  } else {
    val healthy = enabledPairs.count {
      it.lastSyncStatus == SyncStatus.SUCCESS || it.lastSyncStatus == SyncStatus.IDLE
    }
    (healthy * 100 / enabledPairs.size)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        // Main Sync Status Hero Section (28dp rounded container)
        SyncHeroCard(
          syncPairs = syncPairs,
          syncProgress = syncProgress,
          animatedProgress = animatedProgress,
          systemStatus = systemStatus,
          syncHealthPercent = syncHealthPercent,
          onSyncAll = onSyncAll
        )
      }

      // Sync Hub & Storage Access Framework Status Card
      item {
        HubSAFStatusCard(
          dirtyCount = dirtyCount,
          onTriggerWorkManagerPush = onTriggerWorkManagerPush
        )
      }

      // Energy & Battery Safeguards Card
      item {
        EnergyHealthCard(
          energyState = energyState,
          onViewEnergySettings = { onNavigateTab(AppNavigationTab.SERVERS_SETTINGS) }
        )
      }

      // Pending Conflicts Alert Banner
      if (pendingConflicts.isNotEmpty()) {
        item {
          PendingConflictsAlertCard(
            conflicts = pendingConflicts,
            onResolve = { onNavigateTab(AppNavigationTab.CONFLICTS) }
          )
        }
      }

      // Section: Active Folder Sync Pairs
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Active Sync Pairs",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextSecondary
            )
            Text(
              text = "${syncPairs.count { it.isEnabled }} of ${syncPairs.size} folders syncing automatically",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted
            )
          }
          TextButton(
            onClick = { onNavigateTab(AppNavigationTab.SYNC_PAIRS) },
            modifier = Modifier.testTag("view_all_pairs_button")
          ) {
            Text(
              text = "View All",
              color = BrandPrimary,
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
          }
        }
      }

      if (syncPairs.isEmpty()) {
        item {
          EmptyPairsCard(onAddPair = onAddPair)
        }
      } else {
        items(syncPairs, key = { it.id }) { pair ->
          SyncPairDashboardItem(
            pair = pair,
            isSyncing = syncProgress.isRunning && syncProgress.currentPairName == pair.name,
            onSync = { onSyncPair(pair.id) },
            onManage = { onNavigateTab(AppNavigationTab.SYNC_PAIRS) }
          )
        }
      }

      // Section: Recent Sync Activity Feed
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Recent Activity Feed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
          )
          Text(
            text = "View All Logs",
            style = MaterialTheme.typography.labelMedium,
            color = BrandPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clickable { onNavigateTab(AppNavigationTab.LOGS) }
              .padding(4.dp)
              .testTag("view_all_logs_link")
          )
        }
      }

      if (recentLogs.isEmpty()) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantLight)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "No sync events recorded yet. Run a sync to see transfer activity.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
              )
            }
          }
        }
      } else {
        items(recentLogs.take(4), key = { it.id }) { log ->
          RecentLogItemCard(log = log)
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }

    // Floating Add Button with Professional Polish rounded-2xl style
    FloatingActionButton(
      onClick = onAddPair,
      containerColor = BrandPrimaryContainer,
      contentColor = BrandOnPrimaryContainer,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 20.dp, bottom = 20.dp)
        .testTag("add_sync_pair_fab")
    ) {
      Icon(
        Icons.Default.Add,
        contentDescription = "Add Sync Pair",
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

@Composable
fun SyncHeroCard(
  syncPairs: List<SyncPair>,
  syncProgress: SyncProgress,
  animatedProgress: Float,
  systemStatus: String,
  syncHealthPercent: Int,
  onSyncAll: () -> Unit
) {
  val totalFiles = syncPairs.sumOf { it.fileCount }
  val totalBytes = syncPairs.sumOf { it.totalBytes }
  val formattedSize = when {
    totalBytes > 1024 * 1024 * 1024 -> "%.2f GB".format(totalBytes / (1024f * 1024f * 1024f))
    totalBytes > 1024 * 1024 -> "%.1f MB".format(totalBytes / (1024f * 1024f))
    totalBytes > 1024 -> "${totalBytes / 1024} KB"
    else -> "$totalBytes B"
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("sync_hero_card"),
    shape = RoundedCornerShape(28.dp),
    color = BrandPrimaryContainer,
    shadowElevation = 1.dp
  ) {
    Column(
      modifier = Modifier.padding(22.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "System Status",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = BrandOnPrimaryContainer.copy(alpha = 0.8f)
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = systemStatus,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BrandOnPrimaryContainer
          )
          Spacer(modifier = Modifier.height(2.dp))
          val lastSyncTime = syncPairs.maxOfOrNull { it.lastSyncTimestamp } ?: 0L
          Text(
            text = if (syncProgress.isRunning) syncProgress.currentPhase else "Last checked: ${formatRelativeTime(lastSyncTime)}",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = BrandOnPrimaryContainer.copy(alpha = 0.75f)
          )
        }

        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.45f)),
          contentAlignment = Alignment.Center
        ) {
          if (syncProgress.isRunning) {
            CircularProgressIndicator(
              modifier = Modifier.size(26.dp),
              color = BrandPrimary,
              strokeWidth = 2.5.dp
            )
          } else {
            Icon(
              Icons.Default.CheckCircle,
              contentDescription = "System Status",
              tint = BrandPrimary,
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }

      AnimatedVisibility(visible = syncProgress.isRunning) {
        Column(modifier = Modifier.padding(top = 14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = syncProgress.currentFileName.ifEmpty { "Transferring files..." },
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = BrandOnPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Text(
              text = "${(animatedProgress * 100).toInt()}%",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = BrandPrimary
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp)),
            color = BrandPrimary,
            trackColor = Color.White.copy(alpha = 0.5f)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2-Column Statistics Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Efficiency Card
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = 0.65f)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "PAIRS IN SYNC",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (syncPairs.isEmpty()) "—" else "$syncHealthPercent%",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = BrandOnPrimaryContainer
            )
          }
        }

        // Data Tracked Card
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = 0.65f)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "DATA TRACKED",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = formattedSize,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = BrandOnPrimaryContainer
            )
          }
        }
      }
    }
  }
}

@Composable
fun EnergyHealthCard(
  energyState: EnergyState,
  onViewEnergySettings: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("energy_health_card"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(EnergyEmerald.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Bolt,
              contentDescription = "Energy Efficiency",
              tint = EnergyEmerald,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Energy & Battery Guard",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = EnergyEmerald.copy(alpha = 0.12f)
        ) {
          Text(
            text = energyState.energyEfficiencyRating,
            color = EnergyEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Battery stat
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Battery Level",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (energyState.batteryLevelPercent >= 0) {
              "${energyState.batteryLevelPercent}%" + if (energyState.isCharging) " ⚡" else ""
            } else {
              "?"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Wi-Fi stat
        Column(modifier = Modifier.weight(1.2f)) {
          Text(
            text = "Network Guard",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (energyState.isWifiConnected) "Wi-Fi" else "Cellular/Off",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (energyState.isWifiConnected) TextPrimary else WarningAmber,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Power save stat
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Power Saver",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (energyState.isPowerSaveMode) "On" else "Off",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (energyState.isPowerSaveMode) WarningAmber else EnergyEmerald,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
fun PendingConflictsAlertCard(
  conflicts: List<FileConflict>,
  onResolve: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onResolve() }
      .testTag("conflicts_alert_card"),
    shape = RoundedCornerShape(20.dp),
    color = ErrorRoseBg,
    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.25f))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.6f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.Warning,
            contentDescription = "Conflict",
            tint = ErrorRose,
            modifier = Modifier.size(22.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          val firstConflict = conflicts.firstOrNull()
          Text(
            text = if (conflicts.size == 1 && firstConflict != null) "Conflict: ${firstConflict.fileName}" else "${conflicts.size} File Conflict(s) Detected",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ErrorRoseDark
          )
          Text(
            text = "${conflicts.size} version(s) found • Tap to resolve",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = ErrorRoseSub
          )
        }
      }
      Icon(
        Icons.Default.ArrowForwardIos,
        contentDescription = "Open",
        tint = ErrorRose,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

@Composable
fun SyncPairDashboardItem(
  pair: SyncPair,
  isSyncing: Boolean,
  onSync: () -> Unit,
  onManage: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onManage() }
      .testTag("sync_pair_item_${pair.id}"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(OutlineVariantLight),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.FolderShared,
              contentDescription = "Folder Shared",
              tint = TextSecondary,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "${pair.localFolderName} → ${pair.remoteRelativePath.trimStart('/')}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "WebDAV: ${pair.name}",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        IconButton(
          onClick = onSync,
          enabled = !isSyncing,
          modifier = Modifier.testTag("sync_pair_button_${pair.id}")
        ) {
          if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandPrimary)
          } else {
            Icon(
              Icons.Default.Sync,
              contentDescription = "Sync Folder",
              tint = if (pair.isEnabled) BrandPrimary else TextMuted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          StatusBadge(status = pair.lastSyncStatus)
          DirectionBadge(direction = pair.syncDirection)
        }

        val relativeTime = formatRelativeTime(pair.lastSyncTimestamp)
        Text(
          text = "Synced: $relativeTime",
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted
        )
      }
    }
  }
}

@Composable
fun RecentLogItemCard(log: SyncLog) {
  val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantLight)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        LogActionChip(action = log.actionType)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = log.fileName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = log.message.ifEmpty { log.relativePath },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Text(
        text = dateStr,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted
      )
    }
  }
}

private fun formatRelativeTime(timestamp: Long): String {
  if (timestamp == 0L) return "Never"
  val diff = System.currentTimeMillis() - timestamp
  val mins = diff / (60 * 1000)
  return when {
    mins < 1 -> "Just now"
    mins < 60 -> "${mins}m ago"
    mins < 1440 -> "${mins / 60}h ago"
    else -> "${mins / 1440}d ago"
  }
}

@Composable
fun HubSAFStatusCard(
  dirtyCount: Int,
  onTriggerWorkManagerPush: () -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantLight)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(BrandPrimaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Hub,
              contentDescription = "Sync Hub",
              tint = BrandOnPrimaryContainer,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Centralized Sync Hub",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = "Storage Access Framework & Client IPC",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }
        }

        // Active Status Pill
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = EnergyEmerald.copy(alpha = 0.15f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.CheckCircle,
              contentDescription = null,
              tint = EnergyEmerald,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Active",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = EnergyEmerald
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Provider Features Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // SAF DocumentsProvider
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "SAF Provider",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "WebDAV Sync Vault",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = "Files app & document picker",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 9.sp,
              color = TextMuted
            )
          }
        }

        // Client IPC ContentProvider
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "IPC Security",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Signature Permission",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = "ACCESS_SYNC_HUB",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 9.sp,
              color = TextMuted
            )
          }
        }
      }

      // Dirty files status banner & trigger push
      if (dirtyCount > 0) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          color = BrandPrimaryContainer.copy(alpha = 0.35f),
          border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "$dirtyCount Modified File${if (dirtyCount > 1) "s" else ""} Pending Push",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                  color = BrandOnPrimaryContainer
                )
                Text(
                  text = "Queued for WorkManager background sync",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = TextMuted
                )
              }
            }

            Button(
              onClick = onTriggerWorkManagerPush,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = BrandPrimary,
                contentColor = Color.White
              ),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.testTag("push_dirty_button")
            ) {
              Text(text = "Push Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}


