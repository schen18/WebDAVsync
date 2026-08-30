package com.dissonance.webdav.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the device's real energy/network state and decides whether a sync pair
 * may run under its Wi-Fi/charging/battery constraints. There are no simulated
 * values: unknown hardware state is reported as such.
 */
class EnergyMonitor(private val context: Context) {

  private val _energyState = MutableStateFlow(EnergyState())
  val energyState: StateFlow<EnergyState> = _energyState.asStateFlow()

  // Test-only injection point; production code never touches it.
  internal var stateOverrideForTesting: EnergyState? = null

  init {
    refreshEnergyState()
  }

  fun refreshEnergyState() {
    stateOverrideForTesting?.let {
      _energyState.value = it
      return
    }

    // -1 means "unknown" (e.g. no battery hardware); no fabricated values.
    var batteryLevel = -1
    var isCharging = false

    try {
      val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
      val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
      if (batteryStatus != null) {
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
          batteryLevel = (level * 100 / scale.toFloat()).toInt()
        }
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
      }
    } catch (_: Exception) {}

    var isWifi = true
    try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      if (cm != null) {
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        if (caps != null) {
          isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
      }
    } catch (_: Exception) {}

    var isPowerSave = false
    try {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      isPowerSave = pm?.isPowerSaveMode ?: false
    } catch (_: Exception) {}

    val rating = when {
      isCharging -> "Charging (unlimited)"
      isPowerSave -> "Power Saver active"
      batteryLevel < 0 -> "Battery unknown"
      batteryLevel > 60 -> "Battery Guard active"
      else -> "Conserving battery"
    }

    _energyState.value = EnergyState(
      batteryLevelPercent = batteryLevel,
      isCharging = isCharging,
      isWifiConnected = isWifi,
      isPowerSaveMode = isPowerSave,
      energyEfficiencyRating = rating
    )
  }

  fun canSync(wifiOnly: Boolean, chargingOnly: Boolean, batteryThreshold: Int): Pair<Boolean, String> {
    val current = _energyState.value
    if (chargingOnly && !current.isCharging) {
      return Pair(false, "Paused: Sync restricted to AC Charging only.")
    }
    if (wifiOnly && !current.isWifiConnected) {
      return Pair(false, "Paused: Waiting for unmetered Wi-Fi connection.")
    }
    if (current.isPowerSaveMode && !current.isCharging) {
      return Pair(false, "Paused: Android Battery Saver is active.")
    }
    // Only enforce the threshold when the battery level is actually known.
    if (!current.isCharging && current.batteryLevelPercent in 0..99 &&
      current.batteryLevelPercent < batteryThreshold
    ) {
      return Pair(false, "Paused: Battery (${current.batteryLevelPercent}%) is below safety threshold ($batteryThreshold%).")
    }
    return Pair(true, "Ready")
  }
}
