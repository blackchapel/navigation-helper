package com.ridelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridelink.app.permissions.PermissionsAndRadioGate
import com.ridelink.app.pillion.PillionScreen
import com.ridelink.app.rider.RiderScreen
import com.ridelink.app.ui.theme.RideLinkTheme

private enum class Role { RIDER, PILLION }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RideLinkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RideLinkApp()
                }
            }
        }
    }
}

@Composable
private fun RideLinkApp() {
    var role by remember { mutableStateOf<Role?>(null) }

    PermissionsAndRadioGate {
        when (role) {
            null -> RolePickerScreen(onRoleSelected = { role = it })
            Role.PILLION -> PillionScreen(onBack = { role = null })
            Role.RIDER -> RiderScreen(onBack = { role = null })
        }
    }
}

@Composable
private fun RolePickerScreen(onRoleSelected: (Role) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "RideLink", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Who's on this phone for this ride?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        Button(
            onClick = { onRoleSelected(Role.RIDER) },
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text("I'm the Rider")
        }

        Button(
            onClick = { onRoleSelected(Role.PILLION) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(top = 16.dp),
        ) {
            Text("I'm the Pillion")
        }
    }
}
