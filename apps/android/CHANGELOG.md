# Sukrara for Android: changelog

Each release of Sukrara for Android (GitHub tag `v<version>`), newest first: `tools/release/release.py` writes each entry
from the [change files](../../changes/README.md) that name it. Up to 0.17.1 every Sukrara app shared one version and one
[changelog](../../CHANGELOG.md).

## 0.18.0 — 2026-11-24

- **Hide the quiet-alerts warning**: when you turned alert sounds or banners down on purpose (the phone beside you
  already rings), *Hide warning* puts the warning away for exactly that. It comes back if anything else is turned down,
  or if you turn it back up and later down again. *Show warning* is in Settings › Alert reliability (Android) and
  Settings › Alerts (Mac).
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
- **Fewer reconnects to a relay that keeps closing**
  ([#263](https://github.com/mauriciabad/sukrara/issues/263)): a relay that answers and then closes every connection
  is retried less and less often, not every 2 s (less traffic and battery).
