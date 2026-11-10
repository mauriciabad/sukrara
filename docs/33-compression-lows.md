# 33 — Night compression lows: notice them, never silence them

*2026-11-10 · investigation report · issue #52 · follow-up #164 · literature (Mensh 2013, Helton 2011, Facchinetti
2016, Baysal 2014, Bequette 2014, Kulzer 2024, Eichenlaub 2024, Lobo 2026, Idi 2025, Manzoni 2025, ADA Standards of
Care 2026), patents (Dexcom, Insulet, UVA, Stanford), the sources of xDrip+, xDrip4iOS, AndroidAPS, Loop, iAPS,
Nightscout, Nocturne and the ghost-lows benchmark, patient forums · a detector prototyped over synthetic nights
([`investigations/52-compression-lows`](../investigations/52-compression-lows)); no real health data*

## Goal

3 a.m. The phone rings *Low glucose · 58 ↓↓*. Either the glucose really is low and the person must eat now, or they
have been lying on the arm with the sensor: pressure squeezes the blood out of the tissue around it, the sensor reads
a fall the body doesn't have (a **compression low**, or *pressure-induced sensor attenuation*). They wake, check with a
meter or eat sugar they don't need (and wake high), and after enough nights some turn night alarms off, until the night
one is real.

Issue #52: flag a sudden night drop that recovers fast as a possible compression low, **without suppressing the
alert**, to take some of that weight off. Find out what is known, what others do, how well a detector can tell the two
apart, and how to show it.

## Short answer

- **Common and recognisable.** Roughly one every 3-6 nights of wear (Navarathna 2023: "2 events every 10 days";
  Lobo 2026: 1,260 dips below 85 mg/dL in 94,201 h; a sensor maker: "once every 5 to 6 days"), mostly at night. Median
  depth 24 mg/dL (10-48), median length 45 min (30-70) (Facchinetti 2016); the reading comes back within ~15-30 min of
  turning over (Mensh 2013). Shape: a first-order fall to a floor and back, a "binary" switch from a flat line.
- **Nobody detects them live, and those who tried warn against it.** xDrip+, xDrip4iOS, AndroidAPS, Loop, Trio, iAPS,
  Nightscout and Juggluco have no detector. The next Nightscout (Nocturne) suggests them **after the night**, for the
  person to accept or dismiss. Research detectors find 55-60 % of them on real data with 0.3-1.15 false flags a day;
  the ghost-lows benchmark: published detectors "reduced false alarms only by missing severe lows". Every DIY
  maintainer who commented: "err heavily on the side of assuming that a drop is real".
- **Prototype result (synthetic nights): at the moment of the alarm, no rule tells pressure from a real low safely.**
  Pressure on top of a real low, the dangerous mix, looks like pressure at the alarm in about half the cases, whatever
  the rule. Any label, hint or softer repeat at that moment would sit on real lows.
- **After the rebound it works well enough to ask, not to state.** A V-shape rule alone (what Nocturne does) labels
  about half of the real lows treated with carbs nobody logged. Two physiological rules fix most of that: the fall
  must *start* abruptly (real glucose can't change its rate faster than ~0.1-0.2 mg/dL/min²) and, when the alarm rang,
  the reading must start coming back within 10 min (someone woken turns over at once; carbs take 15-30 min to show).
  With both, 1-min readings: about half of the compressions labelled (46 % of those that rang the alarm), 4 of 800
  real lows labelled, and never one whose carbs were logged. What still fools it: carbs eaten and not logged, mostly
  by someone awake who ate as it fell.
- **Recommendation: build the after-the-night question, change nothing in alerts** (#164). The alarm rings as
  today. Later, a quiet card: *"03:12: fell to 58 and came back in 9 min, no carbs logged. Pressure on your sensor?"*
  *Yes, I was lying on it* / *No, I ate* (logs the carbs) / dismiss. Only the person's *yes* puts a "pressure" mark on
  the dip; the mark never changes an alert, a statistic or a report.

## 1. What a compression low is

| | Source |
|---|---|
| Pressure "may decrease blood flow and oxygen tension"; readings > 25 mg/dL off the other sensors were "strongly correlated with subjects lying on the sensors", mostly drops; dips of ~40 mg/dL lasting 30-60 min, back within ~15-30 min of a position change | [Mensh 2013](https://pmc.ncbi.nlm.nih.gov/articles/PMC3879750/) (4 sensors per person, filmed sleep) |
| "When the subject rolled off the sensor, the sensor signal recovers" (pig and human) | [Helton 2011](https://pmc.ncbi.nlm.nih.gov/articles/PMC3192630/) |
| Median duration 45 (30-70) min, amplitude 24 (10-48) mg/dL, more frequent at night; "output of a first-order linear dynamic system driven by a rectangular function" | [Facchinetti 2016](https://doi.org/10.1089/dia.2015.0250) (72 people, Dexcom G4) |
| "rapid, nonphysiologic decreases with first-order behavior", attenuated "roughly 15-30 minutes" | [Bequette 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4455470/) |
| "This can lead to alarm fatigue, ie, users tend to ignore or turn off alarms during the night" | [Kulzer 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11418455/) |
| Night lows are "significantly longer (60 vs 35 minutes) and enveloped by less rapidly changing glucose levels" | [Eichenlaub 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11418509/) |
| Glucose changes faster than 2 mg/dL/min ~10 % of the time around meals and "more gradually at night"; a "maximal sustained rate … of about 4 to 5 mg/dL/min and a maximum acceleration … of about 0.1 to 0.2 mg/dL/min/min" | Dunn 2004 (PMID 15333478); [Dexcom US 10,231,659](https://patents.google.com/patent/US10231659B2/en) |
| "When low sensor glucose readings occur … without associated symptoms, causes of artifactual hypoglycemia should be investigated, such as compression of a CGM sensor during sleep" | [ADA Standards of Care 2026, §6](https://pmc.ncbi.nlm.nih.gov/articles/PMC12690178/) |

The overlap that makes it hard: lead-ins to severe real lows fall at 2.5-5 mg/dL/min (Li 2026, preprint), the same
range as compression onsets (Baysal: -1.9 to -3.1 mg/dL/min). Rate alone can't separate them.

## 2. What exists

| Who | What | Note |
|---|---|---|
| [Baysal 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4455457/) (Stanford, [US 9,227,014](https://patents.google.com/patent/US9227014B2/en)) | start: rate < -2 mg/dL/min and (previous rate > 0 or rate ratio > 1.5); end: rate back up with the reading above a Kalman estimate | per *reading* 64-88 % detected, 1.7-7 % false; built to keep a pump from suspending, not to inform a person |
| Idi 2025, Manzoni 2025, Lobo 2026 | isolation forest / Bayesian smoothness / screening + probability | real data: 55 % found with 3 false alarms in 10 days; 57 % with 1.15 false a day; PR-AUC 0.59 |
| UVA ([US 2024/0139415](https://patents.google.com/patent/US20240139415A1/en)) | drop > 7.5-10 mg/dL in 5 min, rise 15-180 min later, a classifier | output includes **disabling the alarm**: the approach Sukrara rejects |
| Dexcom patents | rate + membrane resistance, temperature, accelerometer, force sensor | sensor-side signals Sukrara doesn't get; responses: prompt to change position, blank the value, still wake with the alarm when unsure |
| Dexcom G7 / G6, Abbott | user guide: "Relieve the pressure and the numbers should get closer"; Libre's *Check blood glucose* symbol: "there may be times when you get a low glucose reading but you do not actually have low glucose" | no detection described; advice is a finger stick when readings don't match symptoms |
| xDrip+, xDrip4iOS, AndroidAPS, Loop, Trio, iAPS, Nightscout, Juggluco | no detector (searched for "compression", "PISA"); smoothing filters only | AAPS #4965: "if you have a false positive … you have a very dangerous situation"; Loop #2120: "err heavily on the side of assuming that a drop is real" |
| Nightscout Nocturne | `CompressionLowDetectionService`: per night, in the sleep window: nadir < 70, ≥ 2 mg/dL/min for ≥ 10 min, back to 80 % of the level within 60 min; a score; **Pending → Accepted / Dismissed** by the person | the closest precedent; never live, never touches alerts |
| ghost-lows benchmark (2026) | Baysal + matched filter must agree, night, depth ≥ 10 mg/dL; "a flag never says a low was not real" | "the rate rules alone also fire on genuine fast falls" |

## 3. What people say

- "I turned off the Dexcom app and Loop to avoid what I thought would be false low alarms" and then "survived a
  serious hypo, the worst in years" ([FUDiabetes, 2026](https://forum.fudiabetes.org/t/alarm-fatigue-and-the-boy-who-cried-wolf/16148)).
  This is the harm to design against: false alarms teaching people to silence real ones.
- "I have to silence the alerts at night because it goes off multiple times saying my BS is in the 50s"
  ([Trustpilot, Dexcom, 2026](https://uk.trustpilot.com/review/dexcom.com?page=6)); "I've had to disable my low glucose
  warnings unfortunately … It was wrecking my sleep" ([diabetes.co.uk](https://www.diabetes.co.uk/forum/threads/libre-2-alarms-every-night.185170/)).
- A parent: "compression low once or twice per week. I have to go in and flip him over like a pancake … he goes from
  smooth and level to crash and burn in a single tick or two" ([FUDiabetes](https://forum.fudiabetes.org/t/false-compression-lows/12624)).
- Woken by an urgent low, the meter read 112: "avoided spiking myself un-necessarily"; "it will go back to the right
  value in about 15 minutes" ([FUDiabetes](https://forum.fudiabetes.org/t/compression-low-on-dexcom-g6/10966)).

What they do: turn over, check with a meter, wait 15 minutes, or silence alarms. What they ask for: fewer false night
alarms without missing real ones, and to know afterwards which ones were pressure.

## 4. The prototype

[`investigations/52-compression-lows`](../investigations/52-compression-lows), Python standard library.

**Synthetic nights** (`traces.py`), 23:00-07:00, a reading a minute (Libre 3) or every 5 min (a follower of a
Dexcom / Nightscout source). Blood glucose drifts slowly; the sensor reads it 4-9 min late (interstitial lag) with
smooth noise, LO / HI and missed readings. Kinds of night (400 each):

| Kind | What happens |
|---|---|
| compression | 1-3 pressure dips: first-order, the same time constant down and up (5-20 min), depth 15-90 mg/dL, held a median 45 min; when the dip makes the alarm ring, 75 % wake and turn over within 4 min |
| true_low_slow / true_low_fast | the glucose falls 0.3-1.2 / 1.5-3.5 mg/dL/min, its rate building up at most 0.08-0.2 mg/dL/min², to 42-66 mg/dL; then carbs a few minutes after the alarm, logged (45 %) or not (30 %), eaten before the alarm by someone awake and watching (10 %, not logged), or nothing (15 %, the body brings it back slowly) |
| compression_on_low | pressure while the glucose is already sliding into a real low |
| correction_fall | a fast fall from a high after a correction bolus that levels off in range |
| quiet | only drift and noise |

**The detector** (`detector.py`), one reading at a time, two outputs, both annotations only:

1. *Suspect* at the fall: ≥ 25 mg/dL below the level of the last 20 min, a 5-min stretch ≥ 2 mg/dL/min, the 15 min
   before not already falling (> -0.8 mg/dL/min), no rapid insulin in the last 3 h, and an **abrupt onset**: two
   5-min rates in a row (least squares) ≥ 2 mg/dL/min apart.
2. *Compression* after the rebound: back to within max(15 mg/dL, 12 %) of the level and ≥ 80 mg/dL, rising ≥ 1.5
   mg/dL/min somewhere, staying there 10 min without overshooting the level by 35, within 150 min; **no carbs logged**
   since the fall; and, if the dip went below 70 (the alarm rang), **the rise started within 10 min of the first low
   reading**.

## 5. Results

![Four synthetic nights: a compression labelled after the rebound; a real fast low with unlogged carbs, not labelled;
pressure on top of a real low, not labelled; a real low with carbs eaten while awake and not logged, wrongly
labelled](img/33/examples.svg)

**Libre 3, a reading a minute, 400 nights per kind** (`evaluate.py`):

| Night kind | Events | Low alarm rang | Suspect at the alarm | Labelled | Labelled, of those that rang | Labels over a real low |
|---|---|---|---|---|---|---|
| compression | 626 | 213 (34 %) | 167 / 213 (78 %) | 318 (51 %) | 99 / 213 (46 %) | 0 |
| true_low_slow | 400 | 354 (88 %) | 1 / 354 (0 %) | 1 (0 %) | 0 / 354 | 0 |
| true_low_fast | 400 | 396 (99 %) | 58 / 396 (15 %) | 3 (1 %) | 2 / 396 (1 %) | 3 |
| compression_on_low | 400 | 395 (99 %) | **221 / 395 (56 %)** | 20 (5 %) | 19 / 395 (5 %) | 7 |
| correction_fall | 400 | 0 | — | 5 (1 %) | — | 0 |
| quiet | 400 nights | — | — | 0 | — | 0 |

Real lows labelled, by what the person did: carbs logged 0 / 355; carbs not logged 0 / 261; nothing 0 / 101; **ate
while awake, not logged 4 / 83**. All 10 labels over a real low (blood glucose 48-69 mg/dL during the dip) had carbs
eaten and not logged: 8 eaten while awake as it fell, 2 after the alarm on top of pressure. 5-min readings: compressions labelled 39 % (38 % of those that
rang), real fast lows 1 %, pressure on a low 3 %, carbs logged 0.

**Which rules matter** (`evaluate.py --sweep`, 200 nights per kind, other seed):

| Readings | Rules | Compressions labelled | Real lows with unlogged carbs labelled | Labels over a real low (all kinds) | Suspect at the alarm: real fast lows | … pressure on a real low |
|---|---|---|---|---|---|---|
| 1 min | V shape only (rate + rebound, like Nocturne) | 65 % | **48 %** | 127 | 89 % | 75 % |
| 1 min | + abrupt onset | 59 % | 5 % | 44 | 8 % | 50 % |
| 1 min | + back within 10 min of the alarm | 60 % | 2 % | 6 | 89 % | 75 % |
| 1 min | both (proposed) | 55 % | 0 % | 2 | 8 % | 50 % |
| 1 min | both, fall ≥ 3 mg/dL/min | 45 % | 0 % | 2 | 3 % | 44 % |
| 5 min | V shape only | 56 % | 39 % | 97 | 86 % | 71 % |
| 5 min | both (proposed) | 43 % | 1 % | 4 | 9 % | 48 % |

What it says:

- **No real-time label.** Even with every rule, pressure on top of a real low "looks like pressure" at the alarm half
  the time, because it *is* pressure, on a person who is also low. A label, a "check with a finger stick" hint, a
  delayed or softer repeat at that moment would land on the most dangerous nights. The issue's "gentler re-check" is
  rejected for that reason. The alarm also doesn't need help to clear: a reading back in range already clears the
  notification (alerts SPEC §1, "Episodes").
- **After the rebound, logging decides.** With carbs logged nothing is ever labelled. The remaining error is a person
  who ate and didn't log it, which is exactly what they can tell in one tap: so Sukrara asks.
- **What these numbers are**: a comparison of rules under stated assumptions (shapes from the literature, behaviour
  made up and written down in `traces.py`), not real-world accuracy. Real recorded nights are the follow-up's first
  step: the owner's own CSV export, run locally, never committed.

## 6. How to show it (design from the person's night)

- **At night, nothing changes.** The low alarm rings with its sound, repeats and *Mute* / *Remind less* exactly as
  today; its text stays the value and arrow (SPEC §1). If they turn over and the reading comes back, the notification
  already clears. No extra notification at 3 a.m.: going back to sleep is the goal.
- **Later, one quiet question** where the person looks anyway: a card on home (the next time they open Sukrara, until
  noon) and the dip's entry in the alert history: *"03:12 · fell to 58 and came back in 9 min, no carbs logged.
  Pressure on your sensor?"* with *Yes, I was lying on it*, *No, I ate* (opens the carbs entry at that time, so the
  log is right) and *Dismiss*. No sound, no notification.
- **The mark is the person's**: only *Yes* puts a small "pressure" mark on that dip in the graph (drawn by the card
  renderer on every surface that shows the history) and on the history entry; tapping it offers *Remove mark*.
  Unanswered questions expire without a mark.
- **It never changes**: alerts, their scenarios, time below range, the AGP / clinic reports or uploads (a doctor sees
  the readings as measured), followers' alerts.
- **TalkBack / VoiceOver** read the card as one sentence with its three actions; the mark has a label ("Marked as
  pressure on the sensor").
- **Where it runs**: on the wearer's phone, which has the readings and the treatment log; the mark travels to followers
  with the treatments so their graphs show it. Followers aren't asked (they don't know whether the wearer ate).

## In Sukrara

This investigation changes no app code and no alert rule (`contracts/alerts` is untouched). What the follow-up #164
brings in, and what stays out:

| Brought in by #164 | Why |
|---|---|
| The detector's two stages, its fixtures (synthetic traces + expected outputs) in `contracts/`, run by the app tests | one rule set, pinned like the alert scenarios |
| The after-the-night question (home card + history), the person-confirmed "pressure" mark in the graph, *Remove mark* | reassurance and a correct log without ever claiming a low wasn't real |
| *No, I ate* logging the carbs at the dip's time | the one error the detector can't see is the one the person can fix in a tap |
| A test that alert outputs are identical with the detector on and off | the guarantee, not a promise |

| Left out | Why |
|---|---|
| Any label, hint or text change in the alert itself | half of pressure-on-a-real-low nights look like pressure at that moment (§5) |
| A softer, delayed or skipped repeat ("gentler re-check") | the same; and repeats already stop when the reading is back |
| Suppressing or delaying the alarm (UVA's patent, some research aims) | Sukrara never silences a real low; the forums show where alarm fatigue ends |
| Labelling without asking (Nocturne-style auto-suggestions shown as facts) | unlogged carbs: a V-shape rule labels ~half of those real lows |
| Excluding marked dips from time below range, AGP or uploads | clinical data stay as measured; the mark is context, not a correction |
| A notification at night when it comes back | the person should go back to sleep; the clearing notification is enough |
| Sensor-side signals (temperature, membrane resistance, accelerometer, force) | Sukrara doesn't get them from the Libre 3 |
| Site advice ("try the other arm") after repeated marks | Sukrara doesn't know where the sensor is; reconsider once marks exist |
| The Python prototype in the apps | it stays in `investigations/52-compression-lows` as the reference implementation and evaluation |
