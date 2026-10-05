# Host setup and frame protocol

Reuse a Nixplay W10F-09 as a local Android photo frame. A macOS host downloads a Google Photos link-shared album, validates and caches JPEGs, and serves them privately over your LAN. A native Android HOME app caches those photos and cycles independently of the host. Firmware and original applications stay recoverable.

Tested hardware: **W10F-09, Android 7.1.2 / API 25, armeabi-v7a**, rendered at 1280 × 800. Other revisions are unverified. This host implementation requires macOS: it uses `sips`, `lockf`, and user LaunchAgents. Bun and Python 3.9+ run the host workflows; the pinned build bootstrap supports Apple Silicon macOS; Android builds use pinned JDK 17, Gradle 8.9, AGP 8.7.3 and SDK 35. There is no Linux-host support claim.

Read [how the frame was converted](how-we-did-it.md) before opening hardware. [Android behavior and recovery](../android/README.md), [public export](public-sharing.md), and [licensing/provenance](licensing.md) explain the relevant boundaries. Original installation evidence is kept separately in the private local repository and is excluded from public exports.

## Host setup

```sh
bun install --frozen-lockfile
bun run scripts/configure.ts configure
bun run scripts/configure.ts doctor
bun run sync
bun start
```

`configure` creates a mode-600 ignored `.env` from [.env.example](../.env.example). Edit its placeholders locally. `ALBUM_URL` and `FRAME_TOKEN` are private. The example binds loopback; set `BIND_ADDRESS` to the host's private LAN address and `FRAME_SERVER_URL` to the matching origin before configuring a frame. Do not bind all interfaces, forward router ports, or expose this service publicly. This project neither enables Google sharing nor changes album contents. Anyone with an album sharing link can view it: [Google's sharing guidance](https://support.google.com/photos/answer/9789702).

For an existing deployment, `bun run scripts/configure.ts migrate` backs up legacy configuration and writes `.env`, preserving private values. After successfully writing `.env` and restrictive backups, migration immediately retires the active legacy files. Run doctor, a real sync and live service checks next; the rollback copies remain private. `rollback` restores configuration backups; it does not roll back code, LaunchAgents or APKs by itself. Keep signing keystores, private keys, photos, original APKs and installed proofs in ignored files referenced by `.env`.

After a first successful sync and authenticated foreground service checks, stop the temporary `bun start` process before installing the server LaunchAgent so both servers do not compete for the port. Then install or update the host jobs:

```sh
python3 scripts/install-agents.py
python3 scripts/install-agents.py --status
# Existing project jobs only:
python3 scripts/install-agents.py --update
```

Daily `SYNC_HOUR`/`SYNC_MINUTE` follow the **host's local timezone** through launchd. `NIGHT_TIMEZONE` controls the frame's separate night schedule. Jobs run only while the user host/session is available; they cannot guarantee a powered-off host sync. `--remove` and `--rollback` manage this project's jobs. Inspect each command's `--help` before device work. [The conversion guide](how-we-did-it.md) lists the USB, signing, SSH and recovery sequence.

## Photos and failure behavior

The bearer-authenticated `/manifest.json` contains stable IDs, image hashes, dimensions, a slideshow interval and optional `capturedMonth`. Photos are fetched through server-relative `/photos/<hash>.jpg` paths. Unknown dates stay blank; dates come only from valid JPEG EXIF capture metadata, without timezone conversion. The app fits photos against black, shows a date briefly, crossfades for 600 ms and caches a last good slideshow. Defaults are 15 seconds per image and black/minimum-window-brightness night mode from 22:00–08:00 in the configured timezone. The panel stays powered.

Enumeration and every image validation must succeed before an atomic manifest change. Failed/empty/unreadable albums retain the prior cache. Videos are skipped. The host limits each download to 20 MB and 30 seconds, the whole sync to 20 minutes, and converts images with `sips` to JPEGs up to 1920 pixels. Kernel locking prevents concurrent syncs. `/health` and `/status` require bearer authentication; config, source URLs and arbitrary paths are never served. HTTP on a trusted LAN does not encrypt photo traffic.

Google's Library API now reads albums/media created by the calling app, rather than arbitrary existing albums. The pinned extractor reads the link-shared page and an undocumented pagination endpoint. Google can change these at any time; a passing local test does not guarantee future compatibility. The output is a display cache, not an original-quality backup. [Google API changes](https://developers.google.com/photos/support/updates), [extractor source](https://github.com/vikas5914/google-photos-album-image-url-fetch).

## Updates and access

The app checks a private same-origin update manifest and verifies the package, installed signing certificate, exact size/hash, Android compatibility and strictly newer version before installation. Preserve the original signing key for all updates. Device Owner enables silent own-package installation; the administrator policy list is empty, but the Android role is broader than installation permission. Without that role, the app reports a permission failure. No arbitrary package, command or URL endpoint exists.

`bun run scripts/stage-update.ts --apk PATH --baseline-apk PATH --installed-proof PATH` publishes a checked candidate atomically. Baseline/proof must represent an actually verified installed version; staging and server status alone do not prove installation. Read back the installed package/hash and inspect foreground playback, cache/night settings and SSH afterward.

Optional SSH uses official F-Droid Termux, dedicated keys and USB-pinned host keys on port 8022. A remote laptop reaches the frame through an existing Tailscale-connected macOS host using OpenSSH ProxyJump. Termux sessions have its app sandbox privileges. Tailscale stays on the host because [the current Android client requires Android 8+](https://tailscale.com/docs/install/android). No network ADB or public ports are needed.

## Verification

```sh
bun run check
python3 tests/public-export.test.py
bun run scripts/verify.ts
python3 scripts/public-export.py
```

`bun run scripts/verify.ts` reads live authenticated host health and a recent private frame receipt. `--expect-version NUMBER --apk PATH` requires a fresh exact installed APK hash, retained roles and matching cache count; optional `--previous-receipt PATH` compares saved settings/manifest hashes. Save a private pre-update receipt before staging if you want that retention gate. Receipts expose only bounded state/hashes and do not replace visible panel inspection or trusted USB package readback.

Backend tests cover authentication, routes, real image validation, atomic updates and last-good retention. Android unit/lint/build checks validate the client; physical inspection is still needed for USB access, visible panel behavior, persistence across reboot and recovery. The public audit scans both current export candidates and reachable original Git history without printing secret values. Public sharing uses a separate fresh-history checkout; never publish the original history directly. License selection and publication remain explicit final decisions.
