#!/usr/bin/env bash
# Renders app-screens.html to PNGs with headless Microsoft Edge (Git Bash on Windows).
# Usage: bash docs/mockups/render.sh
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
edge="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
page="file:///$(cygpath -m "$dir/app-screens.html")"

shot() { # name width height scale
  rm -f "$dir/$1.png"
  "$edge" --headless=new --disable-gpu --hide-scrollbars --virtual-time-budget=8000 \
    --force-device-scale-factor="$4" --window-size="$2,$3" \
    --screenshot="$(cygpath -w "$dir/$1.png")" "$page#$1" 2>/dev/null
  # Edge can return before the PNG is flushed; wait up to 10 s for it.
  for _ in $(seq 1 20); do [ -s "$dir/$1.png" ] && break; sleep 0.5; done
  [ -s "$dir/$1.png" ] || { echo "FAILED $1.png" >&2; exit 1; }
  echo "rendered $1.png"
}

for screen in expenses add insights askai settings; do
  shot "$screen" 412 915 3
done
shot overview 2560 1440 1
