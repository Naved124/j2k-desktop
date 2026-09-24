#!/usr/bin/env bash
# Builds J2K Desktop and installs it for your user only (no root, any distro):
#   ~/.local/opt/j2k-desktop, ~/.local/bin/j2k-desktop, a menu entry and repo-link handling.
# Run from anywhere:  bash packaging/linux/install-local.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

./gradlew :desktopApp:createDistributable

app="$root/desktopApp/build/compose/binaries/main/app/j2k-desktop"
dest="$HOME/.local/opt/j2k-desktop"
apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
icons="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/512x512/apps"

rm -rf "$dest"
mkdir -p "$(dirname "$dest")" "$HOME/.local/bin" "$apps" "$icons"
cp -a "$app" "$dest"
ln -sf "$dest/bin/j2k-desktop" "$HOME/.local/bin/j2k-desktop"
cp "$root/desktopApp/icons/icon.png" "$icons/j2k-desktop.png"
cp "$root/packaging/linux/j2k-desktop.desktop" "$apps/j2k-desktop.desktop"

# Links from extension repo websites ("Add to Tachiyomi/Mihon") open the app
rm -f "$apps/j2k-desktop-links.desktop"   # the old helper from scripts/install-link-handler.sh
xdg-mime default j2k-desktop.desktop x-scheme-handler/tachiyomi
xdg-mime default j2k-desktop.desktop x-scheme-handler/mihon
command -v update-desktop-database >/dev/null && update-desktop-database "$apps" || true

echo "Installed. Start it from your app launcher or run: j2k-desktop"
echo "(Make sure ~/.local/bin is in your PATH.)"
