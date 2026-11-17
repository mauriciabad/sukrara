#!/usr/bin/env bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Tests of tools/ci/garmin-sdk.sh (CI's rules job): what it decides without credentials, offline. A fake HOME and a
# `curl` that fails prove that no case below touches the network or needs a secret.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin"
printf '#!/bin/sh\necho "curl $*" >> "%s/net.log"; exit 99\n' "$T" > "$T/bin/curl"
chmod +x "$T/bin/curl"
fails=0

run() { # run <name> <mode> <manifest> [env…] → $T/<name>.{out,log}, the exit code in $T/<name>.rc
  local name="$1" mode="$2" manifest="$3"
  shift 3
  : > "$T/$name.out"
  env -u GARMIN_CI_USERNAME -u GARMIN_CI_PASSWORD -u GARMIN_CI_TOKEN -u GITHUB_ACTIONS HOME="$T/home-$name" \
    PATH="$T/bin:$PATH" GITHUB_OUTPUT="$T/$name.out" GITHUB_PATH="$T/$name.path" GITHUB_ENV="$T/$name.env" RUNNER_TEMP="$T" "$@" \
    "$ROOT/tools/ci/garmin-sdk.sh" "$mode" "$manifest" > "$T/$name.log" 2>&1
  echo $? > "$T/$name.rc"
}
expect() { # expect <name> <rc> <out line>…
  local name="$1" rc="$2"
  shift 2
  if [ "$(cat "$T/$name.rc")" != "$rc" ]; then
    echo "FAIL $name: exit $(cat "$T/$name.rc"), expected $rc"; sed 's/^/  /' "$T/$name.log"; fails=$((fails + 1)); return
  fi
  for line in "$@"; do
    grep -qxF "$line" "$T/$name.out" || { echo "FAIL $name: no '$line' in GITHUB_OUTPUT"; sed 's/^/  /' "$T/$name.out"; fails=$((fails + 1)); return; }
  done
  echo "ok   $name"
}
manifest() { # a manifest with these products
  local f="$T/manifest-$1.xml"
  shift
  { echo '<iq:manifest xmlns:iq="http://www.garmin.com/xml/connectiq" version="3"><iq:application id="x" type="watchface"><iq:products>'
    for d in "$@"; do echo "  <iq:product id=\"$d\"/>"; done
    echo '</iq:products></iq:application></iq:manifest>'; } > "$f"
  echo "$f"
}

# No Garmin project yet: nothing to build, gate passes
run no-project plan "$T/none.xml"
expect no-project 0 "project=false"
run no-project-setup setup "$T/none.xml"
expect no-project-setup 0 "ready=false"

# The plan: the products sorted, a key that changes with the list only
M1="$(manifest one descentmk343mm fenix847mm)"
M2="$(manifest two fenix847mm descentmk343mm)"
M3="$(manifest three descentmk343mm)"
run plan1 plan "$M1"; run plan2 plan "$M2"; run plan3 plan "$M3"
expect plan1 0 "project=true" "devices=descentmk343mm fenix847mm" 'devices-json=["descentmk343mm","fenix847mm"]'
expect plan3 0 'devices-json=["descentmk343mm"]'
k1="$(grep '^devices-key=' "$T/plan1.out")"; k2="$(grep '^devices-key=' "$T/plan2.out")"; k3="$(grep '^devices-key=' "$T/plan3.out")"
if [ -n "$k1" ] && [ "$k1" = "$k2" ] && [ "$k1" != "$k3" ]; then echo "ok   devices-key"; else echo "FAIL devices-key: $k1 / $k2 / $k3"; fails=$((fails + 1)); fi

# No credentials, no cached device files (now, forks, outside pull requests): skip cleanly, say why, no download
run no-creds setup "$M3" GITHUB_ACTIONS=true
expect no-creds 0 "ready=false"
grep -q '^::notice title=Garmin::skipped: the device files (descentmk343mm) need a Garmin sign-in' "$T/no-creds.log" ||
  { echo "FAIL no-creds: no notice"; cat "$T/no-creds.log"; fails=$((fails + 1)); }
# half a password is no credential
run half-creds setup "$M3" GARMIN_CI_USERNAME=ci@example.invalid
expect half-creds 0 "ready=false"

# The cache has everything (a fork's run restoring the base branch's cache too): ready without a credential or network
H="$T/home-cached/.Garmin/ConnectIQ"
mkdir -p "$H/Devices/descentmk343mm" "$H/Sdks/connectiq-sdk-lin-x/bin"
touch "$H/Devices/descentmk343mm/compiler.json"
# shellcheck disable=SC1091
SDKDIR="$H/Sdks/$(. "$ROOT/tools/ci/garmin.lock"; echo "${CIQ_SDK_LINUX_ZIP%.zip}")"
mkdir -p "$SDKDIR/bin" && printf '#!/bin/sh\n' > "$SDKDIR/bin/monkeyc" && chmod +x "$SDKDIR/bin/monkeyc"
run cached setup "$M3"
expect cached 0 "ready=true" "downloaded=false"
grep -qxF "$SDKDIR/bin" "$T/cached.path" || { echo "FAIL cached: the SDK's bin isn't on GITHUB_PATH"; fails=$((fails + 1)); }
# apps/garmin/build.sh's inputs: the SDK and a developer key made for this run (a 4096-bit RSA key in DER)
grep -qxF "CIQ_SDK=$SDKDIR" "$T/cached.env" || { echo "FAIL cached: no CIQ_SDK in GITHUB_ENV"; fails=$((fails + 1)); }
if ! grep -qxF "CIQ_KEY=$T/ci-developer-key.der" "$T/cached.env" ||
  ! openssl pkey -inform DER -in "$T/ci-developer-key.der" -noout -text 2>/dev/null | grep -q '4096 bit'; then
  echo "FAIL cached: no 4096-bit CIQ_KEY"; fails=$((fails + 1))
fi

# A product added to the manifest that the cache lacks: no credential → a skip naming only that one, no download
mkdir -p "$T/home-partly/.Garmin/ConnectIQ/Devices/descentmk343mm"
touch "$T/home-partly/.Garmin/ConnectIQ/Devices/descentmk343mm/compiler.json"
run partly setup "$M1" GITHUB_ACTIONS=true
expect partly 0 "ready=false"
grep -q '^::notice title=Garmin::skipped: the device files (fenix847mm) need a Garmin sign-in' "$T/partly.log" ||
  { echo "FAIL partly: the notice doesn't name only the missing device"; cat "$T/partly.log"; fails=$((fails + 1)); }

[ ! -e "$T/net.log" ] || { echo "FAIL: a case without a credential used the network:"; cat "$T/net.log"; fails=$((fails + 1)); }

# With a credential and a cache miss it does download (and a failed download fails the job, never a silent skip)
run creds-miss setup "$M3" GARMIN_CI_TOKEN=e30K
expect creds-miss 1
grep -q 'connect-iq-sdk-manager-cli\|developer.garmin.com' "$T/net.log" ||
  { echo "FAIL creds-miss: no download attempted"; fails=$((fails + 1)); }
[ ! -e "$T/home-creds-miss/.Garmin/ConnectIQ/token.json" ] || { echo "FAIL creds-miss: token.json left behind"; fails=$((fails + 1)); }

if [ "$fails" -gt 0 ]; then echo "$fails failed"; exit 1; fi
echo "garmin-sdk.sh: all ok"
