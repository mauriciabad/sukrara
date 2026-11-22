# Sukrara on Garmin — the app, its glance, the data field, the complications

*2026-11-22 · design spec · approved by the owner on the design canvases
([round 2, final](https://claude.ai/code/artifact/e53c8936-3c98-4978-90e2-797751ca0507);
[round 1](https://claude.ai/code/artifact/43aed169-cf1b-44f7-9723-e7f0b00455c7)) · issue #199 · platform facts: docs/47
(the code session's report)*

**How to use it.** The reference is [`tools/watchface/app.js`](../../../tools/watchface/app.js): `screen(cfg)` returns
every screen as a display list (origin at the screen's centre, y down, 1 unit = 1 px of the 390 × 390 Descent Mk3i 43 mm,
drawn in order), from one input `d` (§2). Where this text and the code disagree, the code wins and the text gets fixed,
as with Aura. The round-2 canvas draws every state from the same code (board → section below). Two known
approximations, to be done right in the port from the phone widget's renderer (the owner): the % column's dots and the
way they mask the line (§4.1).

**Ground rules** (Aura's, binding): Sukrara decides, the watch shows (bands, trend, pill, metrics, the kinds' order, the
last treatment's rule all come from the phone; the watch only computes ages and countdowns from absolute times at
every draw); never an old value as current; never colour alone; Aura's palette, type and arrow (`aura.js` `pal()`,
`widths()`, the arrow path); every word en / es; every state has a visible way out (§10).

## 1. Surfaces

| Surface | Garmin app | Reference | Canvas board |
|---|---|---|---|
| Glance | the Sukrara watch app (glance + app + background + 4 complications) | `S.glance` | The glance |
| App: page 1 (the widget), page 2 (numbers) | same | `S.appA`, `S.appNums` | The app |
| One list (log kinds + mute), amount, hold to log, sending, logged, queued | same | `S.actions`, `S.logAmount`, `S.logConfirm`, `S.logSending`, `S.logDone`, `S.logQueued` | Logging |
| Mute: lengths, muted (undo), unmute / change time | same | `S.muteLengths`, `S.muteDone`, `S.muted` | Mute |
| Alerts: Garmin's notification, the app's alert, remind less | same | `S.wake`, `S.alert`, `S.remindDone` | Alerts |
| Data field: whole / half / band / quarter / cap, its own alert | a separate data-field app | `S.df1` … `S.df4small`, `S.dfAlert` | Data field |
| Complications as other faces draw them | the watch app publishes 4 | `S.faceCx` | Complications |

## 2. The input (what the payload must give each screen)

`d` in `app.js` (`data(cfg)` fills it from a made-up day; the port fills it from the payload + the watch clock):
`g` (mg/dL; LO < 40, HI > 400), `rate` (mg/dL/min, null = no trend), `age` (min, from the reading's time), `readAt`
("20:38"), `unit`, `hist` (`{m: minutes ago, v}` every 5 min, ≥ 12 h for the app, ≤ 3 h for the data field),
`tr` (treatments in the window `{m, k: rapid / long / carbs, a}`), `pill` (Aura's: `{k, text, filled, icon}`), `clock`,
`sensorDays`, `sensorState` (`ok` / `warmup` …, Aura's sensor states), `tir1` / `tir7` (`{pct, split}` per band: the
phone's metrics as set, in the card's priority), `slot` (the watch's last treatment, §8: `{k, m, a}` or null),
`order` (the kinds' order, §5). Settings (§9) arrive resolved per watch.

## 3. The glance (`S.glance`)

Garmin draws the list and Sukrara's icon (60 × 60); the app draws 248 × 103 to its right (Descent; other sizes in
docs/47). No input: selecting it opens the app.
- **The widget's top row**: value + arrow left (font 58, 54 when a row sits under it); from the right end, in the card's
  priority, as many slots as fit (r 23, stroke 5, 6 apart): the main metric's ring, then the last treatment (§8) when
  there is one, else the second metric (dimmed). Rings carry **no period label** and a bigger number (23).
- **Under the value**, only when needed: the reading's time when late (≥ 90 s), then Sukrara's pill (font 15).
- **States**: old (≥ 5 min) struck grey with a hollow ring for the arrow; no reading for 20 min → "No reading since" +
  the time; warming up → the pie + "Warming up" + "Ready at …".

## 4. The app

UP / DOWN or a swipe between the two pages (dots at the bottom); START opens the one list (§5) from either page.

### 4.1 Page 1: the widget (`S.appA`)
- Top: the clock (22, soft). The value + arrow (88) between the card's first two places: right = the main metric's ring;
  left = the next in the card's priority: the last treatment (§8, never dimmed) when there is one, else the second metric
  (dimmed). Rings r 25, stroke 6, no period label, number 22.
- **The chart**: 12 h (the place's `graph.hours`), x −134 … 146, y −22 … 88. Height on **Kovatchev's symmetrised
  scale** over 40–300 mg/dL (K(g) = 1.509·((ln g)^1.084 − 5.381), as Aura's rail; symmetric there: lows 27 %, the range
  47 %, highs 26 %; above 300 pins to the top). Bands: very good 70–140 (tight green 13 %), good 141–180 (target green
  7.5 %), dashed limit lines, labels 70 / 180 right. The line in its bands' colours, the bead; treatment marks per
  `graph.marks` with labels in the hours' row (an overlapping label is dropped). Old: the line grey, ending at the
  reading.
- **The % column** (the widget's, on per the place's setting): the window's readings as dot rows per 10 mg/dL from the
  plot's left edge (up to 22 % of its width), **over the line, masking it** (each row repaints the plot's own background,
  band tint included, before its dots), the shares above / in / below to its left. Port it from the widget's renderer.
- Bottom row: Sukrara's pill (or the reading's time when late), else the sensor's days.
- **States**: low / high (pill), muted (tonal pill counting down), old, no reading for 20 min, warming up.

### 4.2 Page 2: numbers (`S.appNums`)
The phone's Glucose report card, simplified: period chips 1d · 7d · 14d · 30d (the phone's `report.days` by default;
tapping one switches it here without saving; buttons: the list's Period), the stacked bar of the bands, *Above range*,
*In range* (with *very good*), *Below range* with their consensus goals as chips (✓ met in green, ✕ not in orange),
then average, GMI, CV each with its goal check. Very high / very low rows and the coverage note stay on the phone.

## 5. The one list (`S.actions`)

START in the app (or holding the Log complication on a face): the three kinds **in the phone's order**, the first
selected, each with its last entry and how long ago ("4.5u · 1h"), then *Mute alerts* (or *Muted* + time left).
The order is one rule on the phone (`TreatmentOrder`, cases in `contracts/treatments/kind-order.json`, the treatment
dialog's focus too): carbs when low, the other insulin after a pen dose, else rapid; then Rapid · Long · Carbs. The
watch never decides it (payload `log.order`).

## 6. Logging

- **Amount** (`S.logAmount`): the kind's name and mark in its colour on top, the amount (128) with its unit, chevrons
  above / below; UP / DOWN one step per press (0.5 u, 5 g; Garmin keeps holds), touch taps / swipes; the person's three
  usual amounts as chips (the chosen one filled in the kind's colour); *Now* (MENU for earlier); START next.
- **Hold to log** (`S.logConfirm`): the amount big in the kind's colour with its mark, "rapid insulin · now", the quiet
  line as the phone says it ("Highs quiet for 1 h"); the rim fills from START over about 1 s while START (or a touch) is
  held; letting go cancels; a notification over the screen cancels. No two-step confirm (the owner).
- **The same kind in the last 15 min** is said first, in the high colour ("4.5u 3 min ago").
- **Sending** (`S.logSending`) until the phone has it → **Logged** (`S.logDone`): check, "Logged 4.5u rapid", the quiet
  line, *Undo* for 10 s (START; a ring counts down).
- **Phone out of reach** (`S.logQueued`): OPEN (§11.1).

## 7. Mute, alerts

- **Mute** (`S.muteLengths` → `S.muteDone` → `S.muted`): from the list, all alerts (very low and no data too, as the
  phone's Mute all): 15 min · 30 min · 1 h · 2 h · Until I unmute; from an alert, that side only (15 min – 2 h). It mutes
  the phone's alerts and the watch's (the phone runs them; a watch → phone command). Done: the off bell, "Alerts muted ·
  for 30 min · until 21:11", *Undo* 10 s. While muted: the pill everywhere, the list's *Muted 1h 20m* → *Unmute* (primary)
  / *Change time*.
- **Garmin's notification** (`S.wake`, the background's `showNotification`, `dismissPrevious`): Sukrara's icon, "Low 62",
  "Falling · 20:41", three actions: *Treatment*, *Remind less*, *Mute lows 30m*. Each opens the app with the action;
  Remind less and Mute send it, show their toast and close.
- **The app's alert** (`S.alert`, the app open; it vibrates itself): the alert's word with the bell, the value with
  **Aura's sparkline** (watch setting *Sparkline on alerts*, on by default; off: value + arrow, bigger), then the phone's
  three as buttons: *Treatment* (filled, the side's kind: carbs for lows, rapid for highs; START), *Remind less* / *Remind
  as usual* (shown only when it would slow anything; the interval under the buttons once relaxed), *Mute*. Very low and
  extremely high: the whole screen in the band's colour, everything white (Aura's very-low block); otherwise a 10 px
  ring in the colour. Treatment opens the amount on that kind with the usual amounts.
- **Remind less** (`S.remindDone`): "Lows: remind less · Reminders every 30 min", *Undo* 10 s.
- Copy is informational (Garmin's store rule): it says what is happening, never promises more.

## 8. The watch's last treatment

**The widget's**, one source of truth (the owner, 2026-11-22: "follow whatever app settings does for the widget… a
single source of truth, so they are synced"): the card's last treatment (`LastTreatment`, the payload's `last` chain:
running rapid insulin, the one covering the alert, else the newest rapid (4 h) or carbs (3 min per gram, 30 min – 3 h);
never long-acting or notes) and the phone's settings for it as resolved for the watch (`graph.last` on / off,
`graph.lastElse` = what the slot shows meanwhile, the payload's `else`). The watch shows **one** treatment, never the
amount: the first item of the first step of `last` whose end is still ahead at every draw (so it goes on time with the
phone away). Drawn as a stack the size of a ring (`lastStack`): the kind's mark (▼ rapid filled, ● carbs) in its
colour, then the time since in Sukrara's short durations, split over two lines from 1 h ("1h" / "5m"). Long-acting
shows on the watch when it shows on the widget: a change to the rule changes both.

## 9. Data field (`S.df*`)

Its own store app; the phone sends it its own data (≤ 3 h). No input. Label *GLUCOSE* (soft, letter-spaced).
- **Aura's sparkline** (§6.5 of Aura's SPEC, `auraSpark`, scaled by `sc` per slot): log time axis (the newest 10 min
  widest; up to 3 h as the width allows), 0.6·sc px per mg/dL never rescaled, the window panning on the last hour,
  overflow fading, the limits dotted with the very-good band at 9 %, the trend ray, the end dot. Whole page: the value
  (116) over the sparkline (sc 1.4); half / band: value and sparkline side by side, centred (Aura's row); quarter / cap:
  value + arrow only.
- **Low / very low**: the slot becomes a block in the band's colour, the value white. Old: struck. Late: the reading's
  time.
- **Its own alert** (`S.dfAlert`, `DataField.showAlert` + vibration; on per activity in its Alerts › Connect IQ): the
  whole page in the band's colour, the bell and the word, the value + arrow white, "Sukrara · now".

## 10. Complications (the watch app publishes four, for any face)

| # | Long label | Short | Value (String, Latin letters and digits only) | Unit | Ranges | Icon | Hold opens |
|---|---|---|---|---|---|---|---|
| 0 | CGM-GB-value (Face It: "Sukrara glucose"; the glance folder's preview) | CGM | "175", "9.7", "LO", "HI"; "---" when old (≥ 5 min), no data, warming up | null | null | a drop | the app |
| 1 | CGM-GB-data | D-DATA | the same | null | [unit, trend 0–9, low, high, very low, very high, reading time, +300 s, +1200 s] | a drop | the app |
| 2 | CGM-GB-graph | D-GRAPH | the same | null | 288 values, 24 h every 5 min, newest first | a drop | the app |
| 3 | Sukrara log (Face It: "Sukrara log") | LOG (fixed) | §8's treatment as the watch setting *Log complication shows* says: the time since "1h 5m" (default), the amount "4.5u", or both "4.5u 1h5m"; else "Log" | null | null | the app's insulin pen (Android `ds_ic_dia_pen`) | the one list |

- Glucose keeps CGM+ GB's name so it is the generic one and CGM+ GB faces find it too (their code reads only the value).
- **Never old as current**: the background republishes at the reading's time + 5 min, phone or not.
- The trend: in CGM-GB-data only; Garmin's system fonts don't draw arrows (§13, decided 5).

## 11. Settings (the watch is a place, docs/38)

*Same as the app* until the watch gets its own value, set on the phone (Share with apps › Watches › Garmin › Sukrara on
your watch); the phone resolves and sends them: metrics (main / second / third, last treatment, no recent treatment, ring
order, number in the ring, rewards), the chart's time, treatment marks, the % column, green in range, the report period
(switchable on the watch, not saved). Watch-only: *Sparkline on alerts* (on), *Log complication shows* (time since);
the data field's *Glucose in the activity* (on: its value every reading in the activity's file, Garmin Connect's chart).
Never per watch: unit, limits, alerts.

## 12. States and exits (for FEATURES.md)

Glance → select → the app. App page 1 ↔ page 2 (UP / DOWN, swipe); BACK leaves the app. START → the one list → a kind →
amount → hold to log (let go: back to the amount; BACK: the list) → logged (*Undo* 10 s) or OPEN (phone away). The list's
*Mute alerts* → a length → muted (*Undo* 10 s); *Muted* → *Unmute* / *Change time* / BACK. Garmin's notification →
Treatment (the amount) / Remind less / Mute (toast, close) / dismiss. The app's alert → Treatment / Remind less ↔ Remind as
usual / Mute (lengths) / BACK (the alert stays on the phone). Data field: no input; its alert dismisses as Garmin's do.

## 13. Decided, and open

Decided (the owner, 2026-11-22):
1. **Logging with the phone out of reach**: the watch keeps the entry with its own time and sends it when the phone is
   back (`S.logQueued`, "Waiting for the phone"); the phone's same-kind check runs when it arrives.
2. **Two buzzes**: Sukrara's own alert on the watch (with its buttons); the Garmin page tells the person to switch
   Sukrara off in Garmin Connect's notifications. Watches without `showNotification` (API 5.0: Venu 2 family, Venu
   Sq 2, D2 Air X10) keep Garmin Connect's forwarded one.
3. **The data field writes glucose into the activity file** (Garmin Connect charts it), on by default; a data-field
   setting turns it off.
4. **The last treatment** follows the widget (§8): no rule or duration of the watch's own.
5. **No arrows in complication values** (tested 2026-11-22): in the simulator (Descent Mk3 43, SDK 9.3.0) every one of
   Garmin's 29 system fonts draws → ↗ ↘ ↑ ↓ as the replacement diamond (text fonts) or a box (number-only fonts), and
   none of the system font files of the fēnix 8 47, Venu 3 and FR 265S contains them. The trend stays in CGM-GB-data.
   The number-only fonts also draw "LO" / "HI" as boxes ("---" and "5.4" render): a face that draws a complication in
   one shows those as boxes; Sukrara keeps the honest words.

Open:
1. The name **CGM-GB-value** in the fēnix 8's own face editor, and Face It's rendering (a real watch).

**Not designed yet**: round MIP (64 colours, 218–280 px), rectangular (Venu Sq 2, Venu X1) and Instinct mono layouts
(#221); Aura's two top fields and more field options (#240, design-first in `aura.js`); the phone's Garmin page rows for
the per-watch settings and the two-buzz line.
