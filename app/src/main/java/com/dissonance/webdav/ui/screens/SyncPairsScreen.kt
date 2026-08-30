package com.dissonance.webdav.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.data.model.ConflictPolicy
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.WebdavServer
import com.dissonance.webdav.sync.LocalFileManager
import com.dissonance.webdav.sync.SyncProgress
import com.dissonance.webdav.ui.components.ConflictPolicyBadge
import com.dissonance.webdav.ui.components.DirectionBadge
import com.dissonance.webdav.ui.components.StatusBadge
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.EnergyEmerald
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.OutlineLight
import com.dissonance.webdav.ui.theme.OutlineVariantLight
import com.dissonance.webdav.ui.theme.TextMuted
import com.dissonance.webdav.ui.theme.TextPrimary
import com.dissonance.webdav.ui.theme.TextSecondary
import com.dissonance.webdav.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPairsScreen(
  syncPairs: List<SyncPair>,
  servers: List<WebdavServer>,
  syncProgress: SyncProgress,
  onSyncPair: (Long) -> Unit,
  onToggleEnabled: (SyncPair) -> Unit,
  onEditPair: (SyncPair) -> Unit,
  onDeletePair: (SyncPair) -> Unit,
  onSelectPairForFiles: (SyncPair) -> Unit,
  isCreating: Boolean,
  editingPair: SyncPair?,
  onOpenCreate: () -> Unit,
  onCloseEditor: () -> Unit,
  onSavePair: (
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
  ) -> Unit
) {
  var pairToDelete by remember { mutableStateOf<SyncPair?>(null) }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = onOpenCreate,
        containerColor = BrandPrimaryContainer,
        contentColor = BrandOnPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("fab_add_sync_pair")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Folder Pair", modifier = Modifier.size(28.dp))
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      Text(
        text = "Folder Synchronization Pairs",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 8.dp)
      )
      Text(
        text = "Map device directories to WebDAV server paths with custom conflict rules and energy thresholds.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (syncPairs.isEmpty()) {
        EmptyPairsCard(onAddPair = onOpenCreate)
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(syncPairs, key = { it.id }) { pair ->
            val isSyncing = syncProgress.isRunning && syncProgress.currentPairName == pair.name
            SyncPairDetailedCard(
              pair = pair,
              isSyncing = isSyncing,
              onSync = { onSyncPair(pair.id) },
              onToggleEnabled = { onToggleEnabled(pair) },
              onEdit = { onEditPair(pair) },
              onDelete = { pairToDelete = pair },
              onViewFiles = { onSelectPairForFiles(pair) }
            )
          }
          item {
            Spacer(modifier = Modifier.height(80.dp))
          }
        }
      }
    }
  }

  // Delete Confirmation Dialog
  if (pairToDelete != null) {
    AlertDialog(
      onDismissRequest = { pairToDelete = null },
      title = { Text("Delete Folder Pair?", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = { Text("Are you sure you want to remove '${pairToDelete?.name}'? Local and remote files will not be deleted from storage.", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            pairToDelete?.let { onDeletePair(it) }
            pairToDelete = null
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRose, contentColor = Color.White)
        ) {
          Text("Delete", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { pairToDelete = null }) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }

  // Add/Edit Dialog
  if (isCreating || editingPair != null) {
    SyncPairEditorDialog(
      pair = editingPair,
      servers = servers,
      onDismiss = onCloseEditor,
      onSave = onSavePair
    )
  }
}

@Composable
fun SyncPairDetailedCard(
  pair: SyncPair,
  isSyncing: Boolean,
  onSync: () -> Unit,
  onToggleEnabled: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onViewFiles: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("pair_card_${pair.id}"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header: Name + Switch
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
              .size(42.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(OutlineVariantLight),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.FolderShared, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = pair.name,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${pair.fileCount} files (${pair.totalBytes / 1024} KB)",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }
        }

        Switch(
          checked = pair.isEnabled,
          onCheckedChange = { onToggleEnabled() },
          modifier = Modifier.testTag("switch_pair_enabled_${pair.id}")
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Path mapping banner
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = OutlineVariantLight.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantLight),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "📱 Local: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
              text = if (pair.localTreeUri.isEmpty()) "${pair.localFolderName} (re-select folder)" else pair.localFolderName,
              fontSize = 11.sp,
              color = if (pair.localTreeUri.isEmpty()) ErrorRose else TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "☁️ WebDAV: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
              text = pair.remoteRelativePath,
              fontSize = 11.sp,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Badges
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        StatusBadge(status = pair.lastSyncStatus)
        DirectionBadge(direction = pair.syncDirection)
        ConflictPolicyBadge(policy = pair.conflictPolicy)
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Energy & Schedule Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextMuted)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (pair.syncIntervalMinutes > 0) "${pair.syncIntervalMinutes}m" else "Manual",
              fontSize = 11.sp,
              color = TextMuted
            )
          }

          if (pair.wifiOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp), tint = EnergyEmerald)
              Spacer(modifier = Modifier.width(3.dp))
              Text(text = "Wi-Fi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EnergyEmerald)
            }
          }

          if (pair.chargingOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(14.dp), tint = EnergyEmerald)
              Spacer(modifier = Modifier.width(3.dp))
              Text(text = "AC Only", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EnergyEmerald)
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = EnergyEmerald)
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = ">${pair.batterySaverThreshold}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EnergyEmerald)
          }
        }

        Text(
          text = formatRelativeTime(pair.lastSyncTimestamp),
          fontSize = 11.sp,
          color = TextMuted
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onViewFiles,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("explore_pair_files_${pair.id}")
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Explore",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = "Edit Pair", tint = TextSecondary)
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Pair", tint = ErrorRose)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
          onClick = onSync,
          enabled = !isSyncing && pair.isEnabled,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
        ) {
          if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Sync",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPairEditorDialog(
  pair: SyncPair?,
  servers: List<WebdavServer>,
  onDismiss: () -> Unit,
  onSave: (
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
  ) -> Unit
) {
  val context = LocalContext.current
  val localFiles = remember { LocalFileManager(context) }

  var name by remember { mutableStateOf(pair?.name ?: "") }
  var serverId by remember { mutableLongStateOf(pair?.serverId ?: servers.firstOrNull()?.id ?: 1L) }
  var treeUri by remember { mutableStateOf(pair?.localTreeUri ?: "") }
  var folderName by remember { mutableStateOf(pair?.localFolderName ?: "") }
  var remotePath by remember { mutableStateOf(pair?.remoteRelativePath ?: "/") }
  var direction by remember { mutableStateOf(pair?.syncDirection ?: SyncDirection.TWO_WAY) }
  var policy by remember { mutableStateOf(pair?.conflictPolicy ?: ConflictPolicy.ASK_USER) }
  var interval by remember { mutableIntStateOf(pair?.syncIntervalMinutes ?: 30) }
  var wifiOnly by remember { mutableStateOf(pair?.wifiOnly ?: true) }
  var chargingOnly by remember { mutableStateOf(pair?.chargingOnly ?: false) }
  var batteryThreshold by remember { mutableIntStateOf(pair?.batterySaverThreshold ?: 20) }
  var excludes by remember { mutableStateOf(pair?.excludePatterns ?: ".tmp, .bak, .DS_Store") }
  var isEnabled by remember { mutableStateOf(pair?.isEnabled ?: true) }

  // System folder picker: grants persistent read/write access to a real device folder.
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      localFiles.persistGrant(uri)
      val displayName = localFiles.folderDisplayName(uri.toString()) ?: "Folder"
      treeUri = uri.toString()
      folderName = displayName
      if (name.isBlank()) name = displayName
      if (remotePath == "/" || remotePath.isBlank()) remotePath = "/$displayName"
    }
  }

  val canSave = treeUri.isNotEmpty() && servers.isNotEmpty() &&
    name.isNotBlank() && remotePath.isNotBlank()

  var serverDropdownExpanded by remember { mutableStateOf(false) }
  var directionDropdownExpanded by remember { mutableStateOf(false) }
  var policyDropdownExpanded by remember { mutableStateOf(false) }
  var intervalDropdownExpanded by remember { mutableStateOf(false) }

  val intervals = listOf(
    15 to "Every 15 minutes (High frequency)",
    30 to "Every 30 minutes (Balanced)",
    60 to "Every 1 hour (Standard)",
    360 to "Every 6 hours (Low energy)",
    1440 to "Once daily (Ultra low power)",
    0 to "Manual trigger only"
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (pair == null) "New Folder Sync Pair" else "Edit Folder Sync Pair",
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Sync Pair Label") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_pair_name"),
          singleLine = true
        )

        // WebDAV Server Dropdown
        ExposedDropdownMenuBox(
          expanded = serverDropdownExpanded,
          onExpandedChange = { serverDropdownExpanded = it }
        ) {
          val selectedServer = servers.find { it.id == serverId } ?: servers.firstOrNull()
          OutlinedTextField(
            value = selectedServer?.name ?: "Select WebDAV Server",
            onValueChange = {},
            readOnly = true,
            label = { Text("WebDAV Server Profile") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = serverDropdownExpanded,
            onDismissRequest = { serverDropdownExpanded = false }
          ) {
            if (servers.isEmpty()) {
              DropdownMenuItem(
                text = { Text("No servers configured (Add in Settings)", color = TextMuted) },
                onClick = { serverDropdownExpanded = false }
              )
            } else {
              servers.forEach { s ->
                DropdownMenuItem(
                  text = { Text(s.name) },
                  onClick = {
                    serverId = s.id
                    serverDropdownExpanded = false
                  }
                )
              }
            }
          }
        }

        // Device folder (SAF tree grant)
        Column {
          Text(
            text = "Device Folder",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (folderName.isEmpty()) "No folder selected" else folderName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (folderName.isEmpty()) WarningAmber else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = if (folderName.isEmpty()) {
                  "Pick any real device folder (Photos, Downloads, ...)"
                } else {
                  "Read/write access granted via Android"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            OutlinedButton(
              onClick = { folderPicker.launch(null) },
              shape = RoundedCornerShape(10.dp),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier.testTag("pick_folder_button")
            ) {
              Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (folderName.isEmpty()) "Choose" else "Change",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }

        // Remote WebDAV path
        OutlinedTextField(
          value = remotePath,
          onValueChange = { remotePath = it },
          label = { Text("Remote WebDAV Path") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        // Sync Direction Dropdown
        ExposedDropdownMenuBox(
          expanded = directionDropdownExpanded,
          onExpandedChange = { directionDropdownExpanded = it }
        ) {
          val dirLabel = when (direction) {
            SyncDirection.TWO_WAY -> "Two-Way Bidirectional Sync"
            SyncDirection.LOCAL_TO_REMOTE -> "Upload Mirror (Device to WebDAV)"
            SyncDirection.REMOTE_TO_LOCAL -> "Download Mirror (WebDAV to Device)"
          }
          OutlinedTextField(
            value = dirLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync Direction") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = directionDropdownExpanded,
            onDismissRequest = { directionDropdownExpanded = false }
          ) {
            SyncDirection.values().forEach { d ->
              DropdownMenuItem(
                text = {
                  Text(
                    when (d) {
                      SyncDirection.TWO_WAY -> "Two-Way Bidirectional Sync"
                      SyncDirection.LOCAL_TO_REMOTE -> "Upload Mirror (Device to WebDAV)"
                      SyncDirection.REMOTE_TO_LOCAL -> "Download Mirror (WebDAV to Device)"
                    }
                  )
                },
                onClick = {
                  direction = d
                  directionDropdownExpanded = false
                }
              )
            }
          }
        }

        // Conflict Resolution Policy Dropdown
        ExposedDropdownMenuBox(
          expanded = policyDropdownExpanded,
          onExpandedChange = { policyDropdownExpanded = it }
        ) {
          val policyLabel = when (policy) {
            ConflictPolicy.ASK_USER -> "Ask User (Manual Resolution Hub)"
            ConflictPolicy.DEVICE_WINS -> "Device Wins (Always keep local version)"
            ConflictPolicy.SERVER_WINS -> "Server Wins (Always keep WebDAV version)"
            ConflictPolicy.KEEP_BOTH -> "Keep Both (Create duplicate copy)"
            ConflictPolicy.LATEST_WINS -> "Latest Wins (Newer timestamp overwrites)"
          }
          OutlinedTextField(
            value = policyLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Conflict Resolution Policy") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = policyDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = policyDropdownExpanded,
            onDismissRequest = { policyDropdownExpanded = false }
          ) {
            ConflictPolicy.values().forEach { p ->
              DropdownMenuItem(
                text = {
                  Text(
                    when (p) {
                      ConflictPolicy.ASK_USER -> "Ask User (Manual Resolution Hub)"
                      ConflictPolicy.DEVICE_WINS -> "Device Wins (Always keep local version)"
                      ConflictPolicy.SERVER_WINS -> "Server Wins (Always keep WebDAV version)"
                      ConflictPolicy.KEEP_BOTH -> "Keep Both (Create duplicate copy)"
                      ConflictPolicy.LATEST_WINS -> "Latest Wins (Newer timestamp overwrites)"
                    }
                  )
                },
                onClick = {
                  policy = p
                  policyDropdownExpanded = false
                }
              )
            }
          }
        }

        // Sync Schedule Interval
        ExposedDropdownMenuBox(
          expanded = intervalDropdownExpanded,
          onExpandedChange = { intervalDropdownExpanded = it }
        ) {
          val label = intervals.find { it.first == interval }?.second ?: "Every ${interval}m"
          OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync Interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = intervalDropdownExpanded,
            onDismissRequest = { intervalDropdownExpanded = false }
          ) {
            intervals.forEach { (mins, text) ->
              DropdownMenuItem(
                text = { Text(text) },
                onClick = {
                  interval = mins
                  intervalDropdownExpanded = false
                }
              )
            }
          }
        }

        // Energy & Battery Safeguards
        Text(
          text = "Energy-Efficient Safeguards",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = EnergyEmerald,
          modifier = Modifier.padding(top = 4.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Wi-Fi Only (Unmetered)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("Pause sync on cellular data to save battery & data", fontSize = 11.sp, color = TextMuted)
          }
          Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("AC Charging Only", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("Only run sync when device is plugged into power", fontSize = 11.sp, color = TextMuted)
          }
          Switch(checked = chargingOnly, onCheckedChange = { chargingOnly = it })
        }

        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Battery Saver Pause Level", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("$batteryThreshold%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EnergyEmerald)
          }
          Slider(
            value = batteryThreshold.toFloat(),
            onValueChange = { batteryThreshold = it.toInt() },
            valueRange = 10f..50f,
            steps = 7
          )
          Text("Sync will automatically pause if battery falls below $batteryThreshold%", fontSize = 11.sp, color = TextMuted)
        }

        // File filters
        OutlinedTextField(
          value = excludes,
          onValueChange = { excludes = it },
          label = { Text("Exclude Patterns (comma separated)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            pair?.id ?: 0L,
            serverId,
            name.trim(),
            folderName,
            treeUri,
            remotePath,
            direction,
            policy,
            interval,
            wifiOnly,
            chargingOnly,
            batteryThreshold,
            excludes,
            isEnabled
          )
        },
        enabled = canSave,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
        modifier = Modifier.testTag("save_sync_pair_button")
      ) {
        Text("Save Sync Pair", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
      }
    }
  )
}

@Composable
fun EmptyPairsCard(onAddPair: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(BrandPrimaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.FolderShared, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(32.dp))
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = "No Sync Pairs Configured",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Link your device folders to remote WebDAV storage paths to start background sync with conflict protection.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = onAddPair,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Create First Sync Pair", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

private fun formatRelativeTime(timestamp: Long?): String {
  if (timestamp == null || timestamp == 0L) return "Never synced"
  val diff = System.currentTimeMillis() - timestamp
  val mins = diff / (1000 * 60)
  return when {
    mins < 1 -> "Just now"
    mins < 60 -> "${mins}m ago"
    mins < 1440 -> "${mins / 60}h ago"
    else -> "${mins / 1440}d ago"
  }
}

