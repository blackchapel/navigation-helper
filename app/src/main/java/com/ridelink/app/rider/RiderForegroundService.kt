package com.ridelink.app.rider

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ridelink.app.MainActivity
import com.ridelink.app.R
import com.ridelink.app.RideLinkApplication
import com.ridelink.app.nearby.NearbyState
import com.ridelink.app.nearby.formatPeerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps [RiderSession]'s Nearby Connections session alive, and this process
 * exempt from Android's background-activity-start restrictions, while
 * RideLink itself is backgrounded -- the whole point of this app is that the
 * rider never needs to look at it again after tapping Start.
 *
 * Also keeps a single ongoing notification up to date whose tap target
 * always opens Google Maps with the most recently received route. This is a
 * deliberate belt-and-suspenders design: a foreground service is not a
 * guaranteed, version-proof bypass of the background-activity-start
 * restriction on every Android version/OEM skin, but a notification tap is
 * the one exemption that has always been honored -- so if the automatic
 * launch above is ever silently blocked, tapping the notification is a
 * guaranteed one-tap fallback.
 */
class RiderForegroundService : Service() {

    private var serviceScope: CoroutineScope? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_LISTENING) {
            stopListening()
        } else {
            startListening()
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (serviceScope != null) return // already running -- re-Start is a no-op

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(NearbyState.Idle, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        RiderSession.start(applicationContext)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope
        scope.launch {
            combine(
                RiderSession.connectionState,
                RiderSession.latestRouteLink,
            ) { state, link -> state to link }
                .collect { (state, link) ->
                    NotificationManagerCompat.from(this@RiderForegroundService)
                        .notify(NOTIFICATION_ID, buildNotification(state, link))
                }
        }
    }

    private fun stopListening() {
        RiderSession.reset()
        serviceScope?.cancel()
        serviceScope = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        RiderSession.reset()
        serviceScope?.cancel()
        serviceScope = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun buildNotification(state: NearbyState, latestRouteLink: String?): Notification {
        val text = when (state) {
            is NearbyState.Idle, is NearbyState.Searching ->
                "Listening for your pillion's route..."
            is NearbyState.Connected ->
                "Connected to ${formatPeerName(state.endpointName)} -- ready for the next route"
            is NearbyState.Disconnected ->
                "Disconnected -- reopen RideLink to reconnect"
        }

        // Tap target is rebuilt on every update: once a route has arrived,
        // tapping always jumps straight into the latest one in Maps, not
        // just back to our own app.
        val contentIntent = if (latestRouteLink != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(latestRouteLink)).apply {
                setPackage(GOOGLE_MAPS_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        // CANCEL_CURRENT (not UPDATE_CURRENT): the intent's data Uri changes
        // between routes, which UPDATE_CURRENT does not reliably refresh.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, RideLinkApplication.RIDER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rider)
            .setContentTitle("RideLink")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_LISTENING = "com.ridelink.app.rider.STOP_LISTENING"

        fun startIntent(context: Context): Intent =
            Intent(context, RiderForegroundService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, RiderForegroundService::class.java).setAction(ACTION_STOP_LISTENING)
    }
}
