package com.ridelink.app.pillion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridelink.app.nearby.NearbyState

@Composable
fun PillionScreen(onBack: () -> Unit) {
    val viewModel: PillionViewModel = viewModel()
    val connectionState by viewModel.connectionState.collectAsState()
    val capturedLink by viewModel.capturedLink.collectAsState()
    val sent by viewModel.sent.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Pillion", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = connectionStatusText(connectionState),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            text = when {
                sent -> "Route sent to rider ✓"
                capturedLink != null -> "Route captured, waiting to connect..."
                else -> "Now open Google Maps, build the route, and tap Share → RideLink."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        TextButton(
            onClick = {
                viewModel.stop()
                onBack()
            },
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text("End")
        }
    }
}

private fun connectionStatusText(state: NearbyState): String = when (state) {
    is NearbyState.Idle -> "Starting..."
    is NearbyState.Searching -> "Waiting for rider to connect..."
    is NearbyState.Connected -> "Connected to rider"
    is NearbyState.Disconnected -> "Disconnected -- reopen this screen to retry"
}
