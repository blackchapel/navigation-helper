package com.ridelink.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ridelink.app.pillion.PillionSession

/**
 * Registered as an ACTION_SEND target for text/plain, so it appears directly
 * in the Android share sheet when the pillion taps Share on a Google Maps
 * route -- no copy/paste and no messaging app in between.
 *
 * Has no UI of its own: it just hands the shared link to [PillionSession]
 * (which sends it as soon as a rider is connected) and closes immediately.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "No route link found in that share.", Toast.LENGTH_SHORT).show()
        } else {
            PillionSession.onLinkCaptured(sharedText.trim())
            Toast.makeText(this, "Route captured -- sending to rider.", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
