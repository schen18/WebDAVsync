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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.FileConflict
import com.dissonance.webdav.ui.components.ConflictStatusChip
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.EnergyEmerald
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.ErrorRoseBg
import com.dissonance.webdav.ui.theme.ErrorRoseDark
import com.dissonance.webdav.ui.theme.ErrorRoseSub
import com.dissonance.webdav.ui.theme.InfoBlue
import com.dissonance.webdav.ui.theme.OutlineLight
import com.dissonance.webdav.ui.theme.OutlineVariantLight
import com.dissonance.webdav.ui.theme.SuccessGreen
import com.dissonance.webdav.ui.theme.TextMuted
import com.dissonance.webdav.ui.theme.TextPrimary
import com.dissonance.webdav.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConflictsScreen(
  pendingConflicts: List<FileConflict>,
  allConflicts: List<FileConflict>,
  onResolveConflict: (conflictId: Long, resolution: ConflictStatus) -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var inspectingConflict by remember { mutableStateOf<FileConflict?>(null) }

  val resolvedConflicts = allConflicts.filter { it.status != ConflictStatus.PENDING }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Text(
      text = "Conflict Resolution Hub",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
      modifier = Modifier.padding(top = 8.dp)
    )
    Text(
      text = "Review and arbitrate when files are edited concurrently on device and WebDAV server.",
      style = MaterialTheme.typography.bodySmall,
      color = TextMuted
    )

    Spacer(modifier = Modifier.height(14.dp))

    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = BrandPrimary,
      indicator = { tabPositions ->
        if (selectedTab < tabPositions.size) {
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
            color = BrandPrimary
          )
        }
      }
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Pending",
              fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              softWrap = false
            )
            if (pendingConflicts.isNotEmpty()) {
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                shape = CircleShape,
                color = ErrorRose,
                modifier = Modifier.size(18.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "${pendingConflicts.size}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = {
          Text(
            text = "History (${resolvedConflicts.size})",
            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
          )
        }
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (selectedTab == 0) {
      if (pendingConflicts.isEmpty()) {
        EmptyConflictsCard()
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          items(pendingConflicts, key = { it.id }) { conflict ->
            PendingConflictCard(
              conflict = conflict,
              onInspect = { inspectingConflict = conflict },
              onResolve = { resolution -> onResolveConflict(conflict.id, resolution) }
            )
          }
          item { Spacer(modifier = Modifier.height(30.dp)) }
        }
      }
    } else {
      if (resolvedConflicts.isEmpty()) {
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
            Text(
              text = "No resolved conflicts history yet.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(resolvedConflicts, key = { it.id }) { conflict ->
            ResolvedConflictCard(conflict = conflict)
          }
          item { Spacer(modifier = Modifier.height(30.dp)) }
        }
      }
    }
  }

  // Deep Conflict Comparator Dialog
  if (inspectingConflict != null) {
    ConflictDetailModal(
      conflict = inspectingConflict!!,
      onDismiss = { inspectingConflict = null },
      onResolve = { resolution ->
        onResolveConflict(inspectingConflict!!.id, resolution)
        inspectingConflict = null
      }
    )
  }
}

@Composable
fun PendingConflictCard(
  conflict: FileConflict,
  onInspect: () -> Unit,
  onResolve: (ConflictStatus) -> Unit
) {
  val dateLocal = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(conflict.localLastModified))
  val dateRemote = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(conflict.remoteLastModified))

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("conflict_card_${conflict.id}"),
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
              .size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(ErrorRoseBg),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRose, modifier = Modifier.size(22.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = conflict.fileName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${conflict.pairName} • ${conflict.relativePath}",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        ConflictStatusChip(status = conflict.status)
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Comparison Box
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Local Device Version Box
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(14.dp),
          color = BrandPrimaryContainer.copy(alpha = 0.4f),
          border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.2f))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Devices, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Device Version", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Modified: $dateLocal", fontSize = 10.sp, color = TextMuted)
            Text("Size: ${conflict.localSize} B", fontSize = 10.sp, color = TextMuted)
            if (conflict.localPreview.isNotEmpty()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = conflict.localPreview,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = TextPrimary
              )
            }
          }
        }

        // Remote WebDAV Version Box
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(14.dp),
          color = BrandPrimaryContainer.copy(alpha = 0.2f),
          border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.15f))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandOnPrimaryContainer, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("WebDAV Server", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandOnPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Modified: $dateRemote", fontSize = 10.sp, color = TextMuted)
            Text("Size: ${conflict.remoteSize} B", fontSize = 10.sp, color = TextMuted)
            if (conflict.remotePreview.isNotEmpty()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = conflict.remotePreview,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = TextPrimary
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Resolution Action Buttons
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
            onClick = { onResolve(ConflictStatus.RESOLVED_LOCAL) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("keep_local_button_${conflict.id}")
          ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Keep Device",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis
            )
          }

          Button(
            onClick = { onResolve(ConflictStatus.RESOLVED_REMOTE) },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("keep_remote_button_${conflict.id}")
          ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Keep Server",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        OutlinedButton(
          onClick = onInspect,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("inspect_conflict_${conflict.id}")
        ) {
          Text(
            text = "Inspect Side-by-Side Diff",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
fun ResolvedConflictCard(conflict: FileConflict) {
  val dateResolved = conflict.resolvedAt?.let {
    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it))
  } ?: "Recently"

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
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(EnergyEmerald.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EnergyEmerald, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = conflict.fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
          Text(
            text = conflict.resolutionNotes.ifEmpty { "Resolved at $dateResolved" },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
        }
      }

      ConflictStatusChip(status = conflict.status)
    }
  }
}

@Composable
fun EmptyConflictsCard() {
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
          .size(54.dp)
          .clip(CircleShape)
          .background(EnergyEmerald.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EnergyEmerald, modifier = Modifier.size(32.dp))
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "No Version Conflicts",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "All folder sync pairs are in perfect agreement. Any future conflicting edits will appear here for side-by-side arbitration.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
    }
  }
}

@Composable
fun ConflictDetailModal(
  conflict: FileConflict,
  onDismiss: () -> Unit,
  onResolve: (ConflictStatus) -> Unit
) {
  val dateLocal = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(conflict.localLastModified))
  val dateRemote = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(conflict.remoteLastModified))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("Conflict Inspector: ${conflict.fileName}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
        Text(conflict.relativePath, fontSize = 11.sp, color = TextMuted)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Device Box
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = BrandPrimaryContainer.copy(alpha = 0.35f),
          border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.2f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("📱 Device Local Copy ($dateLocal)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = conflict.localPreview.ifEmpty { "(Empty file or binary data)" },
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = TextPrimary
            )
          }
        }

        // Server Box
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = BrandPrimaryContainer.copy(alpha = 0.2f),
          border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.15f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("☁️ WebDAV Server Copy ($dateRemote)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandOnPrimaryContainer)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = conflict.remotePreview.ifEmpty { "(Empty file or binary data)" },
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = TextPrimary
            )
          }
        }

        Text(
          text = "Select resolution strategy:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )

        // Options
        Button(
          onClick = { onResolve(ConflictStatus.RESOLVED_LOCAL) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
        ) {
          Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Keep Device Version",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }

        Button(
          onClick = { onResolve(ConflictStatus.RESOLVED_REMOTE) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = BrandPrimaryContainer, contentColor = BrandOnPrimaryContainer)
        ) {
          Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Keep Server Version",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }

        OutlinedButton(
          onClick = { onResolve(ConflictStatus.RESOLVED_BOTH) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Keep Both Files",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
      }
    }
  )
}

