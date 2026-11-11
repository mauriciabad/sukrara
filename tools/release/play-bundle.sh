#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the Google Play app bundle (the play flavor, docs/20-publishing.md › Flavors) and checks it before an upload:
#   tools/release/play-bundle.sh              signed with THE release key (Play's upload key and app-signing key)
#   tools/release/play-bundle.sh --unsigned   CI: no key and no Juggluco checkout; every other check
# The bundle: signer = tools/release/android-cert.sha256 (keytool on the .aab), base module only, arm64 only, the
# Libre 3 core, the R8 mapping inside for Play's crash reports, the System UI module's META-INF/xposed (the Play build
# has the root features: the owner's decision of 2026-11-11, #142). With bundletool ($BUNDLETOOL = the bundletool-all
# jar, or `bundletool` on PATH): `validate`, then its universal APK, signed with the release key, goes through
# verify-apk.sh --play (permissions = allow.json with play_permissions, USE_EXACT_ALARM included; no update-check code;
# the LSPosed hooks keep intercept; not debuggable; sounds; R8 mapping). Without bundletool the same checks run on the
# play APK Gradle builds alongside, from the same R8 output.
# Signed: copies the bundle to apps/android/app/build/dist/Sukrara-<version>-play.aab and archives its mapping in
# ~/.android/sukrara-mappings/<version>-play-mapping.txt. Uploading is a separate step (the owner, or the service
# account: docs/20-publishing.md). Refuses unless tools/check/strict exists. Prints the bundle's path on the last line.
set -eu
. "$(dirname "$0")/lib.sh"
root="$(repo_root)"
here="$root/tools/release"
unsigned=0
case "${1:-}" in
  --unsigned) unsigned=1 ;;
  "") ;;
  *) echo "usage: tools/release/play-bundle.sh [--unsigned]" >&2; exit 2 ;;
esac
if [ $unsigned = 0 ] && [ ! -e "$root/tools/check/strict" ]; then
  echo "refusing: tools/check/strict is missing (every registry file must exist before a release)" >&2; exit 2
fi
set -- $(version_info)
version="$1"
fail=0
bad() { echo "FAIL $*"; fail=1; }
ok() { echo "ok   $*"; }

JAVA_HOME="$(dirname "$(dirname "$(jdk_tool java)")")"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export JAVA_HOME ANDROID_HOME
java="$JAVA_HOME/bin/java"

out="$root/apps/android/app/build/outputs"
aab="$out/bundle/playRelease/app-play-release.aab"
mapping="$out/mapping/playRelease/mapping.txt"
apk_dir="$out/apk/play/release"
rm -f "$aab" "$apk_dir"/*.apk   # a failed build must not leave an old bundle to upload
# the APK only costs its packaging (one R8 run feeds both); it is what gets checked when there is no bundletool
tasks=":app:bundlePlayRelease :app:assemblePlayRelease"
if [ $unsigned = 1 ]; then
  # CI: no release key and no Libre 3 core (a separate checkout); everything else is built as for the upload
  (cd "$root/apps/android" && ./gradlew --no-daemon --console=plain $tasks \
    -x verifyReleaseSigning -x verifyJuggluco -x verifyPlayReleaseBundleNativeCore -x verifyPlayReleaseApkNativeCore)
else
  SUKRARA_KEYSTORE_PASSWORD="$(release_password)" || { echo "no release key password (Keychain item sukrara-android-release)" >&2; exit 2; }
  export SUKRARA_KEYSTORE_PASSWORD
  # --no-daemon: the key's password must not stay in a long-lived Gradle daemon's environment.
  (cd "$root/apps/android" && ./gradlew --no-daemon --console=plain $tasks)
  unset SUKRARA_KEYSTORE_PASSWORD
fi
[ -f "$aab" ] || { echo "FAIL no bundle at $aab" >&2; exit 1; }

# bundletool: $BUNDLETOOL (bundletool-all-<version>.jar from github.com/google/bundletool/releases) or on PATH (brew
# install bundletool). The jar AGP keeps in the Gradle cache isn't runnable on its own (no dependencies inside).
bundletool=""
if [ -n "${BUNDLETOOL:-}" ] && [ -f "$BUNDLETOOL" ]; then bundletool="$java -jar $BUNDLETOOL"
elif command -v bundletool >/dev/null 2>&1; then bundletool="bundletool"; fi

# The bundle itself.
entries="$(unzip -Z1 "$aab")"
has() { printf '%s\n' "$entries" | grep -Eq "$1"; }
for e in BundleConfig.pb base/manifest/AndroidManifest.xml base/dex/classes.dex base/resources.pb; do
  if printf '%s\n' "$entries" | grep -qxF "$e"; then ok "bundle has $e"; else bad "bundle lacks $e"; fi
done
if has '^BUNDLE-METADATA/com\.android\.tools\.build\.obfuscation/proguard\.map$'; then ok "R8 mapping inside (Play deobfuscates crashes)"
else bad "no R8 mapping in BUNDLE-METADATA"; fi
modules="$(printf '%s\n' "$entries" | grep -E '^[^/]+/manifest/AndroidManifest\.xml$' | cut -d/ -f1 | tr '\n' ' ')"
[ "$modules" = "base " ] && ok "one module (base)" || bad "modules: $modules (want base only)"
abis="$(printf '%s\n' "$entries" | sed -n 's|^base/lib/\([^/]*\)/.*|\1|p' | sort -u | tr '\n' ' ')"
[ "$abis" = "arm64-v8a " ] && ok "native code for arm64-v8a only" || bad "native ABIs: '${abis:-none}' (want arm64-v8a)"
if [ $unsigned = 1 ]; then echo "skip Libre 3 native core and signer (--unsigned)"
else
  has '^base/lib/arm64-v8a/libsukrara_libre3\.so$' && ok "Libre 3 native core present" || bad "base/lib/arm64-v8a/libsukrara_libre3.so missing"
  signer="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$aab" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'a-f' 'A-F' | sort -u)"
  if [ "$signer" = "$(cat "$here/android-cert.sha256")" ]; then ok "bundle signed by the release key (= Play's upload key)"
  else bad "bundle signer '${signer:-none}' is not the release key"; fi
fi
if has '^base/root/META-INF/xposed/java_init\.list$'; then ok "META-INF/xposed inside (the System UI module)"
else bad "no base/root/META-INF/xposed/java_init.list: LSPosed wouldn't find the System UI module"; fi

# What a phone gets: bundletool's universal APK, or Gradle's play APK.
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT INT TERM
verify_flags="--play"
[ $unsigned = 1 ] && verify_flags="--play --unsigned"
if [ -n "$bundletool" ]; then
  if $bundletool validate --bundle="$aab" >"$tmp/validate.txt" 2>&1; then ok "bundletool validate"
  else bad "bundletool validate: $(tail -3 "$tmp/validate.txt")"; fi
  sign=""
  if [ $unsigned = 0 ]; then
    # the password goes to bundletool in a private file, never on a command line (ps shows those)
    (umask 077 && release_password > "$tmp/pw")
    sign="--ks=$(release_keystore) --ks-key-alias=sukrara --ks-pass=file:$tmp/pw --key-pass=file:$tmp/pw"
  fi
  # shellcheck disable=SC2086
  if $bundletool build-apks --bundle="$aab" --output="$tmp/universal.apks" --mode=universal $sign >"$tmp/build-apks.txt" 2>&1; then
    rm -f "$tmp/pw"
    unzip -p "$tmp/universal.apks" universal.apk > "$tmp/universal.apk"
    echo "== verify-apk.sh $verify_flags (bundletool's universal APK)"
    # shellcheck disable=SC2086
    "$here/verify-apk.sh" $verify_flags "$tmp/universal.apk" "$mapping" || fail=1
  else
    rm -f "$tmp/pw"
    bad "bundletool build-apks: $(tail -3 "$tmp/build-apks.txt")"
  fi
else
  echo "note: no bundletool (\$BUNDLETOOL or PATH): checking the play APK Gradle built from the same R8 output instead"
  apk="$(ls "$apk_dir"/*.apk 2>/dev/null | head -1)"
  [ -n "$apk" ] || { echo "FAIL no play APK in $apk_dir" >&2; exit 1; }
  # shellcheck disable=SC2086
  "$here/verify-apk.sh" $verify_flags "$apk" "$mapping" || fail=1
fi
[ $fail = 0 ] || { echo "play-bundle: checks failed (see FAIL above); nothing copied" >&2; exit 1; }

if [ $unsigned = 1 ]; then echo "$aab"; exit 0; fi
dist="$root/apps/android/app/build/dist"
mkdir -p "$dist" "$HOME/.android/sukrara-mappings"
cp "$aab" "$dist/Sukrara-$version-play.aab"
cp "$mapping" "$HOME/.android/sukrara-mappings/$version-play-mapping.txt"
echo "mapping archived: $HOME/.android/sukrara-mappings/$version-play-mapping.txt"
echo "$dist/Sukrara-$version-play.aab"
