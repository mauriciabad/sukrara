#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Put Sukrara on the owner's phone or Mac without losing anyone's merged work (docs/DEVELOPMENT.md "Install").
#   tools/install.sh phone [--dry-run]   apps/android release APK → verify-apk.sh → adb install -r (keeps data)
#   tools/install.sh mac   [--dry-run]   apps/macos bundle.sh → verify-app.sh → over the installed Sukrara.app
#                                        (/Applications, else ~/Applications; one copy stays; quit and reopened)
# Refuses unless: the tree is clean, HEAD contains origin/main and every branch in tools/check/integration.txt,
# HEAD contains the commit installed last time (else features vanish from the device), and tools/check/run.sh passes.
# Records what it installed in <git common dir>/sukrara-installed-<target> (shared by all worktrees).
# --dry-run runs every check but only prints the build / install commands.
set -eu
TARGET=${1:-}; DRY=0
[ "${2:-}" = "--dry-run" ] && DRY=1
case "$TARGET" in phone|mac) ;; *) echo "usage: tools/install.sh phone|mac [--dry-run]" >&2; exit 2 ;; esac
ROOT=$(git rev-parse --show-toplevel); cd "$ROOT"
. tools/release/lib.sh
SERIAL=${ANDROID_SERIAL:-62240DLCH0005W}
RECORD="$(cd "$(git rev-parse --git-common-dir)" && pwd)/sukrara-installed-$TARGET"
die() { echo "REFUSED: $*" >&2; exit 1; }
run() { if [ $DRY = 1 ]; then echo "would run: $*"; else echo "+ $*"; "$@"; fi; }

# 1. Everything merged is in this build.
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "uncommitted changes: commit them (or use a clean worktree)."
git fetch -q origin main 2>/dev/null || echo "note: could not fetch origin/main, using the local copy"
git merge-base --is-ancestor origin/main HEAD || die "HEAD lacks origin/main: merge it first."
grep -v '^\s*#' tools/check/integration.txt | while read -r b; do
  [ -n "$b" ] || continue
  if git rev-parse -q --verify "$b" >/dev/null; then
    git merge-base --is-ancestor "$b" HEAD || { echo "REFUSED: HEAD lacks integration branch $b: merge it first." >&2; exit 1; }
  else echo "note: integration branch $b not found locally, skipped"; fi
done
if [ -f "$RECORD" ]; then
  LAST=$(cut -d' ' -f1 "$RECORD")
  if git cat-file -e "$LAST^{commit}" 2>/dev/null; then
    git merge-base --is-ancestor "$LAST" HEAD || die "the $TARGET runs $(cat "$RECORD"), which HEAD does not contain.
  Installing would remove that work from the device. Merge $LAST first (ask the session that built it)."
  else echo "note: last installed commit $LAST is unknown here (not fetched?)"; fi
fi
tools/check/run.sh --range origin/main..HEAD || die "tools/check/run.sh failed (see above)."

VI=$(version_info) || die "VERSION is not a valid MAJOR.MINOR.PATCH (see above)."
set -- $VI
VERSION=$1
HEAD=$(git rev-parse --short HEAD)
if [ "$TARGET" = phone ]; then
  # 2a. Signed release APK, verified, in-place update (keeps identity, followers, settings). Gradle reads the key's
  # password only from the environment; it comes from the Keychain here and never goes on a command line.
  APK=apps/android/app/build/outputs/apk/full/release/app-full-release.apk
  MAPPING=apps/android/app/build/outputs/mapping/fullRelease/mapping.txt
  if [ $DRY = 0 ]; then
    SUKRARA_KEYSTORE_PASSWORD=$(release_password) || die "no release key password (Keychain item sukrara-android-release, docs/20-publishing.md)."
    export SUKRARA_KEYSTORE_PASSWORD
  fi
  # Gradle needs JDK 21 and the SDK (a worktree has no local.properties).
  JAVA_HOME="$(dirname "$(dirname "$(jdk_tool java)")")"
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  export JAVA_HOME ANDROID_HOME
  run sh -c "cd apps/android && ./gradlew -q :app:assembleFullRelease"
  run tools/release/verify-apk.sh "$APK" "$MAPPING"
  run adb -s "$SERIAL" install -r "$APK"
  if [ $DRY = 1 ]; then GOT="$VERSION (dry run)"; else
    GOT=$(adb -s "$SERIAL" shell dumpsys package app.mauri.sukrara | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')
    [ "$GOT" = "$VERSION" ] || die "phone reports versionName '$GOT' for app.mauri.sukrara, expected '$VERSION'."
  fi
else
  # 2b. Team-signed bundle with the widget, replacing the installed copy wherever it is: /Applications (the notarized
  # release from the dmg or the cask) if it's there, else ~/Applications. Exactly one Sukrara stays on the Mac:
  # Spotlight, Launchpad, sukrara: links and the widget gallery otherwise show two. SUKRARA_MAC_APP_DIRS (only for
  # --dry-run tests on a scratch path) replaces the two folders: space-separated, preferred first, the last one is used
  # when none has a copy.
  [ -z "${SUKRARA_MAC_APP_DIRS:-}" ] || [ $DRY = 1 ] || die "SUKRARA_MAC_APP_DIRS is only for --dry-run."
  APP_DIRS=${SUKRARA_MAC_APP_DIRS:-"/Applications $HOME/Applications"}
  BUNDLE_ID=app.mauri.sukrara
  BUILT=apps/macos/build/Sukrara.app
  LSREGISTER=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister
  plist() { /usr/libexec/PlistBuddy -c "Print :$2" "$1/Contents/Info.plist" 2>/dev/null || true; }
  # Every refusal comes before anything changes on the Mac (the owner keeps an alerting app either way).
  DEST=; OTHERS=; LAST_DIR=
  for d in $APP_DIRS; do
    LAST_DIR=$d; a="$d/Sukrara.app"
    [ -e "$a" ] || [ -L "$a" ] || continue
    [ "$(plist "$a" CFBundleIdentifier)" = $BUNDLE_ID ] ||
      die "$a is not Sukrara (bundle id '$(plist "$a" CFBundleIdentifier)'): move it away by hand."
    # A notarized copy that has run is guarded by macOS App Management: the terminal app needs that permission.
    { [ -w "$d" ] && [ -w "$a" ]; } || die "$a can't be replaced by $(id -un). Either macOS protects it: allow the
  terminal app this runs in under System Settings › Privacy & Security › App Management, restart that app and run
  again; or its owner / permissions are wrong (ls -ld \"$a\")."
    if [ -z "$DEST" ]; then DEST=$a; else OTHERS="$OTHERS $a"; fi
  done
  [ -n "$LAST_DIR" ] || die "SUKRARA_MAC_APP_DIRS is empty."
  DEST=${DEST:-$LAST_DIR/Sukrara.app}
  DIR=${DEST%/*}
  if [ -d "$DIR" ]; then [ -w "$DIR" ] || die "$DIR isn't writable by $(id -un)."
  else [ -w "${DIR%/*}" ] || die "can't create $DIR."; fi
  STAGE="$DIR/.Sukrara.app.installing" # no .app suffix: LaunchServices and Spotlight leave it alone
  echo "mac: installing to $DEST$([ -e "$DEST" ] && echo " (replacing $(plist "$DEST" CFBundleShortVersionString))")"
  for o in $OTHERS; do echo "mac: and removing the second copy $o ($(plist "$o" CFBundleShortVersionString))"; done

  run sh -c "cd apps/macos && Bundle/bundle.sh"
  run tools/release/verify-app.sh "$BUILT"
  run rm -rf "$STAGE"
  run mkdir -p "$DIR"
  run ditto "$BUILT" "$STAGE"
  # Quit the way the menu does (the app saves the follower state and closes its relays, at most 2 s), then make sure.
  # From here on, a failure still reopens whatever copy is at DEST.
  RUNNING=$(pgrep -x Sukrara >/dev/null && echo yes || echo no)
  if [ $DRY = 1 ]; then echo "would quit Sukrara (running: $RUNNING), and reopen it from $DEST at the end"; else
    trap '[ -d "$DEST" ] && open "$DEST"' EXIT
    if [ $RUNNING = yes ]; then
      echo "+ quit Sukrara"
      osascript -e "tell application id \"$BUNDLE_ID\" to quit" >/dev/null 2>&1 &
      OSA=$!
      i=0; while pgrep -x Sukrara >/dev/null && [ $i -lt 10 ]; do sleep 1; i=$((i + 1)); done
      kill $OSA 2>/dev/null || true
      if pkill -x Sukrara; then sleep 2; fi
      pkill -9 -x Sukrara || true
    fi
  fi
  # The second copy goes first (its widget, its registration, its files), then the new bundle takes DEST's place.
  for o in $OTHERS; do
    if [ -d "$o/Contents/PlugIns/SukraraWidget.appex" ]; then run pluginkit -r "$o/Contents/PlugIns/SukraraWidget.appex"; fi
    run "$LSREGISTER" -u "$o"
    run rm -rf "$o"
  done
  run rm -rf "$DEST"
  run mv "$STAGE" "$DEST"
  run "$LSREGISTER" -f "$DEST"
  run swift tools/release/url-handler.swift "$DEST" || echo "WARN: sukrara: links may still open another app (see above)."
  run pkill -f SukraraWidget.appex || true
  # Unregister every other registered copy (worktree / test builds, a mounted dmg): Spotlight, Launchpad and links
  # would find them, and chronod may run their stale extension.
  "$LSREGISTER" -dump 2>/dev/null | awk -v id=$BUNDLE_ID '/^path:/ { p = $0 } /^identifier:/ { if ($2 == id) print p }' |
    sed -e 's/^path: *//' -e 's/ (0x[0-9a-f]*)$//' | sort -u | grep -vxF "$DEST" |
    while IFS= read -r p; do run "$LSREGISTER" -u "$p"; done
  pluginkit -mAv 2>/dev/null | grep "$BUNDLE_ID.widget" | awk -F'\t' '{print $NF}' | grep -v "^$DEST/" |
    while IFS= read -r p; do run pluginkit -r "$p"; done
  run open "$DEST"
  if [ $DRY = 1 ]; then GOT="$VERSION (dry run)"; else
    trap - EXIT
    GOT=$(plist "$DEST" CFBundleShortVersionString)
    [ "$GOT" = "$VERSION" ] || die "$DEST reports version '$GOT', expected '$VERSION'."
  fi
fi

# 3. Record and show what the device now runs.
LINE="$(git rev-parse HEAD) $(date '+%Y-%m-%dT%H:%M') $GOT $(git rev-parse --abbrev-ref HEAD)"
if [ $DRY = 1 ]; then echo "would record in $RECORD: $LINE"; else echo "$LINE" > "$RECORD"; fi
echo "$TARGET: Sukrara $GOT from $HEAD ($(git log -1 --format=%s HEAD))"
