---
apps: [android, mac, aura, garmin-app, garmin-field, face-sdk]
kind: feature
---
- **When the phone and the watch speak different versions, Sukrara says which to update**: a watch app that can't
  read the phone's payload keeps showing the current glucose and names the app to update (Sukrara Aura under the
  value, the watch app with *Open on phone* to that update, the data field in its label), and the phone says it on
  home and on its Garmin page, with one button to that update (the Connect IQ listing, or Sukrara's own). Entries from the watch wait until both match.
- **A follower whose Sukrara and the wearer's differ says which to update**: on Android's home and the Mac's panel,
  the reason names it (*Update Sukrara*, with one button to this copy's update, or *Ana needs to update Sukrara*) over
  the last value, and clears itself once the readings come again. Its *No data* alarm says the same, with *Update*.
