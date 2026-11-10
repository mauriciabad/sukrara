# Settings schema

[`settings.json`](settings.json) is THE settings schema of every Sukrara app (its `_about` explains every field).
`python3 tools/settings/gen.py` turns it into `SettingsSpec.kt` (`:core:data`) and `SettingsSpec.swift`
(`SukraraCore`): number formats, option lists, the inventory, and the store / key constants. App code takes every
stored key from the generated `SettingsSpec` (tools/check rule `settings` fails on a key written by hand).
[`durations.json`](durations.json) holds the duration formatting cases every `Durations` formatter passes; [`units.json`](units.json)
the glucose units (mg/dL ↔ mmol/L, how values and rates are written, typed mmol/L back to stored mg/dL) every
`GlucoseUnit` passes. A glucose number format has a `mmol` variant (its range, step, stepper click and presets in
mmol/L); `gen.py` refuses a variant whose grid doesn't come back unchanged through the stored mg/dL.

## Stores

- **Android:** one Preferences DataStore file per area: `settings` (general), `graph`, `alerts`, `sensor`, `sharing`,
  `integrations`, `widgets`, `pens`, `statusbar`, `lock`. Complex values (lists, per-place maps) are JSON strings
  under their key. `secure` = the Keystore SecureStore (secrets, never in a DataStore or the settings file).
- **Mac:** `defaults` = the app's UserDefaults (App Group suite where the widget reads it), `keychain` = the Keychain
  (secrets), `system` = macOS state (`SMAppService.mainApp` for Open at login).
- **Alerts:** ONE key `alerts` per platform holding the JSON object of [`../alerts/SPEC.md`](../alerts/SPEC.md) §3.
  The schema's `alerts.*` entries address its fields with `path` (`{kind}` = each kind of the `alertKind` options;
  `mirrorExcept` = the kinds a follower never takes from the wearer: no data). Safety rule for every app: if
  the stored value can't be decoded, the app keeps running with the **last value it decoded** (in memory, else the
  SPEC defaults with alerts ON), shows a visible "Alert settings couldn't be read" error with a way to reset them,
  and never turns alerts off or drops a threshold silently. Each app's alert-settings store has a test for it.
