package com.dissonance.webdav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dissonance.webdav.data.model.ConflictPolicy
import com.dissonance.webdav.data.model.ConflictStatus
import com.dissonance.webdav.data.model.LogActionType
import com.dissonance.webdav.data.model.SyncDirection
import com.dissonance.webdav.data.model.SyncStatus
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
import com.dissonance.webdav.ui.theme.SuccessGreen
import com.dissonance.webdav.ui.theme.SuccessGreenBg
import com.dissonance.webdav.ui.theme.WarningAmber
import com.dissonance.webdav.ui.theme.WarningAmberBg

@Composable
fun StatusBadge(
  status: SyncStatus,
  modifier: Modifier = Modifier
) {
  val (bgColor, textColor, label) = when (status) {
    SyncStatus.SUCCESS -> Triple(SuccessGreenBg, SuccessGreen, "In Sync")
    SyncStatus.RUNNING -> Triple(BrandPrimaryContainer, BrandPrimary, "Syncing...")
    SyncStatus.CONFLICT_DETECTED -> Triple(ErrorRoseBg, ErrorRoseDark, "Conflict")
    SyncStatus.WARNING -> Triple(WarningAmberBg, WarningAmber, "Warning")
    SyncStatus.ERROR -> Triple(ErrorRoseBg, ErrorRoseDark, "Error")
    SyncStatus.PAUSED_BATTERY -> Triple(WarningAmberBg, WarningAmber, "Paused (Bat)")
    SyncStatus.PAUSED_NETWORK -> Triple(InfoBlueBg, InfoBlue, "Paused (Wi-Fi)")
    SyncStatus.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Idle")
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = bgColor
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(textColor)
      )
      Text(
        text = label,
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 5.dp)
      )
    }
  }
}

@Composable
fun DirectionBadge(
  direction: SyncDirection,
  modifier: Modifier = Modifier
) {
  val (label, color, bg) = when (direction) {
    SyncDirection.TWO_WAY -> Triple("Two-Way", BrandPrimary, BrandPrimaryContainer.copy(alpha = 0.5f))
    SyncDirection.LOCAL_TO_REMOTE -> Triple("Upload", BrandPrimary, BrandPrimaryContainer.copy(alpha = 0.3f))
    SyncDirection.REMOTE_TO_LOCAL -> Triple("Download", InfoBlue, InfoBlueBg.copy(alpha = 0.4f))
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = bg
  ) {
    Text(
      text = label,
      color = color,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
    )
  }
}

@Composable
fun ConflictPolicyBadge(
  policy: ConflictPolicy,
  modifier: Modifier = Modifier
) {
  val label = when (policy) {
    ConflictPolicy.ASK_USER -> "Ask User"
    ConflictPolicy.DEVICE_WINS -> "Device Wins"
    ConflictPolicy.SERVER_WINS -> "Server Wins"
    ConflictPolicy.KEEP_BOTH -> "Keep Both"
    ConflictPolicy.LATEST_WINS -> "Latest Wins"
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
    )
  }
}

@Composable
fun LogActionChip(
  action: LogActionType,
  modifier: Modifier = Modifier
) {
  val (label, color, bg) = when (action) {
    LogActionType.UPLOAD -> Triple("UPLOAD", BrandPrimary, BrandPrimaryContainer)
    LogActionType.DOWNLOAD -> Triple("DOWNLOAD", InfoBlue, InfoBlueBg)
    LogActionType.CONFLICT -> Triple("CONFLICT", ErrorRoseDark, ErrorRoseBg)
    LogActionType.DELETE -> Triple("DELETE", WarningAmber, WarningAmberBg)
    LogActionType.MKCOL -> Triple("MKDIR", SuccessGreen, SuccessGreenBg)
    LogActionType.ERROR -> Triple("ERROR", ErrorRoseDark, ErrorRoseBg)
    LogActionType.SCAN -> Triple("SCAN", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    LogActionType.SKIP -> Triple("SKIP", WarningAmber, WarningAmberBg)
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(6.dp),
    color = bg
  ) {
    Text(
      text = label,
      color = color,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
    )
  }
}

@Composable
fun ConflictStatusChip(
  status: ConflictStatus,
  modifier: Modifier = Modifier
) {
  val (label, color, bg) = when (status) {
    ConflictStatus.PENDING -> Triple("ACTION REQUIRED", ErrorRoseDark, ErrorRoseBg)
    ConflictStatus.RESOLVED_LOCAL -> Triple("KEEP DEVICE", SuccessGreen, SuccessGreenBg)
    ConflictStatus.RESOLVED_REMOTE -> Triple("KEEP SERVER", InfoBlue, InfoBlueBg)
    ConflictStatus.RESOLVED_BOTH -> Triple("KEEP BOTH", BrandOnPrimaryContainer, BrandPrimaryContainer)
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(6.dp),
    color = bg
  ) {
    Text(
      text = label,
      color = color,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
    )
  }
}

