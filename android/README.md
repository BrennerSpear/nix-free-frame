# Android photo frame

Native Java HOME app (`works.tycho.frame`), minimum API21. The verified target is W10F-09 on Android 7.1.2/API25/ARM32. Photos fit against black, cycle at the host manifest interval, and are cached privately after hash verification. The app fetches on launch and periodically while active; failed requests preserve the prior cache. Same-origin URLs are required, redirects rejected, and bounded manifests/photos enforced.

Runtime USB configuration transfers the private server URL and bearer token; distributable APKs must never embed them. The optional touch settings interface supports a tap on an empty screen or long press on a photo; the tested frame uses USB setup. `.env` configures build/tool paths and initial USB settings. Private keystores and baseline/proof files remain outside source control.

Build tooling: AGP8.7.3, Gradle8.9, JDK17, compile/target SDK35. Use the pinned bootstrap and tracked Gradle wrapper; do not substitute an unverified local toolchain. Run unit tests, lint and an APK build before installation. See checked-in script help and [host setup](../README.md).

A valid `capturedMonth` briefly appears as month/year at the bottom right, followed by a fade. Unknown dates stay blank. Photo crossfade is 600 ms. Night mode displays black at minimum app-window brightness while keeping the panel powered. It follows its configured timezone independently of the Android system timezone; equal start/end hours disable it. On touch devices a night tap briefly wakes playback. Debug presentation previews expire without persisting schedule changes.

The optional Device Owner updater validates a strictly newer version, package, installed certificate, exact SHA-256/size and same origin before PackageInstaller installation. The empty administrator policy does not remove the broader authority of Android's Device Owner role. No wipe/reset prerequisite is automated. Silent installation without owner reports permission failure. Actual installation evidence must be read back; a server report alone is insufficient.

USB recovery uses the DUMP-protected receiver `works.tycho.frame.CLEAR_OWNER` on `works.tycho.frame/.OwnerRecoveryReceiver`, which clears only this app's owner/admin role. Verify absence before restoring the device's captured original Backup Manager state. This tested firmware did not restore backup automatically. Preserve data and original apps throughout retirement.

The fixed Termux SSH bridge pins the verified F-Droid signer, requires the scoped RUN_COMMAND grant and invokes only its predetermined managed local script with background execution. Inputs cannot supply commands, arguments or URLs. Termux's battery exemption and permission need a persistence wait. Termux:Boot alone failed on this vendor firmware; the fixed bridge passed a real boot. SSH provides the Termux sandbox, not Android shell/root access.

[How the conversion was verified and its limits](../docs/how-we-did-it.md) covers normal USB boot access, persistence waits, independent power, failed-process observations, recovery and the host/Tailscale topology. [Android build requirements](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [background activity limits](https://developer.android.com/guide/components/activities/background-starts).
