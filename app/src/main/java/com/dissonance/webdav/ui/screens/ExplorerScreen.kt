package com.dissonance.webdav.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.data.model.FileSyncState
import com.dissonance.webdav.data.model.SyncPair
import com.dissonance.webdav.data.model.SyncedFileRecord
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.EnergyEmerald
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.ErrorRoseBg
import com.dissonance.webdav.ui.theme.ErrorRoseDark
import com.dissonance.webdav.ui.theme.InfoBlue
import com.dissonance.webdav.ui.theme.InfoBlueBg
import com.dissonance.webdav.ui.theme.OutlineLight
import com.dissonance.webdav.ui.theme.OutlineVariantLight
import com.dissonance.webdav.ui.theme.SuccessGreen
import com.dissonance.webdav.ui.theme.SuccessGreenBg
import com.dissonance.webdav.ui.theme.TextMuted
import com.dissonance.webdav.ui.theme.TextPrimary
import com.dissonance.webdav.ui.theme.TextSecondary
import com.dissonance.webdav.ui.theme.WarningAmber
import com.dissonance.webdav.ui.theme.WarningAmberBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
  syncPairs: List<SyncPair>,
  selectedPair: SyncPair?,
  files: List<SyncedFileRecord>,
  inspectingFileContent: Pair<String, String>?,
  onSelectPair: (SyncPair?) -> Unit,
  onOpenFile: (SyncPair, String) -> Unit,
  onCloseFileViewer: () -> Unit,
  onSaveFileContent: (SyncPair, String, String) -> Unit,
  onCreateFile: (SyncPair, String, String) -> Unit,
  onSyncPair: (Long) -> Unit
) {
  var pairDropdownExpanded by remember { mutableStateOf(false) }
  var isCreatingFile by remember { mutableStateOf(false) }
  var newFileName by remember { mutableStateOf("") }
  var newFileContent by remember { mutableStateOf("") }

  val activePair = selectedPair ?: syncPairs.firstOrNull()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Text(
      text = "Offline Access & File Explorer",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
      modifier = Modifier.padding(top = 8.dp)
    )
    Text(
      text = "Browse the device folders behind each sync pair. View & edit files; changes sync on the next run.",
      style = MaterialTheme.typography.bodySmall,
      color = TextMuted
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Folder Pair Picker
    if (syncPairs.isNotEmpty()) {
      ExposedDropdownMenuBox(
        expanded = pairDropdownExpanded,
        onExpandedChange = { pairDropdownExpanded = it }
      ) {
        OutlinedTextField(
          value = activePair?.let { "${it.name} (${it.localFolderName})" } ?: "Select Folder Pair",
          onValueChange = {},
          readOnly = true,
          label = { Text("Active Sync Folder") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pairDropdownExpanded) },
          modifier = Modifier
            .menuAnchor()
            .fillMaxWidth()
        )
        ExposedDropdownMenu(
          expanded = pairDropdownExpanded,
          onDismissRequest = { pairDropdownExpanded = false }
        ) {
          syncPairs.forEach { pair ->
            DropdownMenuItem(
              text = { Text("${pair.name} (${pair.localFolderName})") },
              onClick = {
                onSelectPair(pair)
                pairDropdownExpanded = false
              }
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Action Header (Create File, Sync Folder)
    if (activePair != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilledTonalButton(
          onClick = { isCreatingFile = true },
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("create_new_file_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "New File",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }

        IconButton(onClick = { onSyncPair(activePair.id) }) {
          Icon(Icons.Default.Refresh, contentDescription = "Sync Folder", tint = BrandPrimary)
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // File records list
    if (syncPairs.isEmpty()) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BrandPrimaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Folder, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No Sync Pairs Configured",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Create a sync folder pair in the Pairs tab to explore local and remote cached files.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      }
    } else if (files.isEmpty()) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BrandPrimaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.OfflinePin, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No Files in Offline Cache",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Sync this folder pair or tap 'New File' to add a document in the granted device folder.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(files, key = { it.id }) { file ->
          FileItemCard(
            file = file,
            onOpen = { activePair?.let { p -> onOpenFile(p, file.relativePath) } }
          )
        }
        item { Spacer(modifier = Modifier.height(30.dp)) }
      }
    }
  }

  // Create Local File Dialog
  if (isCreatingFile && activePair != null) {
    AlertDialog(
      onDismissRequest = { isCreatingFile = false },
      title = { Text("Create Local Document", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = newFileName,
            onValueChange = { newFileName = it },
            label = { Text("File Name (e.g. Ideas.md, Plan.txt)") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_new_file_name"),
            singleLine = true
          )
          OutlinedTextField(
            value = newFileContent,
            onValueChange = { newFileContent = it },
            label = { Text("Initial Content") },
            modifier = Modifier
              .fillMaxWidth()
              .height(120.dp)
              .testTag("input_new_file_content")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newFileName.isNotBlank()) {
              onCreateFile(activePair, newFileName.trim(), newFileContent)
              newFileName = ""
              newFileContent = ""
              isCreatingFile = false
            }
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
        ) {
          Text("Create & Sync", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { isCreatingFile = false }) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }

  // File Viewer & Editor Modal
  if (inspectingFileContent != null) {
    val (path, initialContent) = inspectingFileContent
    var editedContent by remember(path) { mutableStateOf(initialContent) }

    AlertDialog(
      onDismissRequest = onCloseFileViewer,
      title = {
        Column {
          Text("Document Editor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
          Text(path, fontSize = 11.sp, color = TextMuted)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = EnergyEmerald.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, EnergyEmerald.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.OfflinePin, contentDescription = null, tint = EnergyEmerald, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Device file • Syncs on next run", color = EnergyEmerald, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          OutlinedTextField(
            value = editedContent,
            onValueChange = { editedContent = it },
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp)
              .testTag("file_editor_textarea"),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { activePair?.let { onSaveFileContent(it, path, editedContent) } },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
          modifier = Modifier.testTag("save_file_content_button")
        ) {
          Text("Save Local Changes", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = onCloseFileViewer) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }
}

@Composable
fun FileItemCard(
  file: SyncedFileRecord,
  onOpen: () -> Unit
) {
  val dateFormatted = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(file.localLastModified))
  val (badgeLabel, badgeColor, badgeBg) = when (file.syncState) {
    FileSyncState.SYNCED -> Triple("In Sync", SuccessGreen, SuccessGreenBg)
    FileSyncState.LOCAL_MODIFIED -> Triple("Local Modified", BrandPrimary, BrandPrimaryContainer)
    FileSyncState.REMOTE_MODIFIED -> Triple("Cloud Modified", WarningAmber, WarningAmberBg)
    FileSyncState.CONFLICT -> Triple("Conflict", ErrorRoseDark, ErrorRoseBg)
    FileSyncState.OFFLINE_AVAILABLE -> Triple("Offline Cache", EnergyEmerald, EnergyEmerald.copy(alpha = 0.12f))
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onOpen() }
      .testTag("file_item_${file.fileName}"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
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
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BrandPrimaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Description, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = file.fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${file.fileSize} B • Modified: $dateFormatted",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
          if (file.previewSnippet.isNotEmpty()) {
            Text(
              text = file.previewSnippet,
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp
            )
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(6.dp),
        color = badgeBg
      ) {
        Text(
          text = badgeLabel,
          color = badgeColor,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
      }
    }
  }
}
