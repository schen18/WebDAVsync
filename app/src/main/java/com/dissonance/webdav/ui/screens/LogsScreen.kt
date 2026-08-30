package com.dissonance.webdav.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.data.model.LogActionType
import com.dissonance.webdav.data.model.LogStatus
import com.dissonance.webdav.data.model.SyncLog
import com.dissonance.webdav.ui.components.LogActionChip
import com.dissonance.webdav.ui.theme.BrandOnPrimaryContainer
import com.dissonance.webdav.ui.theme.BrandPrimary
import com.dissonance.webdav.ui.theme.BrandPrimaryContainer
import com.dissonance.webdav.ui.theme.ErrorRose
import com.dissonance.webdav.ui.theme.ErrorRoseBg
import com.dissonance.webdav.ui.theme.ErrorRoseDark
import com.dissonance.webdav.ui.theme.InfoBlue
import com.dissonance.webdav.ui.theme.InfoBlueBg
import com.dissonance.webdav.ui.theme.OutlineLight
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

@Composable
fun LogsScreen(
  logs: List<SyncLog>,
  onClearLogs: () -> Unit
) {
  var selectedFilter by remember { mutableStateOf("ALL") }
  var showClearConfirm by remember { mutableStateOf(false) }

  val filteredLogs = logs.filter { log ->
    when (selectedFilter) {
      "UPLOADS" -> log.actionType == LogActionType.UPLOAD
      "DOWNLOADS" -> log.actionType == LogActionType.DOWNLOAD
      "CONFLICTS" -> log.actionType == LogActionType.CONFLICT
      "ERRORS" -> log.actionType == LogActionType.ERROR || log.status == LogStatus.FAILED
      "SKIPS" -> log.actionType == LogActionType.SKIP
      else -> true
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Synchronization Logs & Audit",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
        Text(
          text = "${logs.size} recorded audit events",
          style = MaterialTheme.typography.bodySmall,
          color = TextMuted
        )
      }

      if (logs.isNotEmpty()) {
        IconButton(
          onClick = { showClearConfirm = true },
          modifier = Modifier.testTag("clear_logs_button")
        ) {
          Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = TextMuted)
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      val filters = listOf(
        "ALL" to "All Events (${logs.size})",
        "UPLOADS" to "Uploads",
        "DOWNLOADS" to "Downloads",
        "CONFLICTS" to "Conflicts",
        "ERRORS" to "Errors",
        "SKIPS" to "Throttled / Skipped"
      )
      items(filters) { (key, label) ->
        FilterChip(
          selected = selectedFilter == key,
          onClick = { selectedFilter = key },
          label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandPrimaryContainer,
            selectedLabelColor = BrandPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = TextSecondary
          ),
          border = FilterChipDefaults.filterChipBorder(
            borderColor = if (selectedFilter == key) BrandPrimary else OutlineLight,
            selectedBorderColor = BrandPrimary,
            enabled = true,
            selected = selectedFilter == key
          ),
          shape = RoundedCornerShape(10.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (filteredLogs.isEmpty()) {
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
              Icon(Icons.Default.History, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No log records found",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Sync activity will be streamed here in real-time.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(filteredLogs, key = { it.id }) { log ->
          DetailedLogCard(log = log)
        }
        item { Spacer(modifier = Modifier.height(30.dp)) }
      }
    }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear All Logs?", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = { Text("This will permanently delete all sync history and audit logs.", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            onClearLogs()
            showClearConfirm = false
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRose, contentColor = Color.White)
        ) {
          Text("Clear All", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }
}

@Composable
fun DetailedLogCard(log: SyncLog) {
  val dateFormatted = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
  val (statusLabel, statusColor, statusBg) = when (log.status) {
    LogStatus.SUCCESS -> Triple("OK", SuccessGreen, SuccessGreenBg)
    LogStatus.FAILED -> Triple("FAIL", ErrorRoseDark, ErrorRoseBg)
    LogStatus.RESOLVED -> Triple("RESOLVED", SuccessGreen, SuccessGreenBg)
    LogStatus.SKIPPED -> Triple("PAUSED", WarningAmber, WarningAmberBg)
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("log_row_${log.id}"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          LogActionChip(action = log.actionType)
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = statusBg
          ) {
            Text(
              text = statusLabel,
              color = statusColor,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          if (log.pairName.isNotEmpty()) {
            Text(
              text = log.pairName,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextMuted
            )
          }
        }

        Text(
          text = dateFormatted,
          fontSize = 10.sp,
          color = TextMuted
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (log.fileName != "-") log.fileName else log.relativePath,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        if (log.bytesTransferred > 0) {
          val sizeFormatted = when {
            log.bytesTransferred > 1024 * 1024 -> "%.1f MB".format(log.bytesTransferred / (1024f * 1024f))
            log.bytesTransferred > 1024 -> "${log.bytesTransferred / 1024} KB"
            else -> "${log.bytesTransferred} B"
          }
          Text(
            text = sizeFormatted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted
          )
        }
      }

      if (log.message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = log.message,
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
