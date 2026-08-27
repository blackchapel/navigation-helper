package com.ridelink.app.permissions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Every permission Nearby Connections needs, gated by API level. */
fun requiredNearbyPermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_SCAN
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return permissions.toTypedArray()
}

private fun hasAllPermissions(context: Context): Boolean =
    requiredNearbyPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(BluetoothManager::class.java)
    return manager?.adapter?.isEnabled == true
}

private fun isWifiEnabled(context: Context): Boolean {
    val manager = context.getSystemService(WifiManager::class.java)
    return manager?.isWifiEnabled == true
}

private fun wifiSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android hasn't allowed toggling Wi-Fi programmatically since API 29 --
        // this opens the quick-settings panel instead of the full Settings app.
        Intent(Settings.Panel.ACTION_WIFI)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }

/**
 * Blocks [content] behind runtime permissions and Bluetooth/Wi-Fi-enabled
 * checks -- all three are required before advertising/discovery will work.
 * Nearby Connections mixes Bluetooth and Wi-Fi mediums, and (per Google's
 * 2026 changelog) is moving away from silently enabling these radios itself,
 * so the app has to check and prompt for both explicitly.
 */
@Composable
fun PermissionsAndRadioGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasAllPermissions(context)) }
    var bluetoothEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }
    var wifiEnabled by remember { mutableStateOf(isWifiEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnabled = isBluetoothEnabled(context)
    }

    val enableWifiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        wifiEnabled = isWifiEnabled(context)
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredNearbyPermissions())
        }
    }

    when {
        !permissionsGranted -> GateScreen(
            message = "RideLink needs Bluetooth and nearby-device permissions " +
                "to connect rider and pillion phones directly -- no internet or " +
                "server involved.",
            buttonText = "Grant permissions",
            onClick = { permissionLauncher.launch(requiredNearbyPermissions()) },
        )

        !bluetoothEnabled -> GateScreen(
            message = "Bluetooth is off. RideLink uses it to connect the two " +
                "phones directly.",
            buttonText = "Enable Bluetooth",
            onClick = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        )

        !wifiEnabled -> GateScreen(
            message = "Wi-Fi is off. RideLink also uses Wi-Fi (no internet " +
                "needed, just the radio) to connect the two phones directly.",
            buttonText = "Enable Wi-Fi",
            onClick = { enableWifiLauncher.launch(wifiSettingsIntent()) },
        )

        else -> content()
    }
}

@Composable
private fun GateScreen(message: String, buttonText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) {
            Text(buttonText)
        }
    }
}
