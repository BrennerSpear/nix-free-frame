# Agent setup runbook

Read this repository's [contributing instructions](contributing.md) first, then [the conversion and recovery guide](how-we-did-it.md), [host configuration and protocol](host-and-protocol.md), [Android behavior](../android/README.md), [licensing/provenance](licensing.md) and [public-sharing boundaries](public-sharing.md). This is an orchestration entrypoint for the existing tools, not a separate skill or a promise that every physical step is automated. Read each CLI's help before use.

## Establish the installation and authority

Determine whether this is a new frame installation or an existing working one. Identify the Mac that will remain awake on the frame's Wi-Fi and the computer physically connected to USB; they can differ. Host services use macOS facilities and the pinned build bootstrap supports Apple Silicon macOS. Require Bun and Python 3.9+. Keep the repository in a stable local path: the background jobs refer to that checkout.

Identify the frame’s private LAN address using its network settings, the router device list or trusted USB network readback; match it to the selected physical device rather than guessing from an open port. Do not enable TCP ADB for discovery.

Ask the user to supply a chosen Google Photos sharing link privately and to enable sharing themselves if necessary. Do not enable sharing, change photos, accept vendor terms, expose a service or change router/Tailscale settings implicitly. Explain that setup stores a photo cache, private configuration, signing identity and recovery evidence on the Mac; it installs user LaunchAgents for the server and daily sync, and installs/configures an Android HOME app on the frame. Optional Device Owner and Termux SSH have separate authority and recovery boundaries. Confirm those optional capabilities are wanted before provisioning them.

## Prepare the host without touching the frame

Install dependencies from the frozen lockfile. For new setup, use `scripts/configure.ts configure`, edit the resulting ignored mode-600 `.env` privately, and run its doctor. Use `migrate` only for a real legacy deployment after inspecting existing sources and preserving its values. Successful migration immediately retires active legacy files after writing `.env` and restrictive rollback backups; validate the new configuration and live service afterward. Never replace an existing signing identity or live recovery evidence with fresh examples.

Resolve `ALBUM_URL`, token, LAN bind address/port, matching frame server origin, runtime path, schedule and timezone from `.env.example`. The example starts on loopback; choose the actual private LAN interface before the frame connects. Do not merge divergent shell environment settings, echo secrets, embed tokens in an APK or include them in URLs. Sync first and verify an authenticated server response before installing/updating scheduled jobs. If using `bun start` for that foreground check, stop it before installing the server LaunchAgent to avoid a port conflict. The daily launchd schedule uses the host's local timezone; night mode uses its own configured timezone.

Follow [host setup](host-and-protocol.md#host-setup) for commands and rollback. Keep the Mac available for syncs and downloads; cached frame playback continues when it is not. Google shared-page extraction is undocumented and can fail; retain the last good cache rather than publishing an empty or partial album.

## Build, select USB target and preserve recovery

Use `scripts/bootstrap-tools.py` for pinned verified downloads. SDK terms require the user's own acceptance. For a new installation only, set signing passwords/alias and use `scripts/create-signing-key.py --new-installation`. Preserve the key privately for every future update. Build with `scripts/build-android.py --checks`; runtime token/server configuration is imported separately.

Have the user read [the illustrated USB guide](usb-setup.md) and [detailed access notes](how-we-did-it.md#accessing-the-internal-usb), disconnect power before opening, attach a data-capable cable and handle any authorization prompt. Discover devices and explicitly select the actual USB serial in `.env` or the command. Require normal boot, authorized USB transport and the tested model/API/ABI. Do not auto-select ambiguous hardware or try firmware flashing, root, network ADB, account wipes or resets to bypass a failed gate.

Use `scripts/device.py doctor` and `backup` before installation. Preserve original HOME, package enabled states, Backup Manager state and original APKs in restrictive ignored files. A partial backup is not sufficient. Follow [the setup sequence](how-we-did-it.md#reproducible-setup-sequence): install, import `.env` settings with `android/configure-usb.py`, select HOME, inspect the screen and allow persistence waits before reboot. Original apps/data stay installed. Do not automatically disable original packages unless their exact original states were recorded and the custom launcher has demonstrated working playback.

## Optional updates and SSH

For silent own-package updates, inspect Device Owner prerequisites and explain the broader Android role. Assign only when authorized; never wipe accounts to satisfy prerequisites. Record original backup state. Recovery must clear only this app's owner/admin role and restore the captured backup setting, not an assumed value.

If remote access is wanted, follow [SSH topology and firmware observations](how-we-did-it.md#ssh-and-firmware-observations). Install verified matching F-Droid Termux/Boot; initial Termux bootstrap and first launch are manual. Run the tracked provisioning script inside Termux with USB-supplied public keys. Obtain the host key through trusted USB, populate the private known-hosts file and use `scripts/ssh-client.py install`/`verify` for targeted client blocks. Preserve unrelated SSH entries. A laptop can use an already working Tailscale host as ProxyJump; do not install unsupported Tailscale on the frame or configure public ports. Termux is an app sandbox, not root or Android shell.

Grant only the frame's scoped RUN_COMMAND permission and Termux exemption, allow the documented 45-second persistence wait, and verify real reboot startup. This firmware suppressed Termux:Boot; the narrowly fixed bridge is the tested startup path. Retain the duplicate-daemon guard. For ordinary changes to an existing frame, prefer a newer same-signer Wi-Fi update over unnecessary USB mutation.

## Verify and report the actual result

Run host unit/type checks, Python workflow/export checks and Android unit/lint/build checks. Follow [verification commands](host-and-protocol.md#verification). Capture a private pre-update receipt if comparing retained settings/manifest hashes; stage only an authorized candidate against a real installed APK baseline/proof. Require a fresh installed APK hash/version receipt and retained state, not merely a staged manifest or downloaded status.

Inspect foreground slideshow, cache/night settings, HOME/owner/permissions and each intended SSH route. Ask the user to confirm the physical panel on its original power adapter with USB disconnected; screenshots alone are not that confirmation. Distinguish completed automated checks, user confirmations and unverified physical steps. Do not claim full vendor restoration was tested: the documented complete reverse sequence remains unverified on the retained deployment.

For failure or retirement, follow [recovery](how-we-did-it.md#recovery-and-troubleshooting), preserve last-good cache and private backups, and restore only captured original state. Configuration, jobs and APK rollback are separate operations. Run the public-safety audit before sharing source, preserve licensing notices and never publish original private history or runtime artifacts. Publishing or changing repository visibility remains an explicit final action.
