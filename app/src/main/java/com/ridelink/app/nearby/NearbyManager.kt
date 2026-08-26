package com.ridelink.app.nearby

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rider and pillion are always exactly one pair, so P2P_POINT_TO_POINT is
 * the natural strategy: one advertiser (pillion), one discoverer (rider).
 */
sealed class NearbyState {
    data object Idle : NearbyState()
    data object Searching : NearbyState()
    data class Connected(val endpointId: String) : NearbyState()
    data object Disconnected : NearbyState()
}

/**
 * Thin wrapper around Google Play services' Nearby Connections API.
 * Handles the whole handoff over local Bluetooth/Wi-Fi -- no server involved.
 */
class NearbyManager(context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null

    private val _state = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private val _receivedPayloads = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val receivedPayloads: SharedFlow<String> = _receivedPayloads

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            _receivedPayloads.tryEmit(String(bytes, Charsets.UTF_8))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Single small BYTES payload per ride -- no progress UI needed.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Both sides are trusted for this two-person use case: auto-accept.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                _state.value = NearbyState.Connected(endpointId)
            } else {
                _state.value = NearbyState.Disconnected
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
            }
            _state.value = NearbyState.Disconnected
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(LOCAL_ENDPOINT_NAME, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            // Discovery keeps running; onDisconnected handles an active connection dropping.
        }
    }

    fun startAdvertising() {
        _state.value = NearbyState.Searching
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        connectionsClient
            .startAdvertising(LOCAL_ENDPOINT_NAME, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener { _state.value = NearbyState.Idle }
    }

    fun startDiscovery() {
        _state.value = NearbyState.Searching
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        connectionsClient
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnFailureListener { _state.value = NearbyState.Idle }
    }

    fun send(text: String) {
        val endpointId = connectedEndpointId ?: return
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _state.value = NearbyState.Idle
    }

    companion object {
        const val SERVICE_ID = "com.ridelink.SERVICE"
        private const val LOCAL_ENDPOINT_NAME = "RideLink-${Build.MODEL}"
    }
}
