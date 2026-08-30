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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.dissonance.webdav.data.model.WebdavServer
import com.dissonance.webdav.sync.EnergyState
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

@Composable
fun SettingsScreen(
  servers: List<WebdavServer>,
  energyState: EnergyState,
  isCreatingServer: Boolean,
  editingServer: WebdavServer?,
  serverTestResult: String?,
  onOpenCreateServer: () -> Unit,
  onOpenEditServer: (WebdavServer) -> Unit,
  onCloseServerEditor: () -> Unit,
  onSaveServer: (id: Long, name: String, url: String, user: String, pass: String, trustSelfSigned: Boolean) -> Unit,
  onDeleteServer: (WebdavServer) -> Unit,
  onTestServer: (WebdavServer) -> Unit,
  onResetAllData: () -> Unit = {}
) {
  var serverToDelete by remember { mutableStateOf<WebdavServer?>(null) }
  var isShowingResetDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Text(
        text = "Servers & Energy Engine",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 8.dp)
      )
      Text(
        text = "Manage WebDAV remote targets (Nextcloud, Synology, ownCloud) and fine-tune power optimization safeguards.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
      )
    }

    // Section: WebDAV Servers
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "WebDAV Server Profiles",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
          onClick = onOpenCreateServer,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("add_server_profile_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Add Server",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }

    if (servers.isEmpty()) {
      item {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantLight)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.Storage,
              contentDescription = null,
              tint = BrandPrimary.copy(alpha = 0.6f),
              modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No WebDAV Servers Configured",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Add your Nextcloud, ownCloud, Synology DSM, TrueNAS, or custom WebDAV endpoint to start syncing folder pairs.",
              fontSize = 12.sp,
              color = TextMuted,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = onOpenCreateServer,
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add WebDAV Server", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    } else {
      items(servers, key = { it.id }) { server ->
        ServerProfileCard(
          server = server,
          onEdit = { onOpenEditServer(server) },
          onDelete = { serverToDelete = server },
          onTest = { onTestServer(server) }
        )
      }
    }

    // Server test feedback banner if active
    if (serverTestResult != null) {
      item {
        val isSuccess = serverTestResult.startsWith("✓")
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSuccess) SuccessGreenBg else ErrorRoseBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, if (isSuccess) SuccessGreen.copy(alpha = 0.3f) else ErrorRose.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              if (isSuccess) Icons.Default.Check else Icons.Default.NetworkCheck,
              contentDescription = null,
              tint = if (isSuccess) SuccessGreen else ErrorRoseDark,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = serverTestResult,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isSuccess) SuccessGreen else ErrorRoseDark
            )
          }
        }
      }
    }

    // Section: Power & Battery Guard Diagnostics
    item {
      Text(
        text = "Energy Efficiency & Power Guard",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(EnergyEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = EnergyEmerald, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(text = "Power Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text(
                  text = "Syncs pause on low battery, cellular data and Battery Saver",
                  fontSize = 11.sp,
                  color = TextMuted,
                  maxLines = 2
                )
              }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(shape = RoundedCornerShape(6.dp), color = EnergyEmerald.copy(alpha = 0.12f)) {
              Text(
                text = energyState.energyEfficiencyRating,
                color = EnergyEmerald,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          // Live device state (read-only; constraints are configured per sync pair)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Battery", fontSize = 11.sp, color = TextMuted)
              Text(
                text = if (energyState.batteryLevelPercent >= 0) "${energyState.batteryLevelPercent}%" else "?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text("Network", fontSize = 11.sp, color = TextMuted)
              Text(
                text = if (energyState.isWifiConnected) "Wi-Fi" else "Metered/Off",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (energyState.isWifiConnected) TextPrimary else WarningAmber
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text("Power Source", fontSize = 11.sp, color = TextMuted)
              Text(
                text = if (energyState.isCharging) "Charging" else "Battery",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
          }
        }
      }
    }

    // Section: App Storage & Maintenance
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineLight)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Storage & Maintenance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "• WebDAV RFC 4918 Compliant (PROPFIND, MKCOL, PUT, GET, DELETE)\n• Supported Backends: Nextcloud, ownCloud, TrueNAS, Synology DSM, Apache, NGINX\n• Encryption: HTTPS with custom CA and self-signed certificate validation support\n• Local Database: SQLite Room DB with full transaction integrity",
            fontSize = 11.sp,
            color = TextMuted,
            lineHeight = 18.sp
          )
          Spacer(modifier = Modifier.height(14.dp))
          Divider(color = OutlineVariantLight)
          Spacer(modifier = Modifier.height(14.dp))

          OutlinedButton(
            onClick = { isShowingResetDialog = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRose),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.5f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reset_all_data_button")
          ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Flush / Reset App",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(40.dp)) }
  }

  // Reset Confirmation Dialog
  if (isShowingResetDialog) {
    AlertDialog(
      onDismissRequest = { isShowingResetDialog = false },
      title = { Text("Reset App & Wipe All Data?", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = {
        Text(
          "This erases all configured WebDAV servers, sync pairs, file records, version conflicts, and sync logs, and releases folder permissions. Files in your device folders and on your servers are not touched.",
          color = TextSecondary,
          fontSize = 13.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onResetAllData()
            isShowingResetDialog = false
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRose, contentColor = Color.White)
        ) {
          Text("Reset Everything", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { isShowingResetDialog = false }) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }

  // Delete Server Confirmation
  if (serverToDelete != null) {
    AlertDialog(
      onDismissRequest = { serverToDelete = null },
      title = { Text("Delete WebDAV Server?", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = { Text("Remove '${serverToDelete?.name}'? Folder pairs associated with this server will stop syncing.", color = TextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            serverToDelete?.let { onDeleteServer(it) }
            serverToDelete = null
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRose, contentColor = Color.White)
        ) {
          Text("Delete", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { serverToDelete = null }) {
          Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }

  // Add/Edit Server Dialog
  if (isCreatingServer || editingServer != null) {
    ServerEditorDialog(
      server = editingServer,
      onDismiss = onCloseServerEditor,
      onSave = onSaveServer
    )
  }
}

@Composable
fun ServerProfileCard(
  server: WebdavServer,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onTest: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("server_card_${server.id}"),
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
              .background(BrandPrimaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Dns, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(22.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(text = server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
              text = server.url,
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Row {
          IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Server", tint = TextMuted)
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete Server", tint = ErrorRose)
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
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          if (server.trustSelfSigned) {
            Surface(shape = RoundedCornerShape(6.dp), color = InfoBlueBg) {
              Text(
                text = "Self-Signed SSL",
                color = InfoBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        OutlinedButton(
          onClick = onTest,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("test_server_button_${server.id}")
        ) {
          Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Test Probe",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }
    }
  }
}

@Composable
fun ServerEditorDialog(
  server: WebdavServer?,
  onDismiss: () -> Unit,
  onSave: (id: Long, name: String, url: String, user: String, pass: String, trustSelfSigned: Boolean) -> Unit
) {
  var name by remember { mutableStateOf(server?.name ?: "") }
  var url by remember { mutableStateOf(server?.url ?: "") }
  var username by remember { mutableStateOf(server?.username ?: "") }
  var password by remember { mutableStateOf(server?.password ?: "") }
  var trustSelfSigned by remember { mutableStateOf(server?.trustSelfSigned ?: true) }

  val canSave = url.isNotBlank() && name.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (server == null) "Add WebDAV Server" else "Edit WebDAV Server", fontWeight = FontWeight.Bold, color = TextPrimary) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Server Display Name") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("WebDAV URL Endpoint (e.g. https://cloud.example.com/remote.php/dav/files/user)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("Username (optional)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("App Password / Token") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Trust Self-Signed Certificates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("For home lab HTTPS servers without commercial CA certs", fontSize = 11.sp, color = TextMuted)
          }
          Switch(checked = trustSelfSigned, onCheckedChange = { trustSelfSigned = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(server?.id ?: 0L, name.trim(), url.trim(), username.trim(), password, trustSelfSigned)
        },
        enabled = canSave,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
      ) {
        Text("Save Server", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
      }
    }
  )
}
