# RideLink -- system state

A technical snapshot of what actually exists in this repo right now.
`README.md` is the user/build-facing doc; this one is for picking up
the codebase quickly. Keep it current alongside every change (see
`CLAUDE.md`).

## Architecture

Two roles, one pairing, no server anywhere:

- **Pillion** (passenger) picks the route in the real Google Maps app and
  shares it into RideLink via the OS share sheet.
- **Rider** (phone mounted on the bike) receives it and RideLink launches
  Google Maps navigation automatically -- no further taps needed, even if
  RideLink is backgrounded.
- The two phones pair over Google's **Nearby Connections API**
  (`play-services-nearby`), `Strategy.P2P_POINT_TO_POINT` -- one
  advertiser (Pillion), one discoverer (Rider). Bluetooth/Wi-Fi only;
  Nearby Connections itself negotiates a bandwidth upgrade (e.g.
  BT -> Wi-Fi Direct) after the initial connection. Pairing is per-ride
  only -- both sides re-discover fresh every session, nothing is
  remembered device-to-device.
- Everything -- route link relay, voice chat signaling, voice audio --
  flows over that same Nearby Connections link. There is no backend, no
  internet/mobile-data dependency, and no third-party server involved at
  any point.

## Module map

`app/src/main/java/com/ridelink/app/`

- `MainActivity.kt` -- role picker, light/dark toggle (persisted via
  `ui/theme/ThemePreferences.kt`), hosts whichever role screen is active.
  Also the landing point for a tapped route notification (see Rider,
  below) -- a notification tap has no background-activity-start
  restriction, so routing through here before relaunching Maps is what
  makes the "opens Maps even from the background" behavior reliable.
- `ShareReceiverActivity.kt` -- the `ACTION_SEND` target that appears in
  Google Maps' share sheet; captures the shared link for the Pillion
  side.
- `RideLinkApplication.kt` -- registers the app's three notification
  channels (rider listening status, new-route alert, voice-call status).
- `nearby/NearbyManager.kt` -- thin wrapper around Nearby Connections.
  Owns the connection lifecycle/state (`NearbyState`), a small tagged
  `ControlMessage` protocol over BYTES payloads (route link,
  voice-chat offer/accept/decline/end), and a separate per-chunk
  sequence-numbered audio-chunk protocol (`AudioChunk`,
  `sendAudioChunk`/`incomingAudioChunks`) -- see Voice chat below for why
  audio doesn't use Nearby's STREAM payload type.
- `permissions/PermissionsGate.kt` -- runtime permission + Bluetooth/
  Wi-Fi-enabled gating shared by both roles before any Nearby call is
  made.
- `pillion/` -- `PillionViewModel`/`PillionScreen`. `PillionSession` is
  application-scoped (survives switching away to Google Maps and back)
  and holds the `NearbyManager` + `VoiceChatSession` for this role.
- `rider/` -- `RiderViewModel`/`RiderScreen`/`RiderSession`/
  `RiderForegroundService`. `RiderForegroundService`
  (`foregroundServiceType="connectedDevice"`) is what keeps the Rider's
  process alive and able to keep launching Maps while backgrounded; it's
  the thing that must be running for the app's core promise ("rider
  never has to touch the phone again") to hold.
- `voicechat/` -- real-time voice chat, layered on top of an
  already-connected `NearbyManager`:
  - `AudioPipeline.kt` -- `AudioCapture` (AudioRecord, 8kHz mono PCM16,
    hands each ~20ms chunk straight to a callback, no internal
    buffering) and `AudioPlayback` (AudioTrack, fed via a small bounded
    drop-oldest queue, `PLAYBACK_QUEUE_CAPACITY = 4` / ~80ms).
  - `AudioRoutingController.kt` -- speaker/earpiece/Bluetooth output
    selection (`AudioManager.setCommunicationDevice` on API 31+, legacy
    speakerphone/SCO APIs below that).
  - `VoiceChatSession.kt` -- offer/accept/decline/end state machine
    (`VoiceChatState`), mute, and the sequence-number gap-skip receive
    policy (stale/duplicate chunks dropped, gaps jumped over rather than
    waited for -- see `docs/KNOWN_GAPS.md` for the trade-off this
    implies).
  - `VoiceCallForegroundService.kt` -- minimal foreground service held
    only for the duration of an active call
    (`foregroundServiceType="microphone"`), deliberately separate from
    `RiderForegroundService`.
  - `VoiceChatControls.kt` -- shared Compose UI for both roles.
- `ui/theme/` -- `Theme.kt` (Compose theme) and `ThemePreferences.kt`
  (persisted light/dark choice, defaulting to system theme).

## Release pipeline

- `app/version.properties` -- the single hand-edited semantic version
  (`VERSION_NAME=1.0.0` currently). `versionCode` is never edited by
  hand -- both workflows compute it from `git rev-list --count HEAD`.
- `.github/workflows/build-apk.yml` ("Test Release") -- manual
  (`workflow_dispatch`, a `branch` input), builds a **signed**
  `assembleRelease` from any branch using the release keystore, with a
  `<version>-<branch>.<short-sha>` versionName. Uploads the APK as an
  artifact and posts a Discord embed.
- `.github/workflows/release.yml` ("Production Release") -- triggers
  automatically on every push to `main`, same signed `assembleRelease`
  build, clean versionName (no branch/sha suffix). Same artifact +
  Discord notification pattern.
- Both read signing material from GitHub secrets
  (`KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`,
  decoded to a runner temp file) and post to a `DISCORD_WEBHOOK_URL`
  secret -- deliberately never the GitHub Actions run link, only the
  artifact's own `actions/upload-artifact@v4` `artifact-url` output.
  `app/build.gradle.kts`'s `signingConfigs.release` reads
  `KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` from the
  environment; unset locally, so a local `assembleRelease` fails loudly
  at signing time (expected -- there's no meaningful unsigned fallback
  for a release build).
- Repo workflow going forward (see `CLAUDE.md`): feature branch -> Test
  Release build -> user approval -> PR -> **explicit** user approval ->
  merge to `main` (which is what actually triggers Production Release).

## Pinned versions (as of this snapshot)

AGP `8.7.2`, Kotlin `2.0.21`, Compose BOM `2024.10.01`,
`play-services-nearby` `19.3.0`, `compileSdk`/`targetSdk` `35`,
`minSdk` `26`, JDK `17`.
