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
import kotlinx.coroutines.channels.BufferOverflow
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
    data class Connected(val endpointId: String, val endpointName: String) : NearbyState()
    data object Disconnected : NearbyState()
}

/** Strips the shared advertising prefix so the UI can show a plain device name. */
fun formatPeerName(endpointName: String): String = endpointName.removePrefix("RideLink-")

/**
 * Small control-channel messages, sent as tagged BYTES payloads -- distinct
 * from the audio chunks sent via [NearbyManager.sendAudioChunk], which use
 * their own tag ([TAG_AUDIO_CHUNK]) and never flow through this envelope
 * (raw PCM bytes decoded as UTF-8 text would be silently corrupted).
 */
sealed class ControlMessage {
    data class RouteLink(val url: String) : ControlMessage()
    data object VoiceChatOffer : ControlMessage()
    data object VoiceChatAccept : ControlMessage()
    data object VoiceChatDecline : ControlMessage()
    data object VoiceChatEnd : ControlMessage()

    fun encode(): ByteArray = when (this) {
        is RouteLink -> byteArrayOf(TAG_ROUTE_LINK) + url.toByteArray(Charsets.UTF_8)
        VoiceChatOffer -> byteArrayOf(TAG_VOICE_OFFER)
        VoiceChatAccept -> byteArrayOf(TAG_VOICE_ACCEPT)
        VoiceChatDecline -> byteArrayOf(TAG_VOICE_DECLINE)
        VoiceChatEnd -> byteArrayOf(TAG_VOICE_END)
    }

    companion object {
        private const val TAG_ROUTE_LINK: Byte = 1
        private const val TAG_VOICE_OFFER: Byte = 2
        private const val TAG_VOICE_ACCEPT: Byte = 3
        private const val TAG_VOICE_DECLINE: Byte = 4
        private const val TAG_VOICE_END: Byte = 5

        fun decode(bytes: ByteArray): ControlMessage? {
            if (bytes.isEmpty()) return null
            return when (bytes[0]) {
                TAG_ROUTE_LINK -> RouteLink(String(bytes, 1, bytes.size - 1, Charsets.UTF_8))
                TAG_VOICE_OFFER -> VoiceChatOffer
                TAG_VOICE_ACCEPT -> VoiceChatAccept
                TAG_VOICE_DECLINE -> VoiceChatDecline
                TAG_VOICE_END -> VoiceChatEnd
                else -> null
            }
        }
    }
}

/** One chunk of the peer's captured mic audio, tagged with its send order. */
data class AudioChunk(val sequenceNumber: Int, val data: ByteArray)

private const val TAG_AUDIO_CHUNK: Byte = 6

/**
 * Thin wrapper around Google Play services' Nearby Connections API.
 * Handles the whole handoff over local Bluetooth/Wi-Fi -- no server involved.
 */
class NearbyManager(context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null
    private var pendingEndpointName: String? = null

    private val _state = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private val _receivedMessages = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 8)
    val receivedMessages: SharedFlow<ControlMessage> = _receivedMessages

    // DROP_OLDEST so a receive-side backlog can never grow into latency --
    // an unread chunk is stale by the time a newer one arrives anyway.
    private val _incomingAudioChunks = MutableSharedFlow<AudioChunk>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingAudioChunks: SharedFlow<AudioChunk> = _incomingAudioChunks

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    if (bytes.isNotEmpty() && bytes[0] == TAG_AUDIO_CHUNK) {
                        decodeAudioChunk(bytes)?.let { _incomingAudioChunks.tryEmit(it) }
                    } else {
                        ControlMessage.decode(bytes)?.let { _receivedMessages.tryEmit(it) }
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Every payload here (control messages, audio chunks) is a single
            // small BYTES payload -- no progress UI needed.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Both sides are trusted for this two-person use case: auto-accept.
            pendingEndpointName = info.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                _state.value = NearbyState.Connected(endpointId, pendingEndpointName ?: "the other device")
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
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .setLowPower(false) // prioritize connection/throughput over battery
            .build()
        connectionsClient
            .startAdvertising(LOCAL_ENDPOINT_NAME, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener { _state.value = NearbyState.Idle }
    }

    fun startDiscovery() {
        _state.value = NearbyState.Searching
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .setLowPower(false)
            .build()
        connectionsClient
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnFailureListener { _state.value = NearbyState.Idle }
    }

    fun send(message: ControlMessage) {
        val endpointId = connectedEndpointId ?: return
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(message.encode()))
    }

    /**
     * Sends one chunk of captured mic audio as its own small BYTES payload --
     * fire-and-forget, nothing to cancel once sent. Chosen over a single
     * long-lived Payload.fromStream() specifically so a lost/delayed chunk
     * can never stall everything queued behind it: STREAM payloads are
     * delivered reliably and in order, which is exactly wrong for real-time
     * audio on a link that can momentarily degrade (bike-riding range).
     */
    fun sendAudioChunk(sequenceNumber: Int, data: ByteArray, length: Int) {
        val endpointId = connectedEndpointId ?: return
        val framed = ByteArray(5 + length)
        framed[0] = TAG_AUDIO_CHUNK
        framed[1] = (sequenceNumber ushr 24).toByte()
        framed[2] = (sequenceNumber ushr 16).toByte()
        framed[3] = (sequenceNumber ushr 8).toByte()
        framed[4] = sequenceNumber.toByte()
        System.arraycopy(data, 0, framed, 5, length)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(framed))
    }

    private fun decodeAudioChunk(bytes: ByteArray): AudioChunk? {
        if (bytes.size <= 5) return null
        val sequenceNumber = ((bytes[1].toInt() and 0xFF) shl 24) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 8) or
            (bytes[4].toInt() and 0xFF)
        return AudioChunk(sequenceNumber, bytes.copyOfRange(5, bytes.size))
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
        private val LOCAL_ENDPOINT_NAME = "RideLink-${Build.MODEL}"
    }
}
