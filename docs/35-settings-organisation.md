# 35 — Settings organisation

*2026-11-14 · #205 (from #204) · plan only · rev. 2 after the owner's review*

Ids, stores, keys and the wire stay; only the schema's `section` and the screens change, on both apps at once.

## Sections

![Proposed section icons, Android and Mac, light and dark](img/settings/sections-icons.png)

*Rendered from the `core/designsystem` vectors on the `CategoryIcon` tones (mask drawn as a circle) and the SF
Symbols.*

| Section (en / es) | Holds | Android | Mac pane |
|---|---|---|---|
| Alerts / Alertas | every `alerts.*`; new subpage **Fine-tuning / Ajuste fino** (`alerts.persistence.*`, `alerts.actions.*`) | row + Alert reliability next to it | Alerts (Fine-tuning… sheet) |
| **Readings / Lecturas** (was "Glucose", today's Ranges) | unit, the 6 ranges, Trend arrow (#204); Mac also the sensor's time left | row (Ranges renamed, live preview) | new pane |
| Graph / Gráfica | time shown, lines, vertical lines, treatments, time-in-range column + crown | row | Graph |
| Rings / Anillos | ring count, slots, ring range, rewards, order, last treatment | **new page**, split from Graph | Rings |
| Status bar, Lock screen | unchanged | rows | — |
| Sensor / Sensor | source, Juggluco; **Sensor end becomes its subpage** (reminders, calendar, countdown, time left on the graph) | row | — |
| Insulin pens / Plumas de insulina | unchanged, moves next to Sensor | row | — |
| Sharing, Share with apps, Backup, **Files** | see [Data pages](#data-pages) | rows | — |
| General / General | Settings from, read aloud, open at login, updates | — (Settings from is an overview row) | General |
| Advanced, About | relays, log; version, updates | rows | — |

Android overview: Search · Profile · Mute all · [Alerts · Alert reliability] · [Settings from · Readings · Graph ·
Rings] · [Status bar · Lock screen] · [Sensor · Insulin pens] · [Sharing · Share with apps] · [Backup · Files] ·
[Show closed tips · Advanced · About].
Mac panes: General · Following · Alerts · Readings · Graph · Rings.

## What moves

| Setting | From → to | Why |
|---|---|---|
| `graph.band.*` (6) | Android Ranges, Mac Graph → **Readings** | they colour every surface, not only the graph |
| `general.unit` | Mac General → Readings (Android: already there) | it's how every reading is shown and typed |
| Trend source + smoothing (new, #204) | → Readings › Trend arrow | part of the reading; drives Falling / Rising fast |
| `graph.ringCount`, `slots`, `ringRange`, `ringMarks`, `ringOrder`, `lastPlace`, `lastElse` | Android Graph → **Rings** page | the same split the Mac already has |
| `sensor.countdownHours`, `graph.sensorTime.place` | Graph → Sensor › Sensor end (Mac: Readings › Sensor) | about the sensor, not the chart |
| `sensor.reminders`, `sensorCalendar.*` | overview row → Sensor › Sensor end | one place for "my sensor" |
| `alerts.persistence.*`, `alerts.actions.*` | Alerts → Alerts › Fine-tuning | rare; no global advanced mode ([AAPS #5464](https://github.com/nightscout/AndroidAPS/issues/5464)) |
| Settings file (export / import) | Advanced → **Files** | a normal task (new phone), not advanced |
| Import history, Export data | Share with apps › Files group (and an overview row) → **Files** | one-off files, not live sharing |
| Juggluco on this network (Mac) | General → Following | both answer "where does this Mac get glucose" |

## Second places and links

A second place edits **the same setting in the same store** (no copy); a link opens the other page.

| Setting / page | Home | Also editable in | Links |
|---|---|---|---|
| Trend source + smoothing | Readings › Trend arrow | Alerts › Falling fast / Rising fast | the card's arrow (long-press / info) → Trend arrow |
| The 6 ranges | Readings | Graph (a *Ranges* row atop) | Rings' *ring range* → Readings; Readings footer → Alerts (thresholds are separate, as LibreLink keeps them) |
| Alert thresholds | Alerts › each alert | Alerts › Review thresholds (exists) | Readings ↔ Alerts |
| Sensor time left on the graph | Sensor › Sensor end | Graph (a *Sensor countdown* row) | — |
| Alert reliability | its own row | — | Alerts ↔ Alert reliability (both ways) |
| Settings from | overview row (Mac: General) | — | atop every mirrored page (exists) |
| Sharing / Share with apps / Backup / Files | own rows | — | each page's foot links the other three (as Sharing does today) |

## Names

**"Glucose" → Readings / Lecturas** (recommended). How each reading is shown (unit), judged (ranges) and where it heads
(arrow). Alternatives: *Ranges and units / Rangos y unidades* (familiar, but the arrow doesn't fit); *Targets / Objetivos* (clinical, but reads as alert levels, which it isn't).

**"Graph": keep — Graph / Gráfica.** Native-English CGM apps say *graph*: Dexcom G7 "trend graph"
([guide](https://dexcompdf.s3.us-west-2.amazonaws.com/en-us/G7-CGM-Users-Guide.pdf)), LibreLink "glucose graph"
([manual](https://freestyleserver.com/Payloads/IFU/2024/q2/ART48521-101_rev-A.pdf)), xDrip+ *Settings › Display ›
Graph Settings* ([docs](https://navid200.github.io/xDrip/docs/Display/Display.html)). Loop alone says "Glucose
Chart" ([displays](https://loopkit.github.io/loopdocs/loop-3/displays-v3/)). *Gráfica* stays.

## Data pages

| Page (en / es) | Holds | Kind |
|---|---|---|
| Sharing / Compartir | followers, invites, devices | live, to people |
| Share with apps / Compartir con apps | Nightscout, LibreView, Tidepool, xDrip+ server, Health Connect, broadcast, Garmin | live, to apps |
| Backup / Copia de seguridad | the automatic backup and restore | automatic copy |
| Files / Archivos | Export data (readings + treatments, report), Import history, Settings file (export / import) | one-off files |

The Mac keeps its Report window and has no data pages.

## In Sukrara

Pending the owner's OK; then one round on both apps: schema `section`s (`settings.pane` gains `readings`), the pages
above, search (old names as keywords), FEATURES.md, README, QA.md, goldens. Trend arrow after #204 (PROTOCOL-v2 key).
Left out: a global advanced mode; renaming ids / keys; a Mac search field; thresholds merged into ranges.

## Open

1. *Readings / Lecturas* as the name.
2. *Files / Archivos*, or *Export and import / Exportar e importar*.
3. Readings' and Graph's icons look alike (both small charts); a distinct Readings glyph (a drop, as on the Mac)?
4. Two nights today: Alerts › Night alerts and Sensor end › Night. Make them one setting?
5. Juggluco to Following on the Mac; Following as the second pane.
6. Sensor end as a subpage of Sensor.
7. *Fine-tuning / Ajuste fino*.

Sources: [Material settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings) ·
[HIG Settings](https://developer.apple.com/design/human-interface-guidelines/settings) · xDrip+
[#1006](https://github.com/NightscoutFoundation/xDrip/issues/1006),
[#1740](https://github.com/NightscoutFoundation/xDrip/issues/1740) ·
[AAPS Preferences](https://wiki.aaps.app/en/latest/SettingUpAaps/Preferences.html) · [PoC showcase](10-showcase.md).
