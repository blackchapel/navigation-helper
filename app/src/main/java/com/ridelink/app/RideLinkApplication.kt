package com.ridelink.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RideLinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            RIDER_NOTIFICATION_CHANNEL_ID,
            "Rider listening status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows whether RideLink is listening for a route from your pillion."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val RIDER_NOTIFICATION_CHANNEL_ID = "rider_listening"
    }
}
