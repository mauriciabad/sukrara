# Graph fixtures (shared by Android and Mac)

"One graph everywhere" as data: each `*.json` here holds `cases` that both renderers must decide the same way.

- Android: `GraphFixtureTest` in `:core:graph` (`cd apps/android && ./gradlew :core:graph:test`)
- Mac: `GraphTests` (`cd apps/macos && swift test --filter GraphTests`); `status.json` also through the alert engine and
  `CardAlertsMapping` in `StatusFixtureTests` (AppTests)
- CI runs both (`.github/workflows/checks.yml`); `tools/check` reminds you to add a case when the card code changes.

A case: `name`; optional `settings` (alert settings JSON, ../alerts/SPEC.md §3, missing keys = defaults);
`reading` `{t, sgv}`; `treatments` `[{id, k: rapid|long|carbs|note, a, t, n?}]`; all times are seconds relative to
"now" (negative = past). `expect`:

| key | meaning |
|---|---|
| `paused` | optional: this device's Mute of a side in force, side (`low`, `high`) → its end in seconds from now; it covers every kind of the side, very low and extremely high included (round f30) |
| `allMuted` | optional: this device's *Mute all*, its end in seconds from now or `"never"` (until turned back on, AlertEngine.NEVER) |
| `status` | `none`, `needed:<kind>`, `taken:<kind>:<treatment id>`, `paused:<kind>` or `muted` (in range or no current reading while Mute all is on) (AlertStatus) |
| `until` | for `taken`: end of the mute, seconds from now |
| `badge` | `null` or `{kind, text, short, tonal}` (StatusBadge.look, English words); `until` is `"never"` for Mute all without an end. A paused badge without an end reads "Muted"; the `muted` badge (kind `muted`: neutral colour, the "notifications off" bell) reads "Muted · 1h 20m" or "Muted" (until you turn it off), short "Muted" (../alerts/copy.json `pill`) |
| `last` | `null` or `{id, until, with?}` (LastTreatment.of; `until` null = its kind mutes nothing; `with` = the carbs drawn with running insulin, `{id, until}`, absent = none) |
| `amounts` | `{id: "4.5u" \| null}` (TreatmentMarks.amount) |

## Treatment label layout (`labels/*.json`)

The layout dump: which treatment labels are drawn, where and with what text (rules: docs/16-graph.md, "Treatment
labels"). Run by `GraphFixtureTest.labels` (Android) and `-graphselftest` (Mac), which also checks that SF Pro's real
width of each label fits the width model. A case: `name`; optional `locale` (`es`: the labels' times in Spanish, "1 h 35 min", "ahora": the widths the Spanish card lays out, #77); `chart` `{w, h}` (the chart's area, dp); `hours` (window);
`pct` (time in range has data, so the % column shows where the chart is ≥ 240 dp wide); `treatments` as above
(`k`: rapid | long | carbs | note; times relative to "now"). Optional `marks` (Treatments on the graph): absent = the defaults (carbs: point
only), `"all"` = every part of every kind, or `{kind: [point, line, amount, time]}` (missing kinds = their default). Optional
`fontScale` (the system font size, absent = 1): the chart's text scale is it capped to 1…1.3 (`GlucoseGraph.textScale`):
the labels, their rows and the side columns' widest labels grow by it; the geometry (marker spacing, the gap between labels, the 90 dp second-row threshold) doesn't. The file's `pctLabel` and `valueLabel` stand in for the widest
% label and the widest value on the right (dp; the real ones are measured with the drawing font): each side column is
it plus the gap of `layout/rules.json` on each side. `expect`:

| key | meaning |
|---|---|
| `plot` | `[left, right]` of the plot, dp from the chart's left (± 0.5) |
| `lanes` | label rows the chart allows (1, or 2 from 90 dp tall) |
| `labels` | every label left to right: `ids` (its entries in label order), `text`, `lane` (0 = the axis row, 1 = under it), `x` (its centre, dp, ± 0.5) |
| `dropped` | the groups left without a label (entry ids), in placing order |

Positions are rounded to 0.5 dp; Android computes in `Float`, the Mac in `Double`. To add a case, write it with an
empty `expect`, run `GraphFixtureTest` (`--rerun`: Gradle doesn't see the fixture files as inputs), check the dump in
the failure message against the rules, paste it, and run the Mac self-test.

## The card's words (`words/words.json`)

Every word the card draws, English and Spanish (#77): the pill (*Eat carbs* / *Come hidratos*, *45m left* / *quedan 45 min*), the short durations (`duration_*`), the slots' periods and names (`period_*`, `slot_*_short`), the value's place (`card_*`), the range words and the chart's *Now*. Keys are Android's `core/graph` string names; run by `CardWordsFixtureTest` (Android, the resources' raw text in each language) and `CardWordsFixtureTests` (Mac, `CardWords.drawn` filled with the file's sample arguments). The Mac keeps these words in code (`CardWords`, SukraraGraph/A11y.swift: the widget extension has no String Catalog), so this file is what keeps the two in step. `layout/rules.json` `warmup` lists the Spanish warm-up words too (`es`), and the Mac renders the whole layout matrix a second time in Spanish (`LayoutTests.spanishWordsLayOutTheSameOnEverySurface`).

## Where the sensor badge goes (`place/place.json`)

One decision for every surface (docs/FEATURES.md, "Sensor badge"): `GlucoseCard.sensorSpot` on both platforms, the
badge's box in the chart and the room it keeps free of treatment labels. Run by `GraphFixtureTest.place` (Android) and
`-graphselftest` (Mac). `rooms`: `{label, room}` = `GlucoseGraph.sensorWidth(label)` in the `TextModel` (dp, ± 0.05).
A case: `name`; `card` `{graph, value, axisLabels?, sensorTime?}` (the WHOLE card, also for surfaces drawn in parts);
`sensor` `live` (13 days left) | `warmup` | `ended` | `none`; `age` (the newest reading's, s); optional `chart`
`{w, h}`. `expect`: `spot` `graph` | `none` (no badge beside the number: the user, 2026-10-21), and with a chart `box` `[x, top, h]` (dp, ± 0.05; no second
label lane). `tools/check` (`graph-surface`) makes every surface go through this decision.

The badge takes no room in the label lanes: it is drawn last, over the time row, with its outline erased from what
passes under it, so it never moves a label or adds the second row (`layout/rules.json` `badge`, below).

## Sensor life (`sensor/*.json`)

The sensor badge and the countdown ring (docs/FEATURES.md, "Sensor life"). Run by `GraphFixtureTest.sensor` (Android)
and `-graphselftest` (Mac). A case: `name`; `start`, `end` (the sensor's, seconds relative to "now"); `countdownHours`
(the window); `latest` (the newest reading's time, `null` = none); optional `warmup` (the warm-up's end; default
start + 60 min). `expect`:

| key | meaning |
|---|---|
| `label` | the badge text, English words: ≥ 24 h left → whole days (`13d`), ≥ 1 h → hours (`20h`), else minutes (`45m`, `0m` once ended) |
| `ended` | `now ≥ end`: while readings keep arriving the value stays (with the `pill`), "Sensor ended" once they stop (#3) |
| `life` | the disc's arc: share of the sensor's life left, 1 → 0 (± 0.001) |
| `warmup` | while warming up (no reading since the start, ≤ 15 min past the warm-up's end) the card's figure, minutes rounded up (`42m`), from 60 of them hours (`1h`: one rounding, so 59:15 left is `1h`), `Almost ready` from the warm-up's end until the first reading (never `0m` or less); `null` = not warming |
| `pill` | the sensor's pill for a reading at `latest` (`StatusBadge.sensor`, English words), `{kind: "sensor", text, short, tonal}`: ended with a fresh or late reading → "Ended · Replace sensor" beside the value; ended without one (none, or ≥ 5 min old) → "Replace sensor" under "Sensor ended"; else `null` |
| `ring` | the ring over the main ring: warming → the warm-up done (fills 0 → 1); else the countdown, share of the window left; `null` = no ring (more than the window left, or ended) (± 0.001) |

## Clock (`clock/*.json`)

Clock times in the device's 12/24-hour setting rather than the locale's default, and the graph's hour labels
(docs/FEATURES.md, "Languages"). Run by `GraphFixtureTest.clock` (Android, `graph/ClockText.kt`) and `-graphselftest`
(Mac, `Clock`). A case: `name`; `locale` (BCP 47); `minute` (of the day, UTC); `hour24` (the setting). `expect`:
`time` (the clock time, e.g. `9:08 PM`) and `axis` (the hour label, `21:00` / `9 PM`). U+00A0 and U+202F compare as
plain spaces.

Not covered yet: mute-line endpoints.

## Time shares against a goal (`share/*.json`)

How a time-in-range share is written next to its goal (report card / Mac report rows, the rings' number, the
coverage note). Run by `GraphFixtureTest.share` (Android, `ShareText` in graph/GlucoseMetrics.kt) and `-graphselftest`
(Mac, `ShareText` in SukraraGraph.swift). A case: `name`; `x` (the share, 0..1); `goal` (`veryHigh`, `above`,
`inRange`, `below`, `veryLow`, `coverage` = at least 70 %, or `null`); `locale`; `expect` (the number without "%").
Rule: whole percent from 10 %, one decimal under 10 %, and more decimals (up to two) whenever the rounded number would
be on the other side of the goal than the value; a non-zero share that rounds to 0 is `<0.1`.


## Time-in-range marks (`mark/*.json`)

The ring's crown / laurel and the % column's crown (docs/FEATURES.md, "Time-in-range marks"). Run by
`GraphFixtureTest.mark` (Android) and `-graphselftest` (Mac). A case: `name`; `tir` (`veryLow`, `low`, `tight`, `target`,
`high`, `veryHigh` shares, missing = 0, and `coverage` of the window); `range` (`inRange` | `tight`). `expect`:

| key | meaning |
|---|---|
| `mark` | `none`, `laurel` (≥ 90 % as shown) or `flowers` (≥ 95 %), by the share alone, or `crown` (nothing outside the range); all only with ≥ 70 % coverage |
| `text` | the ring's number (ShareText: never `100` unless it is all) |
| `column` | `[below, in range, above]` as the % column shows them (largest remainder; in range is 100 only when it is all) |
| `crown` | whether the column's 100 wears the crown |
| `flower` | the laurel's flower (`Bloom`): `none`, `lavender` (95), `forget_me_not` (96), `daisy` (97), `cherry` (98), `rosette` (99 and the crowned 100) |

## Range word (`range/range.json`)

The range in words under an out-of-range value, so no card says it by colour alone (#26, WCAG 1.4.1; docs/FEATURES.md,
"Range in words"): `RangeState.shown` on both platforms. A case: `name`; `sgv`; optional `age` (the reading's, s,
default 60), `bands` (the person's, missing = the defaults) and `pill` (`{kind, tonal}`, the status pill the card draws;
absent = none). `expect`: `range` (`very_low`, `low`, `high`, `very_high`, `extremely_high` or `null` = no word) and
`text` (the English word drawn). Where it goes on each surface is `layout/rules.json` `status` (`value.range`).

## Spoken sentence (`spoken/*.json`)

THE sentence TalkBack and VoiceOver say for the card on every surface (#25; docs/FEATURES.md, "Spoken sentence"): `A11y`
on both platforms. Run by `SpokenFixtureTest` (Android) and `SpokenFixtureTests` (Mac). Every case's `expect` is
`{en, es}`, the exact sentence; `{time}` stands for `Freshness.timeText` of the reading (the clock's own format).

`card.json` (`A11y.card`): optional `reading` `{age, sgv, rate?}` (absent = none yet), `history` `{sgv, hours}` (a reading
every 5 min before it at that value), `sensor` `{start, end}` (s from now; warm-up ends an hour after the start),
`status` (`needed:low|high`, `taken:carbs|rapid:<end>`, `paused:low|high:<end>`, `muted:<end>`; `<end>` = s from now or
`never`), `unit` (`mmol`; default mg/dL; ../settings/units.json) and `card` (the place's style:
`value` (default true), `graph` (the chart's hours; absent = no chart), `badge` (default true), `rings`
`[{kind, window, range?}]`, `sensorTime` (default false), `countdownHours`).

`slots.json` (`A11y.metric` / `A11y.last`): `slot` `{kind, range?, window, text}` (`text` = the number the slot draws,
`<0.1` / `>99.9` included) or `last` `{k, a, age}` with an optional `with` (the carbs drawn inside running insulin).

## Trend arrow (`trend/trend.json`)

The one trend arrow per platform (`TrendAngle`): a case is `rate` (mg/dL/min, null = no trend) and `expect`
`{text, deg, double}` — the text arrow every text place writes (`○` without a rate), the drawn arrow's rotation in
degrees (y-down, negative = up) and whether it is doubled. Android `TrendFixtureTest` (plus the glucose notification's
title in `LiveNotificationContentTest`), Mac `TrendFixtureTests` (plus the menu bar and alert titles in
`MenubarTitleTests`).

## Freshness steps (`freshness/freshness.json`)

What every glucose surface shows as the latest reading ages (90 s time, 5 min struck + grey + "no trend" ring, 20 min
"no readings" icon, 3 h no chart), through the one shared `Freshness.look` per platform; a sensor past its end keeps
its value with the `ended` flag while its readings keep arriving, and is "Sensor ended" once they stop (#3). The file also holds the
**surface registry** (`surfaces.android` / `surfaces.mac`: id → files): `FreshnessFixtureTest` (Android, Robolectric on
JDK 21) and `-graphselftest` (Mac) render every case through one adapter per surface id and fail when a listed surface
has none; `tools/check` `freshness-surface` scans the listed files (no value formatted outside the look, no own
thresholds). A new glucose surface: add it to the registry, write its adapter on its platform. `steps` pins the redraw
times (`Freshness.nextChangeAt`), which `FreshnessFixtureTest.night` replays with Android's alarm. `times` pins what the
reading's time says (`Freshness.timeKind`: `time` = the clock time, today's and at most 12 h old; `day` = the weekday
with it, from 12 h or on another day; `date` after 6 days), run by `FreshnessFixtureTest.times` and
`FreshnessFixtureTests.timeKinds` in UTC.

## Surfaces and layout across surfaces (`surfaces/surfaces.json`, `layout/*.json`)

"Things like this cannot happen again" (the user, 2026-10-21, after a badge move reached one surface only).

- `surfaces/surfaces.json` is THE registry of every place that shows the card or its value, per platform: `id` (the
  `CardSurface` enums list exactly these), `kind` (`card`: draws through `GlucoseCard.draw` / `image`; `glyph`: text and
  icons the system lays out, from `GlucoseCard.face`), `files`, `entry` (the real entry path the tests call), `parts`
  and `sizes` (card surfaces); plus the renderer's `owners`, the `api` a surface may use of the renderer's namespaces,
  and `value_text_ok` (files that write a glucose value as data or event text). `tools/check` (card-surface, card-api,
  card-text) holds the code to it.
- `layout/scenes.json`: the scenes (synthetic readings by integer maths, treatments, sensor states; `now` fixed, UTC)
  and settings variants every surface is rendered with.
- The renderer reports what it laid out (`CardLayout`: element, box, what it shows; `(no room)` = decided but left out
  for room, `[...]` = how it was placed). `CardLayoutTest` (Android) and `LayoutSelfTest` (Mac, in `-graphselftest`)
  render every registered surface through its real entry path at each size, for every scene and settings variant.
- `layout/rules.json`: for each rule, the elements and the facts (`by`) they may depend on: all renders with the same
  facts must show the same elements (the surface is never a fact). `mode: subset`: the shown slots must be one priority
  order cut at different lengths. `box`: positions relative to the chart's bottom-left count too.
  `gaps`: a column whose margins are one constant: from the `column` element's box to the boxes of its `labels`
  elements (the text as drawn, measured with the drawing font) there are exactly `gap` dp on the `align` side (to
  each label) and on the other side (to the widest), whatever the surface, size, labels or platform.
- `layout/rules.json` `distribution`: the distribution's dot pitch, dot radius and the outline erased around the dots
  (`cut`, dp); both renderers' constants must equal them and every drawn distribution's erased box
  (`chart.distribution.cut`) is its dots' box (`chart.distribution.dots`) plus `cut` on the right and the top.
- `layout/rules.json` `badge` `covered`: an hour label the sensor badge's erased outline would cut into is left out and recorded as
  `chart.hour.covered` (compared as `time.text`, so the labels stay laid out as if the badge weren't there); no drawn hour
  label starts inside the badge as drawn (`chart.sensor.drawn`) plus its erased outline (#71).
- `layout/rules.json` `warmup`: a warming-up sensor's time left never passes for a value (#27): every render whose
  value's place shows the warm-up (`value.warmup`) draws it at most `maxText` dp tall and labelled
  (`value.warmup.text` starts with one of `words`: `Warming up · 40m`, `Almost ready`).
- `layout/scenes.json` `scroll`: scrolled charts (the app panel, the Mac dropdown), each case a scene and how far back
  (`back`, s) the window ends and, zoomed, how long it is (`span`, s), rendered through the scrollable surfaces' entry
  paths (Android `PanelScrollTest`: `PanelCard` with a `ChartScroll`; Mac `PanelScrollTests`: `PanelView`'s
  `ChartScroll` and `ChartEase`): the chart must equal the chart of the visible data alone (every reading and treatment
  outside the window removed, now = the window's end, the time shown = the window's length), its y range (`chart.y`)
  the visible readings', the Now pill (`chart.now`) only when scrolled or zoomed, treatment labels only up to
  `treatmentLabels`, and `expect` (font-independent: `chart`, `chart.pct`, `chart.y`, `chart.window`,
  `chart.markers`) on both platforms.
- `layout/expect.json`: the shared rules' answers, exactly the ones Android's card and glyph surfaces produce,
  written by `CardLayoutTest` with `-Dlayout.write=true` (review the diff: it is where Android and the Mac agree;
  without it the test fails on any answer that changed, is new or is no longer rendered); the Mac checks its own
  surfaces against it and fails on an answer that isn't there.
- Facts: `scene`, `settings`; `unit` (the glucose unit the card writes in: `mgdl` / `mmol`); `face` (the surface shows a value: a card with one, or a glyph surface), `value`
  (`true` / `false` for cards, `glyph`), `graph`, `labels`, `side`, `hours`, `sensorTime`, `badge` (the card style),
  `rings` (`KIND:window:RANGE:marks,…|lastPlace|lastElse`), `chart` (`WxH` dp), `pctWide` (chart ≥ 240 dp), `lane2`,
  `sensorShown`, `part` and `box` (the part and box the slots were laid out in).
