# Operation, maintenance and protocol

The frame can fetch its link-shared Google Photos album directly and play a private local cache. A computer still builds/signs updates and supplies initial provisioning and optional maintenance. The existing Mac cache/server/jobs remain available for rollback; do not retire them automatically.

## Existing deployment migration

Read each CLI’s help first. Preserve the private `.env`, signing key, installed APK/proof and a pre-update frame receipt. Build a strictly newer same-signer APK using `scripts/build-android.py --checks`, validate it for API25, then stage through the existing own-package OTA path:

```sh
bun run scripts/stage-update.ts --apk PATH --baseline-apk PATH --installed-proof PATH
```

The live maintenance server must run the current code before provisioning direct mode. Restart only this project’s server when authorized; retain its normal service configuration and rollback. Do not start competing servers on the same port.

```sh
bun run scripts/direct-config.ts --help
bun run scripts/direct-config.ts direct
# Explicit rollback, when wanted:
bun run scripts/direct-config.ts host
```

These commands read secrets from mode-600 `.env`, accept no secret arguments, and atomically stage an ignored private configuration. Direct staging uses `ALBUM_URL`, `SYNC_HOUR`, `SYNC_MINUTE`, `NIGHT_TIMEZONE`, and `SLIDESHOW_INTERVAL_SECONDS`. Defaults are 09:00, America/New_York, and 15 seconds. Staging a new revision requests a new sync; it does not prove successful import or fetching.

Keep relative paths rooted correctly when building in a separate checkout. Use an ignored private config with absolute tool/key/runtime references where needed. Never publish the original private repository history.

## Configuration transport

The fixed bearer-authenticated `/direct-config` route reads only its dedicated mode-600 file and returns an RSA-OAEP-wrapped random AES-256-GCM envelope. The device’s RSA private key stays in its private app data. The request supplies its public key in a header; no secret album URL appears in a request URL. Missing configuration returns 404. This is explicit provisioning, not automatic opt-in from host settings.

The established maintenance connection uses HTTP on a trusted LAN. Encryption prevents a passive observer from directly reading the configuration response, but the bearer token and recipient header do not provide protection from an active LAN attacker. Do not expose the server publicly, forward router ports, or describe this as authenticated end-to-end TLS.

## Direct photo protocol and limits

Google requests use HTTPS, normal certificate verification, and no login, cookies or OAuth. Album pages allow only `photos.google.com` and `photos.app.goo.gl`; image hosts must match `lh` plus digits under `googleusercontent.com`. Every GET redirect is revalidated; at most five redirects are followed. RPC POSTs never redirect.

The restricted data-only parser reads embedded AF_initDataCallback metadata and private `snAcKc` pagination without executing JavaScript. It deduplicates IDs, rejects malformed/repeated continuation tokens, caps enumeration at 100 pages/10,000 items, and the sync accepts at most 5,000 media entries. Videos are skipped. Empty or incomplete enumeration fails the whole refresh.

Limits include 8 MiB metadata responses, 32 MiB per image, 15-second connection/read timeouts, a 90-second transfer deadline, a five-minute pagination deadline and a 30-minute sync budget checked between images. A final network operation can extend a loop deadline by its bounded request timeout. Sequential images request `=w1920-h1920`; sampled native decoding bounds decoded sides to 2560 pixels. Cache accounting caps retained direct JPEGs at 1 GiB and requires 96 MiB free before downloads. Parser nesting/node limits and native out-of-memory handling bound common failure modes.

A full enumeration and all verified image processing precede publication. Normalized image bytes are synced, image names persisted, then the manifest is atomically replaced and its directory synced before old files are removed. Host/direct caches remain separate. Interrupted or failed work preserves the prior manifest; this is a display cache, not an original-quality backup.

## Verification and retained services

```sh
bun run check
python3 scripts/build-android.py --checks
bun run scripts/verify.ts --expect-version NUMBER --apk PATH --expect-mode direct --previous-receipt PATH
```

The verifier requires fresh installed-hash/role evidence, successful direct mode for the current configuration revision and current native fixture results. Native fixtures include full cache-transaction failure/corruption/cancellation cases with exact last-good byte comparisons. Compare retained presentation settings, direct photo/date counts and actual installed APK hash; verify both intended SSH routes. Receipts contain bounded counts, hashes and fixed failure codes, not album links or photo IDs.

For a host-independence test, make only this project’s photo delivery unavailable while keeping maintenance/update access and rollback intact; require a fresh direct sync to succeed. Test malformed/failed fetching against the retained manifest and restore intended private configuration afterward. Host-delivery denial is distinct from disabling frame Wi-Fi. Physical Wi-Fi-offline playback, cold-power recovery and day/night panel appearance require explicit observations; do not infer them from successful HTTP or unit checks.

Legacy host setup, USB installation and recovery remain in [the agent runbook](agent-setup.md) and [conversion history](how-we-did-it.md). Host jobs must stay in place until retirement is explicitly authorized.
