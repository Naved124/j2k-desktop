#!/usr/bin/env bash
# Makes tachiyomi:// and mihon:// "add repo" links (the "Add" buttons on extension repo websites)
# open J2K Desktop. The link is queued in a file; the app picks it up within a couple of seconds
# (or on its next start if it isn't running).
set -euo pipefail

BIN_DIR="$HOME/.local/bin"
APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$BIN_DIR" "$APPS_DIR"

cat > "$BIN_DIR/j2k-add-repo" <<'SCRIPT'
#!/usr/bin/env bash
data="${XDG_DATA_HOME:-$HOME/.local/share}/j2k-desktop"
mkdir -p "$data"
printf '%s\n' "$1" >> "$data/pending-repos.txt"
if command -v notify-send >/dev/null 2>&1; then
  notify-send "J2K Desktop" "Repo queued. Open J2K Desktop to see its extensions."
fi
SCRIPT
chmod +x "$BIN_DIR/j2k-add-repo"

cat > "$APPS_DIR/j2k-desktop-links.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=J2K Desktop (repo links)
Exec=$BIN_DIR/j2k-add-repo %u
MimeType=x-scheme-handler/tachiyomi;x-scheme-handler/mihon;
NoDisplay=true
DESKTOP

xdg-mime default j2k-desktop-links.desktop x-scheme-handler/tachiyomi
xdg-mime default j2k-desktop-links.desktop x-scheme-handler/mihon
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" || true

echo "Done. tachiyomi:// and mihon:// links now go to J2K Desktop."
echo "Check with: xdg-mime query default x-scheme-handler/tachiyomi"
