#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# The checks' own tests: stage throwaway files in a TEMPORARY index (the real index and work tree are never touched)
# and assert what tools/check/check.py --staged says about them; the coupling rules (parity, features, trailers,
# merges) run over throwaway commits made with commit-tree (objects only: no branch, ref or HEAD moves).
# Run: tools/check/run.sh --selftest (CI rules job).
set -u
root="$(git rev-parse --show-toplevel)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# commit-tree needs an identity; CI's checkout has none
export GIT_AUTHOR_NAME=selftest GIT_AUTHOR_EMAIL=selftest@invalid GIT_COMMITTER_NAME=selftest GIT_COMMITTER_EMAIL=selftest@invalid
# Each case starts from a copy of the real index (what you are about to commit; in CI the checkout), and only the
# FAIL lines the case adds over that baseline count.
real_index="$(git -C "$root" rev-parse --path-format=absolute --git-path index)"
export GIT_INDEX_FILE="$tmp/index"
fails=0
cp "$real_index" "$GIT_INDEX_FILE"
python3 -I "$root/tools/check/check.py" --staged --only static 2>&1 | grep '^FAIL' > "$tmp/baseline"

# judge <name> <expect: fail|pass> <pattern> <output>: fail = a line "FAIL [<pattern>]"; pass = no such line (no FAIL
# at all when the pattern is empty)
judge() {
  name="$1" expect="$2" pattern="$3" out="$4"
  if [ "$expect" = fail ]; then
    if printf '%s' "$out" | grep -q "^FAIL \[$pattern\]"; then echo "ok   $name"; else echo "FAIL $name (expected FAIL [$pattern])"; printf '%s\n' "$out"; fails=$((fails + 1)); fi
  else
    if printf '%s' "$out" | grep -q "^FAIL${pattern:+ \[$pattern\]}"; then echo "FAIL $name (expected no failure)"; printf '%s\n' "$out"; fails=$((fails + 1)); else echo "ok   $name"; fi
  fi
}

# The static checks over the temporary index: the FAIL lines the case adds over the baseline.
staged() { python3 -I "$root/tools/check/check.py" --staged --only static 2>&1 | grep '^FAIL' | grep -vxF -f "$tmp/baseline"; }

# case <name> <expect: fail|pass> <grep pattern for fail> <path> <content>
case_() {
  name="$1" expect="$2" pattern="$3" path="$4" content="$5"
  cp "$real_index" "$GIT_INDEX_FILE"
  blob="$(printf '%s\n' "$content" | git -C "$root" hash-object -w --stdin)"
  git -C "$root" update-index --add --cacheinfo 100644 "$blob" "$path"
  judge "$name" "$expect" "$pattern" "$(staged)"
}

# gone <name> <expect> <pattern> <path>...: the index without these files
gone() {
  name="$1" expect="$2" pattern="$3"
  shift 3
  cp "$real_index" "$GIT_INDEX_FILE"
  git -C "$root" update-index --force-remove "$@"
  judge "$name" "$expect" "$pattern" "$(staged)"
}

# commit <parent> <message> <path> <content>: a commit object on <parent> that adds one file (nothing points at it)
commit() {
  git -C "$root" read-tree "$1"
  blob="$(printf '%s\n' "$4" | git -C "$root" hash-object -w --stdin)"
  git -C "$root" update-index --add --cacheinfo 100644 "$blob" "$3"
  git -C "$root" commit-tree "$(git -C "$root" write-tree)" -p "$1" -m "$2"
}

# merge <first parent> <second parent> <message>: a merge commit object with the second parent's tree
merge() {
  git -C "$root" read-tree "$2"
  git -C "$root" commit-tree "$(git -C "$root" write-tree)" -p "$1" -p "$2" -m "$3"
}

# coupling <name> <expect> <pattern> <check.py mode args…>: the coupling rules (parity, features) as a range / merge
coupling() {
  name="$1" expect="$2" pattern="$3"
  shift 3
  judge "$name" "$expect" "$pattern" "$(python3 -I "$root/tools/check/check.py" --only coupling "$@" 2>&1 | grep '^FAIL')"
}

HDR='// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later'

case_ "Kotlin without header fails" fail header apps/android/x/src/main/kotlin/A.kt 'fun a() = 1'
case_ "an XML resource without header fails" fail header apps/android/x/src/main/res/drawable/x.xml '<vector/>'
case_ "a workflow without header fails" fail header .github/workflows/x.yml 'name: x'
case_ "a lint baseline (lint writes it) passes" pass "" apps/android/x/lint-baseline.xml '<issues/>'
case_ "hardcoded UI text fails" fail strings apps/android/x/src/main/kotlin/A.kt "$HDR
@Composable fun A() { Text(\"hi there\") }"
case_ "same under poc/ passes" pass "" poc/x/src/main/kotlin/A.kt '@Composable fun A() { Text("hi there") }'
case_ "a debug fake in src/main fails" fail debug apps/android/app/src/main/kotlin/app/mauri/sukrara/DebugFakes.kt "$HDR
object DebugFakes"
case_ "a debug fake in src/debug passes" pass "" apps/android/app/src/debug/kotlin/app/mauri/sukrara/DebugFakes.kt "$HDR
object DebugFakes"
case_ "src/main naming DebugReceiver fails" fail debug apps/android/app/src/main/kotlin/app/mauri/sukrara/App.kt "$HDR
val r = DebugReceiver()"
case_ "Mac debugsend outside #if DEBUG fails" fail debug apps/macos/Sources/SukraraApp/Launch.swift "$HDR
let flag = \"-debugsend\"
func debugsend() {}"
case_ "Mac debugsend inside #if DEBUG passes" pass "" apps/macos/Sources/SukraraApp/Launch.swift "$HDR
#if DEBUG
    func debugsend() {}
#else
    let x = 1
#endif"
ALL_DEBUG='`SYNTHETIC --ez on true --ei cycle 135`, `ALERTS --es kinds high`, `PEN_READ …`, `PEN_TAGS --ez blocked
true`, `PEN_JUGGLUCO --ez installed true`, `RELAYS --es urls off`; Mac `sukrara-snap card|windows-es <dir>`,
`SUKRARA_DEV=1 SUKRARA_FIXTURE=1`.'
case_ "the debug commands the code has pass" pass "" docs/DEVELOPMENT.md "Debug commands: $ALL_DEBUG"
case_ "a debug command the code doesn't have fails" fail debug-docs docs/DEVELOPMENT.md "Debug commands: $ALL_DEBUG
Also \`SENSOR_END …\`."
case_ "a debug extra the code doesn't read fails" fail debug-docs docs/DEVELOPMENT.md "Debug commands: $ALL_DEBUG
Also \`DebugReceiver --es cmd sensorEnd\`."
case_ "a debug extra of the wrong type fails" fail debug-docs docs/QA.md "Debug commands: $ALL_DEBUG
Also \`SYNTHETIC --es cycle 135\`."
case_ "a doc without every debug command fails" fail debug-docs docs/FEATURES.md "Debug commands: \`SYNTHETIC --ez on true\`."
case_ "a sukrara-snap surface it doesn't have fails" fail debug-docs docs/DEVELOPMENT.md "Debug commands: $ALL_DEBUG
Also \`sukrara-snap widgets <dir>\`."
case_ "a hand-written DataStore key fails" fail settings apps/android/core/data/src/main/kotlin/app/mauri/sukrara/Store.kt "$HDR
val k = booleanPreferencesKey(\"graphHours\")"
case_ "a hand-written defaults key fails" fail settings apps/macos/Sources/SukraraCore/Store.swift "$HDR
let v = UserDefaults.standard.integer(forKey: \"graphHours\")"
case_ "a Stepper in a Mac settings pane fails" fail settings apps/macos/Sources/SukraraApp/Windows/Settings/X.swift "$HDR
let s = Stepper(\"x\", value: \$v, in: 1...9)"
case_ "minutes written by hand fail" fail durations apps/android/x/src/main/kotlin/app/mauri/sukrara/D.kt "$HDR
fun d(m: Int) = \"\$m min\""
case_ "a cleartext URL in app Kotlin fails" fail cleartext apps/android/integrations/src/main/kotlin/app/mauri/sukrara/N.kt "$HDR
val u = \"http://example.org\""
case_ "a cleartext URL in Mac Swift fails" fail cleartext apps/macos/Sources/SukraraShare/N.swift "$HDR
let u = URL(string: \"http://example.org\")"
case_ "a new URLSession in Mac Swift fails" fail cleartext apps/macos/Sources/SukraraApp/N.swift "$HDR
let s = URLSession(configuration: .ephemeral)"
case_ "an image outside the allowed folders fails" fail images apps/android/x/shot.png 'not really a png'
case_ "an image in the frozen PoC passes" pass "" poc/spikes/brand/assets/x/icon.png 'not really a png'
case_ "an nsec in app code fails" fail secrets apps/android/x/src/main/kotlin/K.kt "$HDR
val k = \"nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq\""

case_ "a changed committed Room schema fails" fail room-schema \
  apps/android/core/data/schemas/app.mauri.sukrara.core.data.db.SukraraDatabase/1.json '{"formatVersion": 1}'
case_ "a new Room schema version passes" pass "" \
  apps/android/core/data/schemas/app.mauri.sukrara.core.data.db.SukraraDatabase/99.json '{"formatVersion": 1}'

case_ "an unlisted beta library fails" fail prerelease apps/android/gradle/libs.versions.toml '[versions]
foo = "2.0.0-beta03"'
case_ "a listed pre-release that is stable now fails" fail prerelease apps/android/gradle/libs.versions.toml '[versions]
material3 = "1.5.0"'
if command -v swift >/dev/null 2>&1; then  # the format check needs a Swift toolchain (NOTE without one)
  case_ "unformatted Mac Swift fails" fail format apps/macos/Sources/SukraraCore/F.swift "$HDR
let  x=1"
  case_ "formatted Mac Swift passes" pass "" apps/macos/Sources/SukraraCore/F.swift "$HDR
let x = 1"
fi

# A file a registry or a check names that doesn't exist: a NOTE while tools/check/strict is absent, then a failure.
durations=apps/android/core/model/src/main/kotlin/app/mauri/sukrara/core/model/Durations.kt
gone "a file the checks name, gone, fails (strict)" fail paths "$durations"
gone "a file allow.json names, gone, fails (strict)" fail cleartext \
  apps/android/sensor/src/main/kotlin/app/mauri/sukrara/sensor/juggluco/JugglucoSource.kt
gone "a surface's file, gone, fails (strict)" fail card-surface \
  apps/android/feature/treatments/src/main/kotlin/app/mauri/sukrara/feature/treatments/TreatmentTileService.kt
cp "$real_index" "$GIT_INDEX_FILE"
git -C "$root" update-index --force-remove tools/check/strict "$durations"
lax="$(python3 -I "$root/tools/check/check.py" --staged --only static 2>&1)"
judge "the same without tools/check/strict passes" pass paths "$(printf '%s\n' "$lax" | grep '^FAIL')"
if printf '%s' "$lax" | grep -q '^NOTE \[pending\] 1 file'; then echo "ok   ... and is counted as pending"
else echo "FAIL a missing named file without tools/check/strict isn't counted as pending"; printf '%s\n' "$lax" | grep pending; fails=$((fails + 1)); fi

# Coupling: a one-sided change of a 'fail' parity group (graph: the Android renderer without the Mac one), as a branch
# range and as a merge, with and without trailers. All of it is commit objects on HEAD; no ref moves.
base="$(git -C "$root" rev-parse HEAD)"
graph=apps/android/core/graph/src/main/kotlin/app/mauri/sukrara/graph/Selftest.kt
one="$(commit "$base" "Android renderer only

Features-skip: self-test" "$graph" "$HDR")"
skip="$(commit "$base" "Android renderer only

Parity-skip: graph: the Mac port is its own round
Features-skip: self-test" "$graph" "$HDR")"
other="$(commit "$base" "Android renderer only

Parity-skip: alerts: a different group
Features-skip: self-test" "$graph" "$HDR")"
nofeat="$(commit "$base" "Android renderer only

Parity-skip: graph: the Mac port is its own round" "$graph" "$HDR")"
coupling "a one-sided branch fails parity" fail parity:graph --range "$base..$one"
coupling "Parity-skip naming the group passes" pass parity:graph --range "$base..$skip"
coupling "Parity-skip naming another group still fails" fail parity:graph --range "$base..$other"
coupling "app code without FEATURES.md or Features-skip fails" fail features --range "$base..$nofeat"
coupling "a Features-skip trailer passes features" pass features --range "$base..$one"
coupling "a merge bringing a one-sided change fails" fail parity:graph --merge-of "$(merge "$base" "$one" "Merge one-sided")"
coupling "a merge whose branch says Parity-skip passes" pass parity:graph --merge-of "$(merge "$base" "$skip" "Merge skipped")"
coupling "a Parity-skip on the merge commit itself passes" pass parity:graph --merge-of "$(merge "$base" "$one" "Merge one-sided

Parity-skip: graph: said once for the whole branch")"

if [ "$fails" -gt 0 ]; then echo "$fails self-test case(s) failed"; exit 1; fi
echo "all check self-tests passed"
