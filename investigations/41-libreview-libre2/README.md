# Investigation 41: a Libre 2 read through Juggluco, to LibreView

Standalone spike for [docs/41](../../docs/41-libreview-other-sensors.md). Python 3.9+ standard library only; nothing
here is part of Sukrara. **No health data in this folder**: `plan` / `post` read a Juggluco `entries.json` you point
them at (never commit one), the fixture is synthetic.

```
lv2.py        what Sukrara sends for a Libre 1 / 2 (5-minute values from Juggluco's SERIAL#lifeCount minute readings,
              record numbers, the Libre 3 app payload) and the real-account calls (sign-in as either identity,
              registration, one upload); writes contracts/treatments/libreview-glucose-fixture.json
test_lv2.py   the values, the fixture, and the whole proof batch against a fake LibreView on loopback
```

```sh
python3 -I investigations/41-libreview-libre2/test_lv2.py
python3 -I investigations/41-libreview-libre2/lv2.py fixture     # after a change: Android's LibreViewPayloadTest reads it
```

The real-account batch (docs/41 "Proof") needs the owner's OK for every step. Credentials come from the login keychain
item `dev.spike.libreview` (investigation 04's) and are never printed or stored; the spike's DeviceIds and token live
in `~/.config/sukrara-spike41/` (delete it afterwards). Get Juggluco's readings with
`adb forward tcp:17599 tcp:17580` and
`curl -g 'http://127.0.0.1:17599/api/v1/entries.json?count=30000&interval=0&find[date][$gte]=<ms>'`, then
`adb forward --remove tcp:17599`.
