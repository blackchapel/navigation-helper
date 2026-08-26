# RideLink

A two-role Android app for a rider (phone mounted on the bike, hands-free
once riding starts) and a pillion (picks the destination). The pillion
builds a route in the real Google Maps app and taps Share; RideLink
captures that share, sends it directly to the rider's phone over a local
Bluetooth/Wi-Fi connection (Google's Nearby Connections API), and the
rider's phone automatically opens Google Maps navigation. **No backend --
the entire handoff is phone-to-phone.**

See `docs` in the approved plan for the full design rationale. Short
version of the flow:

1. Both people open RideLink and pick a role: Rider or Pillion.
2. Pillion taps Start -> app advertises nearby; Rider taps Start -> app
   discovers and connects. No manual pairing code needed.
3. Pillion opens Google Maps, builds the route, taps **Share -> RideLink**.
4. RideLink sends that link to the rider's phone the moment both a
   connection and a captured link exist.
5. Rider's phone automatically launches Google Maps navigation. Nothing
   else to tap.

Pairing is per-ride only in v1 -- both apps re-discover each other fresh
every time (see the plan for why, and what a v2 "remembered devices" mode
would need).

## Building

Requires Android Studio (Ladybird/Koala or newer) with:
- JDK 17
- Android SDK platform 35, build-tools matching AGP 8.7.x

Open the project root in Android Studio and let it sync -- this repo does
not include the Android SDK or a working internet connection was not
available to fully resolve dependencies at generation time, so **the first
sync in Android Studio is the real build verification step**. If Android
Studio prompts to upgrade AGP/Gradle/library versions, accept it; the
versions pinned here (`AGP 8.7.2`, `Kotlin 2.0.21`, `Compose BOM
2024.10.01`, `play-services-nearby 19.3.0`) were current as of this app's
initial design but may have since moved.

No API keys, accounts, or `local.properties` entries beyond the standard
`sdk.dir` are needed -- there's no backend and no Maps/Places API usage of
our own (Google Maps itself handles all of that, launched via intent).

## Testing

Nearby Connections behavior is unreliable on emulators, so use two physical
Android devices:

1. Install the app on both, grant Bluetooth/nearby-device permissions when
   prompted, and enable Bluetooth if asked.
2. Device A: "I'm the Pillion" -> Start.
3. Device B: "I'm the Rider" -> Start. Both screens should show "Connected"
   within a few seconds.
4. On Device A, open Google Maps, build a route, Share -> RideLink.
5. Device A shows "Route sent to rider". Device B should automatically
   launch Google Maps navigation with no further taps.
