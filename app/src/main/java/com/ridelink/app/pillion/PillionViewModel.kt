package com.ridelink.app.pillion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ridelink.app.nearby.NearbyManager
import com.ridelink.app.nearby.NearbyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Application-scoped so a captured share survives the pillion switching away
 * to Google Maps and back -- an Activity/ViewModel-scoped instance could be
 * torn down while the app is backgrounded.
 */
object PillionSession {
    private var manager: NearbyManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _capturedLink = MutableStateFlow<String?>(null)
    val capturedLink: StateFlow<String?> = _capturedLink.asStateFlow()

    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent.asStateFlow()

    fun manager(context: android.content.Context): NearbyManager {
        val existing = manager
        if (existing != null) return existing

        val created = NearbyManager(context.applicationContext)
        manager = created
        scope.launch {
            combine(created.state, _capturedLink) { state, link -> state to link }
                .collect { (state, link) ->
                    if (state is NearbyState.Connected && link != null && !_sent.value) {
                        created.send(link)
                        _sent.value = true
                    }
                }
        }
        return created
    }

    fun onLinkCaptured(link: String) {
        _capturedLink.value = link
    }

    fun reset() {
        manager?.stop()
        manager = null
        _capturedLink.value = null
        _sent.value = false
    }
}

class PillionViewModel(application: Application) : AndroidViewModel(application) {

    private val nearbyManager get() = PillionSession.manager(getApplication())

    val connectionState: StateFlow<NearbyState> get() = nearbyManager.state
    val capturedLink: StateFlow<String?> = PillionSession.capturedLink
    val sent: StateFlow<Boolean> = PillionSession.sent

    fun start() {
        nearbyManager.startAdvertising()
    }

    fun stop() {
        PillionSession.reset()
    }
}
