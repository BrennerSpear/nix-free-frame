# Agent setup runbook

Read [contributing instructions](contributing.md), [conversion/recovery](how-we-did-it.md), [operation/protocol](host-and-protocol.md), [Android behavior](../android/README.md), [licensing](licensing.md) and [public-sharing boundaries](public-sharing.md). Read each tool’s help before use.

## Establish current state

Identify the exact frame and whether this is a new installation or an existing working deployment. Tested target: W10F-09, Android7.1.2/API25/ARM32. Build tooling supports Apple Silicon macOS. Inspect actual installed/staged versions, signing identity, host health, latest receipt, retained caches/settings and intended SSH routes. Historical proofs are not fresh state.

Get the chosen existing Google Photos sharing link privately. Explain that anyone holding it can view the album. Do not enable sharing, modify album contents, change router settings or switch providers implicitly. A computer prepares/signs/provisions software; daily direct photo delivery subsequently runs on the frame.

## New installation

Use frozen dependencies and `scripts/configure.ts configure`; edit mode-600 `.env` locally and run doctor. Use migration only for an existing legacy configuration after preserving its rollback copies. Preserve existing keys and proofs. Bootstrap pinned tools; SDK terms require the user’s own acceptance. Generate a signing key only for a genuinely new installation.

Follow [USB setup](usb-setup.md). Require the selected authorized USB device in normal boot, perform the complete private backup, then use the documented device/install/configuration sequence. Keep original applications and data recoverable. Device Owner and optional Termux SSH each require their own understood authority boundary. Do not wipe accounts, flash firmware, root the device or enable network ADB to bypass prerequisites.

Initial private server/token configuration still uses the existing USB import. Configure a private LAN maintenance origin, run the current server, then provision direct mode as below. Build tools and initial provisioning are not on-device operations.

## Existing frame: direct migration

1. Save a private pre-update receipt and preserve the actually installed APK/proof, signing key and host cache/jobs.
2. Run repository checks and build a strictly newer same-signer APK with unit/lint checks. Verify API25 package/signature compatibility and scan decoded APK/upload candidates for actual private values.
3. Stage via `scripts/stage-update.ts` using the real installed baseline. Prefer ordinary signed OTA over unnecessary USB changes. Require an actual installed APK hash and a fresh resumed/HOME/owner receipt.
4. Ensure the project’s maintenance server supports `/direct-config`. Run `scripts/direct-config.ts direct` using the existing private `.env`; never pass secrets in arguments. It stages an encrypted private configuration with a fresh revision.
5. Wait for import and successful direct enumeration/download/publication. Check direct count, dates, current-version native fixtures, preserved presentation settings and both SSH routes. Generic failure codes require diagnosis; do not report a running or staged refresh as success.
6. Demonstrate a fresh successful direct sync with this project’s host photo endpoints unavailable. Preserve maintenance/update access and restore normal host service afterward. Test failed refresh retention, then restore the intended album revision and verify success again.
7. Obtain physical confirmation of visible cycling/day/night behavior. A cold-power boot and true Wi-Fi-disconnected playback are separate checks; mark them unverified until observed. Do not claim these from a host endpoint-denial test.

Direct and host preferences/caches are separate. `scripts/direct-config.ts host` stages a reversible return to host delivery without erasing its retained settings/cache. Keep the host jobs, recovery backups, original firmware and signing identity until retirement is concretely authorized.

## Optional access and recovery

The existing fixed Termux startup bridge remains unchanged: official signer pin, scoped RUN_COMMAND and a fixed local managed script only. Termux SSH has its app sandbox privileges. Use pinned host keys and existing Mini/laptop routes; do not assume SSH can read the frame app’s private files or grant Android shell authority.

For owner removal, follow the tested two-step sequence: clear only this app’s owner/admin role using the protected receiver, then restore the captured original Backup Manager setting. Restore original launcher/package states exactly and allow persistence waits. Full vendor restoration remains a separate unverified end-to-end procedure on a retained deployment.

Run the public-safety audit before sharing source. Keep configuration, photos, keys, private proofs and original history outside distributable files. Record implementation/live evidence in the designated private log; publication does not authorize changing repository visibility or merging a PR.
