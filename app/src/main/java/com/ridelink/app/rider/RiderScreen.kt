package com.ridelink.app.rider

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
fun RiderScreen(onBack: () -> Unit) {
    val viewModel: RiderViewModel = viewModel()
    val connectionState by viewModel.connectionState.collectAsState()
    val routeOpened by viewModel.routeOpened.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Rider", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = when {
                routeOpened -> "Route received -- opening Google Maps..."
                else -> connectionStatusText(connectionState)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )

        TextButton(onClick = onBack, modifier = Modifier.padding(top = 32.dp)) {
            Text("End")
        }
    }
}

private fun connectionStatusText(state: NearbyState): String = when (state) {
    is NearbyState.Idle -> "Starting..."
    is NearbyState.Searching -> "Looking for pillion's phone..."
    is NearbyState.Connected -> "Connected -- waiting for route..."
    is NearbyState.Disconnected -> "Disconnected -- reopen this screen to retry"
}
