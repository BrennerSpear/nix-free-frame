# Direct Android operation

The native app implements direct shared-album fetching, sampled image normalization, transactional local caching, and calendar-based daily scheduling. The computer remains useful for building/signing software, initial provisioning and optional maintenance. The prior host mode, its separate cache/settings, and recovery path are retained.

See [the operation/protocol reference](host-and-protocol.md), [Android behavior](../android/README.md) and [agent runbook](agent-setup.md) for current commands, limits, trust boundaries and verification gates. Google’s embedded metadata and pagination remain undocumented interfaces that can change.

## Overnight behavior

The 2.1.0/code 16 implementation adds true display sleep without replacing Android 7.1.2 or the vendor firmware. It arms a local-calendar morning wake using `setExactAndAllowWhileIdle` before calling `lockNow()`. Sleep is gated by a successful wake probe, a nonsecure keyguard and the `force-lock` grant. Missing prerequisites retain the black-screen fallback. API25 does not let Device Owner bypass an empty admin policy declaration: the app now requests only `force-lock`, and the installed grant may require confirmation through **Allow display sleep** on the frame. [Android details](../android/README.md#overnight-display-sleep).

Morning wake restores HOME and daily sync catch-up. Boot/time changes rebuild the schedule; a separate maintenance alarm continues while the foreground timer is paused. The optional private DEBUG verification actions cover alarm delivery, a short sleep/wake, temporary day playback, timed Wi-Fi disconnection/restoration and an ordinary owner-initiated reboot. Their actions and durations are fixed, and Wi-Fi recovery is armed and persisted before disconnecting. These mechanisms require fresh device evidence; this section does not claim they have passed on the deployed frame.

## Verification boundary

Use fresh installed-APK and frame-receipt evidence for the deployed version. Native synthetic fixtures exercise JPEG EXIF capture dates and all eight orientations on the actual frame. The complete direct-sync cache transaction also receives injected download/enumeration failures, an empty result, cached-image corruption and a configuration cancellation; checks require exact retained manifest/image bytes and cleanup of new staged files. These fixtures do not simulate destructive power loss. A successful refresh while host photo delivery is denied proves that refresh did not use the Mac photo cache. It does not prove Wi-Fi-disconnected operation, physical day/night appearance or recovery after a cold-power boot. Those physical checks remain unverified until separately confirmed. Do not disable or remove the previous host jobs automatically.

## Research provenance

The following assessment records the read-only investigation preceding implementation. Its feasibility language describes that earlier point in time; it is preserved for source provenance and alternatives, not as the current deployment status.

# Historical feasibility research (2026-10-04)

Researched 2026-10-04. Read-only investigation; no application, service, sharing, authentication or device changes. This is a feasibility assessment, not a tested Android implementation.

## Recommendation

The frame can plausibly fetch its existing link-shared Google Photos album directly in the native Java app, eliminating the Mac from normal photo delivery. Prefer this to moving the whole Bun server into Termux. Keep the present cache and slideshow as the fallback while testing a new direct-fetch mode. A development machine would still build/sign updates; Android-only normal operation does not imply on-device builds or automatic update publishing.

The existing backend does not log into Google. Its extractor requests the shared page without OAuth/cookies, parses media metadata and pagination capabilities embedded in the page, and downloads images from Google image servers. A secret album link is sufficient for this route. It is link-shared content, not a truly unshared album. Google explicitly says people without a Google account can receive a link and anyone holding that link can view the album. Keeping the link confidential reduces exposure; it is not account-bound authorization or evidence of public discoverability. [Google sharing help](https://support.google.com/photos/answer/6131416).

## Actual source findings

Read `src/core.ts`, `src/captured-month.ts`, the patched installed package `node_modules/@marcus5914/google-photos-album-image-url-fetch/dist/index.mjs`, and native presentation/cache code.

- The package GETs shared HTML with no authentication headers. It finds an `AF_initDataCallback(...)` block, parses a JavaScript object using its bundled parser, and validates the nested album media arrays. These are undocumented website internals, not a stable official API.
- For albums exceeding the initial embedded page, it extracts album/auth keys and calls private `PhotosUi/data/batchexecute` RPC `snAcKc`. It deduplicates media IDs, rejects repeated continuation tokens, caps traversal at 100 pages, and raises on incomplete/malformed pagination. A native port must preserve completeness checks rather than interpreting a partial response as album deletions.
- The host filters videos, fetches bounded image downloads from HTTPS `*.googleusercontent.com`, requests approximately 1920-pixel images, normalizes with macOS `sips`, hashes JPEGs, and atomically publishes a manifest only after a full successful snapshot. It uses `lockf` and launchd; these prevent an unchanged Android/Termux deployment even apart from Bun.
- Dates come from real JPEG EXIF; shared-page modification/addition timestamps are not capture dates. Unknown capture dates remain blank. Native decoding/recompression may remove EXIF, so extract the capture label before re-encoding and retain it separately.

## Native Java implementation shape (inference)

Add a bounded HTTPS worker to the existing HOME app, including strict redirect/host policy, private shared-link storage, response/album/page/time limits, complete parsing, video filtering, sequential downloads and transactional cache replacement. Do not execute remote JavaScript. Parsing the site's JavaScript object literal needs a constrained parser or carefully implemented equivalent, rather than assuming it is JSON. Continue offline playback from the last valid cache on network/parser/TLS failures.

Android BitmapFactory sampling/scaling and JPEG compression can replace `sips`; preserve the existing `DateTimeOriginal`-only label policy using platform ExifInterface, which has that tag by API24 and is available on this API25 frame. Parse date strings directly: modern `getDateTimeOriginal()` convenience methods require API31. [Android ExifInterface](https://developer.android.com/reference/android/media/ExifInterface).

Keep sync owned by the foreground HOME process, also during its black night screen. Schedule a daily target time with retry/catch-up on launch and reboot; do not assume a 24-hour delay equals 9AM local time across DST. Memory, storage, TLS behavior, interrupted downloads, EXIF retention and actual firmware reboot persistence need device tests. This route is technically plausible but inherits Google's website-change risk.

## Termux option

Termux supports Android7+ and its package build system includes ARM32. NodeJS has an ARM build branch, so a Node/Python-based on-device fetch/cache service is plausible. [Termux support](https://github.com/termux/termux-app), [NodeJS Android package source](https://github.com/termux/termux-packages/blob/master/packages/nodejs/build.sh).

It is not a drop-in deployment of this repository: official Bun downloads list x64/ARM64, with no ARM32 Android target, and the backend calls Bun-specific APIs plus macOS commands. [Bun installation/platforms](https://bun.sh/docs/installation). A rewrite would need image normalization, locking, scheduling, HTTP serving and supervised startup. This firmware already suppressed Termux:Boot; the fixed bridge only starts a managed SSH script. Extending it to general network-provided commands would break its narrow trust boundary. Prefer native Java for fewer moving parts; do not install or change the existing SSH task during this research.

## Truly unshared albums and current official APIs

Account-bound private Photos content needs an authorized Google-account access route. Browser cookie scraping on this old frame is not an OAuth substitute and adds brittle sign-in/session maintenance. No login is needed for the existing secret shared-link route.

1. **Library API:** Since the 2025 changes, read/list/search methods are restricted to content created by the app. OAuth login does not restore arbitrary existing-album access. [Scopes](https://developers.google.com/photos/overview/authorization), [official changes](https://developers.google.com/photos/support/updates).
2. **Picker API:** OAuth plus explicit user selection can import selected existing library items. Sessions represent selections, not a standing subscription to future photos added to an album; selected media URLs last 60 minutes and require OAuth bearer headers. It can support manually refreshed private selections, but does not replace unattended album sync. [Sessions](https://developers.google.com/photos/picker/guides/sessions), [media retrieval](https://developers.google.com/photos/picker/guides/media-items).
3. **Ambient API:** This is the actual official API intended for smart TVs and photo frames. It supports limited-input-device OAuth and user-configured media sources; `mediaSourceId` can retrieve a selected source. Access requires acceptance into the Google Photos Partner Program. This is the best official account-authorized product route if approval is available, not a guaranteed self-serve hobby integration. [Ambient overview](https://developers.google.com/photos/ambient/guides/about), [partner gate](https://developers.google.com/photos/partner-program/overview), [source listing](https://developers.google.com/photos/ambient/reference/rest/v1/mediaItems/list), [device OAuth](https://developers.google.com/photos/ambient/guides/configure-your-app).

Current API integration also entails minimum necessary scopes, meaningful consent/disclosure, deletion controls and secure token storage under Google's [Photos API policy](https://developers.google.com/photos/support/api-policy). The source-read shared-page extractor is not evidence of official API approval or of a durable service guarantee. For an official Ambient integration, separately resolve its applicable cache/content/UX obligations before promising the existing persistent offline-cache behavior.
