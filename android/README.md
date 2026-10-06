# Android photo frame

Native Java HOME app (`works.tycho.frame`), minimum API21. The hardware target is W10F-09, Android 7.1.2/API25, ARM32. The operating system and vendor firmware remain unchanged; app version 2.1.0/code 16 adds the display-sleep and verification paths described below. Source support does not establish successful installation or physical verification. Direct mode fetches a privately configured link-shared Google Photos album through HTTPS and plays a local cache. Host mode remains available for rollback.

## Configuration and playback

The maintenance service imports a bounded configuration envelope from the already configured server. The frame generates its own private RSA key; the server encrypts a random AES-GCM key to its public key. The album capability stays out of APKs and command arguments. This protects response contents from passive observation; the existing HTTP bearer maintenance connection still assumes a trusted LAN and does not protect against an active LAN attacker able to intercept the bearer token. See [protocol](../docs/host-and-protocol.md).

Direct configuration lives in separate private preferences and photos in `direct-photos`; host settings and the `photos` cache stay intact. A first direct-sync failure continues using the host cache. Later direct failures retain the last direct manifest. Returning to host mode selects the retained host cache.

While HOME is active, a minute timer checks the configured local daily slot. True display sleep pauses the foreground timer; the morning wake restores HOME and its missed-slot check. Default sync is 09:00 in the configured timezone; calendar slots account for DST, startup catches missed work, and retries grow from 5 minutes to at most 6 hours. Only one photo sync runs per app process.

Photos fit against black with a 600 ms crossfade. Default interval is 15 seconds. Only actual EXIF DateTimeOriginal supplies the brief month/year label; unknown dates stay blank. Sampled native decoding applies EXIF orientation before JPEG compression. Night mode defaults to 22:00–08:00 America/New_York. Existing night preferences are preserved.

## Overnight display sleep

“Turn the display off at night” requests real display sleep through `lockNow()`. Before sleeping, the app requires a successfully delivered wake-alarm probe, a granted `force-lock` admin policy, a nonsecure keyguard and a future wake alarm. Otherwise it retains the black, minimum-brightness fallback. The admin XML now declares only `force-lock`; the previous empty declaration could not authorize this API on Android 7.1. An installed owner may need its grant refreshed through the app’s **Allow display sleep** button and Android’s own confirmation screen. Installation alone is not proof of that grant.

A one-shot `RTC_WAKEUP`/`setExactAndAllowWhileIdle` alarm targets the next actual local night-to-day boundary, including skipped/repeated DST hours. A bounded screen wake lock starts HOME; its normal keep-screen-on behavior then takes over. Boot and clock/timezone changes rebuild alarms, and a separate 15-minute maintenance alarm provides catch-up while the Activity sleeps. “Exact” is the scheduling API, not a promise against vendor delays. A display-off receipt and backlight reading need live confirmation; waking from sleep does not power on a shut-down frame.

## Private verification tasks

DEBUG builds accept five fixed, explicitly requested tasks through the existing encrypted configuration envelope: `alarm_probe`, `power_cycle`, `day_preview`, `wifi_cycle`, and `reboot`. Callers supply an ID and one allowed action, never shell commands or arbitrary durations. Probe/sleep tests arm a 90-second wake; day preview expires. Reboot uses the Device Owner API on API24+, without changing the OS.

The Wi-Fi test first probes its receiver with Wi-Fi unchanged, then arms restoration and persists its deadline before disabling the radio for a nominal 90 seconds. Private alarms survive process death; boot recovery restores Wi-Fi, and failed restoration retries back off to five minutes. Completion requires observed radio-off/on transitions, cached-photo presentations and a positive backlight sample. Each CPU lease is bounded. A task receipt is evidence of that task’s checks, not a substitute for physical appearance or a cold-power test.

## Updates and recovery

Build with the pinned JDK17, Gradle8.9, AGP8.7.3 and SDK35 tools. Run unit tests, lint, build, and API25 signature checks before deployment. APKs contain no private server, token or album settings. Preserve the signing identity and choose a version newer than both installed and staged APKs.

Device Owner permits the existing own-package updater to install only a newer same-signer package with validated size/hash and compatibility. The `force-lock` declaration supports display sleep; the Device Owner role retains broader Android authority. Read back actual installed APK bytes and fresh receipts; staging alone proves no installation.

The fixed Termux SSH bridge still pins the official signer, requires scoped RUN_COMMAND, and starts only its predetermined managed script. It accepts no commands or URLs. SSH is the Termux sandbox. Owner retirement still uses the DUMP-protected CLEAR_OWNER receiver followed by restoring the captured original Backup Manager state. Follow [recovery](../docs/how-we-did-it.md#recovery-and-troubleshooting).

Native synthetic checks exercise actual JPEG/EXIF decoding, eight orientations and blank unknown dates. Injected inputs also run the full direct-sync cache transaction: corrupted-cache repair, interruption after a new image is cached, incomplete/empty enumeration, and configuration cancellation must preserve the exact prior manifest/image bytes and leave no extra staged files. Separate primitives check directory syncing and checksum rejection. A current-version `imageChecksPassed` receipt is device evidence for these fixtures, not a physical power-loss test. Visible day/night behavior, Wi-Fi-disconnected playback and cold-power recovery require separate physical confirmation.
