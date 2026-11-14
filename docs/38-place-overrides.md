# 38 — A setting the app sets, that a place can change

*2026-11-15 · decision · issue #209 · Android and Mac*

## Goal

Some settings have one value for the app, the notification and the widgets (Mac: the menu bar panel and the
widgets), and a place may differ. The owner: show only what differs, make changing the shared value the main action
and a place's value secondary, let *follow the shared value* be a value a place can choose (a switch: follow / on /
off), one shared component per platform.

## Rounds

| Round | Design | Owner's verdict |
|---|---|---|
| 1 (8a539afc) | A *Default* row + an *Exceptions* row naming the places that differ; Android an Exceptions page, Mac an *Edit…* sheet | "very disappointed" |
| 2 | Five alternatives: baseline, one row + its page (B), place chips, place tabs, set it at the place | "all still bad"; B the least bad. No selects inside an Android row, no ⓘ hiding the places on the Mac, "Exceptions" is a terrible word |
| 3 | B iterated (below) | approved: "I approve the component design" |

Round 3's details the owner shaped: the Mac nests the places (Apple's ways of nesting, below); a following place's
pop-up is quiet; the row's summary never breaks inside an item, one line when one place differs, one line per value
when more do, and no line is a bare number.

## Choice

| Decision | Chosen | Why, over the other way |
|---|---|---|
| What places follow | **The app's own value** (Mac: the menu bar panel's). Only the notification and widgets can differ (Mac: the widgets) | A separate default plus an App place was four values for three places, and every word for it (Default, Global) needs explaining; the app's value is one the person can point at. Every combination stays possible |
| Words | A place says its own value or *Same as the app*; its dialog's first choice *Same as the app (12 h)*; a widget's own settings *Same as in Settings* | *Exceptions* and *Default* are jargon (NN/g; Chrome's site settings call the section *Customised behaviours*). The short form keeps a following place quiet; the dialog says the value when choosing |
| Android: the setting | One row; its summary names only what differs; it opens the setting's page: the app's values as a radio list (as *Screen timeout*), then one row per place, each opening the stock list dialog | Google's settings guide: radio buttons go "in a dialog or child screen"; SettingsLib's drop-down is used only in the APN editor, never in a row. Cost: the app's value is 2 taps (as Screen timeout) |
| Android: a switch | The two-part row (switch = the app's value, text opens the page); the page's main switch, then the places (Same as the app / On / Off) | The row the app already uses for switches with more behind them (a1ccea39) |
| Mac: nesting | **A page per setting** inside the pane, with a back button: the menu bar's value, then the widgets' pop-up | Apple nests by indenting, by a nested view behind a chevron, or by a disclosure triangle (HIG Layout, Toggles, Lists and tables, Disclosure controls); System Settings uses the nested view for this exact case (Accessibility › Display › *Text size*: the shared size, then a pop-up per app, "Use Preferred Reading Size" or its own). The ⓘ "doesn't support navigation"; a sheet is modal; a triangle hides whether the widgets differ. Indenting in the pane (shown as Mac 2) shows a widgets row under every setting even when nothing differs |
| Mac: a following place | Its pop-up shows just the value, grey and borderless; its own value gets the normal pop-up | What you set stands out; what you didn't stays in the background. The menu's first item says *Same as the menu bar (12 h)* |
| The summary | Nothing differs: `12 h`. One place: `12 h · 3 h in the notification`. More, or the one line doesn't fit: one line per value, each saying where (`12 h in the app` / `3 h in the notification` / `6 h on widgets`); same values share a line | A line never breaks inside an item, and a bare `12 h` on its own line reads oddly. Android measures the one line with the row's real width and text style, so it holds at every font size and language |
| One widget's own settings | Kept, the same rows (*Same as in Settings*) | The lock-screen widget has no settings screen, and setting all widgets at once stays possible |

## In Sukrara

- **Added**: schema `graph.hours.place` / `graph.sensorTime.place` for the notification and widgets (Mac: widgets)
  only, `0` / `same` = Same as the app. Android `:core:designsystem` `PlaceSummary.kt`: `placeSummary` (pure, unit
  tested), `PlacesRow` (measures whether the one line fits), `PlacesSwitchRow`, `PlaceDialog` / `OnOffPlaceDialog`,
  and a wrapped line keeps its last two words together; the pages `TimeShownContent` / `SensorTimeContent`
  (`SettingsRoute.PlacePage`). Mac `PlaceSettings.swift`: `PlaceSummary`, `PlaceSettingRow`, `SettingPage` (the page
  over the pane, back button, ⌘[, Esc), `PlacePopUp` / `SwitchPlacePopUp` (quiet while following). The Mac has one
  place, so its line is one sentence (`%@ on widgets`).
- **Discarded**: the *Default* + *Exceptions* rows, the Exceptions page, the Mac's *Edit…* sheet, `ExceptionsRow`,
  `OverrideDialog`, `PlaceExceptions.swift`, the App and menu bar places and their keys (`placeHoursApp`,
  `placeSensorTimeApp`, `menubarHours`, `placeSensorTimeMenubar`; no migration). Round 2's chips (not a SettingsLib
  component), tabs (the differences hidden behind a dot) and set-it-at-the-place (no single place lists the
  differences).

## Pictures

[`docs/img/settings/overrides/`](img/settings/overrides/): `*-before-*` the round 1 design, `*-after-*` this one,
light and dark, English and Spanish. Mockups of every state: issue #209.

## Sources

- Android settings pattern (radio buttons in a dialog or child screen; status as secondary text; dependent settings
  below): https://developer.android.com/design/ui/mobile/guides/patterns/settings
- AOSP Screen timeout (a radio page with Screen attention below):
  https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/display/ScreenTimeoutSettings.java
- SettingsLib `ListPreference` (a dialog of radio buttons):
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SettingsLib/Spa/spa/src/com/android/settingslib/spa/widget/preference/ListPreference.kt
- AOSP per-app aspect ratio (*App default* first; *Changed apps* listed apart) and per-app language (*System default*):
  https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/res/values/strings.xml
- Apple HIG: Layout (indentation, progressive disclosure, nested views)
  https://developer.apple.com/design/human-interface-guidelines/layout · Toggles (primary and subordinate settings in a
  grouped form) https://developer.apple.com/design/human-interface-guidelines/toggles · Lists and tables (the info
  button doesn't navigate; drill in with a disclosure indicator)
  https://developer.apple.com/design/human-interface-guidelines/lists-and-tables · Disclosure controls
  https://developer.apple.com/design/human-interface-guidelines/disclosure-controls · Pop-up buttons
  https://developer.apple.com/design/human-interface-guidelines/pop-up-buttons
- macOS Text size per app (*Use Preferred Reading Size*):
  https://support.apple.com/guide/mac-help/increase-font-size-and-icons-mchld786f2cd/mac
- macOS Notifications, per-app *Show previews* (*Default*: the setting at the top):
  https://support.apple.com/guide/mac-help/change-notifications-settings-mh40583/mac
- Chrome site settings strings (*Customized behaviors*): https://github.com/chromium/chromium/blob/main/chrome/app/settings_strings.grdp
- NN/g, users' language over jargon: https://www.nngroup.com/articles/4-principles-reduce-cognitive-load/
- Other CGM apps: xDrip+'s per-surface settings with nothing shared
  (https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/res/xml/xdrip_plus_prefs.xml#L240), Juggluco's
  widget showing the notification's image (https://www.juggluco.nl/Juggluco/changes.html).
