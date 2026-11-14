# 38 — A default with per-place exceptions

*2026-11-14 · decision · issue #209 · Android and Mac*

## Goal

Some settings have one value for every place (app, notification, widgets, menu bar panel) and may differ in one
place. The owner: show only what differs from the default, make editing the default the main action and the
exceptions secondary, give every such setting a default, and let *Default* be a value a place can choose (a switch:
*Default* / *On* / *Off*). One shared component per platform.

## What existed

| Setting | Android | Mac |
|---|---|---|
| Time shown (`graph.hours` + `graph.hours.place`) | Graph: *Default* + one row per place, each *Same as default (12 h)* or its own | Graph: *Default* + a pop-up per place (*Same as default*) |
| Sensor time left (`graph.sensorTime.place`) | Graph and Sensor end: one switch per place, **no default** | Graph › Sensor: two toggles, **no default** |
| One home widget's own settings (`widget.overrides`) | its configure screen: each row *Same as the app* / its own | — (Mac widgets follow the Widgets place) |

No other setting is per place. The unmerged `i36-one-notification` branch adds notification settings; none are per
place today. If one becomes per place, it uses this component.

## Options compared

| Option | Seen in | Verdict |
|---|---|---|
| A. Every place listed with its value (what we had) | — | Every value always shown; the default is one row among four; a switch has no default. |
| B. Every place always listed, each *Default (…)* or its own | macOS Notifications (each app page's *Show previews*: *Default*), iOS | Good choice list; still shows every place and repeats the default on each row. |
| **C. The default row + one *Exceptions* row naming only the places that differ, opening the places** | Android *App languages* (each app *System default* or its own); Chrome site settings (*Default behaviour*, then *Customised behaviours*); Safari › Websites (*Configured websites*, then *When visiting other websites*) | **Chosen.** |
| D. Exceptions shown inline only when set, plus *Add exception* | Chrome's *Add* | Rows appear and disappear (rejected in a1ccea39); adding takes two dialogs. |
| E. A page per place with all its settings | Android per-app notification pages | Splits one setting across pages; the default disappears. |

## Choice: C

- **The default is the setting's row** on its page: one tap to change it, read-only on a follower outside Custom.
- **One *Exceptions* row** under it says only what differs (*Notification: 3 h*, *Widgets: Off*) or *None*.
- **The places**:
  - Android: an Exceptions page (`SettingsRoute.PlaceExceptions`), one row per place: its own value or *Default (12 h)*.
  - Mac: *Edit…* opens a sheet with a pop-up per place (HIG: pop-up buttons for mutually exclusive choices; sheets for
    a short task; the same Done / Esc as *Details…*).
- **Default is a value**: every place's dialog or pop-up lists *Default (…)* first, the default's value in brackets
  (as macOS *Default* and Android *System default*), then the values. A switch becomes *Default (On)* / *On* / *Off*.
- **Every place setting has a default**: the sensor's time left gains `graph.sensorTime`; its places become
  `placeOverride` (`default` / `on` / `off`, new keys, no migration).
- **The places are each device's own** (never mirrored), so a follower edits them in every *Settings from* mode.
- **The widget's own settings** use the same rows and dialogs: *Default (…)* shows the Widgets place's value.

Shared code: Android `:core:designsystem` `PlaceOverrides.kt` (`ExceptionsRow`, `PlaceRow`, `OverrideDialog`,
`OnOffOverrideDialog`); Mac `PlaceExceptions.swift` (`ExceptionsRow`, `ExceptionsSheet`, `OverridePicker`) and
`NumberSettingRow(defaultValue:)`.

## Pictures

[`docs/img/settings/overrides/`](img/settings/overrides/): before and after, light and dark, Android and Mac.

## Sources

- Material settings pattern: https://developer.android.com/design/ui/mobile/guides/patterns/settings
- Android per-app languages (*System default*): https://developer.android.com/guide/topics/resources/app-languages
- AOSP `AppLocalePickerActivity` (system locale = no override): https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/localepicker/AppLocalePickerActivity.java
- macOS Notifications, per-app *Show previews* (*Default*): https://support.apple.com/guide/mac-help/notifications-settings-mh40583/mac
- Safari › Websites (configured websites, *When visiting other websites*): https://support.apple.com/guide/safari/customize-settings-per-website-ibrw7f78f7fe/mac
- Safari page zoom default and exceptions: https://support.apple.com/guide/safari/zoom-in-on-webpages-ibrw1068/mac
- Chrome site settings, default and customised behaviours: https://www.webnots.com/how-to-block-popups-in-google-chrome/
- Apple HIG, pop-up buttons: https://developer.apple.com/design/human-interface-guidelines/pop-up-buttons
- Apple HIG, sheets: https://developer.apple.com/design/human-interface-guidelines/sheets
- Apple HIG, settings: https://developer.apple.com/design/human-interface-guidelines/settings
