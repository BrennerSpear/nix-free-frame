# Preparing a public snapshot

The original repository remains a private working/recovery record. Its `spec/` files contain personal installation evidence and its Git history contains private host addresses/paths and author identity even after HEAD is edited. Do not push that repository or rewrite/delete its history as cleanup.

Run `python3 scripts/public-export.py` for a read-only audit. It checks approved current source roots and every reachable history blob against private configuration values, home paths, private addresses, tailnet names, keys and author identity. Address exceptions are restricted to two exact synthetic LAN fixtures in `tests/config.test.ts` and the three exact RFC1918 CIDR definitions in `scripts/ssh-client.py`; these exercise/implement private-address validation. All other private-address literals still fail. Reports name categories/counts rather than secret values. This is a bounded audit, not a claim that arbitrary private data can be recognized automatically; inspect the actual candidate before sharing. Private hashes/screenshots/album metadata are excluded with the entire `spec/` tree rather than cosmetically redacted.

After implementation/checks pass, run:

```sh
python3 scripts/public-export.py --output /tmp/nix-free-frame-public
```

The output must be new and outside this checkout. The exporter refuses findings and symlinks, copies only approved source/docs, excludes personal agent context and `spec/`, creates generic contributor entrypoints and a single anonymous local commit, and sets no remote. It never pushes. Source readback verifies the copied bytes and fresh history. `.env`, runtime evidence, tool downloads, build outputs, keys, vendor APKs and original history are excluded. Executable wrapper scripts retain their executable modes. Review both files and the fresh commit metadata before publishing. Preserve this private repository and recovery artifacts separately.

Open the exported checkout, install frozen dependencies, use `.env.example` for a fresh configure/doctor, bootstrap pinned tools and run host/Android checks. Missing private values should fail clearly. A clean clone cannot reproduce a pre-existing frame's signer or installed proof; supply your own private initial key and capture actual installed baseline. Never borrow somebody else's proof.

License choice is still pending; see [licensing](licensing.md). Public publication is a separate final action, after concrete review and authorization. No public home-service exposure accompanies source sharing.
