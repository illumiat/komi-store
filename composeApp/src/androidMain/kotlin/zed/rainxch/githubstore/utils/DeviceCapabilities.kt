package zed.rainxch.githubstore.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

internal enum class ThermalPressure {
    None,
    Light,
    Moderate,
    Severe,
    Critical,
}

internal data class DeviceCapabilities(
    val isLowRam: Boolean,
    val totalMemoryBytes: Long,
    val thermalPressure: ThermalPressure,
    val isPowerSaveMode: Boolean,
)

// Sustained performance trades peak clocks for a steady range, which only pays off where the
// device can afford the lost headroom and is not already throttling itself.
internal val DeviceCapabilities.supportsSustainedPerformance: Boolean
    get() =
        !isLowRam &&
            !isPowerSaveMode &&
            thermalPressure.ordinal <= ThermalPressure.Moderate.ordinal

internal fun Context.readDeviceCapabilities(): DeviceCapabilities {
    val activityManager = getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val powerManager = getSystemService(PowerManager::class.java)
    return DeviceCapabilities(
        isLowRam = activityManager.isLowRamDevice,
        totalMemoryBytes = memory.totalMem,
        thermalPressure = powerManager.thermalPressure(),
        isPowerSaveMode = powerManager.isPowerSaveMode,
    )
}

private fun PowerManager?.thermalPressure(): ThermalPressure {
    if (this == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ThermalPressure.None
    return when (currentThermalStatus) {
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalPressure.Light

        PowerManager.THERMAL_STATUS_MODERATE -> ThermalPressure.Moderate

        PowerManager.THERMAL_STATUS_SEVERE -> ThermalPressure.Severe

        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> ThermalPressure.Critical

        else -> ThermalPressure.None
    }
}
