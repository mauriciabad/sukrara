# Sukrara on Garmin — the app, its glance, the data field, the complications

*2026-11-22 · design spec · approved by the owner on the design canvases
([round 2, final](https://claude.ai/code/artifact/e53c8936-3c98-4978-90e2-797751ca0507);
[round 1](https://claude.ai/code/artifact/43aed169-cf1b-44f7-9723-e7f0b00455c7)) · issue #199 · platform facts: docs/47
(the code session's report)*

**How to use it.** The reference is [`tools/watchface/app.js`](../../../tools/watchface/app.js): `screen(cfg)` returns
every screen as a display list (origin at the screen's centre, y down, 1 unit = 1 px of the 390 × 390 Descent Mk3i 43 mm,
drawn in order), from one input `d` (§2). Every other screen class (round 360 – 466 AMOLED, round MIP 218 – 280, the Venu Sq 2
and X1) is `cfg.cls` on the same code (§14). Where this text and the code disagree, the code wins and the text gets fixed,
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
| Data field: whole / half / band / quarter / cap, its own alert | a separate data-field app | `S.df1` … `S.df4small` (any class: `S.dfSlot`, §14.4), `S.dfAlert` | Data field |
| Complications as other faces draw them | the watch app publishes 4 | `S.faceCx` | Complications |
| Waiting for the phone (no message yet): app, glance, data field | both | `S.setup`, `S.glanceSetup`, `S.dfSetup` | Waiting for the phone |

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
  the time; warming up → the pie + "Warming up" + "Ready at …"; no message from the phone yet → "Waiting for your phone" (§4.3).

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
then average, GMI, CV, each with the goal check the phone's report gives it (✓ met / ✕ not: the phone sends whether
each goal is met, so the watch holds no threshold of its own). Very high / very low rows and the coverage note stay on the phone.

### 4.3 Waiting for the phone (`S.setup`)

Nothing to set up on the watch (the owner, 2026-11-22: no more configuration than one switch): Sukrara for Android
sends to every Sukrara app Garmin Connect reports, its one switch (*Send to Sukrara on your watch*) on by default.
Until the phone's first message reaches this app (a fresh install: up to a minute; Sukrara for Android missing; that
switch off; Garmin Connect not running), every surface says so instead of drawing empty values. **The watch never names
a phone screen or setting**: the phone's pages change, the watch app in the store doesn't; the phone and the website
say where.
- **The app**: one page, no dots: Sukrara for Android's own icon (the disc, the cube, the dot: the phone app that
  needs to be open, big enough to read; the owner, 2026-11-22), not the watch's cyan launcher icon; *Waiting for your phone*; soft: *Sukrara for Android sends your
  glucose here*; a button **Open on phone** (START or a tap) → `Communications.openWebPage` with the setup link
  (`https://sukrara.mauri.app/garmin/setup/`, `/es/garmin/setup/` when the watch's language is Spanish). Garmin Connect
  shows it on the phone; Sukrara for Android claims it as an App Link and opens where the person can act (its Garmin
  page, or its setup when that isn't done); without Sukrara the website's page says how to get it. After the press:
  *Look at your phone* for 5 s, then the button again. On opening and after the press the app also asks the phone at
  once (the fetch), so a working phone fills it in seconds. START never opens the list here. The first message (push or
  fetch) replaces the page with page 1 by itself; an entry logged before the first push (no `key` yet) waits in the
  outbox like one with the phone away. BACK leaves the app.
- **The glance**: *Waiting for your phone* (soft) where the value goes; selecting it opens this page.
- **Complications**: as with no data ("---", *Log*); selecting one opens this page.
- **The data field** (`S.dfSetup`): *GLUCOSE* and *Waiting for phone* (soft, two lines where narrow); no alert,
  nothing written to the activity.

## 5. The one list (`S.actions`)

START in the app (or a tap on the Log complication, on a face of a touch watch): the three kinds **in the phone's order**, the first
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
- **No reading for 20 min**: "---"; on the whole page *No reading since* + the reading's time.
- **Its own alert** (`S.dfAlert`, `DataField.showAlert` + vibration; on per activity in its Alerts › Connect IQ), for
  every kind the phone rings: the whole page in the level's colour (a rate alert: the value's band; no data: grey), the
  bell and the word, the value + arrow, "Sukrara · now". Ink white on the low side, else black or white by contrast
  (`inkOn`); an old value struck in that ink.
- **Always its own black ground**, whatever the activity's colours (no light design).
- **Where the slot is**: Connect IQ gives a field its size, not its place; the field works it out from which edges the
  screen cuts (`obscurity`).
- ***Record glucose***: the value into the activity's file at each new reading while it's fresh; from 5 min old,
  Garmin's "no value", so the file never carries an old value as current.

## 10. Complications (the watch app publishes four, for any face)

| # | Long label | Short | Value (String, Latin letters and digits only) | Unit | Ranges | Icon | Selecting it opens |
|---|---|---|---|---|---|---|---|
| 0 | CGM-GB-value (Face It: "Sukrara glucose"; the glance folder's preview) | CGM | "175", "9.7", "LO", "HI"; "---" when old (≥ 5 min), no data, warming up | null | null | a drop | the app |
| 1 | CGM-GB-data | D-DATA | the same | null | [unit, trend 0–9, low, high, very low, very high, reading time, +300 s, +1200 s] | a drop | the app |
| 2 | CGM-GB-graph | D-GRAPH | the same | null | 288 values, 24 h every 5 min, newest first | a drop | the app |
| 3 | Sukrara log (Face It: "Sukrara log") | LOG (fixed) | §8's treatment as the watch setting *Log complication shows* says: the time since "1h 5m" (default), the amount "4.5u", or both "4.5u 1h5m"; else "Log" | null | null | the app's insulin pen (Android `ds_ic_dia_pen`) | the one list |

- Glucose keeps CGM+ GB's name so it is the generic one and CGM+ GB faces find it too (their code reads only the value).
- **Never old as current**: the background republishes at the reading's time + 5 min, phone or not.
- The trend: in CGM-GB-data only; Garmin's system fonts don't draw arrows (§13, decided 5).

## 11. Settings (the watch is a place, docs/38)

*Same as the app* until the watch gets its own value, set on the phone (Share with apps › Garmin › *Sukrara on your
watch* › *Sukrara*); the phone resolves and sends them: metrics (main / second / third, last treatment, no recent treatment, ring
order, number in the ring, rewards), the chart's time, treatment marks, the % column, the report period
(switchable on the watch, not saved). Watch-only: *Sparkline on alerts* (on), *Log complication shows* (time since);
the data field's *Record glucose* (on: its value every reading in the activity's file, Garmin Connect's chart).
Never per watch: unit, limits, alerts.
The phone tells the watches apart by the payload's `w` (push only), which the app sends back on its fetch (`&w=`).

## 12. States and exits (for FEATURES.md)

Waiting for the phone (§4.3) → *Open on phone* (*Look at your phone*, 5 s) / BACK; it leaves by itself on the
phone's first message. Glance → select → the app. App page 1 ↔ page 2 (UP / DOWN, swipe); BACK leaves the app. START → the one list → a kind →
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

**Not designed yet**: the Instinct 3 Solar / Instinct E mono layouts (1 bit, 32 KB glance and field; #221); Aura's
two top fields and more field options (#240, design-first in `aura.js`).

## 14. Screen classes

The 390 × 390 design (§1–§12) on every screen the manifests target (docs/47 §9: 73 products, API 5+, all but the 1-bit
Instinct 3 Solar / E). The reference is `app.js` with `cfg.cls` (its table `CLS`: one watch per class, read from that
watch's SDK 9.3 device files: screen, panel, touch, the glance's icon and content areas, the data-field slots, the
button angles); `tools/watchface/app-build.mjs` renders every screen of §1 on each into `docs/img/app/ref/<class>/`.

| Class | Reference watch (cls) | Same screen | Pictures |
|---|---|---|---|
| Round AMOLED 360 | FR 265S (`r360`) | Venu 2S | `round-360/` |
| Round AMOLED 390 | Descent Mk3 43 (`r390`, the design) | docs/47 §9's 19 | `round-390/` |
| Round AMOLED 416 | fēnix 8 43 (`r416`) | epix 2 / Pro 47, fēnix 9 43 / Pro 43, fēnix E, FR 265, Venu 2 / 2 Plus, D2 Air X10, D2 Mach 1, Approach S72 43, Instinct 3 AMOLED 50 | `round-416/` |
| Round AMOLED 454 | fēnix 8 47 / 51 (`r454`) | fēnix 8 Pro, fēnix 9 47 / Pro 47, Descent Mk3 51, epix Pro 51, FR 965 / 970 / 570 47, Venu 3, Venu 4 45, MARQ 3, D2 Mach 2, Approach S70 47 / S72 47 | `round-454/` |
| Round AMOLED 466 | fēnix 9 Pro 51 (`r466`) | — | `round-466/` |
| Round MIP 218 | FR 255S (`m218`, no touch) | FR 255S Music | `mip-218/` |
| Round MIP 240 | fēnix 7S (`m240`) | fēnix 7S Pro | `mip-240/` |
| Round MIP 260 | fēnix 7 (`m260`) | fēnix 7 Pro, FR 255 / 955, fēnix 8 Solar 47, fēnix 9 Pro Solar 47 | `mip-260/` |
| Round MIP 280 | fēnix 7X (`m280`) | fēnix 7X Pro, enduro 3 / 4, fēnix 8 Solar 51, fēnix 9 Pro Solar 51 | `mip-280/` |
| Rectangle 320 × 360 | Venu Sq 2 (`sq2`) | Venu Sq 2 Music | `rect-venu-sq2/` |
| Rectangle 448 × 486 | Venu X1 (`x1`) | — | `rect-venu-x1/` |

### 14.1 What scales and what doesn't (every class)
- **Layout units.** Round classes draw the 390's layout × s (s = W / 390: 0.56 – 1.19). Rectangles have their own
  layouts in the Venu Sq 2's px (s = 1; Venu X1 1.2, its extra room going to the layout, §14.5).
- **Type: the atlas set.** Every size snaps to the class's atlas set (`ATLAS`, `snapFont`): AMOLED 14, 16 … 32 (every
  2), 36, 40 … 64 (every 4), 72 … 136 (every 8), 148; MIP 12, 14 … 24, 28, 32 … 48, 56 … 88. Floor: 14 px AMOLED, 12 px
  MIP (the 390's 14–15 px labels stay 14 on the 360; on MIP they are 12, the smallest Atkinson that keeps 1 / 7 / 3 /
  8 apart on a 202 ppi panel). Big sizes are digits-only atlases. The 390 keeps its approved sizes. Widths are
  measured at the snapped size (`tw` / `bw`), so nothing collides when a size rounds up.
- **Strokes**: AMOLED × s to the half px, never under 1; MIP whole px, hairlines (≤ 1.6 at 390) 1 px, everything else
  ≥ 2 px (rings 4, the chart's line 2 – 3, the rims 5 – 7).
- **Physical size**: the AMOLEDs are 310 – 327 ppi, the MIPs 202 ppi, so a MIP pixel is 1.6× larger: the 260's
  layout is 0.67× the 390's in px but 1.07× in mm. The MIP floors are about px, not legibility.
- **The glance's places** (`S.glance`): the same two as page 1, the main metric's ring and then the last treatment
  (else the second metric, dimmed); never a third. They are decided per class, never per reading: a class keeps both
  when the widest value (288 and its arrow) fits beside them at the smallest value size (58 → 46; 54 → 42 with a row
  under it); otherwise it shows the main ring only, always. Every class keeps both today. The value then takes the
  largest size that fits beside them (the 390: 58 for 175, 52 for 288).
- **Text stays inside the screen and clear of the rim**: every text's box lies 2 px inside the screen's shape, and
  inside the rim where the screen draws one (`clippedTexts`; `app-build.mjs --check` lists any, and the build fails on
  one). Where a screen can't fit something, it gives way by a rule: the alert's value + sparkline narrows the
  sparkline (132 → 96 units), then the value (84 → 68); the alert's *Reminders every 30 min* line under the buttons is
  left out where it would reach the rim (the 218, 240 and 260 MIPs; the button's *Remind as usual* already says it); the
  glance mock's Garmin rows (Heart Rate, Body Battery) are left out where the circle would cut them.
- **The chart's height** scales with s; the hours row and the % labels don't (floors), so on MIP the plot is 6 – 8 px
  shorter than s says. The drop rules (§14.3) give it back its width.

### 14.2 Round AMOLED 360 / 416 / 454 / 466
The 390 scaled. Only the glance and the data field take their device's own geometry:
- **Glance**: the device's content area (`ga`) with the 390's glance (248 × 103) scaled to the tighter of its height
  and width: FR 265S 240 × 104 (scale 0.97), fēnix 8 43 325 × 122 (1.18), fēnix 8 47 349 × 130 (1.26), fēnix 9 Pro 51
  359 × 130 (1.26); the icon area (`gi`) is the device's (40 – 60 px). The epix / FR 265 / Venu style 416 glances
  (274 × 103) take the same rule. Value 50 – 68 px, both places on every one.
- **Data field**: by slot room (§14.4), from the device's own layouts.
- **Buttons**: the class's angles (START 60 – 63°, BACK 118 – 122°, UP 268 – 271°, DOWN 238 – 242°).
- **Two-button rounds** (Venu 2 / 2S / 2 Plus / 3 / 3S / 4, vívoactive 5 / 6, Approach S50 / S72, D2 Air X10; Approach
  S70 has MENU too): no UP / DOWN. Page 1 ↔ 2 and the amount by swipe and tap (the chevrons are the targets; the + / −
  hints go, `cfg.buttons: 'two'`). The D2 Air X10's top button sends no onSelect (device file): hold to log is a touch
  hold there.
- **Buttons only**: Instinct 3 AMOLED 45 / 50 and Crossover AMOLED (390 / 416) have no touch: everything already works
  with buttons (UP / DOWN pages and amounts, START opens and holds to log, BACK leaves). Their glances have no icon
  area (320 × 99, 340 × 92): the content starts at the left. Hold to log starts at their START (69°).

### 14.3 Round MIP, 64 colours (218 / 240 / 260 / 280)
- **Palette** (`PAL_MIP`), each AMOLED token to one of the 64 (channels 00 / 55 / AA / FF), picked for hue and
  sunlight, not nearest: ink FFFFFF, soft AAAAAA, dim / faint 555555 (dim is never text: text in dim draws AAAAAA),
  grid 555555 dithered 50 %, grey AAAAAA (old is also struck / hollow), very low FF0000, low FF5555, tight 00FF55,
  target AAFF00, high FFAA00, very high FF5500, extremely high FF55FF, rapid 55AAFF, long AAAAFF, carbs FFAA55, note
  AAAAAA, on-colour 000000.
- **No alpha**: every tint becomes one of three things. A solid where the tint is ≥ 60 % (the low block 85 % → FF5555);
  an **outline** where text sits on it (the tonal pill → a 2 px outline in the kind's colour; `card2`, a selected row /
  chip / secondary button → black with a 2 px AAAAAA outline); a **dither** where it's a background area (the chart's
  very-good band 13 % → 005500 checkerboard, good 7.5 % → 005500 one pixel in four, the sparkline's band, the remind /
  mute discs, the % column's mask). Partial strokes (the sparkline's overflow fade) step to the colour × 0.55 (555555
  at least). The references run the whole screen through a 64-colour filter, so anti-aliased edges show as the panel
  shows them.
- **Blocks**: the ink on a colour block is white or black, whichever has the higher contrast (`inkOn`): very low
  FF0000 → black 5.3 : 1 (white 4.0); low FF5555 → black 6.7 (white 3.1); extremely high FF55FF → black 8.0 (white
  2.6). The block's secondary buttons become outlines in that ink.
- **No always-on dimming**: MIP is always on in full colour; no AOD palette, no luminance budget. (AMOLED: the data
  field keeps Aura's always-on palette while the activity screen dims.)
- **Contrast** (WCAG, on black): ink 21, soft / grey / note 9.0, low 6.7, very high 6.6, very low 5.3, extremely high
  8.0, rapid 8.6, long 9.9, carbs 11.1, high 11.0, tight 15.4, target 17.0; dim 2.8 (marks only, never text). On the
  very-good band's dither (005500 at most): ink 9.1, every band colour ≥ 2.8, the line drawn over it in full. Pairs that
  are close in colour carry a second cue, as on AMOLED: tight / target (1.1, hue) sit in the same band anyway; high /
  carbs (1.0) — carbs always has its ● mark; low / very high (1.0) sit at opposite ends of the chart and carry words.
- **Smaller screens, what goes first** (`drops()`), page 1: the sensor's days (≤ 260, MIP), then the % column and the
  chart's 70 / 180 labels (≤ 240: the dashed lines stay, the plot takes their width), then the clock (218). Never: the
  value, the arrow, the two places, the chart, the pill, the page dots.
- **Glance**: content only 63 px high on the fēnix 7 family (151 / 171 / 191 wide), 93 on FR 255 / 955, 81 – 88 on fēnix
  8 Solar / enduro, 140 × 79 on FR 255S: the 390 glance at 0.56 – 0.79; value 32 – 42 px (28 – 32 on the FR 255S),
  rings r 13 – 16 px with 12 – 16 px numbers; both places on every one.
- **FR 255 / 255S**: buttons only (as the Instinct AMOLEDs).

### 14.4 The data field: the form by slot room
One rule on every class (`S.dfSlot`, `fitBox`): **whole** when the slot is the screen (the 390's whole page);
**value + sparkline** when a box of **260 × 100 units** (× s px) fits inside the slot and the screen (the box sits at
the slot's side toward the centre, as wide as the circle allows there); otherwise **value + arrow**, the largest atlas
size that fits (label row when ≥ 64 units high). Every size the layout computes snaps to the class's atlas set, the
390 included (76.8 → 80); the approved fixed sizes stay. The 390's half and band are side by side, its quarter and cap
value-only, and so on every round class, AMOLED and MIP (the thresholds are in units, so the MIP slots, 0.56 – 0.72×
in px, keep the 390's forms). Rectangles have no obscured edges: the Venu Sq 2's 97 px slots are under 100 (band and
the 4-field top value-only, half side by side); the Venu X1's 169 / 173 px band and top go side by side. Low / very
low: the slot is the band's block with `inkOn`, cut by the screen's shape like every slot (the field's text keeps
inside it, §14.1).

### 14.5 Rectangles (Venu Sq 2, Venu X1)
Their own layouts in `R` (`R.appA`, `R.appNums`, `R.actions`, `R.logAmount`), the rest the round screens with the
rectangle's edge (`rim`, `full`):
- **Glance**: full-width rows, the icon at the left edge (Sq 2: 40 px at x 0; X1 65 px), content 254 × 114 / 314 × 150.
- **Page 1**: the corners hold the two places (main right, the next left) beside the clock; the value + arrow gets the
  whole width (96 units); the chart runs edge to edge (x −116 … 126 on the Sq 2, 120 units high; the X1's 295 × 165), the % column and its
  shares on the left, 70 / 180 on the right; the pill / sensor row at the bottom; the page dots in the bottom-left
  corner.
- **Page 2**: chips across the top, the stacked bar down the left edge, the three shares with their goals, then
  average / GMI / CV across the bottom.
- **The list**: full-width rows (selected 64 high), the last entry right-aligned; Sukrara top-left.
- **Amount**: no UP / DOWN: the chevrons are full-width buttons above and below the number (tap; swipe up / down too),
  the usual amounts, *Now*.
- **Hold to log / sending / logged / alerts / mute**: the round screens; the rim is the screen's rounded rectangle and
  hold to log fills it clockwise from START (the top-right button). Alert buttons widen to the screen.
- **Data field**: §14.4.
- **Buttons**: START top right, BACK bottom right; the hints are bars on the right edge at the button's height (Garmin's
  `hint_button_right_top / bottom`). Swipes: up / down pages, right = BACK. The Venu Sq 2's top button has no onSelect
  in the device file: hold to log is a touch hold there (test on the watch).

### 14.6 Every screen per class
| Screen (§1) | Round AMOLED 360 – 466 | Round MIP 218 – 280 | Rectangles |
|---|---|---|---|
| Glance | the device's content area (§14.2), both places | 63 – 93 px high, both places | full-width row, both places |
| Page 1 | scaled | scaled, drops (§14.3) | own (§14.5) |
| Page 2 | scaled | scaled | own |
| The list | scaled | scaled, selected row outlined | own |
| Amount | scaled (two-button: no + / −) | scaled | own, chevron buttons |
| Hold to log, sending, logged, queued | scaled, rim from START | scaled | rounded-rect rim from START |
| Mute: lengths, muted, undo | scaled | scaled, outlines | the round ones, full width |
| Garmin's notification | Garmin's (illustrative) | Garmin's | Garmin's (none on the Sq 2, API 5.0: §13 decided 2) |
| The app's alert | scaled | block ink by `inkOn` | full-width buttons |
| Data field | by room (§14.4) | by room | by room |
| Complications | the face's | the face's | the face's |

