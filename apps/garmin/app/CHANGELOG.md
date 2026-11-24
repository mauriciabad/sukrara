# Sukrara watch app: changelog

Each release of Sukrara watch app (GitHub tag `garmin-app-v<version>`), newest first: `tools/release/release.py` writes each entry
from the [change files](../../../changes/README.md) that name it. Up to 0.17.1 every Sukrara app shared one version and one
[changelog](../../../CHANGELOG.md).

## 0.18.0 — 2026-11-24

- **When the phone and the watch speak different versions, Sukrara says which to update**: a watch app that can't
  read the phone's payload keeps showing the current glucose and names the app to update (Sukrara Aura under the
  value, the watch app with *Open on phone* to that update, the data field in its label), and the phone says it on
  home and on its Garmin page, with one button to that update (the Connect IQ listing, or Sukrara's own). Entries from the watch wait until both match.
- **A follower whose Sukrara and the wearer's differ says which to update**: on Android's home and the Mac's panel,
  the reason names it (*Update Sukrara*, with one button to this copy's update, or *Ana needs to update Sukrara*) over
  the last value, and clears itself once the readings come again. Its *No data* alarm says the same, with *Update*,
  and the wearer's Settings › Sharing says it on that follower's row. An invite, or a phone offering *Pair nearby*, from a
  Sukrara of another version says which one to update instead of failing. A wearer whose Sukrara is older than a follower's is told so on home, with
  *Update*.
