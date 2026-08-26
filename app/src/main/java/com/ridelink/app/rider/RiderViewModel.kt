package com.ridelink.app.rider

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ridelink.app.nearby.NearbyManager
import com.ridelink.app.nearby.NearbyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RiderViewModel(application: Application) : AndroidViewModel(application) {

    private val nearbyManager = NearbyManager(application)

    val connectionState: StateFlow<NearbyState> = nearbyManager.state

    private val _routeOpened = MutableStateFlow(false)
    val routeOpened: StateFlow<Boolean> = _routeOpened.asStateFlow()

    init {
        viewModelScope.launch {
            nearbyManager.receivedPayloads.collect { link ->
                _routeOpened.value = true
                openInGoogleMaps(link)
            }
        }
    }

    fun start() {
        nearbyManager.startDiscovery()
    }

    fun stop() {
        nearbyManager.stop()
    }

    override fun onCleared() {
        nearbyManager.stop()
    }

    private fun openInGoogleMaps(link: String) {
        val context = getApplication<Application>()
        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapsIntent)
        } catch (e: ActivityNotFoundException) {
            // Google Maps isn't installed under the expected package name --
            // fall back to letting Android resolve the link generally.
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    private companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
