# J2K Desktop

A desktop port of the [TachiyomiJ2K](https://github.com/Jays2Kings/tachiyomiJ2K) manga reader, built with Kotlin and Compose Multiplatform. It runs Tachiyomi/Mihon extensions and is designed for reading on a laptop with a touchpad.

## Features

- **Extensions:** add any Tachiyomi/Mihon extension repo (there are no built-in repos), then install, update and configure sources. Keiyoushi's prebuilt JVM jars load directly.
- **Reader:**
  - Three modes: Paged (single page or two-page spread, RTL/LTR), Vertical, and Webtoon.
  - One page per touchpad swipe, tap zones, and Ctrl+scroll to zoom.
  - A small page number, plus a lean bottom bar that a click shows and scrolling hides.
  - A settings panel and fullscreen. Your place is saved as you read.
- **Library:**
  - Categories, unread badges, and library updates (manually or when the app starts).
  - A per-manga scanlator group filter.
- **Recents:** reading history, and new chapters found by library updates.
- **Downloads** for offline reading, saved to `~/Documents/J2KDesktop/downloads/<source>/<manga>/<chapter>/`.
- **Cloudflare and WebView sources:** handled by driving the Chromium, Chrome, Brave or Edge you already have installed. If a site asks for a click, a small window opens for it.
- **Backups:** imports `.tachibk` / `.proto.gz` backups from Tachiyomi, J2K and Mihon, and writes backups those apps can import.
- **Tracking:** AniList, with your own API client (set it up in More → Tracking).
- **Local manga:** folders or `.cbz`/`.zip` files in `~/Documents/J2KDesktop/local`.

## Run from source

Needs JDK 21.

```
./gradlew :desktopApp:run
```

## Install

**Arch Linux (pacman):**

```
cd packaging/arch && makepkg -si
```

This installs to `/opt/j2k-desktop` with a `j2k-desktop` command, a menu entry and `tachiyomi://` link handling.

**Any Linux, just for your user:** run `bash packaging/linux/install-local.sh`.

**Windows / .deb:** push a version tag (`git tag v1.0.0 && git push origin v1.0.0`). GitHub Actions then builds the Linux tarball and `.deb`, the Windows `.msi`/`.exe` and a portable zip, and attaches them to a GitHub Release.

The builds bundle their own Java runtime, so nothing else needs installing (except a Chromium-based browser for Cloudflare/WebView sources).

## Where things are

| What | Linux | Windows |
|---|---|---|
| Library, progress, settings, extensions | `~/.local/share/j2k-desktop` | `%APPDATA%\j2k-desktop` |
| Caches | `~/.cache/j2k-desktop` | `%LOCALAPPDATA%\j2k-desktop\cache` |
| Downloads / local manga | `~/Documents/J2KDesktop` | `Documents\J2KDesktop` |

## Project layout

- `desktopApp`: the launcher (window, icon, packaging).
- `shared`: the app: UI, reader, library, downloads, backups, tracking, extension loading.
- `source-api`: the Tachiyomi extension API (`eu.kanade.tachiyomi.*`), the network layer, the browser bridge (`dev.naved.j2kdesktop.browser`) and `android.webkit`.
- `android-compat`: desktop stand-ins for the Android classes extensions use (Context, SharedPreferences, graphics, preferences, …).
