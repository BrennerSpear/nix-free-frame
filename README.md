# Nix Free Frame

This Nixplay frame now fetches a Google Photos album over its own Wi-Fi and keeps a local copy for its slideshow. A Mac prepares and installs the software; daily photo fetching runs on the frame. No firmware replacement is needed.

![Google Photos flows directly to the frame over Wi-Fi; a computer handles setup and maintenance](docs/images/how-it-works.png)

Tested hardware: [Nixplay 10.1-inch W10F](https://www.amazon.com/dp/B07V42JLFH), specifically **W10F-09**, Android 7.1.2, 1280 × 800. Other revisions may differ. The build/setup tools support an Apple Silicon Mac.

## Getting started

1. **Connect the frame for initial setup.** The micro-USB port is internal: read [the USB guide](docs/usb-setup.md), unplug power before opening, and stop if your assembly differs.
2. **Choose an album.** Supply an existing Google Photos sharing link privately. Anyone holding the link can view that album; this software does not enable sharing for you.
3. **Give your coding agent this repository.** Ask it to follow [the setup runbook](docs/agent-setup.md). You handle the cable, device prompts, and physical screen confirmation.

Photos refresh daily, crossfade every 15 seconds, show capture dates when known, and dim overnight. Failed refreshes retain the last complete slideshow. Original applications, recovery backups, and the previous Mac delivery path remain available.

Google’s shared-page format is undocumented and can change. See [operation and verification limits](docs/android-only.md).
