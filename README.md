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
- **Downloads** for offline reading, saved to `~/Documents/J2KDesktop/downloads/<source>/<manga>/<chapter>/`. The Downloads page lists the queue in order (first clicked, first downloaded), with progress, pause/resume and reordering.
- **Incognito:** a sidebar switch that stops history, reading progress, tracking and new cover caching. It lasts for the current session only.
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

## Adding an extension repo

The app has no built-in repos, so you add the ones you want. For example, the Keiyoushi repo:

```
https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json
```

**In the app**

1. Open **Browse**, then the **Extensions** chip at the top.
2. Paste the URL into the "Repo URL (…/index.min.json) or a tachiyomi:// link" field and press **Add**.
3. After a few seconds the extension list appears. Install what you want; its sources then show under **Sources**.

**From a terminal** (works with the app open or closed; an open app picks it up within 2 seconds):

```
echo "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json" >> ~/.local/share/j2k-desktop/pending-repos.txt
```

On Windows the file is `%APPDATA%\j2k-desktop\pending-repos.txt`.

**From a website:** with the app installed (Arch package or `install-local.sh`), "Add repo" buttons that open `tachiyomi://` or `mihon://` links send the repo to J2K Desktop.

**From a backup:** importing a Tachiyomi/Mihon backup (More → Backup) re-adds the repos it contains.

## License

J2K Desktop is free to download and use for your own personal, non-commercial use.
You may not redistribute or republish it, fork it to release your own version, or make money from it, unless you have written permission.
See [LICENSE](LICENSE) for the full terms.

Some files come from other projects and keep their own licenses (Apache 2.0); see [NOTICE](NOTICE).

