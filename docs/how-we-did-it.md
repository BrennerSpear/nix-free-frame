# How this W10F-09 was converted

## Physical topology and data flow

The frame remains an Android appliance on its original power adapter, connected to Wi-Fi. A macOS host fetches an already link-shared Google Photos album once daily, decodes and converts the images, and publishes an authenticated local manifest and JPEG cache. The frame retrieves that manifest and verifies image hashes before replacing its own cache. It continues cycling offline when the host or internet is unavailable. USB connects a development computer to the board only for first setup or recovery. No ROM flash, bootloader modification, persistent root or TCP ADB was used.

The [10.1-inch Smart Digital Photo Frame listing](https://www.amazon.com/dp/B07V42JLFH) identifies the W10F family; it does not establish which hardware revision a seller will supply. The actual tested unit identified as **W10F-09 / w10f09, Android 7.1.2, API 25, armeabi-v7a** and produced 1280 × 800 Android screenshots. Normal-boot USB ADB worked. Physical independent-power slideshow playback, reboot startup, offline cache, a natural daily host sync, silent signed Wi-Fi updates and two key-only SSH routes were verified on this installation. These findings establish that unit's behavior, not every Nixplay frame's compatibility. Screenshots establish rendered output; the owner separately confirmed the physical panel cycling with USB disconnected.

## Accessing the internal USB

Disconnect power before opening. Use a plastic opening tool, small screwdriver, soft work surface and a **data-capable micro-USB cable**. Preserve ribbon cables and stop if the assembly differs.

A [firsthand W10F owner's notes by u/Single-Guarantee-557](https://www.reddit.com/r/selfhosted/comments/1l3bz5a/comment/ojali9n/) describe removing the front bezel, six silver chassis screws and two black board screws to reach micro-USB underneath the board. That comment is W10F-family evidence, not an independently verified screw-count guide for W10F-09. We used the internal micro-USB to reach our unit in normal boot; we did not independently record its screw layout. The comment's network-ADB instructions are not part of this project. Different-model teardown photographs should not be treated as this model's assembly diagram.

On the physically connected computer, list devices and explicitly choose the USB serial. Confirm `device` state, expected model, API/ABI and `sys.boot_completed=1` before writes. Recovery/sideload/bootloader access does not satisfy this project's normal-boot gate. If the unit cannot pass it, stop rather than attempting another frame's ROM or wiping accounts.

## Why keep the firmware?

The existing firmware already booted Android, drove the panel and Wi-Fi, and allowed normal USB development. Replacing only the HOME application preserved the vendor hardware integration and left original apps/data installed. Back up original HOME, package enabled states, Backup Manager state and original APKs privately before changing anything. APK copies alone are not complete app-data backups or proof of bootloader recovery. The generic tools must never erase accounts, app data or partitions to make Device Owner provisioning succeed.

## Reproducible setup sequence

1. On macOS, install frozen Bun dependencies, configure `.env`, run its doctor, perform a successful sync, and verify the bearer-protected local server. Set a stable private LAN host address. Install the user LaunchAgents only after this succeeds. `.env` is the private textual source; binaries and evidence stay in ignored paths.
2. Bootstrap the pinned Android tools, build and check a token-free APK. Preserve a dedicated signing keystore privately from the first installation onward. A replacement APK must have the same signing identity. Do not distribute a configured APK or private build output.
3. Over explicit trusted USB, capture device identity and recovery state, install the APK, transfer server/token settings privately, and select `works.tycho.frame/.FrameActivity` as HOME. Disable original launcher packages only after recording their states and demonstrating custom playback. Leave original APKs and data installed. Allow **at least 30 seconds** after HOME/package changes before reboot or removing power.
4. Assign Device Owner only on compatible hardware/account state; do not auto-wipe to satisfy Android's prerequisites. Record the original backup setting. Verify the empty-policy owner, playback and original package state. The clear-owner recovery receiver removes only this app's role; restoration also needs the original backup setting.
5. If SSH is desired, use verified matching F-Droid Termux and Termux:Boot builds, provision dedicated public keys, pin the frame's host key through USB, and verify key-only authentication. Configure a local alias and optional ProxyJump through an already authenticated Tailscale host. Preserve unrelated SSH entries and authorized keys.
6. Allow the Termux permission/battery changes to persist for **45 seconds** before reboot. Verify actual reboot HOME playback, listener, both SSH routes, retained cache/settings and permission/owner state. On this firmware the fixed startup bridge is required; installing Boot alone was not enough.
7. Verify on the original power adapter with USB disconnected. For later app changes, use the existing signed same-origin Wi-Fi update path and a strictly increased version code. Read actual installed evidence and foreground behavior after publication. Use `bun run scripts/verify.ts --expect-version VERSION_CODE --apk CANDIDATE_APK --previous-receipt PRIVATE_PRE_UPDATE_RECEIPT` for the bounded installed-hash/retained-state gate.

The checked-in workflow commands are:

```sh
python3 scripts/bootstrap-tools.py --install
# New installations only: set private signing passwords/alias in .env first
python3 scripts/create-signing-key.py --new-installation
python3 scripts/build-android.py --checks
python3 scripts/device.py discover
python3 scripts/device.py doctor --serial DEVICE_SERIAL
python3 scripts/device.py backup --serial DEVICE_SERIAL
python3 scripts/device.py install --serial DEVICE_SERIAL --apk android/app/build/outputs/apk/debug/app-debug.apk
python3 android/configure-usb.py --serial DEVICE_SERIAL
python3 scripts/device.py home --serial DEVICE_SERIAL
python3 scripts/device.py owner --serial DEVICE_SERIAL
python3 scripts/device.py permissions --serial DEVICE_SERIAL
```

The signing-key command refuses an existing key or installed baseline. For an already installed frame, preserve the existing identity and omit that command.

Supply your explicit USB serial locally; never copy another installation's serial. The bootstrap does not accept Android SDK license terms on your behalf: inspect and accept required official terms locally. Initial Termux package bootstrap remains interactive. Run `scripts/provision-termux-ssh.sh PUBLIC_KEYS_FILE` **inside Termux**, not on your Mac; transfer it and public keys through trusted USB. Host-key capture remains manual through USB. Capture the frame host key through USB into the private known-hosts file named by `.env`; never use a network key scan as its trust source. `python3 scripts/ssh-client.py install [--jump EXISTING_ALIAS]` adds only its marked block to `~/.ssh/config`, creates a restrictive backup and preserves unrelated content. `verify` checks the key-only route; `remove` removes only that marked block while retaining keys and pins. Use `--config PATH` for a separate client config or a laptop-specific configuration. The existing jump alias must already authenticate and reach the host; the script does not configure Tailscale or router rules. Use `clear-owner`, `restore-home --original-home PACKAGE/ACTIVITY` and `retire-access` only against captured original state when recovering/retiring. Full vendor retirement still requires manual recorded-state checks and visible confirmation.

Use the checked-in workflows and their `--help` for command syntax; [host setup and protocol](host-and-protocol.md) covers host configuration, jobs and update staging. Some first-install gates require physical USB and the owner's visible confirmation. Preparing source does not redo those physical steps on an already working frame.

## Google shared-page extraction

Google's [Library API changes](https://developers.google.com/photos/support/updates) restrict retrieval to app-created content and removed shared-album API functionality after March 31, 2025. We therefore use an explicitly link-shared page, without a Google login or cookies. The pinned extractor follows undocumented pagination. Its local patch rejects malformed entries/tokens instead of silently accepting a partial list. Full enumeration and validated downloads precede atomic cache publication; failure retains the prior manifest. This depends on Google's web behavior and may break. The [Ambient partner program](https://developers.google.com/photos/partner-program/overview) is a separate integration, not an unauthenticated alternative. Never enable sharing automatically or publish the sharing link.

## Dates, night and updates

Date labels use actual valid embedded EXIF capture-month metadata. Missing/malformed dates are left blank; upload/update/file timestamps never substitute. There is a brief soft label and 600 ms crossfade. Night mode is timezone-specific black output with minimum app-window brightness, not panel power-off; the timer wakes playback in the morning. The frame clock still needs to be accurate. The tested defaults are 15 seconds/photo and 22:00–08:00 night hours.

The own-package updater accepts only the configured origin, package, installed certificate, exact hash/size and a higher version. Downloads are bounded and redirects rejected. Device Owner permits unattended PackageInstaller sessions; no arbitrary network command endpoint exists. Cache/settings survive installation. The backend's bounded status report is supplemental evidence, not proof of install. Verify the actual installed APK/version/hash using the trusted management route and physical/foreground readback.

## SSH and firmware observations

Current [Tailscale Android](https://tailscale.com/docs/install/android) requires Android 8+. On this Android 7 frame, [Termux OpenSSH](https://github.com/termux/termux-packages/tree/master/packages/openssh) supplies a key-only port-8022 listener. The remote path is laptop → Tailscale-connected host → frame LAN SSH, using ordinary OpenSSH ProxyJump. The frame has no Tailscale APK, root SSH, public forwarding, or Android shell privileges through Termux.

Official F-Droid Termux 0.118.3 and Boot 0.8.1 were verified for API25/ARM32 and matched the pinned F-Droid signer. Follow [Termux's source guidance](https://github.com/termux/termux-app) rather than mixing APK signatures. [Termux:Boot](https://github.com/termux/termux-boot) normally requires first launch and boot scripts. Here the vendor firmware marked its receiver process bad after reboot, even with battery exemption retained. Boot remains installed, but unattended startup relies on the narrow fixed app bridge: verify Termux's signer and scoped RUN_COMMAND grant, then invoke only the predetermined managed local startup script once per app process. It exposes no caller-supplied command/arguments/URL. See [the pinned RUN_COMMAND implementation](https://github.com/termux/termux-app/blob/v0.118.3/app/src/main/java/com/termux/app/RunCommandService.java).

The script takes a Termux wake lock and runs `sshd -D` as a tracked task. Its duplicate guard checks the dedicated PID, same UID and exact daemon/config command before reusing a listener. A Wi-Fi replacement app installation retained the same live SSH daemon. An earlier app update installed successfully but Android marked our process bad, preventing foreground restart; a controlled reboot recovered it. Later tested signed updates recovered foreground automatically. Do not infer universal firmware reliability from a passing candidate.

## Recovery and troubleshooting

If sync fails, inspect private local logs/status and leave the last good cache intact. If DHCP changed, update host binding and the frame's private origin together; do not weaken bearer authentication. If an update reports `permission`, inspect Device Owner rather than opening a generic installer. If installed status arrives but playback is absent, verify actual HOME/process state through trusted access; the tested firmware once required a controlled reboot.

To retire Device Owner, use the shell-protected `works.tycho.frame.CLEAR_OWNER` receiver over explicit USB, verify owner/admin absence, then restore **the captured original** Backup Manager state and read it back. This firmware kept Backup Manager disabled after owner removal until explicitly restored. The role-clear plus backup-restore was tested with photos/settings retained. Never assume every device originally had backup enabled.

For full retirement, revoke only our Termux RUN_COMMAND grant, stop the dedicated daemon, restore its backed-up properties if unrelated integrations permit, and remove only this project's SSH blocks/keys/pins. Restore original package enabled states and recorded HOME, launch it, inspect the physical screen and retain our app until vendor operation is demonstrated. Wait 30 seconds before reboot. **Complete return to the vendor launcher has not been exercised end to end on the retained deployment.** Do not present the documented reverse sequence as proven full restoration. Keep USB recovery available; do not replace it with unsecured network ADB.
