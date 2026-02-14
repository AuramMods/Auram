#!/usr/bin/env sh
set -eu

# Run from: /server/
# Source:   ../minecraft/
# Dest:     current directory (/server/)

SRC_BASE="../minecraft"

DIRS="mods config kubejs scripts defaultconfigs"

for d in $DIRS; do
  # Remove existing destination directory to avoid merge-copy
  rm -rf "./$d"

  # Copy from client to server (preserve attributes when possible)
  if [ -d "$SRC_BASE/$d" ]; then
    cp -a "$SRC_BASE/$d" "./"
  else
    echo "WARN: source directory missing: $SRC_BASE/$d" >&2
  fi
done

echo "Done."
