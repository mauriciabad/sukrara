#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# What a build of this checkout is, on one line: tools/release/build-info.sh android | mac (docs/DEVELOPMENT.md ›
# Development and release builds). Both apps carry it (Android: the manifest's app.mauri.sukrara.build meta-data; Mac:
# Info.plist SukraraBuild) and show it wherever they show their version; verify-apk.sh / verify-app.sh check it. The
# app's release commit is the last commit that changed its version file (apps/android/VERSION, apps/macos/VERSION).
#   release                                   only with SUKRARA_RELEASE_BUILD=1 (the release scripts set it), and then
#                                             the source must be the app's release commit's (website/public/appcast.xml
#                                             may differ, release.py commits it after the gate), else this refuses
#                                             (exit 1)
#   development <commits> <commit> <date>     every other build: the commits since the app's release commit (those
#                                             that change more than website/public/appcast.xml; "?" in a shallow
#                                             clone), HEAD's short hash and its commit date (yyyy-mm-dd); "?" for what
#                                             git can't tell
# So no build can say it is a release by accident: install.sh, CI and every local build are development builds.
set -eu
case "${1:-}" in
  android) file=apps/android/VERSION ;;
  mac) file=apps/macos/VERSION ;;
  *) echo "usage: tools/release/build-info.sh android | mac" >&2; exit 2 ;;
esac
release=${SUKRARA_RELEASE_BUILD:-}
refuse() { echo "build-info.sh: SUKRARA_RELEASE_BUILD=1 but $*: a release is built from the release commit" >&2; exit 1; }
if ! root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null); then
  [ "$release" != 1 ] || refuse "this isn't a git checkout"
  echo "development ? ? ?"; exit 0
fi
cd "$root"
base=$(git log -1 --format=%H -- "$file" 2>/dev/null || true)
if [ "$release" = 1 ]; then
  [ -n "$base" ] || refuse "no commit changed $file"
  [ "$(git rev-parse --is-shallow-repository)" = false ] || refuse "this clone is shallow"
  git diff --quiet "$base" -- . ':(exclude)website/public/appcast.xml' ||
    refuse "the source differs from the release commit $(git rev-parse --short=8 "$base") ($(git diff --name-only "$base" -- . ':(exclude)website/public/appcast.xml' | head -3 | tr '\n' ' '))"
  untracked=$(git ls-files --others --exclude-standard | head -3 | tr '\n' ' ')
  [ -z "$untracked" ] || refuse "files git doesn't track are in the tree ($untracked)"
  echo release; exit 0
fi
commits="?"
if [ -n "$base" ] && [ "$(git rev-parse --is-shallow-repository)" = false ]; then
  commits=$(git rev-list --count "$base"..HEAD -- . ':(exclude)website/public/appcast.xml')
fi
echo "development $commits $(git rev-parse --short=8 HEAD) $(git log -1 --format=%cs HEAD)"
