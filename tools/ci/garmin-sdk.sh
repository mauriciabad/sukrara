#!/usr/bin/env bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# The Connect IQ SDK and the Garmin apps' device files on a CI runner (checks.yml `garmin`, Linux x86_64), pinned by
# tools/ci/garmin.lock. Writes `key=value` lines to $GITHUB_OUTPUT (and stdout).
#
#   tools/ci/garmin-sdk.sh plan  [manifest…]  project=true|false, sdk=<zip name>, devices=<ids>, devices-key=<hash>:
#                                             what the cache steps restore (every manifest's products);
#                                             devices-json=["id",…]: the FIRST manifest's (the face's: garmin-all's
#                                             matrix); no network
#   tools/ci/garmin-sdk.sh setup [manifest…]  ready=true|false: the SDK in ~/.Garmin/ConnectIQ/Sdks (bin on
#                                             $GITHUB_PATH), every manifest device (+ its simulator fonts) in
#                                             ~/.Garmin/ConnectIQ/Devices, Fonts; downloaded=true when it signed in;
#                                             $GITHUB_ENV gets CIQ_SDK (the SDK) and CIQ_KEY: a developer key made for
#                                             this run only (apps/garmin/build.sh signs with it; never the store key)
#
# The SDK is public. The device files need a Garmin sign-in, used only when the cache doesn't have them, with the CI
# account's secrets (never the owner's own Garmin account; docs/DEVELOPMENT.md › Garmin face on CI):
#   GARMIN_CI_USERNAME + GARMIN_CI_PASSWORD   the dedicated CI account (no watch, no data, no two-step verification)
#   GARMIN_CI_TOKEN                           or: base64 of the token.json `connect-iq-sdk-manager login` wrote after a
#                                             browser sign-in of that account (when Garmin refuses scripted sign-ins)
# Without both the files and a credential (now, forks, pull requests from outside) it says why, writes ready=false and
# exits 0: the job skips its steps and `gate` passes. The sign-in token is deleted before the script returns; it is
# never cached, printed or left for the steps that run the project's code.
# The manifests default to every app's: apps/garmin/{aura,app,field}/manifest.xml, those that exist (none: no Garmin
# project, project=false / ready=false).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=garmin.lock source-path=SCRIPTDIR
. "$ROOT/tools/ci/garmin.lock"
MODE="${1:-}"
[ $# -gt 0 ] && shift
MANIFESTS=()
if [ $# -gt 0 ]; then
  for m in "$@"; do [ -f "$m" ] && MANIFESTS+=("$m"); done
else
  for a in aura app field; do [ -f "$ROOT/apps/garmin/$a/manifest.xml" ] && MANIFESTS+=("$ROOT/apps/garmin/$a/manifest.xml"); done
fi
CIQ="$HOME/.Garmin/ConnectIQ"
OUT="${GITHUB_OUTPUT:-/dev/null}"

out() { echo "$1=$2" | tee -a "$OUT"; }
note() { if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::notice title=Garmin::$1"; else echo "garmin: $1"; fi; }
fail() { if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error title=Garmin::$1"; else echo "garmin: $1" >&2; fi; exit 1; }

# Products (<iq:product id="…"/>) of the manifests given (default: all), sorted, one per line.
products() { sed -n 's/.*<iq:product[^>]*id="\([^"]*\)".*/\1/p' "$@" | sort -u; }
devices() { products "${MANIFESTS[@]}"; }

missing() {
  local d
  for d in $(devices); do [ -f "$CIQ/Devices/$d/compiler.json" ] || echo "$d"; done
}

plan() {
  if [ ${#MANIFESTS[@]} = 0 ]; then
    out project false
    return
  fi
  local ids m
  for m in "${MANIFESTS[@]}"; do
    [ -n "$(products "$m")" ] || fail "$m names no <iq:product>"
  done
  ids="$(devices)"
  out project true
  out sdk "$CIQ_SDK_LINUX_ZIP"
  out devices "$(echo "$ids" | tr '\n' ' ' | sed 's/ $//')"
  out devices-json "[$(products "${MANIFESTS[0]}" | sed 's/.*/"&"/' | tr '\n' ',' | sed 's/,$//')]"
  # the device files don't depend on the SDK; a new device (or a new list) is a new cache entry
  out devices-key "$( (echo "$ids"; echo "cli $CIQ_CLI_VERSION") | sha256sum | cut -c1-16)"
}

sdk() {
  local dir="$CIQ/Sdks/${CIQ_SDK_LINUX_ZIP%.zip}" zip
  if [ ! -x "$dir/bin/monkeyc" ]; then
    zip="$(mktemp -d)/$CIQ_SDK_LINUX_ZIP"
    echo "Connect IQ SDK $CIQ_SDK_VERSION: downloading $CIQ_SDK_LINUX_ZIP"
    curl -fsSL --retry 3 -o "$zip" "https://developer.garmin.com/downloads/connect-iq/sdks/$CIQ_SDK_LINUX_ZIP" ||
      fail "the SDK download failed ($CIQ_SDK_LINUX_ZIP)"
    echo "$CIQ_SDK_LINUX_SHA256  $zip" | sha256sum -c --quiet - || fail "the SDK's sha256 isn't tools/ci/garmin.lock's"
    mkdir -p "$dir"
    unzip -q "$zip" -d "$dir"
    rm -f "$zip"
  fi
  echo "$dir/" > "$CIQ/current-sdk.cfg"
  echo "$dir/bin" >> "${GITHUB_PATH:-/dev/null}"
  echo "CIQ_SDK=$dir" >> "${GITHUB_ENV:-/dev/null}"
  echo "Connect IQ SDK $(cat "$dir/bin/version.txt" 2>/dev/null || echo "$CIQ_SDK_VERSION") at $dir"
}

# Signs in (the CI account), downloads the manifest's devices and their fonts, deletes the token whatever happens.
TMPD=""
forget() { rm -f "$CIQ/token.json"; [ -z "$TMPD" ] || rm -rf "$TMPD"; }
trap forget EXIT

download() {
  local tmp cli
  TMPD="$(mktemp -d)"
  tmp="$TMPD"
  curl -fsSL --retry 3 -o "$tmp/cli.tgz" "https://github.com/lindell/connect-iq-sdk-manager-cli/releases/download/v$CIQ_CLI_VERSION/connect-iq-sdk-manager-cli_${CIQ_CLI_VERSION}_Linux_x86_64.tar.gz" ||
    fail "the SDK Manager CLI download failed (v$CIQ_CLI_VERSION)"
  echo "$CIQ_CLI_LINUX_SHA256  $tmp/cli.tgz" | sha256sum -c --quiet - || fail "the SDK Manager CLI's sha256 isn't tools/ci/garmin.lock's"
  tar -xzf "$tmp/cli.tgz" -C "$tmp" connect-iq-sdk-manager
  cli="$tmp/connect-iq-sdk-manager"
  "$cli" agreement accept --agreement-hash="$CIQ_AGREEMENT_HASH" >/dev/null ||
    fail "the SDK Manager CLI refused the Connect IQ SDK agreement (its message is above): if Garmin changed the agreement, read it (connect-iq-sdk-manager agreement view) and put its hash in tools/ci/garmin.lock"
  mkdir -p "$CIQ"
  if [ -n "${GARMIN_CI_TOKEN:-}" ]; then
    (umask 077; echo "$GARMIN_CI_TOKEN" | base64 -d > "$CIQ/token.json") || fail "GARMIN_CI_TOKEN isn't base64 of a token.json"
  else
    # the CLI reads GARMIN_USERNAME / GARMIN_PASSWORD; only this command gets them
    GARMIN_USERNAME="$GARMIN_CI_USERNAME" GARMIN_PASSWORD="$GARMIN_CI_PASSWORD" "$cli" login >/dev/null ||
      fail "the CI account's Garmin sign-in failed (wrong GARMIN_CI_USERNAME / GARMIN_CI_PASSWORD, two-step verification on, or Garmin refuses scripted sign-ins: use GARMIN_CI_TOKEN; docs/DEVELOPMENT.md › Garmin face on CI)"
  fi
  local m
  for m in "${MANIFESTS[@]}"; do
    "$cli" device download --manifest="$m" --include-fonts ||
      fail "the device download failed (an expired GARMIN_CI_TOKEN: sign in again and replace it; docs/DEVELOPMENT.md › Garmin face on CI)"
  done
  forget
}

setup() {
  if [ ${#MANIFESTS[@]} = 0 ]; then
    note "no Garmin project (no manifest): nothing to build"
    out ready false
    return
  fi
  local need creds=""
  need="$(missing | tr '\n' ' ' | sed 's/ $//')"
  if [ -n "${GARMIN_CI_TOKEN:-}" ] || { [ -n "${GARMIN_CI_USERNAME:-}" ] && [ -n "${GARMIN_CI_PASSWORD:-}" ]; }; then
    creds=yes
  fi
  if [ -n "$need" ] && [ -z "$creds" ]; then
    note "skipped: the device files ($need) need a Garmin sign-in and this run has no CI account (GARMIN_CI_USERNAME + GARMIN_CI_PASSWORD, or GARMIN_CI_TOKEN; forks and outside pull requests never do). Setup: docs/DEVELOPMENT.md › Garmin face on CI"
    out ready false
    return
  fi
  sdk
  if [ -n "$need" ]; then
    echo "device files: $need (signing in as the CI account)"
    download
    need="$(missing | tr '\n' ' ' | sed 's/ $//')"
    [ -z "$need" ] || fail "downloaded, but still missing: $need"
    out downloaded true
  else
    echo "device files: from the cache ($(devices | tr '\n' ' '))"
    out downloaded false
  fi
  # Every build is signed; CI's builds only run in the simulator, so a key made here and thrown away with the runner
  # does (the store key never leaves the release Mac: docs/20-publishing.md › Garmin).
  local key="${RUNNER_TEMP:-$(mktemp -d)}/ci-developer-key.der"
  openssl genrsa 4096 2>/dev/null | openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -out "$key"
  echo "CIQ_KEY=$key" >> "${GITHUB_ENV:-/dev/null}"
  out ready true
}

case "$MODE" in
  plan) plan ;;
  setup) setup ;;
  *) sed -n '5,12p' "$0" >&2; exit 2 ;;
esac
