# Glucose alerts — shared spec (investigation 15)

One rule set for **Sukrara for Android** (wearer + follower) and **Sukrara for Mac**. Report: [docs/15](../../../docs/15-alerts.md).

## 1. Alert kinds

| `k` | Fires when (reading ≤ 15 min old) | Default on | Default threshold | Default re-alert | Sound mood |
|---|---|---|---|---|---|
| `very_low` | `sgv < mgdl` | on | 55 mg/dL | 5 min, alarm volume | *game over*: longer (~3–4 s), unmistakable, not harsh |
| `low` | `sgv < mgdl` (and not very low) | on | 70 mg/dL | 5 min | *lost a life*: short, descending |
| `high` | `sgv ≥ mgdl` (and not very high) | on | 180 mg/dL | 15 min | *final lap*: short "speed up" jingle (frequent → brief) |
| `very_high` | `sgv ≥ mgdl` (and not extremely high) | on | 250 mg/dL | 10 min | *boss incoming*: a bit longer than high |
| `extremely_high` | `sgv ≥ mgdl` | on | 300 mg/dL | 5 min, alarm volume | *final boss*: the most insistent high, never muted |
| `falling_fast` | `rate ≤ −rate` | off | 2.0 mg/dL/min | 30 min | quick down sweep |
| `rising_fast` | `rate ≥ +rate` | off | 2.0 mg/dL/min | 30 min | quick up sweep |
| `no_data` | no reading for `min` minutes since the last reading, or since the reading source started when that is newer (a source that never delivers a first reading alerts too), unless the sensor has ended or is warming up (core/SensorLife; a running one clears) | on | 20 min | 60 min | *connection lost* blip |

The defaults are the user's own settings (2026-10-18). Lows are **strictly below** the threshold (low 70 alerts at
69, not at 70); highs are at or above it.

**The source's start.** No data needs something to count from: the last reading, or the moment the reading source
started (a sensor paired and connecting, Juggluco chosen or reached, a follow the wearer allowed), whichever is newer,
until the source delivers its first reading; from then on its readings alone (a first reading that arrives already old
counts as it is, never postponed to the start). So a sensor paired but silent from the start alerts `min` minutes
after it started, and a new source never alerts at once because of an older source's last reading. The same start
given again changes nothing. Before any source starts nothing counts (scenarios `other.txt`).

**The clock.** A device clock set back (by more than a minute: NTP corrections are ignored) moves every time the rules
keep back with it: a repeat, a treatment or mute window, Mute all, the last reading's age and the source's start stay
as far away as they were, measured exactly on a clock that never jumps (Android `elapsedRealtime`, Mac
`CLOCK_MONOTONIC`; the first input after a restart has none, so what was due comes at most one input later). A reading
dated after now counts as taken now. A clock set forward needs nothing: what came due alerts at once (scenarios
`other.txt`).

**LO / HI.** A sensor that reads below / above what it can measure sends a flagged reading, LO (`sgv < 40`, sent as
39) or HI (`sgv > 400`, sent as 401), never a made-up number. Its real value is beyond every threshold: LO matches
every low kind and HI every high kind whatever their thresholds, so the most severe one turned on (very low /
extremely high) alerts at once. The card shows "LO" / "HI" (scenarios `levels.txt`).

- **Level alerts** (`very_low`, `low`, `high`, `very_high`, `extremely_high`): at most one is active, the most severe
  that matches. **Rate alerts** are independent of level alerts; `rate` is the reading's trend in mg/dL/min. No rate →
  no rate alert, but a fresh reading without a rate (a LO / HI is sent without one) is no sign the fast change stopped:
  an open rate episode stays as it is (its alert stays up, its repeat stays due, its hold doesn't run down) and a rate
  side's Mute / Remind less isn't back by it (scenarios `other.txt`). `no_data` is checked by a timer, cleared by the
  next reading.
- **Episodes.** Each group (level, rate, no data) has at most one episode: a stretch of time on one *side* (lows,
  highs, falling fast, rising fast, no data). It starts when a kind of that side matches and ends once nothing of that
  side has matched for the **hold time** = the kind's `repeatMin`, at least 5 min. A reading back in range for a
  moment clears the notification but keeps the episode: no new "first" alert, the repeat count goes on, and the
  treatments logged in the episode still count.
- **When it alerts**, within an episode:
  - the first match: at once;
  - a more severe kind of the same side: at once (low → very low, high → very high → extremely high); getting better
    (very low → low) doesn't alert, the next alert is the repeat;
  - then every `repeatMin` minutes counted from the last alert (0 = once per episode);
  - **a treatment restarts the count**: logging the treatment that answers it (§2) clears the alert, and when that
    treatment's mute window ends and it still matches, it alerts **at once**; the repeats count from that alert. With
    the mute off a treatment doesn't change the timing.
- **Waiting before the first alert** (#37, `persistence`; off by default): a value hovering at a threshold gave a
  stream of alerts, and alarm fatigue is the most common reason people stop using diabetes technology (Shivers et al.,
  J Diabetes Sci Technol 2013;7:789; a 2025 review in *Children* 12:1668: 26–50 % of users). So a level side may wait:
  its episode's **first** alert comes only once the side has been out of range for `lowMin` (lows) / `highMin`
  (highs) minutes **in a row**, counted from the reading that took it out; a reading back in range by the margin
  (below) starts the count again. Repeats and a more severe kind after the first alert don't wait (above). **Very low
  and extremely high never wait, nor does a LO or a HI**: the safety net is never delayed. What others do: Dexcom G7
  delays only its High alert ("Delay 1st Alert", 15 min–4 h), never a low; LoopFollow's "persistent minutes" (default
  0) want every reading in the window past the threshold; xDrip+'s persistent high waits 60 min; Libre and Nightscout
  have none. Limits: lows 0–15 min, highs 0–240 min, both 0 (= at once) by default, today's behaviour.
- **Back by the margin** (#37, `persistence.marginMgdl`, default 0 = today's behaviour): a level episode counts as back
  in range only once a fresh reading is at least the margin inside the threshold (lows ≥ the highest enabled low
  threshold + margin, highs < the lowest enabled high threshold − margin; a LO / HI never). Between the threshold and
  the margin nothing alerts and the notification goes (the value isn't past the threshold), but the episode holds (its
  hold time doesn't run) and a waiting first alert keeps counting, so a value bouncing 68–73 around a low threshold of
  70 with a 5 mg/dL margin is **one** episode: no new first alert, the repeat count goes on. No product was found with
  such a band (the closest is xDrip+'s "heading in the right direction" check); a Libre reads within a few mg/dL, so
  5 (lows) to 10 (highs) is the sensible range to suggest.
- **Night alerts** (#38, `night`; off by default): a second set of every kind's rule (on, threshold, repeat, like an
  alarm) in force every day from `from` to `to`, local time of the device that runs the rules (the window may cross
  midnight; `from` = `to` is never; 22:00–07:00 when first turned on). **Very low is always on at night** (its night
  switch isn't offered; a stored `off` reads as on); everything else (the quiet rules, the waits near a threshold, the
  notification actions, the sounds) is the same day and night. The rules switch at the window's edges: the timer wakes
  then and what matches under the new rules counts at once, as after a settings change (a high of 200 clears at 22:00
  when the night high is 250; at 07:00 it alerts again, at once or at its episode's repeat). They are rules: the
  wearer's run for the followers uses them (a follower in *Wearer* mode alerts as the wearer's phone does) and they are
  mirrored like the thresholds (no data's own night rule and *like an alarm* stay each device's). While they apply,
  every surface that shows alert states says so: "Night alerts until 7:00" (Android home under the card, Settings ›
  Alerts › Night alerts; the Mac panel and Settings › Alerts), with the way to change or leave them (the night page:
  its switch, its hours). What others do: Dexcom G7's Alert Profiles (one second profile, by hand or on a schedule;
  urgent low can't be turned off; signal loss is shared), G6's Alert Schedule, xDrip+'s per-alert active hours,
  LoopFollow's day / night (night from 22:00 to 06:00), Nightscout's DAY_START 7 / DAY_END 21, Medtronic Guardian
  Connect's day and night high alerts.
- **Headline** (the notification title, the same on every platform, generated by the receiver from `k` and `tr`):
  `Very low glucose`, `Low glucose`, `High glucose`, `Very high glucose`, `Extremely high glucose`, `Falling fast`,
  `Rising fast`, `No glucose data`. Level alerts acknowledge the treatments logged in the episode (or within the
  mute length before it started), whether or not they muted anything: lows `· eat more` (1 carbs entry), `· eat more
  again` (2 or more); highs `· still high after insulin` / `· still high after more insulin`. Highs never suggest more
  insulin (stacking rapid doses is dangerous). Body: the value and arrow only (`69 mg/dL ↘`); fast changes add the rate
  (`120 mg/dL ↓ · falling 2.4 mg/dL/min`); no threshold, no "from the wearer's phone" (user, 2026-10-18).

## 2. Mute rules

Extremely high and no data are never muted by a treatment: extremely high is the ceiling no treatment rule can
silence. Very low is muted by carbs (2026-10-18: you ate, give it time; extremely high stays the safety net on the high
side). The user's own Mute / Mute all (below) cover them too (round f30).

| Option | Default | Applies to | Rule |
|---|---|---|---|
| `muteAfterInsulin` | on, 60 min | `high`, `very_high`, `rising_fast` | A `rapid` treatment with `now − min < t ≤ now` (not deleted) mutes them. |
| `muteAfterCarbs` | on, 15 min | `very_low`, `low`, `falling_fast` | A `carbs` treatment with `now − min < t ≤ now` (not deleted) mutes them. |
| `muteFallingHigh` | on, 20 min | `high`, `very_high` | `rate < 0` and `sgv + rate × min < high.mgdl` (it will be back below the high threshold within `min` minutes at the current rate). Re-evaluated on every reading. It only postpones: when it ends, an alert that came due meanwhile fires at once; it doesn't restart the count. |

**Notification actions** (round f30; the user, 2026-10-21: "two kinds of buttons … Mute 30 min doesn't conceptually
belong there"). Android's guidance: "A notification can offer up to three action buttons … these action buttons must
not duplicate the action performed when the user taps the notification"
([build-notification#Actions](https://developer.android.com/develop/ui/views/notifications/build-notification#Actions));
lengths to pick from are a direct reply's choices
([RemoteInput.Builder#setChoices](https://developer.android.com/reference/android/app/RemoteInput.Builder#setChoices)).
So every alert notification, every kind, has exactly **two** actions, the same on Android and Mac:

| Button | Effect |
|---|---|
| *Mute…* | One action whose choices are the lengths: Android a direct reply with the choices `muteMin` first, then 15 min / 30 min / 1 h / 2 h (written by the one duration formatter) and free typing; Mac a text-input action (empty = `muteMin`). Typed text is read by the shared duration parser (`45`, `1 h 30`, `1:30`, `2h`; spikes/durations/durations.json), 1–720 min; anything else changes nothing. |
| *Remind less* (Mac *Remind Less*; es *Avisar menos*) | The side's repeats come every `max(repeatMin, relaxMin)` minutes, counted from the last alert. Shown when it would slow anything (the kind repeats faster than `relaxMin`); once relaxed the same slot is *Remind as usual*. The interval is said in the secondary text (Android: the notification's subtext "Reminders every 30 min"), never in the button. |

**Copy** (round f31, #42): the alert notification's words and every mute / remind-less state line are in
[`copy.json`](copy.json), en + es: the title (headline and treatment context), the body (value and arrow in the
person's unit, the fast kinds' rate, LO / HI, no data's time and last value, *No reading yet* for a source that never
delivered one), the actions (the Mac's in HIG title case with an ellipsis where text input follows), the test alert's
title and what Mute all covers (*Very low and no data included, on this phone only* / *on this Mac only*). Each line is
built by one function per platform (Android `AlertCopy` / `MuteText`, Mac `AlertText` / `MuteCopy`), and both check
every case in both languages (Android `AlertCopyTest`, Mac `MuteCopyTests` and `AlertCopyTests`). Every muted state
counts down (Android notification chronometer, app and Mac rows / pill tick each minute); Mac notifications say
"Until 10:42".

**Scope and end** (the user, 2026-10-21: "if the user is high, mutes for 2 h, injects insulin and then goes low, the
low would be muted, and we don't want that … I don't want muting to stop when merely back in normal range"):

- Mute and Remind less act on **one side**: lows (low and very low), highs (high, very high, extremely high), falling fast,
  rising fast, no data. A high's Mute can never quiet a low, and vice versa.
- They cover **every kind of the side**, very low and extremely high included (the user's explicit decision, round
  f30; before, the notification never muted those two). Treatment mutes are unchanged: rapid insulin never quiets
  extremely high.
- They don't end with the episode. A Mute ends at its time; Mute and Remind less both end once the side is **back by the
  margin**: lows at least `backMgdl` above the low threshold (the highest enabled low-side one), highs below the high
  threshold (the lowest enabled high-side one) minus `backMgdl`, a rate side while its rate doesn't match, no data while
  readings come — for `backMin` minutes in a row (fresh readings; the engine reports `back <side>`). A reading that
  crosses back out before that restarts the count, so glucose that oscillates at the edge stays quiet. Defaults:
  **10 mg/dL for 10 min** (a Libre's reading noise is a few mg/dL; 10 minutes is two of the shortest repeats).
- A Mute quiets like a treatment mute: the alert clears; when it ends and the side still matches it alerts **at once**
  and the repeats count from there. Together with a treatment mute, the later end wins.

**Mute all** (round f30; the user: "a global mute button with a time option for when alerts turn back on
automatically (Never is also an option)"; round f31: "the Never option makes no sense, it should be always"): from the Android home screen and the Mac's dropdown, on that device only.
Lengths *For 30 min* / *For 1 h* / *For 2 h* / *For 4 h* / *For 8 h* / **Until you turn it off** (round f31, after AOSP Do Not Disturb's "Until you turn off"; es *Hasta que lo desactives*) / custom (5 min–24 h, schema `muteAll`). It
quiets every kind, no data included; when it ends whatever still matches alerts at once. It is visible wherever alerts
show: the home control / dropdown row ("Alerts muted · 1 h 12 min left", counting down; "Alerts muted until you turn it off"), the card's pill on
every surface (`muted`, "Muted · 1h 20m" in range; the paused badge out of range), the Mac menu bar.

After *Mute…* Android replaces the alert's notification with a silent "Low alerts muted" / "Until 10:42" with a count-down chronometer to the end (round f31), whose two buttons
are the ways out, *Unmute* and *Change time* (the same Mute… input); it goes when the mute ends (or the alert comes back). Android
asks an app to update a notification after a direct reply. macOS removes a notification once any of its actions is
used, so the Mac posts an alert that is still up again without sound after *Remind less* / *Remind as usual* (with the
other button) and after a *Mute…* reply that isn't a length (nothing changes): an action never makes an active alert
vanish. The history records "Low muted for 1 h (notification)", never "Low cleared": the episode goes on.

**The actions act on the device they're tapped on only** (user, 2026-10-21: "Mac notifications should work on their
own. Mac and Android don't have to be from the same person, so if someone wants to mute, it shouldn't mute the
other"): its notifications, its badge / pill and its history. Nothing about them is sent:
- On the **wearer** they change only the phone's own notifications. What goes to the followers (§4) is what the rules
  decide **without** them: the wearer runs the rules twice on the same readings and treatments, once for its own
  notifications (with its Mute / Remind less / Mute all) and once for the followers (without).
- On a **follower that mirrors the wearer** (`source` "wearer"; a phone or the Mac) they quiet the wearer's alerts on
  that device (dropped while muted; while relaxed, a repeat shows only if `relaxMin` has passed since the last one
  shown, a more severe kind always shows). They end by the same time and margin rules on the readings that device
  gets (not with the wearer's episode). The wearer's `clear` removes the alert shown there even when a repeat in
  between was skipped.
- On a follower with its **own** rules (`source` "own") they act on its engine, like on the wearer.

**States and exits** (round f29, f30). Every quiet state has a visible place and a way out on every device, all acting
on that device only:

| State | Shown | Ways out |
|---|---|---|
| Normal (alerting) | the notification; the "Eat carbs" / "Take insulin" pill | *Mute…*, *Remind less* (notification; the app's sheet / Active now / Mac panel) |
| Side muted (N left) | Android: the counting-down "Low alerts muted" notification; the tonal pill "Nm left"; the line "Low alerts muted · 12 min left" (ticks); sheet, Active now; Mac panel rows, pill menu, Settings › Alerts | *Unmute* · *Change…* / Change to 15 min / 30 min / 1 h / 2 h · ends at its time or back by the margin |
| Quiet after carbs / rapid insulin (shared data) | the tonal pill, the last-treatment ring; the line "Low alerts muted after carbs · 12 min left" (ticks, round f33); Active now | *Alert me anyway* (this device only, the treatment stays) · ends at `t + min` · deleting the treatment |
| Quiet: falling back into range | "High alerts muted while falling back into range"; Active now | *Alert me anyway* · ends when the prediction no longer holds |
| Side relaxed | Android: a "Low relaxed: reminders every 30 min" chip under the card; the notification's *Remind as usual*; Mac panel row; Active now | *Remind as usual* · ends back by the margin |
| Muted + relaxed | both of the above | each exit on its own; the margin ends both |
| All alerts muted (for a time / until you turn it off) | home control (Android) / dropdown row (Mac): "Alerts muted · 1 h 12 min left" (ticks) / "Alerts muted until you turn it off"; the `muted` pill on every card surface; the Mac menu bar; Active now | *Unmute* · *Change time* (a new length) · ends at its time (never with Never) |
| Mute ending | — | the alert comes at once if it still matches |
| Back by the margin | history "Low back in range: mute and relax ended" | — (nothing stays) |

- **Unmute / Alert me anyway** (`unmute`): the side's Mute ends and whatever quiets its episode now is lifted: the
  treatments logged so far and the falling-high mute, for the rest of the episode (a treatment logged later mutes
  again). Still matching → it alerts **at once** and the repeats count from there; back in range → nothing, and it
  alerts at once if it matches again within the episode. Nothing quiet → nothing happens.
- **Change** = a new *Mute* on a muted side: the end becomes now + the new length (shorter or longer).
- **Remind as usual** (`unrelax`): the kind's own interval again; a repeat already due under it alerts at once.
- **Unmute** (`unmute all`): Mute all ends; whatever matches and isn't muted otherwise alerts at once, however recent
  its last alert (as when Mute all ends at its time).
- On a **follower that mirrors the wearer** Unmute / Remind as usual / Unmute (Mute all) show the wearer's newest
  alert at once if the wearer still has it up (it was dropped or skipped here). A treatment mute can't be lifted there:
  the wearer's phone decides (the sheet says so and links to the alert settings, where *Settings from* Default /
  Custom runs the rules on this device).
- **Accessibility**: every way out is reachable without the notification: Android as buttons on the home control,
  sheet and Active now and as TalkBack actions on the card; Mac as panel rows, the pill's menu and Settings buttons.

Treatment mutes are different: carbs and rapid insulin are shared data (the treatment log syncs), so a treatment
quiets the wearer, its followers and the Mac alike.

A muted alert is not "fired": no notification here. A treatment-muted one isn't sent to the followers either (they
have the same treatments); a notification-muted one is (above). A treatment mute ends at exactly `t + min`,
and the timer wakes then (Android: an exact alarm; Mac: a one-shot timer), so it alerts on time even between
readings. The UI shows why it's quiet ("High, muted: insulin 12 min ago"); `AlertStatus` (Android) applies the same
treatment rules for the badge under the value. Once *Alert me anyway* lifts a treatment's quiet, every card drops it
at once: no after-treatment pill, and the mute line and ring of the entries logged up to then end at that moment.

## 3. Settings (JSON, same keys on both platforms)

```json
{
  "v": 1,
  "on": true,
  "alerts": {
    "very_low":     {"on": true,  "mgdl": 55,  "repeatMin": 5,  "alarm": true},
    "low":          {"on": true,  "mgdl": 70,  "repeatMin": 5,  "alarm": false},
    "high":         {"on": true,  "mgdl": 180, "repeatMin": 15, "alarm": false},
    "very_high":    {"on": true,  "mgdl": 250, "repeatMin": 10, "alarm": false},
    "extremely_high": {"on": true, "mgdl": 300, "repeatMin": 5, "alarm": true},
    "falling_fast": {"on": false, "rate": 2.0, "repeatMin": 30, "alarm": false},
    "rising_fast":  {"on": false, "rate": 2.0, "repeatMin": 30, "alarm": false},
    "no_data":      {"on": true,  "min": 20,   "repeatMin": 60, "alarm": false}
  },
  "muteAfterInsulin": {"on": true, "min": 60},
  "muteFallingHigh":  {"on": true, "min": 20},
  "muteAfterCarbs":   {"on": true, "min": 15},
  "sounds": {"pack": "quest", "kinds": {}, "shuffle": true},
  "notificationActions": {"muteMin": 30, "relaxMin": 30, "backMgdl": 10, "backMin": 10},
  "persistence": {"lowMin": 0, "highMin": 0, "marginMgdl": 0},
  "escalation": {"on": true, "min": 5, "follow": true},
  "night": {"on": false, "from": 1320, "to": 420,
    "alerts": {
      "very_low":     {"on": true,  "mgdl": 55,  "repeatMin": 5,  "alarm": true},
      "low":          {"on": true,  "mgdl": 70,  "repeatMin": 5,  "alarm": false},
      "high":         {"on": true,  "mgdl": 180, "repeatMin": 15, "alarm": false},
      "very_high":    {"on": true,  "mgdl": 250, "repeatMin": 10, "alarm": false},
      "extremely_high": {"on": true, "mgdl": 300, "repeatMin": 5, "alarm": true},
      "falling_fast": {"on": false, "rate": 2.0, "repeatMin": 30, "alarm": false},
      "rising_fast":  {"on": false, "rate": 2.0, "repeatMin": 30, "alarm": false},
      "no_data":      {"on": true,  "min": 20,   "repeatMin": 60, "alarm": false}
    }},
  "source": "wearer"
}
```

- `night` (#38, §1 "Night alerts"): `on`, `from` / `to` in minutes after local midnight (0–1439; 1320 = 22:00, 420 =
  07:00), `alerts` = every kind's night rule in the `alerts` shape (the day's defaults until changed; `very_low.on` is
  always read as on). Mirrored like the day rules (`on`, `from`, `to`, each kind's `on` / threshold / `repeatMin` but no
  data's); `alarm` and no data's are each device's.
- `escalation` (#39, §4 "No answer to a very low"; each device's, never mirrored): `on` and `min` (5–30, default 5)
  are the wearer's (tell the followers when nobody answers a very low on this phone, after `min` minutes), `follow` a
  follower's (show those escalations here; default on).
- `persistence` (#37, §1 "Waiting before the first alert", "Back by the margin"; rules like the thresholds, so the
  wearer's run for the followers uses them and a follower in *Wearer* mode shows them): `lowMin` (0–15) for low,
  `highMin` (0–240) for high and very high, `marginMgdl` (0–20, both sides).

- `alarm` (per kind, local like the sound, also on a follower that mirrors the wearer): ring **like an alarm** instead
  of as a notification. Android: alarm volume (`USAGE_ALARM`, through "Alarms" in Do Not Disturb);
  Mac: Time Sensitive (breaks through Focus) when the build is entitled to it, else a normal notification.
- `sounds.pack`: the pack every kind uses; `sounds.kinds[k]` overrides one kind (mix and match); `"system"` = the
  platform's default notification sound, `"none"` = silent. `shuffle`: pick a random variant of the pack each time
  (off = always variant 1).
- `notificationActions` (per device, also on a follower that mirrors the wearer): `muteMin` = the first length
  *Mute…* offers (1–720, default 30; presets 15 / 30 / 60 / 120 min, shown as 15 min / 30 min / 1 h / 2 h); `relaxMin`
  = the interval *Remind less* sets (5–120, default 30: slower than every level alert's default repeat, so the button shows
  on all of them; the settings offer only presets slower than every enabled level alert's repeat); `backMgdl` (0–50,
  default 10) and `backMin` (0–60, default 10) = the margin that ends Mute and Remind less (§2). Mute all's state is not a
  setting: the engine keeps it (snapshot) on that device.
- `source` (followers only): `"wearer"` (default) = alert exactly when the wearer's app does (mirror its `alert`
  events, §4); `"own"` = run these rules locally on the followed readings and treatments. **Not chosen on its own**
  (round f28): it is derived from the follower's *Settings from* (`follower.settingsFrom`, spikes/settings/settings.json):
  *Wearer* → `"wearer"`, *Default* / *Custom* → `"own"`. The settings file is per device; in *Wearer* mode the follower
  also shows the wearer's own values of each kind's `on` / threshold / `repeatMin` (not `no_data`) and the three quiet
  rules, from the wearer's settings event (PROTOCOL-v2 "Settings"); *Default* runs the defaults, *Custom* this file.
- In `"wearer"` mode a follower still checks **`no_data` locally** (it's about *its* link to the wearer, which the
  wearer can't report), with its own `no_data` settings.

## 4. `alert` event (wearer → followers)

New ephemeral event, same envelope as **live** (PROTOCOL-v2): kind **21079**, signed by `D`, `["gk", gk_n]`,
expiration now + 1 h, content NIP-44 `D → G_n`:

```json
{"v": 2, "type": "alert", "id": "<16 hex>", "k": "high", "state": "fire", "at": 1760650000,
 "t": 1760649990, "sgv": 192, "r": -0.4, "th": 180, "n": 2, "tr": 1}
{"v": 2, "type": "alert", "id": "<16 hex>", "k": "very_low", "state": "fire", "at": 1760650300,
 "t": 1760650290, "sgv": 52, "th": 55, "n": 2, "tr": 0, "esc": 5}
```

| Field | Meaning |
|---|---|
| `id` | random per firing; a `clear` repeats the `id` of the firing it ends |
| `k` | alert kind (§1) |
| `state` | `fire` (the rules alert, including re-alerts; the wearer's own notification actions don't change it, §2), `clear` (condition left: remove it) or `answered` (#39: the very low `id` escalated with `esc` was answered on the wearer's phone) |
| `at` | when the wearer fired/cleared (unix s) |
| `t`, `sgv`, `r` | the reading that triggered it (`no_data`: the last reading, may be null) |
| `th` | the threshold that matched (mg/dL, mg/dL/min, or minutes for `no_data`) |
| `n` | 1 for the first firing of the episode, 2… for the next ones |
| `tr` | treatments acknowledged in the headline (§1): 0, 1, or 2 for two or more; 0 for rate and no-data alerts |
| `esc` | (#39, `fire` of `very_low` only; absent = not escalated) nobody has answered this very low on the wearer's phone for `esc` minutes |

- Published on **relays** (Lane LIVE, follows the "Nostr" switch, ignores the batching interval: alerts are always
  sent at once) and over **BLE** to subscribed followers (backlog key `alert`, so a follower that connects within
  minutes still gets the newest one).
- Followers accept it with the **same checks as live** (author ∈ `roster.devices`, known `gk`, decrypts, `v == 2`),
  dedupe by event id (it can arrive over BLE and several relays), and **ignore `at` older than 10 min**.
- In `"wearer"` mode the follower posts the same kind with its **own** sound choice (unless its own notification
  actions quiet it, §2); `clear` removes it.
  In `"own"` mode `alert` events are ignored.
- Followers subscribe with `{"kinds":[21078,21079], "authors": roster.devices, "since": now − 120}`.

**No answer to a very low** (#39, `escalation`; scenarios `escalation.txt`). The followers already get every very low at
once. When the wearer's phone shows a very low and **nobody answers it there for `escalation.min` minutes (default 5)**,
the wearer escalates it: its `fire` events for that very low carry `esc` (the minutes without an answer) — the repeat
due then, or the newest firing sent again with the same `id` when no repeat is due. A follower that opted in
(`escalation.follow`, default on) shows it as **"Very low glucose · no response"** at alarm volume (Android the
alarm channel; Mac Time Sensitive when entitled), **through its own Mute, Remind less and Mute all, once**; that
follower's own quiet states apply to the escalated repeats after it (still worded as escalated while shown). The
default is the safest reasonable one: a very low repeats every 5 min (Libre's re-alarm interval too); Nightscout's
Pushover retries urgent alarms every 2 min; Medtronic CareLink Connect texts the care partner after a low isn't cleared
within 0–30 min (0 by default); xDrip+'s experimental emergency SMS waits 60 min; Dexcom Follow only teased "missed
alert" labels.
- **An answer on the wearer's phone**: tapping or swiping the alert, opening the app, any of its notification actions
  (*Mute…*, *Remind less*, *Unmute*), *Mute all*, or logging carbs, after the very low first fired. Not an answer: a
  Mute or Mute all set before it (nobody heard this alert, so it escalates all the same). The wait counts from the
  first very-low firing of the stretch (what the followers got, §2), checked on the timer.
- **Answered after the escalation**: the wearer sends `answered` with the escalated `id`; followers show that very low
  again without the escalation and without sound ("follower answered very_low").
- It ends with the very low: the wearer's `clear` of it, or a less severe kind (the stretch is over). One escalation
  per stretch; a later very low starts a new wait.
- The wearer can turn sending off (`escalation.on`) or wait longer (`min`, 5–30); followers with their own rules
  (*Settings from* Default / Custom) ignore `alert` events, escalations included (their own very low alerts run).

## 5. Sound packs

`spikes/alerts/sounds/` generates every pack from code (`gen.py`, original melodies, no samples). Files:
`<pack>_<kind>_<n>.ogg` (Android, `res/raw`) and `.caf` (Mac, IMA4, in `Sukrara.app/Contents/Resources`; before a
notification the app copies the one it rings to `~/Library/Sounds/Sukrara <pack> <kind> <n>.caf` and names that copy,
because Notification Center didn't find the bundled file and played the default sound; `NotificationSounds.swift`). Every pack has the 7 kinds × 3 variants. Packs:
`arcade` (8-bit), `kart` (racing), `quest` (adventure / RPG, the default), `soft` (calm, non-game).

## 6. Platform notes (details and sources: docs/15)

| | Android | Mac |
|---|---|---|
| Per-kind sound | two notification channels per kind (`alert_n_<kind>` notification volume, `alert_a_<kind>` alarm volume; `alarm` picks one); its sound is `content://<pkg>.alertsound/<kind>`, an exported provider that picks the pack + variant **each time SystemUI opens it** | `UNNotificationSound(named:)` per notification |
| User override | channel settings (sound, vibration, DND) win over the app | none (macOS only has "Play sound for notification" on/off) |
| Category | `alarm`: `USAGE_ALARM` (alarm volume, "Alarms only" DND); otherwise `USAGE_NOTIFICATION_EVENT` (DND "Reminders"). Every alert posts `CATEGORY_ALARM` (exempt from the per-app and post-unlock notification cooldowns, so two alerts in one reading both ring in full) except a notification-volume alert while DND would filter an alarm and a reminder differently: then `CATEGORY_REMINDER` | `alarm`: `.timeSensitive` only with the entitlement, else `.active` |
| Haptics | a primitives-only `VibrationEffect` per channel (falls for lows, speeding-up clicks for high…); an on/off waveform where primitives aren't supported. Haptic channels inside the sound file are muted for notifications | — |

## 7. Scenarios (the regression guard)

The rules are pinned down as **timelines** in [`scenarios/*.txt`](scenarios/), the one source of truth every
implementation runs: Android `AlertScenarioTest` (JUnit, one test per scenario) and the Mac `AlertsTests`.
**Every change to the alert rules adds or updates a scenario**, and every suite must pass:

```sh
cd apps/android && ./gradlew :core:alerts:test
cd apps/macos && swift test --filter AlertsTests
```

Format (one scenario per `scenario` line; `#` starts a comment):

```
scenario The user's timeline: carbs during a low
settings low.repeat=5 carbs.mute=5          # on top of the defaults (§3)
 0  glucose 69   => Low glucose
 3  carbs 5      => clear low
 8               => Low glucose · eat more
15  glucose 70   => nothing
25                                          # the last line's minute is where the run ends (or `end N`)
```

- A line is `<minute>[:<ss>] [events, …] [=> expectations; …]`. Events: `glucose <mg/dL> [rate <mg/dL/min>]
  [age <min>]` (what the sensor reads from now on; without `rate` the readings have no rate), `readings every <n>` (default 1 min), `sensor off|on`,
  `sensor ended` (the sensor reaches its end now; readings stop), `sensor started` (a new sensor starts now, 60 min
  warm-up; readings stay off until `sensor on`), `source started` (the reading source starts now: no data counts from
  it, §1), `clock back <length>` (the wearer's clock is set back: from then on it gets the scenario's time minus the
  length and stamps readings and treatments with it, and it is ticked at once, as the system's clock-change notice
  does; expectations stay on the scenario's minutes, which are also the monotonic clock the driver passes),
  `carbs <g>`, `insulin <u>` (rapid, logged now), `delete carbs|insulin` (the newest one), `set <key>=<value> …`,
  `mute <kind> [<length>]` (the notification's Mute… with `muteMin` or a picked / typed length, read by the shared
  duration parser: `10`, `2h`, `1 h 30`; on a muted side: Change), `relax <kind>` (Remind less), `unmute <kind>` (Unmute /
  Alert me anyway), `unrelax <kind>` (Remind as usual), `mute all <length>|never` (Mute all), `unmute all` (Turn alerts
  back on), `follower mute|relax|unmute|unrelax <kind> [<length>]` and `follower mute all …` / `follower unmute all`
  (the same on the mirroring follower, `mirror` only),
  `ack <kind>` (#39: the wearer taps, swipes or opens the alert of that kind: an answer on the wearer's phone),
  `restart` (the app process restarts: a new engine, seeded with the last reading, restores the old one's snapshot).
- `at HH:MM` (a line of its own, like `settings`): the wearer's local time at minute 0 (the drivers run in UTC on the
  same day; without it minute 0 is 08:54), for the night window.
- `mirror` (a line of its own, like `settings`): also run a follower (a phone or the Mac) that mirrors the wearer:
  the wearer's second engine, which gets everything but the wearer's `mute` / `relax` / `mute all` (what it publishes,
  §2), feeds the platform's `AlertMirror`, which also gets every reading (its Mute / Remind less end by the margin). Its
  actions are expected as `follower <headline>` / `follower clear <kind>` / `follower back <side>`, compared
  apart from the wearer's (the order between the two within a minute is free); the follower's own `no_data` isn't run.
- Settings keys: `alerts=on|off`, `<kind>=on|off|<threshold>`, `<kind>.repeat=<min>`,
  `carbs.mute|insulin.mute|falling.mute=off|<min>`, `mute.min=<min>`, `relax.min=<min>`, `back.mgdl=<mg/dL>`,
  `back.min=<min>`, `persist.low=<min>`, `persist.high=<min>`, `persist.margin=<mg/dL>`, `night=on|off`,
  `night.from=HH:MM`, `night.to=HH:MM`, `night.<kind>=on|off|<threshold>`, `night.<kind>.repeat=<min>`,
  `escalate=off|<min>` (the wearer's), `follower.escalate=on|off` (the follower's opt-in).
- Expectations: the fired headline (§1; a follower's escalated very low is `Very low glucose · no response`, #39),
  `clear <kind>`, `back <side>` (the side's Mute / Remind less ended: back by the margin), `follower answered <kind>`
  (the escalation answered on the wearer's phone: shown again plain, without sound), or `resume <kind>` (a `restart` shows that alert again without sound), in the order they happen; `nothing` (or none) = no
  action. **Strict**: every minute without a line must have no action, and every action must be listed.
- Driver (same on both platforms): time starts on a whole minute. At each instant, first the line's events in order
  (a treatment, a deletion or a `set` re-evaluates at once, like the apps do when the log or the settings change),
  then a reading if one is due (the first at the first `glucose`, then every `readings every` minutes while the sensor
  is on), otherwise the engine's timer if it is due, otherwise (`mirror`) the follower's own timer (its Mute or Mute
  all ending). The next instant is the earliest of the next line, the next reading and both devices' `nextCheckAt`.
  Actions are compared per minute.
- A follower's Mute all that ends at its time shows the wearer's newest alert still up at once, on its timer or on the
  first input at or after its end, whichever comes first (scenarios `devices.txt`).
