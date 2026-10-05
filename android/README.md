# Android photo frame

Native Java HOME app (`works.tycho.frame`), minimum API21. The verified target is W10F-09, Android 7.1.2/API25, ARM32. Direct mode fetches a privately configured link-shared Google Photos album through HTTPS and plays a local cache. Host mode remains available for rollback.

## Configuration and playback

The maintenance service imports a bounded configuration envelope from the already configured server. The frame generates its own private RSA key; the server encrypts a random AES-GCM key to its public key. The album capability stays out of APKs and command arguments. This protects response contents from passive observation; the existing HTTP bearer maintenance connection still assumes a trusted LAN and does not protect against an active LAN attacker able to intercept the bearer token. See [protocol](../docs/host-and-protocol.md).

Direct configuration lives in separate private preferences and photos in `direct-photos`; host settings and the `photos` cache stay intact. A first direct-sync failure continues using the host cache. Later direct failures retain the last direct manifest. Returning to host mode selects the retained host cache.

While HOME is active, a minute timer checks the configured local daily slot, including during the black night screen. Default sync is 09:00 in the configured timezone; calendar slots account for DST, startup catches missed work, and retries grow from 5 minutes to at most 6 hours. Only one photo sync runs per app process.

Photos fit against black with a 600 ms crossfade. Default interval is 15 seconds. Only actual EXIF DateTimeOriginal supplies the brief month/year label; unknown dates stay blank. Sampled native decoding applies EXIF orientation before JPEG compression. Night mode defaults to 22:00–08:00 America/New_York at minimum window brightness; the panel remains powered. Existing night preferences are preserved.

## Updates and recovery

Build with the pinned JDK17, Gradle8.9, AGP8.7.3 and SDK35 tools. Run unit tests, lint, build, and API25 signature checks before deployment. APKs contain no private server, token or album settings. Preserve the signing identity and choose a version newer than both installed and staged APKs.

Device Owner permits the existing own-package updater to install only a newer same-signer package with validated size/hash and compatibility. Its empty policy list does not remove the broader Android role. Read back actual installed APK bytes and fresh receipts; staging alone proves no installation.

The fixed Termux SSH bridge still pins the official signer, requires scoped RUN_COMMAND, and starts only its predetermined managed script. It accepts no commands or URLs. SSH is the Termux sandbox. Owner retirement still uses the DUMP-protected CLEAR_OWNER receiver followed by restoring the captured original Backup Manager state. Follow [recovery](../docs/how-we-did-it.md#recovery-and-troubleshooting).

Native synthetic checks exercise actual JPEG/EXIF decoding, eight orientations and blank unknown dates. Injected inputs also run the full direct-sync cache transaction: corrupted-cache repair, interruption after a new image is cached, incomplete/empty enumeration, and configuration cancellation must preserve the exact prior manifest/image bytes and leave no extra staged files. Separate primitives check directory syncing and checksum rejection. A current-version `imageChecksPassed` receipt is device evidence for these fixtures, not a physical power-loss test. Visible day/night behavior, Wi-Fi-disconnected playback and cold-power recovery require separate physical confirmation.
