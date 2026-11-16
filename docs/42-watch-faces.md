# 42 — Watch faces: what it takes to design a good one

*2026-11-16 · research report, kept up to date with the lessons of each design round · web research (about 350
sources: watch platforms' guidelines, Garmin's docs and manuals, horology, perception and safety-critical display
research, diabetes care, CGM watch apps and their issue trackers; about 220 store reviews and forum threads by
people who wear one) and Sukrara's own rules · **no design**: each face's
design lives in its own place*

## Goal

The owner wants Sukrara watch faces, the first for their Garmin Descent Mk3i, later more designed by other agents.
The brief, in their words: a face that "doesn't scream 'I have diabetes', yet the owner is able to read the data he
needs"; "the artistic and creativity aspect is very important", while being rigorous and useful day to day;
digital time, seconds, battery time remaining, month, day of month, day of week, glucose value and trend arrow;
custom data fields like Garmin's own faces; and a face that "reacts to the blood glucose and context/situation that
is going on and actions [the] user has taken".

This report is the reusable part: what a person with diabetes needs from the wrist, which data and states a face has
to carry, what makes a face legible, calm and discreet, the ways each datum can be drawn, and the facts of the
platforms. On purpose it holds **no design and no preferred style**, so every face can be its own.

## How to use this report

- **Read it before sketching.** It is a toolbox and the evidence behind each trade-off, not a style guide: no layout,
  palette or look in it is a recommendation, and the examples are there to widen the space, not to be copied.
- **The hard rules are few** and listed in [§4](#4-must-the-few-hard-rules); everything else is a trade-off each face
  decides, with its evidence.
- **Platform limits are facts, not design rules** ([§10](#10-platform-notes)). Design for the person; use a
  platform's features where they help; a limit is solved later, not designed around.
- **Your face's design** (sketches, mockups, choices) goes in its own place. What a design round teaches that helps
  *any* face (a reason, a usability fix, an idea that failed and why) goes in [§12](#12-lessons-from-design-rounds).
- The full research notes, with every source: [evidence/42-watch-faces](evidence/42-watch-faces/) (platforms,
  Garmin, horology and encodings, human factors, diabetes, user voices).

## 1. The person and their day

### 1.1 Why the wrist

- **Glances are short.** About half of all smartwatch uses are time checks of about 1.9 s; half of 798,423 logged
  sessions lasted 5 s or less, partly because that's when screens time out ([Pizza et al. 2016](https://www.semanticscholar.org/paper/Smartwatch-in-vivo-Pizza-Brown/9508762477c897fbd687b0b29226ef73ce140dc8),
  [Visuri et al. 2017](https://ubicomp.oulu.fi/files/chi17.pdf)). Apple's budget is "about five seconds or less" per
  interaction ([WWDC15](https://developer.apple.com/videos/play/wwdc2015/802/)); people check the time about 150
  times a day ([Wear OS](https://developer.android.com/design/ui/wear/guides/surfaces/watch-faces)). A glucose face
  is read inside those 2 s, many times a day.
- **The wrist is the discreet place.** "Smartwatch facilitated discrete access" to glucose
  ([Sehgal 2024](https://onlinelibrary.wiley.com/doi/10.1111/dme.15168)); Dexcom markets exactly the meeting, the
  date, the dinner ([Dexcom](https://www.dexcom.com/en-us/all-access/dexcom-cgm-explained/direct-to-apple-watch)).
  Pulling out a phone is visible and slow; a glance at a watch reads as checking the time.
- **For some it is the only warning.** About a quarter of people with type 1 have impaired awareness of hypoglycaemia
  (27 % pooled, 17–21 % in CGM-era cohorts: [PMC11178233](https://pmc.ncbi.nlm.nih.gov/articles/PMC11178233/)).

### 1.2 Moments, needs and what can be known

| Moment | What a glance must answer | What the face could know (on the watch or from Sukrara) |
|---|---|---|
| Any time, 2 s | The time; *am I OK?*: value, direction, and **is it current** | Reading age |
| Meeting, date, meal out, class, cinema | The same, without anyone noticing; no sound, no colour flash | Recent insulin / carbs; calendar |
| Around a meal | *Did I bolus? How long ago?* (the most asked-for thing next to the value: [docs/39](39-metrics.md)); a rise within ~2 h of a bolus is expected ([Brazilian statement 2021](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/)) | Last treatment, kind, amount, time |
| Night, bed | Lows matter most and are least trustworthy (compression lows, see 1.3); a check that doesn't wake a partner ("so bright that I can't even sleep": [xDrip #1137](https://github.com/NightscoutFoundation/xDrip/issues/1137)) | Time of day, sleep schedule; evening exercise or alcohol earlier |
| Sport | Zone readable in motion ("it is good enough to see 'green'": [AAPS #2271](https://github.com/nightscout/AndroidAPS/issues/2271)); the low line moves up (1.4) | Activity, heart rate (on the watch) |
| Before driving | Above the driving line (≥ 90 mg/dL ADA, ≥ 5 mmol/L UK) and its trend; nothing to operate while driving | Not reliably detectable |
| Phone far, dead or locked | Whether the value is current, and which link broke ([urchin](https://github.com/longlandm/urchin-cgm) shows each hop) | Reading age; phone connection |
| Shower, swim, dive | A gap is expected (Bluetooth doesn't cross water); before a dive, a *sequence* of checks (1.4) | Water activity; dive (native Garmin only) |
| Morning after a night out | Lows peak the next morning (1.4) | User-logged |
| Sick day | Ketones and frequent checks | User-declared |
| Travel | A time-zone change shifts insulin timing ([PMC7962589](https://pmc.ncbi.nlm.nih.gov/articles/PMC7962589/)) | Time-zone change |
| Alerts quiet or muted | What is quiet and until when (users lose track: [GDH #259](https://github.com/pachi81/GlucoDataHandler/issues/259)) | Sukrara's alert status |
| Sensor change day | Days left (carry a spare: [GDH #295](https://github.com/pachi81/GlucoDataHandler/issues/295)); warm-up has no value | Sukrara's sensor life |

### 1.3 Clinical facts that shape the display

- **Ranges** (international consensus, [Battelino 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6973648/)): five
  named states, not three: very low < 54 mg/dL (3.0), low 54–69, in range 70–180 (3.9–10.0), high 181–250, very high
  > 250 (13.9). Targets: > 70 % in range, < 4 % below 70, < 1 % below 54. Pregnancy uses 63–140; older or high-risk
  adults > 50 %. → Zones are personal settings, never fixed numbers in a design. Sukrara's own bands and limits:
  [§3](#3-states-every-face-must-carry).
- **A trend arrow looks back** (about the last 15 min) and implies the next 15–30 min; it can still point down after
  glucose has levelled, so guidance says to read it with the last ~10 min of the curve
  ([Brazilian statement](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/)). → A short history disambiguates an
  arrow.
- **The same glyph means different speeds on different systems**: Dexcom has 7 states (↑↑ > 3 mg/dL/min, ↑ 2–3, ↗
  1–2, → < 1), Libre 5 (no double), Medtronic up to three arrows ([table](https://pmc.ncbi.nlm.nih.gov/articles/PMC7780381/)).
  → A face that draws its own arrow chooses its vocabulary; Sukrara has one ([§3](#3-states-every-face-must-carry)).
  Every surface must draw the same arrow or people notice ([AAPS #3414](https://github.com/nightscout/AndroidAPS/issues/3414)).
- **Context changes the meaning of a rise or fall**: rising right after a meal bolus is expected, rising 4 h later
  may need action; arrow-based corrections aren't advised within ~2 h of a bolus
  ([Endocrine Society on Kudva 2018](https://www.endocrine.org/news-and-advocacy/news-room/2018/streamlined-method-helps-people-with-diabetes-act-on-cgm-data)).
  → Time since the last insulin is the context that decides between "fine" and "act".
- **The number trails the blood** by 5–25 min (interstitial lag about 5–8 min, more after meals:
  [Basu 2013](https://www.researchgate.net/publication/256452201_Time_Lag_of_Glucose_From_Intravascular_to_Interstitial_Compartment_in_Type_1_Diabetes),
  [Sinha 2017](https://sites.bu.edu/bionicpancreas/files/2017/05/Sinha-CGM-lag-analysis-JDST-2017.pdf)). During fast
  moves (exercise, rescue carbs) direction and speed matter more than the exact value.
- **Least trustworthy**: at low values, in the first 12 h of a sensor, and at night, where lying on the sensor makes
  false drops of more than 25 mg/dL that rebound in 15–30 min (median 45 min long), roughly one every 3–6 nights
  ([docs/33](33-compression-lows.md), [Mensh 2013](https://pubmed.ncbi.nlm.nih.gov/23911167)).
- **Stale data is a known safety failure**: a Dexcom G7 app froze on a stale value (FDA class 2 recall, 2025) and G7
  apps including the watch app failed to warn of missed data
  ([FDA](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfRES/res.cfm?id=213398)). Conventions users know:
  Dexcom shows dashes after 10.5 min; Dexcom and Libre alarm *signal loss* at 20 min; DIY faces strike or grey the
  value at 10–15 min ([evidence: diabetes §6](evidence/42-watch-faces/diabetes.md)). Sukrara's steps: §3.
- **Sensor life**: warm-up 30 min (G7) to 60 min (Libre 3), wear 10–15 days, G7 has a 12 h grace period. Warm-up, the
  end countdown and "no sensor" are real, recurring states.

### 1.4 Situations with their own rules

- **Exercise** moves the danger line up and into the night: start aerobic exercise at 126–180 mg/dL; during exercise
  the low alert at 100 mg/dL (5.6); 4.4–10 mmol/L for 90 min after; after evening or long exercise a night low alert
  at ≥ 80 mg/dL ([Riddell 2017](https://www.digibete.org/wp-content/uploads/2018/01/Exercise-management-in-type-1-diabetes-a-consensus-Mike-Riddell-Lancet-DE-2017.pdf),
  [Moser 2020](https://link.springer.com/article/10.1007/s00125-020-05263-9)). Aerobic effort lowers glucose,
  sprints and competition stress can raise it: "exercising" alone doesn't predict the direction.
- **Driving**: UK "five to drive" (≥ 5 mmol/L; 4–5 eat first; check before and every 2 h; after a hypo wait 45 min),
  ADA: raise it if < 90 mg/dL ([Diabetes UK](https://www.diabetes.org.uk/living-with-diabetes/life-with-diabetes/driving),
  [ADA 2024](https://diabetesjournals.org/care/article/47/11/1889/157262/Diabetes-and-Driving-A-Statement-of-the-American)).
  The DVLA's 2019 wording said the CGM must not be actively used while driving; whether a glance at the wrist counts
  as active use isn't addressed anywhere.
- **Night**: 55 % of severe hypos happen during sleep; only 1 of 16 people with type 1 woke at 2.2 mmol/L against
  10 of 16 controls ([Schultes 2007](https://journals.plos.org/plosmedicine/article?id=10.1371%2Fjournal.pmed.0040069)).
  Heart rate from a watch detects early-night lows weakly (AUROC 0.78–0.83, PPV 0.13–0.25) and late-night lows not
  at all ([PMC11851596](https://pmc.ncbi.nlm.nih.gov/articles/PMC11851596/)): context, never a substitute.
- **Alcohol**: lows peak the morning after (5 of 6 participants between 10:00 and 12:00), and a hypo is easily taken
  for drunkenness ([Turner 2001](https://diabetesonthenet.com/journal-diabetes-nursing/effects-of-alcohol-on-blood-glucose-levels-in-people-with-type-1-diabetes-a-systematic-review/),
  [Browne 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4120421/)).
- **Diving** (the Descent is a dive computer): ≥ 150 mg/dL (8.3) and stable or rising on three checks, 60, 30 and
  0 min before; no dive below 150 or above 300; the buddy signal for a low is an "L" with thumb and index
  ([DAN](https://dan.org/health-medicine/health-resource/health-safety-guidelines/guidelines-for-diabetes-and-recreational-diving/)).
  Sensors are rated to 1–2.4 m and Bluetooth fails underwater, so a face's role is on the surface: before, between
  and after dives. A protocol is a *sequence with a trend*, not a single number.
- **Sick days** add ketones (≥ 0.6 mmol/L act, ≥ 1.5 high, ≥ 3.0 DKA range: [ISPAD 2022](https://www.digibete.org/wp-content/uploads/2023/05/ISPAD-Ch_13_SDR.pdf));
  continuous ketone sensors are coming. **Menstrual cycle**: more time above range in the late luteal phase, more
  below around menstruation ([PMC9962060](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9962060/)).

### 1.5 Stigma and discretion

- **Stigma is the norm, not a niche**: 76 % of people with type 1 say diabetes carries stigma (n = 5,422,
  [Liu 2017](https://diabetesjournals.org/clinical/article/35/1/27/35423/Stigma-in-People-With-Type-1-or-Type-2-Diabetes)).
  Adults inject in toilet cubicles, avoid swimming to hide a pump, hide it from partners and at work, get blamed for
  eating sweets ([Browne 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4120421/)). Teens hide devices at first, some
  later use them to educate ([PHCFM 2023](https://phcfm.org/index.php/phcfm/article/view/3879/6164)).
- **Alarms in public embarrass**, people turn them off at work and social events, and some feel alarms "signal
  personal failure" ([Keeping safe](https://www.researchgate.net/publication/318613732_Keeping_safe_Continuous_glucose_monitoring_CGM_in_persons_with_Type_1_diabetes_and_impaired_awareness_of_hypoglycaemia_a_qualitative_study),
  [Natale 2023](https://onlinelibrary.wiley.com/doi/10.1111/1753-0407.13454)). → How a high value looks *to the
  wearer* carries weight too: a red number can read as a verdict.
- **Abstract displays already exist in the wild**: Nightscout Clock's faces where glucose drives a unicorn's mane, a
  race car that stops when data is stale, a dragon whose fire goes out ([nightscout-clock](https://github.com/ktomy/nightscout-clock));
  SugarPixel's emoji and few-lights night mode ([Diabetech](https://www.diabetech.info/p/sugarpixel-the-cgm-display-with-customizable-alerts-how-it-works));
  a wristband that shows a colour instead of a number ([UW–Madison](https://bmedesign.engr.wisc.edu/public/projects/f25/when_they_glow-you_know/file/view/54e70151-de59-44d7-9586-436a619241ab/GlucoseAlerting_Final_Report.pdf)).
- **Gap**: no study measures how bystanders perceive a number, a colour or a graph on someone's watch. Stigma research
  predates wrist displays.

### 1.6 Alarm fatigue and the wrist

- **More alarms isn't more safety**: in 95 Dexcom users, lower high thresholds doubled high alarms (30.9 vs 14.2) and
  went with *longer* highs; 9 % had the low alarm off ([Lin 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC6977942/)).
  Half of former CGM users cite too many alarms; patients responded to 29 % of alarms
  ([review 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/)).
- **Some states are designed to break discretion**: Dexcom's urgent low (55) and failure alerts can't be silenced and
  escalate to sound ([Dexcom](https://www.dexcom.com/en-us/faqs/are-there-any-changes-to-alerts-for-dexcom-g7-compared-to-dexcom-g6)).
  Sukrara's equivalents (extremely high and no data are never muted by a treatment) are in
  [contracts/alerts/SPEC.md](../contracts/alerts/SPEC.md) §2.
- **Vibration** is less embarrassing than sound and noticed in noise ([review 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12731774/));
  awake users recognise wrist-vibration patterns 89–99 % of the time ([GoodVibes](https://arxiv.org/pdf/2406.01738));
  in device studies pulsed vibration woke 100 % of sleepers and continuous 92 % (vendor summary), though a watch alarm
  can be dismissed without truly waking. Users ask for one small tick
  per reading while falling fast ([GDH #226](https://github.com/pachi81/GlucoDataHandler/issues/226)) and for the
  watch to alert when worn, the phone when not ([GDH #230](https://github.com/pachi81/GlucoDataHandler/issues/230)).
- **The ecosystem is split on whether the wrist alerts at all** (Garmin's Dexcom app and Sugar Sense: never; Juggluco,
  AAPS, xDrip+, GDH: yes). A face is the *display*; the alert itself is Sukrara's (phone, and the watch when it can).

### 1.7 What users say

From about 220 Garmin store reviews of 15 CGM faces and apps, Play reviews from watch users, Diabetes UK forum threads,
Hacker News and a study of young adults given smartwatches ([all quotes and links](evidence/42-watch-faces/user-voices.md);
Reddit stayed blocked). *Many* = 5 or more independent voices, *several* = 2–4.

- **The point is not taking the phone out** (many): "keeps me from pulling out my phone constantly"; "I don't have to
  stop running to check my blood sugar". So the face has to answer the whole question in the glance: value,
  direction, freshness.
- **It must be there the moment the wrist comes up, and in always-on** (several): "Every time I raise my hand, I'll
  see this data"; a complaint that the dimmed face shows only the time in the dark; "please implement the 'screen
  always on'… Very important for me".
- **Stale data is the normal case, not an edge case** (many; most 1–2★ reviews): "the number simply grey", "---
  (no data) even after 30 minutes", "stop updating for hours", upstream servers and logins breaking. How it's shown
  decides whether the face stays useful: "when there is no BT connection the watch face is usless… a big BT symbol
  accross the watch" (a stale mark must never cover the time); people value seeing *when* the last reading came and
  *why* ("No phone connection").
- **Wanted next to the value**: delta (several; a face that showed it per minute got "Incorrect BG delta": use the
  unit people expect or say it), minutes since the reading (several; hidden or cryptic versions confuse), insulin on
  board "more than my steps" (several; Sukrara computes no IOB today), watch battery (many; one face shows it only
  when low), a 3 h average. Heart rate, steps and seconds are wanted by about as many as want them gone ("in night I
  don't need them").
- **The hierarchy is personal** (several each way): "the BG is more important than the time itself" vs "clock a bit
  bigger"; "option to swap time and BG positions". Same for the graph: "Finally can have Dexcom graph on my watch face"
  vs "option of not displaying the graph, and instead a larger display of current reading"; a graph that "always
  starts blank" annoys; a graph line that looks like a stale strike-out confuses.
- **Small grey secondary text fails** (several): "hard to read since it's grey… for older eyes"; "not legible in very
  dark grey and small font"; one app is called *No Glasses Required CGM*.
- **Expected details**: mmol/L everywhere, including the range settings (8+ reviews across 4 faces); Dexcom's double
  arrows; settings with "a sample of what you're changing".
- **Non-numeric works for some**: "Very easy to read at a glance, without needing to necessarily look at the BG
  number" (a dial face). **Emotion counts**: "The little emoticon at the bottom warms my heart every time I see it".
- **Discretion is the gesture as much as the graphics**: "discreet monitoring of my levels during meetings"; "one
  shuffle of my sleeve and one tap" against a phone's unlock; but "I still don't want people to get the wrong idea"
  (a long look at the watch reads as boredom). **In the dark the screen itself is the problem**: "I would not use a
  smart watch in the cinema either, the screen is also bright".
- **Parents** are a large share of the voices (many): they read their child's glucose on their own watch, and want the
  child's watch to work without a phone at school. A child's watch failed: "vibrations… very short, and the kid did
  not fell it".
- **The wrist is the quiet first step of an alert** (several): "notify my iWatch (as the vibration will wake me) but
  not… my iPhone" (a partner asleep); buzz at 70 before sound at 55. Too much is intrusive ("a bit intrusive with
  alarms on my wrist. Instead I have G7 as a complication"), and a watch that suddenly made "VERY LOUD noises" surprised.
- **A good face prevents alarms**: "so easy to just look at the watch and have a toffee or bolus before blood glucose
  gets low / high enough for the alarms to go off".
- **Contexts named**: trail running, cycling with gloves, physical work "in the bush", a bus driver ("I need to ensure
  my glucose does not go low while driving"), a dance class, the cinema; and a wish after drinking for "a little thing
  on my watch… 'eat something before you go to sleep'". Nothing found on swimming or diving.
- **Not found**: first-hand stories of strangers asking about the number or colours drawing looks (that lived mostly
  on Reddit).

## 2. What a face can show

Asked by the owner in **bold**; the rest is what the evidence says people use or ask for. "Sukrara has it" means the
phone already computes it ([docs/FEATURES.md](FEATURES.md)).

| Datum | The question it answers | Precision needed | Changes | Source | Notes |
|---|---|---|---|---|---|
| **Time (digital)** | What time is it? | 1 min | 1/min | Watch | Always present; 12/24 h is the watch's setting |
| **Seconds** | Exact second; *is it alive?* | 1 s, or only "running" | 1/s | Watch | Usually only while the face is awake ([§10](#10-platform-notes)) |
| **Day of week** | Which day? | Exact | 1/day | Watch | Locale words (en/es); first day of week is a setting |
| **Day of month** | Which date? | Exact | 1/day | Watch | |
| **Month** | Which month? | Exact | 1/month | Watch | Rarely needed at a glance; a calendar question |
| **Battery time left** | When must I charge? | ~1 day | Slow | Watch | Garmin gives days left directly |
| **Glucose value** | Where am I? | 1 mg/dL / 0.1 mmol/L, or the zone only | 1/min (Libre 3) to 1/5 min | Sukrara | mg/dL 40–400 and *LO*/*HI*; mmol/L 2.2–22.2 with one decimal |
| **Trend** | Where am I heading, how fast? | 5–7 classes, or continuous | 1/min | Sukrara | One trend for every surface ([§3](#3-states-every-face-must-carry)) |
| Reading age | Is it current? | "now" / minutes / old | Continuous | Sukrara + watch clock | The most safety-relevant datum after the value (1.3) |
| Zone / range word | Am I in range, how far out? | 7 bands | With the value | Sukrara | Never by colour alone |
| Delta | How much since the last reading? | 1 mg/dL | 1/min | Sukrara | Asked for in every DIY face; redundant with a continuous trend |
| Short history | What has it done lately? | Shape | 1/min | Sukrara | Disambiguates the arrow (1.3) |
| Last insulin / carbs | Did I bolus? How long ago? How much? | Minutes; units / grams | On action | Sukrara | Kind matters (rapid, long, carbs) |
| Quiet / muted state | Is something holding alerts, until when? | Minutes left | On action | Sukrara | Quiet after insulin 60 min, after carbs 15 min (defaults) |
| Alert needed | Must I act now? | Kind | On event | Sukrara | Words exist: *Eat carbs*, *Take insulin* (card words) |
| Sensor life | Days left; warming up; ended | Days → hours → minutes | Slow | Sukrara | Warm-up never as a number that passes for glucose |
| Time in range | How is my day going? | 1 % (5 % meaningful) | Slow | Sukrara | Motivating for some, stressful for others: opt-in ([docs/39](39-metrics.md)) |
| Phone / link | Is the chain working? | Up / down | On change | Watch | |
| Prediction | Where will I be in 15–30 min? | Zone | 1/min | Planned | Predictive alerts cut time below range by > 40 % in teens ([Drug Topics](https://www.drugtopics.com/view/real-time-cgm-predictive-alarm-improves-glycemic-control-in-adolescents-with-t1d)) |
| Heart rate, body battery, stress, steps, floors, calories | Body and activity context | As the watch gives | 1/min–slow | Watch | Exercise context for glucose |
| Sunrise / sunset, weather, altitude, pressure, compass | Outdoor context | As given | Slow | Watch | Weather needs the phone |
| Notifications, alarms set, do-not-disturb | Is anything waiting? | Count / on-off | On change | Watch | |
| Second time zone | Travel, people abroad | 1 h | 1/min | Watch | Descent's bezel has an ALT TZ mark |
| Dive: surface interval, no-fly time | Can I dive / fly? | Hours | Slow | Watch (native only) | Not readable by third-party Garmin faces |

## 3. States every face must carry

Sukrara decides these once, for every surface ([docs/FEATURES.md](FEATURES.md), [contracts/graph](../contracts/graph/README.md),
[contracts/alerts/SPEC.md](../contracts/alerts/SPEC.md)). A face **shows** them; it never decides them differently
(CLAUDE.md "One graph everywhere": a face is a surface).

| State | True when (defaults) | The face must convey | Never |
|---|---|---|---|
| Fresh | Reading ≤ 90 s old | Value, trend, zone | — |
| Late | 90 s – 5 min | The value is from a few minutes ago | — |
| Stale | 5–20 min | Old: Sukrara strikes it through and greys it; the trend becomes a hollow ring | Show it as current |
| No readings | 20 min – 3 h | *No reading since* + time; no value as if live | A number that looks live |
| Long gap | ≥ 3 h | No value, no history | |
| Very low / low / in range (tight 70–140, target 141–179) / high / very high / extremely high | Bands: ≤ 55, 56–69, 70–179, 180–249, 250–299, ≥ 300 ([bands.json](../contracts/graph/range/bands.json)); the user's own thresholds | Which band; a word under an out-of-range value (*Low*, *Very high*…) | The band by colour alone (#26); very high's orange and low's red look alike to protan / deutan eyes |
| LO / HI | Sensor says < 40 / > 400 | *LO* / *HI*, no number, no trend | 39 or 401 |
| Trend | Rate from the readings (*Trend arrow* setting) | Direction and speed: drawn angle = clamp(rate / 3, −1, 1) × 90°, doubled from ±3 held 2 min; text arrow bands ±1/2/3 with 0.2 hysteresis ([docs/34](34-trend.md)) | A trend computed differently from the phone |
| No trend | Too few readings, LO / HI, stale | A hollow ring where the arrow goes (not a dot, not `?`) | A flat arrow (reads as "steady") |
| Alert needed | Low / high side out of range, not quiet | The need: *Eat carbs* / *Take insulin* (card words), or the alert kind | |
| Quiet after a treatment | Insulin mutes highs 60 min, carbs mute lows 15 min; or glucose clearly heading back (20 min) | That it's handled, and until when (*10m left*) | Hide that an alert is being held |
| Muted by the user | Mute of a side, or *Mute all* (with or without an end) | *Muted* and time left | |
| Warm-up | New sensor, no reading yet (60 min + 15 grace) | *Warming up · 42m* in words, the disc filling | A figure that could pass for glucose (#27) |
| Sensor ending | Last 8 h (setting 1–24) | A countdown | |
| Sensor ended | Past its end, readings stopped | *Sensor ended* (checkered flag) | The last reading's time (looks like a gap) |
| Ended but still sending | Past its end, readings arrive | The value + *Ended · Replace sensor* | |
| No sensor / not monitoring | Nothing paired | Not monitoring, plainly | An empty slot that looks like a gap |
| Unit | mg/dL or mmol/L (per device) | Widest values fit: `400`, `LO`, `22.2`, `10.0` | |
| Watch can't reach Sukrara | No data delivered to the watch | The age keeps counting from the **reading's** time, so it shows as late / stale by itself | Count age from when the watch received it |

## 4. Must: the few hard rules

Everything else in this report is a trade-off. These are not, because breaking them makes the face unsafe or makes it
contradict the phone:

1. **Never show an old value as current.** The age is part of the value ([§3](#3-states-every-face-must-carry), 1.3
   recalls). Wear OS and Fitbit replace stale live numbers with `--` rather than let them mislead
   ([Wear OS](https://developer.android.com/training/wearables/always-on), [Fitbit](https://dev.fitbit.com/blog/2019-12-19-announcing-fitbit-os-sdk-4.1/)).
2. **Same decisions as every other Sukrara surface**: bands, words, trend, freshness, sensor states (§3).
3. **Never colour alone** for anything that matters: colour-blind eyes, tinted and night modes (Apple's tinted mode,
   Garmin's Red Shift re-tints the whole screen), always-on dimming. Shape, position, a word or a glyph carries it
   too ([Apple HIG](https://developer.apple.com/design/human-interface-guidelines/accessibility)).
4. **The wearer can always get the exact number and its age**, in at most one action, whatever the face's discretion
   level (the brief: "able to read the data he needs").
5. **Warm-up, ended, LO / HI and no-data never show a figure that could be read as glucose.**
6. **Every state has a visible way to see it, change it and leave it** (CLAUDE.md *Complete flows*): a mode the face
   enters (discreet, quiet, night…) shows that it's on and how it ends.
7. **The time is always readable**, in every state and mode.

## 5. Seeing at a glance: perception

Numbers for sizes, contrast, colour and motion ([full notes](evidence/42-watch-faces/human-factors.md)).

### Geometry: how far, at what angle

- People read a watch at **28 cm** (SD 5) with the watch pitched 50° and the line of sight 10° off the face's normal:
  slightly oblique, closer than a phone ([Blascheck 2019](https://petra.isenberg.cc/publications/papers/Blascheck_2018_GVS.pdf)).
- On the Descent 43 mm (12.8 px/mm), at 28 cm **1 arcmin ≈ 0.08 mm ≈ 1 px**. The whole face is about 6° wide.

| Character (cap) height | Arcmin | At 28 cm | At 33 cm | Source |
|---|---|---|---|---|
| Non-critical, not time-critical | 10′ | ≈ 0.8 mm · 10 px | 12 px | FAA HFDS |
| Minimum | 16′ | ≈ 1.3 mm · 17 px | 20 px | ISO 9241-303, MIL-STD-1472F |
| Preferred | 20–22′ | ≈ 1.7–1.8 mm · 21–23 px | 25–27 px | ISO 9241-303 |
| Preferred on colour displays | 30′ | ≈ 2.4 mm · 31 px | 37 px | Vendor summary of MIL-STD |

These are for steady reading at a desk; a 2-s glance, walking, an oblique wrist and sunlight all need more. Garmin's
smallest built-in fonts (19–27 px line height, roughly 13–19 px caps) sit at or under the minimum: fine for labels,
not for the value ([sources](evidence/42-watch-faces/human-factors.md#4-numbers-and-type)).

### What a glance can read

- **A time check takes about 1.9 s**; time is 50 % of watch interactions but 14 % of the time spent; more than 70 % of
  activity-tracker use is glances with no further interaction ([Pizza 2016](https://www.semanticscholar.org/paper/Smartwatch-in-vivo-Pizza-Brown/9508762477c897fbd687b0b29226ef73ce140dc8),
  [Gouveia 2016](http://persuasive.cut.ac.cy/2016/07/22/exploring-the-design-space-of-glanceable-feedback-for-physical-activity-trackers/)).
  The face *is* the product; detail screens are secondary.
- **Comparing two values on a watch**: donut 159–216 ms, bar 245–285 ms, concentric radial bars 1.5–1.8 s, about 6×
  slower ([Blascheck 2019](https://petra.isenberg.cc/publications/papers/Blascheck_2018_GVS.pdf)). **Reading a
  proportion**: bar 318 ms, radial 397 ms, text "70 %" 503 ms and the most errors; people rated the ring the most
  beautiful and the bar the most efficient: about 80 ms of speed against beauty, a free choice. **A single live value
  (like glucose) is untested** ([Blascheck 2023](https://petra.isenberg.cc/publications/papers/Blascheck_2023_SOP.pdf)).
- **Walking** lowers accuracy reading watch visualizations; radial progress reads best at the quarters, and people
  asked for tick marks ([Grioui 2024](https://arxiv.org/abs/2407.17893)). With the watch flat, OLED contrast drops so
  far the researchers had to recolour the stimuli ([Blascheck 2023](https://petra.isenberg.cc/publications/papers/Blascheck_2023_SOP.pdf)).
- **What real faces carry**: a median of 5 data items, icon + text the most common form, charts rare; of 358
  best-selling faces 60 % are digital, 80 % have no animation and none animates data; 62 % of people never change
  their face: a face is chosen once and lived with ([Islam 2020](https://arxiv.org/pdf/2009.00750),
  [Islam review](https://arxiv.org/pdf/2310.16185)).
- **Six qualities of glanceable feedback**: abstract; part of existing routines; compared with a target or norm;
  actionable; builds a checking habit; a door to more detail ([Gouveia 2016](http://persuasive.cut.ac.cy/2016/07/22/exploring-the-design-space-of-glanceable-feedback-for-physical-activity-trackers/)).

### Which encoding for which job

- **Accuracy order** (Cleveland & McGill, replicated by [Heer & Bostock](http://vis.stanford.edu/files/2010-MTurk-CHI.pdf)):
  position on a common scale > position on unaligned scales > length, direction, angle > area > volume, curvature >
  shading, saturation. A ring encodes by angle; a tape or bar by position.
- **Bertin**: only position and size read as quantities; lightness is ordered; hue is for categories, not magnitudes
  ([summary](https://www.axismaps.com/guide/visual-variables)).
- **Pre-attentive** in < 200–250 ms, whatever the clutter: hue, intensity, size, orientation, length, curvature,
  closure, flicker, motion. A target defined by a *combination* of features doesn't pop out, and colour variation
  masks shape patterns ([Healey](https://www.csc2.ncsu.edu/faculty/healey/PP/)). An urgent state carried by one unique
  feature pops out; one carried by a combination doesn't.
- **A number alone hides the margin**: "it may be difficult… to determine the margin relative to targets or limits…
  a scale, dial, or tape may be needed"; digits should match the data's accuracy (FAA [AC 25-11B](https://www.faa.gov/documentlibrary/media/advisory_circular/ac_25-11b.pdf)
  §5.7). The three-pointer altimeter took ~7 s to read with > 11 % big errors; the fix was a counter plus a pointer
  ([summary](http://users.sussex.ac.uk/~blayw/flyingl.html)).
- **Sparklines**: Tufte's own worked example is a patient's last 80 glucose readings: the normal range as a grey band,
  the newest point tied to the printed number by the same colour accent, no "vulgar stoplight" colours
  ([Tufte](https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/)).
- **Gestalt**: on a 6° face, proximity, common region and similarity alone tell which number goes with which unit.

### Type and numbers

- **Humanist faces read faster at a glance**: Frutiger cut total glance time by 10.6 % against a square "technical"
  grotesque (Eurostile) in a driving study ([Reimer 2014](https://dspace.mit.edu/handle/1721.1/96509)).
- **Lowercase** took 26 % more time to read at a glance, **condensed** 11 % more; capitals read better partly because
  they are bigger, but confuse more letters ([Sawyer 2017](https://www.bendsawyer.com/wp-content/uploads/2025/05/Sawyer_et-al_2017_The-Cost-of-Cool-Typographic-Style-Legibility-in-Reading.pdf)).
  Small text cost 27 % more glance time, tight spacing 9 % ([Dobres 2018](https://benwolfe.net/pdf/Dobres_AppErgo_2018.pdf)).
- **Digit confusions**: segment digits that differ by few segments are confused most (1/7, 3/8, 5/6, 6/8/9/0); a
  redesigned seven-segment font halved errors ([Gunderson 1991](https://experts.illinois.edu/en/publications/legibility-of-seven-segment-numeric-led-displays-comparisons-of-t/));
  segment decimals invite out-by-ten errors ([Thimbleby](https://www.researchgate.net/publication/266653791_Reasons_to_question_seven_segment_displays)).
  An mmol/L value (`5.6`, `15.6`) is exactly that error surface.
- **Fixed-width figures** keep a changing number from changing its own width each minute (11:11 vs 18:18).
- **Rolling or animated digits** must not suggest a motion that doesn't match the real trend (AC 25-11B §5.7.2.1).

### Colour and light

- **1 in 12 men and 1 in 200 women** have a colour-vision deficiency, mostly red–green ([NEI](https://www.nei.nih.gov/eye-health-information/eye-conditions-and-diseases/color-blindness)).
- **AC 25-11B §5.8**: colour coding always with a second code (size, shape, place); at most 6 coding colours; no
  distinctions by shades of one hue; pure blue is too dark to carry anything important; ageing eyes lose red focus
  and blue–green discrimination. Pairs to avoid: saturated red/blue, red/green, blue/green, yellow/green, magenta,
  blue or red on black.
- **Learned meanings** the wearer brings: red warning, amber caution, green normal, white for values and scales
  (aviation, cars, CGM apps). The Descent itself codes its dive bars green / yellow / red (ascent rate, tissue load).
- **Contrast floors**: 3:1 for graphics needed to understand content ([WCAG 1.4.11](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html));
  in cars 5:1 at night, 3:1 by day, 2:1 in direct sun (ISO 15008).
- **OLED in sunlight**: contrast "declines sharply as ambient light gets brighter" ([Chen 2017](https://opg.optica.org/oe/fulltext.cfm?uri=oe-25-26-33643&id=380371));
  a palette that works indoors can wash out on a beach or a boat.
- **Night**: full dark adaptation takes ≥ 30 min, bright light for > 1 s spoils it; red light preserves it but
  "severely distorts colors" and erases red's meaning ([FAA](https://www.faa.gov/pilots/safety/pilotsafetybrochures/media/pilot_vision.pdf)).
  In dark surroundings light-on-dark text gets harder to read as it gets smaller ([Dobres 2017](https://pubmed.ncbi.nlm.nih.gov/28166901/)):
  a tension with dark always-on faces.
- **Underwater**, reflected red disappears by ~5–6 m; an emissive screen at 30 cm is probably much less affected (no
  study found).

### Motion and change

- **Change blindness**: a change between two glances is invisible, because the eye has no memory of the last frame
  ([Simons & Rensink 2005](https://www.cs.ubc.ca/~rensink/publications/download/S&R-TICS-05a.pdf)). If "something
  happened since you last looked" matters (a new low, a treatment, an alert), the face has to *encode* it.
- **Motion is the strongest peripheral signal**: colour targets in the periphery were missed 25 % of the time, motion
  under 2 %; motion "can contribute strongly to distraction and irritation" ([Bartram, Ware & Calvert 2003](https://interruptions.net/literature/Bartram-IJHCS03-BW.pdf)).
  It reaches bystanders' eyes too.
- **Trend indicators need hysteresis and damping**, and damping adds lag (AC 25-11B A.4.5); Sukrara's trend already
  settles that balance ([docs/34](34-trend.md)).
- **Blinking**: 0.8–4 Hz, cancellable after ~10 s (FAA); medical standards discourage flashing text. **Photosensitivity**:
  ≤ 3 flashes a second, and a flashing area under 0.006 sr: the whole face at 28 cm is 0.0093 sr, so a full-face flash
  fails ([WCAG 2.3.1](https://www.w3.org/WAI/WCAG21/Understanding/three-flashes-or-below-threshold.html)).
- **Reduced motion**: non-essential animation must be possible to turn off ([WCAG 2.3.3](https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions.html)).

### Calm and peripheral displays

- **Case's calm-technology principles**: the smallest possible amount of attention; inform and create calm; use the
  periphery; **work even when it fails** (default to a usable state); **respect social norms**
  ([calmtech.com](https://calmtech.com/)).
- **Ambient-display heuristics**: "sufficient information design" (just enough); consistent, intuitive mapping;
  **visible state** with noticeable transitions; easy path to more detail; unobtrusive until needed; notice it
  "because of a change in the data… not because its design clashes". In the field, people thought one ambient display
  "was broken" ([Mankoff 2003](https://faculty.washington.edu/garyhs/docs/mankoff-CHI2003-heuristics.pdf)): a calm face
  must still look alive.
- **Notification levels** run *user poll → change blind → make aware → interrupt → demand attention*; ambient
  displays by definition carry "important but not critical" information ([Pousman & Stasko 2006](https://faculty.cc.gatech.edu/~stasko/papers/avi06.pdf)).
  A glucose face spans the whole scale, ambient in range and demanding at a very low.

## 6. Principles from watch platforms and watchmaking

Each with its reason; the face decides how far to follow.

### Hierarchy and restraint
- **Rank by the precision and urgency each datum needs, not by convention.** The B-Uhr gives the outer ring to the
  minutes because navigators needed them; the regulator moves the hours to a subdial "where imprecision does not
  matter much" ([Quill & Pad](https://quillandpad.com/2020/07/11/a-crash-course-in-b-uhren-and-flieger-pilots-watches-covering-both-historic-and-modern-examples-a-pilots-watch-photofest/)).
  The longest radius, the biggest size or the strongest contrast goes to what needs it most.
- **Contrast separates value from context**, not only size: Apple's heart-rate slot draws the value high-contrast and
  the graph and labels low-contrast grey ([HIG Complications](https://developer.apple.com/design/human-interface-guidelines/complications)).
- **"The great dials say remarkably little"** ([The Horologue](https://thehorologue.com/article/the-dial)). Every
  glyph competes with the data; a fourth central hand or a third subdial makes people "stop and look for longer"
  ([Fratello](https://www.fratellowatches.com/different-date-displays-watches/)). Braun used one typeface and "black
  and white and a bit of metal" ([Dezeen](https://www.dezeen.com/2017/09/18/braun-philosophy-about-reducing-bling-bling-watch-clock-designer-dietrich-lubs-interview/)).
- **One message vs many slots** are both official: Android Wear (2014) said "stick to one message" and "a watch dial
  is a timeline; add data to it" ([AOSP doc](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/lollipop-mr1-release/docs/html/design/wear/watchfaces.jd));
  Infograph and Wear OS allow 8 slots. The owner's list is already 8 data; each extra slot spends glance time.
- **A slot earns its place by staying current**: "a static complication… may be less likely to remain in a prominent
  position" ([HIG](https://developer.apple.com/design/human-interface-guidelines/complications)).

### Reading time and values
- **Digits for "what is the value", position for "how far from a line / how long until".** Digital time reads 3.5–4×
  faster with 10× fewer errors for absolute time ([1971 review](https://pubmed.ncbi.nlm.nih.gov/15676699/); a 2023
  study agrees even for people who prefer dials, [Springer](https://link.springer.com/chapter/10.1007/978-3-031-35681-0_27));
  analogue reads faster for time differences ([MDPI 2024](https://www.mdpi.com/2076-328X/14/9/847)). Glucose needs
  both questions: the value, and the distance to a threshold.
- **Precision is a design variable.** A single-hand watch reads "almost to the nearest minute" and its buyers don't
  care whether it is 13:29 or 13:31 ([MeisterSinger](https://meistersinger.com/en/the-brand/single-hand-watches/));
  word clocks round to 5 min and add corner dots for the rest ([QlockTwo](https://newatlas.com/qlocktwo-typographic-clock/12620/)).
  Layered precision lets the glance stop early; rounding needs a rule for which way it goes.
- **One unmistakable anchor**: a round face without numerals hangs on one landmark (the B-Uhr's triangle, Grand
  Seiko's double index, Movado's single dot) and the wrist rotates. On the Descent the printed bezel ring is a second
  frame of reference ([§10](#10-platform-notes)).
- **Pointers must stay distinguishable in the worst light**: ISO 6425 requires the minute hand to be told from the
  hour hand at 25 cm in the dark, and a visible sign that the watch is running
  ([ISO 6425 sample](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf)).
  "Is it alive?" has a precedent in a standard.

### Colour and light
- **One accent, one role, borrowed meaning**: Braun's alarm clocks all had a yellow seconds hand; Hilfiker's red
  seconds hand is shaped like the dispatcher's signal baton ([Wikipedia](https://en.wikipedia.org/wiki/Swiss_railway_clock)).
  An accent copied from a real-world signal arrives with that signal's meaning, and "avoid colours used in places
  they may have a pre-conceived meaning which does not apply" ([Pebble](https://developer.rebble.io/guides/design-and-interaction/recommended/)).
- **Zones on the scale, a neutral pointer**: aviation airspeed arcs (white, green, yellow, red line) put the meaning
  on the scale, not on the needle ([Wikipedia / FAA](https://en.wikipedia.org/wiki/Airspeed_indicator)). The
  pointer's colour then stays calm.
- **Black is the ground on emissive screens**: it saves power and merges with the bezel, which "will provide visual
  padding" ([WWDC15](https://developer.apple.com/videos/play/wwdc2015/802/)); "every pixel uses power"; Garmin:
  light-on-dark, "dark-on-light… will look out of place on an AMOLED display"
  ([Garmin UX](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Incorporating_the_Visual_Design_and_Product_Personalities.html)).
  Avoid full-screen colour on views that stay on screen ([HIG Color](https://developer.apple.com/design/human-interface-guidelines/color)).
- **Night**: Apple Ultra's Night Mode turns faces red automatically from the light sensor, down to 1 nit "so as not
  to disturb people close by" ([Apple](https://www.apple.com/newsroom/2023/09/apple-unveils-apple-watch-ultra-2/));
  green lume (~515 nm) looks brightest in the dark, blue dimmer ([Aquastar](https://aquastar.ch/journal/superluminova-lume-explained/)).
  The Descent has Lume (turquoise glow from the light sensor) and Red Shift natively.
- **Hand angles carry mood**: 10:10 reads as a smile and raised liking modestly; 8:20 reads sad
  ([Karim 2017](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5572348/), small samples). A data pointer's resting
  angle may be read as an emotion: a falling trend drawn as a frown is a judgement.

### Type
- **One family, few sizes, no thin weights**: Apple: avoid Ultralight, Thin and Light; minimum 12 pt, lines ≥ 2 pt
  "especially when the wearer is in motion" ([HIG Typography](https://developer.apple.com/design/human-interface-guidelines/typography),
  [Complications](https://developer.apple.com/design/human-interface-guidelines/complications)); Wear OS: 12 sp
  essential, 10 sp other ([quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality));
  Samsung: one font, no drastic size jumps ([typography](https://developer.samsung.com/one-ui-watch-tizen/visual/typography.html)).
  In physical units for the Descent: [§5](#5-seeing-at-a-glance-perception).
- **A numeral's shape is legibility and identity at once**: railways banned Roman numerals for big Arabic ones; Max
  Bill drew his own; the Tangente's narrow numerals structure an empty dial ([Monochrome](https://monochrome-watches.com/dial-styles-history-and-evolution-of-a-watchs-crucial-part-abcs-of-time/),
  [Gear Patrol](https://www.gearpatrol.com/watches/a519830/time-and-type/)). Display-only numeral fonts are cheap
  (TTMM shipped fonts of ten glyphs: [TTMM](https://ttmm.is/pebble-watch/)).
- **Size for the widest value** (Zepp sizes every field for "000"/"00000": [Zepp](https://docs.zepp.com/docs/designs/customization/watchface/)):
  `400`, `22.2`, `HI`, `--`, Spanish weekday and month words, a 3-digit heart rate.

### Round screens and edges
- **Stay inside the bezel and away from the edge in always-on**: the system shifts the picture a few pixels to protect
  the panel, so edge elements can be pushed off ([Wear OS](https://developer.android.com/training/wearables/always-on),
  [Samsung](https://developer.samsung.com/one-ui-watch-tizen/watch-face/states.html)); Garmin asks faces to move
  elements up to 4 px a minute.
- **Thin rings at the very edge fail on round screens**; use thick rings or inset them; each text row has a different
  width, so don't reflow text ([Pebble round](https://developer.rebble.io/guides/design-and-interaction/in-the-round/)).
  "Be open to creating a new UI" for round screens instead of porting a rectangular one.
- **The edge can carry a reading**: Apple's Modular Ultra uses "the outermost edge to present real-time data,
  including seconds, altitude, or depth"; text can curve along nearly 180° of the bezel
  ([Apple](https://www.apple.com/newsroom/2023/09/apple-unveils-apple-watch-ultra-2/)).
- **Items along the curve read as equal or as flow**; motion out from the centre reads as expansion
  ([Samsung circular UX](https://developer.samsung.com/one-ui-watch-tizen/circular-ux.html)).

### Always-on is a designed form
- **Not a dimmed copy**: "Do not simply lower the brightness level of an active state" ([Samsung](https://developer.samsung.com/one-ui-watch-tizen/watch-face/states.html));
  replace large bright areas with outlines and dim interiors (Apple's Numerals Duo); avoid much white or blue, prefer
  light grey and thin weights ([Garmin](https://developer.garmin.com/connect-iq/articles/user-experience-guidelines/Watch_Faces.html)).
- **Keep the layout and let motion settle** between modes: "Avoid making distracting interface changes when Always On
  begins or ends"; "Gracefully transition motion to a resting state" ([HIG Always On](https://developer.apple.com/design/human-interface-guidelines/always-on)).
  Apple's own faces still change shape continuously (Metropolitan's numerals "rotate to become pills when your wrist
  is down").
- **Budgets**: 10–20 % of pixels or luminance lit, once-a-minute updates, usually no seconds (table in
  [§10](#10-platform-notes)). On the Descent, always-on cuts battery from 10 to 4 days.

### Motion
- **Motion means something**: Spring Drive's glide "the natural flow of time", deadbeat seconds were built so
  observatories could read the exact second, Hilfiker's hand pauses ~1.5 s at 12 waiting for the master clock
  ([Galaxus](https://www.galaxus.at/en/page/come-to-think-of-it-why-does-the-second-hand-of-the-swiss-railway-clock-pause-at-12-21628)).
  Flowing, measured or synchronised with something outside are three different messages.
- **A change of rhythm can be the alarm**: Citizen and Seiko seconds hands jump in 2-s steps when the charge is low
  ([Citizen](https://www.citizenwatch-global.com/support/faq/gfaq/malfunction)) — a second datum and a warning
  without one more pixel, readable only by those who know.
- **Motion only when the data needs it**: Samsung's example blinks only when heart rate is outside 60–100
  ([Samsung](https://developer.samsung.com/codelab/watch-face-studio/design-optimization.html)); Pebble animates "to
  draw the eye to updated or changing information" and lets users turn it off. "Make motion optional… avoid using it
  as the only way to communicate" ([HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion)).
- **A transition can mark the update**: ClockClock's 24 clocks spin and line up into digits once a minute; Apple's
  Flux numerals "snap into new forms when the minute changes". A new glucose reading is a moment a face can mark.

### Customisation
- **People expect to choose**: data slots, colour themes, hands/numeral styles; Wear OS listings must show more than
  one permutation ([quality WO-G6](https://developer.android.com/docs/quality-guidelines/wear-app-quality)). The most
  loved Garmin faces sell exactly this (Crystal: data fields, indicators, meters, a dozen themes; its 2026 rebuild
  added full-colour always-on, per-element always-on toggles, on-watch settings "a million times easier", custom
  colours: [the5krunner](https://the5krunner.com/2026/03/20/garmin-crystal-watchface-reborn/)).
- **On the watch: binary choices and short lists; the rest on the phone** ([Android Wear 2014](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/lollipop-mr1-release/docs/html/design/wear/watchfaces.jd)).
  Settings that only exist on the phone confuse people ([Segment34 FAQ](https://github.com/ludw/Segment34mkII/blob/main/FAQ.md)).
- **A slot must handle everything a source can say**: no data, the widest value, a unit, an error. On Wear OS the
  source sends data and the face decides how it looks ("the data source plays no part in determining how the data
  should be represented": [WFF](https://developer.android.com/training/wearables/wff/complications)); a slot that
  shows "null null" becomes a 53-comment issue ([xDrip #303](https://github.com/NightscoutFoundation/xDrip/issues/303)).
- **Previews are judged first**: store previews standardise 10:09 and default data ([Zepp](https://docs.zepp.com/docs/watchface/specification/));
  the face's default state is its first impression.

## 7. Encodings: ways to draw each datum

A catalogue to widen the space, from watchmaking, conceptual watches, smartwatch faces and instruments
([full catalogue with every source](evidence/42-watch-faces/horology-and-encodings.md)). **Glance**: L one look,
M a second look or a learned key, H decoding or a press. **Discreet**: P public (anyone reads it), C coded (looks
like watch furniture; needs a key), H hidden until asked. Entries marked † are invented, untested.

### Time (hours and minutes)

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Digits, any size relation (hours big / minutes small, stacked, side by side) | Most digital faces | 1 min · L | P | The hierarchy between H and M is a choice |
| Segment / dot-matrix digits | Pulsar 1972 (27 dots a digit), [Segment34](https://github.com/ludw/Segment34mkII) | 1 min · L | P | Lit-pixel budget |
| Mechanical-disc digits (jumping) | Lange Zeitwerk, IWC Pallweber | 1 min · L | P | Big area |
| Words, with dots for the remainder | [QlockTwo](https://newatlas.com/qlocktwo-typographic-clock/12620/), Pebble Fuzzy Text | 5 min (1 with dots) · L–M | P | One language |
| Wandering numeral travelling a minute arc | AP Starwheel, Urwerk | 1 min · M | C | Uses part of the dial |
| Jumping hour + retrograde minute arc | Chanel Monsieur, VCA Pont des Amoureux | 1 min · L | P | The snap-back draws the eye |
| Hands (two, one, 24 h) | universal; MeisterSinger single hand; Solar Dial | 1–15 min · L | P | The owner asked for digital time; listed for other faces |
| Minutes outer, hours inner | B-Uhr Type B | 1 min · M | P | Hour demoted |
| Discs turning under a fixed mark | Ressence, Ziiiro swirls | 1 min to "about quarter to" · L–M | C | Approximate |
| Count of lit elements (rings, dots, squares of ten) | Tokyoflash RPM, Nooka Zot, TTMM 9TTMM | 1 min · M→L with practice | C | Puzzle fatigue ("I just want to know the time") |
| Morphing / highlighted numerals | Apple Metropolitan, Contour, Flux | 1 min · L | P | Power and animation cost |
| Clock of clocks forming digits once a minute | [ClockClock 24](https://www.humanssince1982.com/en-us/blogs/concepts/the-clock-clock-2008-2010) | 1 min · L when formed | P | Unreadable mid-transition |
| Hidden until asked | Hermès Le Temps Suspendu, Pulsar press | — · H | H | No passive glance |

### Seconds

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Sweeping or ticking element round the perimeter | seconds hands; Apple Modular Ultra's edge count-up | 1 s · L | P | A full ring lights many pixels |
| Stop-to-go: a lap, then a pause | [Hilfiker](https://en.wikipedia.org/wiki/Swiss_railway_clock) | ~1 s · L | P | The pause carries its own meaning |
| Retrograde arc | Moser, 30-s arc | 1 s · L | P | The snap-back is an attention event |
| Jumping digits | F.P. Journe Vagabondage III | 1 s · L | P | Constant change pulls the eye |
| Fill or gradient that moves with the second | Apple Flux (fills the face), Palette | ~5 s · L | C | Large lit area |
| Rhythm of something else (a blinking colon, a pulse, a character's step) | Mickey's tapping foot | liveness · L | P | Whimsy, motion cost |
| A gap travelling the minute track (one missing tick) † | — | 1 s · M | C | An absence pops out less than a presence |
| Running indicator only (proves "alive", not which second) | [ISO 6425](https://cdn.standards.iteh.ai/samples/12774/166555c91df4457294ef397a7a10d6a4/ISO-6425-1996.pdf) | — · L | P | |
| Seconds on demand (press, tap, wrist raise) | Pulsar hold; Exactograph's dial "expands on tap" | 1 s · H | H | |

### Day of week, date, month

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Window or text (abbreviated / full word) | Day-Date | exact · L | P | Language, width |
| Outsize date | Lange 1 | exact · L | P | Space |
| Pointer on a 1–31 track, or a marked numeral on a ring | Oris pointer date; Nomos coloured apertures | exact · M / L | P | One more pointer |
| Retrograde arc (7 steps or 31) | weekday snail cam; Hermès date arc | exact · L | P | |
| One glyph per weekday | Seiko kanji wheel (月 火 水 木 金 土 日) | exact · L once learned | C | |
| A dot on a spiral of 31 | [Ochs und Junior](https://www.ochsundjunior.swiss/watches/moon-phase/) (indices double as 5-day marks) | exact · L after learning | C | Needs the key |
| Month as the hour index it equals (1 = January) | Ochs und Junior perpetual | exact · M | C | |
| Weekday as an accented index 1–7 † | — | exact · L after learning | C | Clashes with the hours |
| Weekend or not by a change of the whole face | Snoopy's "Sunday Surprise" | weekend · L | C | |
| Season as day length | Solar Dial | weeks · L | C | Depends on location |

### Battery time left

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Arc between full and empty | Lange "Auf/Ab" power reserve (1879) | continuous · L | P | |
| Days-left number | Segment34's battery-days field | 1 day · L | P | |
| A drawn element that erodes with the days (a chapter ring shortening) † | — | days · L | C | Can read as decoration, or as a bug |
| Rhythm change when low (2-s steps) | Citizen, Seiko | threshold · L if known | C | Only while awake |
| Countdown that appears only near the end | Hermès L'heure impatiente | 1 min · L | P | |

### Glucose: the value and its zone (a quantity with a good zone)

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Digits | every CGM face | exact · L | P | The most public form |
| Zones on a scale, neutral pointer | FAA airspeed arcs | zone + position · L | P | Zone limits must be the user's |
| Fine scale where decisions happen (magnified low band) | Blancpain X Fathoms: a 0–15 m hand with the 3–6 m band in orange | high where it matters · L–M | P/C | Two pointers to tell apart |
| Memory of the extreme (the night's lowest stays until morning) | X Fathoms max-depth hand | value · L | C | Needs a reset rule |
| Zone only: a colour, a shape, a word, a mood | GDH's "very big and coloured"; Nightscout Clock creatures; a colour band | zone · L | P→C | The exact number one action away (§4) |
| Fuzzy words ("about 6, steady") † | after Fuzzy Text | coarse · L | P | Words are public |
| A fourth hand pointing at mmol/L on the hour numerals, posing as a GMT hand † | GMT hand, pointer date | ~0.2 mmol/L · L | C | Wraps above 12; risk of reading it as time |
| The target range as a sector band, in or out † | 1930s sector dials | zone · L | C | |
| Level of a liquid (height = value, tilt = trend) † | HYT, Flux | coarse · L | C | Large lit area |
| Range as daylight: the value as a sun inside its "day" arc † | Solar Dial | zone · L | C | Collides with real day and night |
| Masked: shown on raise or tap, otherwise hidden † | Hermès L'heure masquée | exact · H | H | Hides when it matters unless the face unmasks itself on an alert |

### Glucose: trend, age and history

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Arrow glyph, 5 or 7 states | Dexcom, Libre | class · L | P | Each system's arrows mean different speeds |
| Continuously turning arrow | Sukrara: angle = clamp(rate / 3) × 90° | continuous · L | P | Pre-attentive (orientation pops out) |
| Tilt of another element (an index, an anchor, the whole value) † | B-Uhr anchor tilted | 5–7 classes · L | C | The anchor stops anchoring |
| Shape of a tip (arrowhead, flat, chevron) † | pointer-date tips | 3–5 classes · M | C | Small |
| Inflection: rising-but-slowing, falling-but-turning | Casio Pro Trek barometer's four arrows | classes · L | P/C | Needs a defined second derivative |
| Delta number | every DIY face | 1 mg/dL · L | P | |
| Signed offset from the last reading | Casio's differential pointer | coarse · M | C | |
| History graph with the newest point marked | Casio pressure graph; Sukrara's 12 h chart | shape · M | P | Small; needs scale cues |
| The hour track as history (each hour index's length = that hour's glucose) † | radial sparkline | profile · M | C | Radial comparison is slow (Blascheck) |
| A marker where the value will be in 15–30 min † | Loop's eventual glucose | zone · L | C | Planned in Sukrara |
| Speed of a motion (a whirl for fast, a twitch for steady) | Weiser's Dangling String | coarse · L, peripheral | C | Motion is noticed by others too |
| Age: text (`now`, `3m`), struck-through or greyed value, a ring that drains between readings, an element that stops moving | Sukrara freshness, xDrip+, Nightscout Clock's stopped car | steps · L | P/C | Must reach "old" unmistakably |
| Seconds that wait for the next reading (stop-to-go synced to the sensor) † | Hilfiker | fresh/due/late · L | C | Drifts from true seconds; bounded wait |
| The "running" element stops or dims when data is old † | ISO 6425 running indicator | binary/graded · L | C | |

### Treatments, quiet and sensor

| Encoding | Example | Precision · glance | Discreet | Watch out |
|---|---|---|---|---|
| Time since as text (`1h 20m`), amount beside it | Sukrara's last-treatment slot | min · L | P | |
| A mark on the time dial at the moment it happened (the dial as a timeline) † | Android Wear's "a watch dial is a timeline" | position · L | C | Wraps after 12 h |
| Quiet window as an arc or countdown that empties | Sukrara's *10m left* badge; Hermès L'heure impatiente | min · L | C | |
| Sensor life as a disc that drains (moon-phase-like); countdown ring in the last hours | Sukrara's sensor disc and countdown ring | days/hours · L | C | |
| Warm-up as something filling | Sukrara's filling disc | min · L | C | Never a figure that passes for glucose |

## 8. Discretion: readable by the wearer only

The owner's brief in one line. Ways to get there, with their costs ([evidence](evidence/42-watch-faces/horology-and-encodings.md#4-discretion-how-a-watch-can-be-readable-only-by-its-wearer)):

1. **Unlabelled positions read with a private key** (Ochs und Junior dots, Ziiiro swirls, a single dot): onlookers see
   ornament. Costs learning time.
2. **Disguise as a familiar complication** (GMT hand, power reserve, sector ring, moon phase): looks like watch
   furniture. Risk: the wearer misreads it too, e.g. glucose as time.
3. **Hidden until asked** (Le Temps Suspendu, Pulsar, Tokyoflash's tilt-to-reveal): loses the zero-effort glance; must
   unmask itself when action is needed.
4. **Behaviour codes**: a rhythm, a jump size, a pause; invisible to anyone who doesn't know.
5. **Glyph systems others don't share**: kanji weekdays, planetary symbols, a personal icon set.
6. **Coarseness**: zones or words instead of numbers reveal less, to the wearer too; the exact value one action away.
7. **Familiar context** (reasoned, untested): a face full of ordinary data (weather, steps, heart rate) makes one more
   number unremarkable; a face where glucose is the only datum makes it the subject.

And what works against it:
- **Pre-attentive channels** (hue jumps, flicker, motion, sudden size change) reach everyone's peripheral vision, not
  only the wearer's ([Healey](https://www.csc2.ncsu.edu/faculty/healey/PP/)). Red and green zones are a public code.
- **Medical iconography**: drops, syringes, the word *glucose*, mg/dL printed beside a number.
- **Discretion against safety**: Dexcom keeps urgent low and failures un-silenceable on purpose. A face's discretion
  can yield when action is needed (it is Sukrara's alert, not the face, that decides that), and §4 rule 4 always
  holds.
What is known about onlookers:
- **Shoulder surfing is opportunistic**: curiosity (34 %), boredom (34 %), "device in line of sight", "reaction to
  stimulus"; text is what gets read (47 %); people react by switching to something harmless
  ([Eiband 2017](https://www.medien.ifi.lmu.de/pubdb/publications/pub/eiband2017chi/eiband2017chi.pdf), phones). A
  change, a flash or a bright patch is the stimulus that draws a stranger's eye.
- **Obfuscation works on casual readers**: handwritten-style fonts deterred them ([CHI EA 2016](https://www.researchgate.net/publication/302073980_My_Scrawl_Hides_It_All_Protecting_Text_Messages_Against_Shoulder_Surfing_With_Handwritten_Fonts)).
- **Reading as medical isn't automatically stigmatising**: a device labelled "medical" was rated more acceptable than
  the same device as a "brain fitness tool" (d = 0.78, N = 1,387: [Kelly & Gilbert 2018](https://doi.org/10.1177/1541931218621237));
  some people with diabetes treat visibility as identity ([PMC13154725](https://pmc.ncbi.nlm.nih.gov/articles/PMC13154725/)).
  The owner's "doesn't scream diabetes" is a personal preference these studies neither confirm nor refute; other
  wearers may want the opposite, so discretion is a setting of the person, not a law of the face.
- **Fashion and social acceptability are separate**: a watch must be "a traditional watch" *and* personal expression
  ([Visuri 2017](https://ubicomp.oulu.fi/files/chi17.pdf), [Ouverson 2017](https://link.springer.com/chapter/10.1007/978-3-319-58071-5_16)).
- **No study yet** of what bystanders notice on a *watch*: a design round can only reason about it, or test it with
  people.

## 9. A face that reacts

What the face could react to, and the evidence on how much to change.

| Trigger | Known from | Example reaction (space of options) |
|---|---|---|
| Zone change, distance to a threshold | Sukrara | Emphasis grows with distance and urgency; nothing changes while in range |
| Fast move, inflection | Sukrara trend | A quiet sign of speed; a turning point |
| Reading age | Watch clock + reading time | The value ages visibly ([§3](#3-states-every-face-must-carry)) |
| A treatment logged | Sukrara | "Handled" state with its remaining time; the time since it |
| An alert needed / muted / quiet | Sukrara alert status | The need in words; the quiet with its end |
| Sensor warming / ending / ended | Sukrara | Countdown, filling, flag |
| Time of day, sleep schedule | Watch | Night rendering; fewer data at night |
| Activity in progress, heart rate | Watch | The zone bigger, the rest smaller (sport) |
| Water / dive | Watch (native) | Gaps expected; pre-dive checklist |
| Phone or link lost | Watch | Which link, since when |
| Watch battery low | Watch | Battery more prominent |
| Wrist raised vs always-on | Watch | Detail when awake, essentials at rest |
| Time-zone change | Watch | Travel aid (insulin timing) |

How much to change:
- **Calm by default**: Weiser and Brown's calm technology moves information between the periphery and the centre,
  with the person in charge ([Weiser & Brown 1995](https://calmtech.com/papers/designing-calm-technology)). The face
  stays in the periphery while nothing needs the wearer.
- **Platforms disagree**: Android Wear asked faces to "react to [data] by changing styles and colors"; Apple's
  always-on rules ask for "a consistent layout" and "infrequent, subtle updates" ([§6](#6-principles-from-watch-platforms-and-watchmaking)).
  A reaction that keeps the layout (emphasis, contrast, size within its place) reconciles them.
- **Gate the change on the data** (Samsung's blink only out of 60–100 bpm): reactions cost attention and power, so they
  appear only when the condition holds.
- **Dark cockpit**: Airbus panels light up only "to indicate where an action is required": "if all the lights are out,
  then the aircraft is ready to fly" ([Airbus](https://www.airbus.com/sites/g/files/jlcbta136/files/2021-10/A330neo-cockpit-commonality-with-A350-innovations.pdf)).
  But dark isn't proof of OK, so pilots cross-check; a face that goes quiet in range still needs a sign that it is
  alive and current (Mankoff's "was broken").
- **Escalation is a ladder with few, redundant steps**: medical alarms (IEC 60601-1-8) have three priorities, each
  coded at once by colour (cyan or yellow steady, yellow 0.4–0.8 Hz, red 1.4–2.8 Hz), sound and glyph count (`!`,
  `!!`, `!!!`); information must look different from alarms ([IEC text](https://standards.har-el.com/Projects/181701/60601-1-8/html/6-3-2-Visual-Generation.htm)).
  Shearwater dive computers make the level readable **without colour**: warnings solid inverted, critical flashing
  between inverted and normal ([Perdix manual](https://www.manualslib.com/manual/1365375/Shearwater-Perdix.html)).
- **Persistence before reacting**: the Descent's "ascending too fast" fires after > 9.1 m/min **for more than 5 s**;
  Sukrara's fast alerts need the rate held 5 min. A face's reaction to a fast move can follow the same rule.
- **Show where it's heading, not only where it is**: an airliner's speed trend vector is an arrow to the speed 10 s
  ahead, drawn only above a dead band, and on Gulfstreams the digits turn amber when the *vector* reaches the low-speed
  band: a predictive alert ([FAA report](https://www.faa.gov/sites/faa.gov/files/2022-11/ASHWG%20LAA%20Report_15%20March%20Final%20Version_Appendix%201.pdf)).
  Up means more, so rising energy moves upward.
- **Bad data must be unusable, not just marked**: lost or wrong primary data is removed or crossed with an X, and the
  detector is independent of the source (AC 25-11B §4.6). Stale glucose is the watch's failed sensor (§4 rule 1).
- **A reaction explains itself**: what happened, why the face looks different, how it ends (CLAUDE.md *Make it
  juicy*: "motion that explains what happened").
- **Context is weak evidence**: heart rate can't tell a low (1.4); "exercising" doesn't say which way glucose goes; a
  reaction inferred from context should be mild and reversible.

## 10. Platform notes

Facts that a design can use or must know; limits here are not design rules ([How to use](#how-to-use-this-report)).
Details and sources: [evidence: Garmin](evidence/42-watch-faces/garmin.md), [evidence: platforms](evidence/42-watch-faces/platforms.md).

### The owner's watch: Garmin Descent Mk3i 43 mm

There is no 42 mm Descent Mk3: the owner's "42 mm" is the **Mk3i 43 mm** (`descentmk343mm`, Connect IQ API 5.1).

| | |
|---|---|
| Display | 1.2″ (30.4 mm) round AMOLED, **390 × 390 px, 325 ppi, ≈ 12.8 px/mm** (1 mm ≈ 13 px), 16-bit colour; sapphire |
| Case | 43 × 43 × 14.15 mm, titanium bezel (carbon-grey DLC or bronze PVD) with four exposed screws at 1:30, 4:30, 7:30, 10:30 |
| Bezel | A **printed 24-hour ring** outside the pixels: 24 at the top, 06 at 3, 18 at 9, "GARMIN" at 6, red "NO-FLY TIME" and cyan "ALT TZ" near 2. Always visible, even with the screen black; every edge pixel reads against it. Garmin's own face points at it for no-fly time and a second time zone |
| Buttons | 5: LIGHT (top left), UP·MENU (mid left), DOWN (bottom left), START·STOP (top right), BACK·LAP (bottom right); all belong to the system on a face |
| Touch | Yes; a face gets **touch-and-hold** with coordinates (e.g. hold a slot to open its app); no tap on this model |
| Modes | Wrist raise wakes the face for ~10 s (every second, animation possible); otherwise once a minute. Always-on optional: ≤ 10 % luminance or the face goes black, no seconds; battery 10 days → 4 days |
| Night | **Red Shift** (red/green/orange tint for night vision, can turn on at bedtime or sunset; a face can't detect it, the system re-tints), **Lume** (native faces only: turquoise glow from the ambient-light sensor), Night Vision mode, **Large Fonts** (a face can detect it) |
| Data a face can read | Battery % and **days left**, steps, floors, intensity minutes, calories, heart rate (and 6 h history), body battery, stress, respiration, SpO2, recovery time, altitude, pressure, temperature, compass, weather and forecast, sunrise/sunset, notifications, alarms, do-not-disturb, phone connected, user's sleep and wake times, 12/24 h, first day of week; 42 system complications |
| Not readable by a face | Dive data (surface interval, no-fly, tissue load), ambient light, Red Shift state, HRV, training readiness, sleep state |
| Other apps' data | Complications published by other apps (up to 4 per app, with value, labels, unit, icon and ranges), also usable in Garmin's own photo face (Face It) |
| Attention | A face can't vibrate or sound by itself; alerts come from Sukrara (phone notifications mirrored by Garmin, or a Sukrara watch app) |
| Fonts | System vector fonts at any size (Bionic numbers, Roboto Condensed, Swiss 721), text along a circle, custom fonts; built-in heights 19–80 px |
| Settings | On the watch (hold MENU › Watch Face › Customize: menus) and in the Connect IQ phone app |
| Mockups | SDK simulator image `descentmk343mm.png` 638 × 830 px with the display at x 131, y 211, 390 × 390 ([copy](https://github.com/wonderfulest/watch-face-designer/blob/main/src/assets/devices/descentmk343mm/descentmk343mm.png)); installed SDK: `~/Library/Application Support/Garmin/ConnectIQ/Devices/descentmk343mm/`; product photos: [Garmin 852217](https://www.garmin.com/en-US/p/852217/) |
| Today's data path | The watch polls every 5 min ([docs/08](08-garmin.md)); push from the phone, a companion app or BLE are faster paths. A limit to solve, not to design for |

### Always-on budgets elsewhere

| Platform | Lit budget | Update | Notes |
|---|---|---|---|
| Garmin AMOLED (API ≥ 5) | ≤ 10 % of total luminance | 1/min | Above it the face goes black; move elements ≤ 4 px a minute |
| Wear OS | ≤ 15 % average luminance (Play review) | ~1/min | ≥ 85 % black; outlines not fills; time always shown |
| Samsung | ≤ 15 % on-pixel ratio | — | Red counts 33 %; no dimmed copy of the active face |
| Fitbit | ≤ 20 % | 1/min | No numeric stats in AOD (stale data) |
| Zepp | ≤ 10 % of pixels | — | No second hand |
| Apple | No quota; the system dims by lit ratio | Down to 1/min (1/s on Series 10) | Outlines and dim interiors; 1 nit night mode |

## 11. Review checklist for a face

Before showing a design to the owner, check it against:

- [ ] A 2-second glance answers: the time; the value or its zone; the direction; **whether it is current**.
- [ ] Every state of [§3](#3-states-every-face-must-carry) is drawn, including stale, no readings, LO/HI, no trend,
  warm-up, ended, quiet, muted, no sensor; and mg/dL *and* mmol/L at their widest.
- [ ] The [§4](#4-must-the-few-hard-rules) rules hold, in colour, in grey (Red Shift / tinted) and in always-on.
- [ ] The discretion level is deliberate: what a stranger at 1 m sees in each state; nothing medical-looking unless
  chosen ([§8](#8-discretion-readable-by-the-wearer-only)).
- [ ] What draws the eye pre-attentively is only what should: nothing moves, flashes or turns saturated while in range.
- [ ] Always-on is designed (not dimmed), within budget, layout kept, and still answers the 2-second question:
  glucose is in it, not only the time.
- [ ] Stale and no-data marks never cover the time, and say since when (and why, when known).
- [ ] Night: brightness, red rendering, nothing that lights a bedroom or a cinema.
- [ ] Secondary data stays legible for older eyes (no small dark-grey text).
- [ ] Sizes in mm at wrist distance meet [§5](#5-seeing-at-a-glance-perception); no thin weights; distinguishable
  digits.
- [ ] Each reaction ([§9](#9-a-face-that-reacts)) is gated on data, explains itself and has a visible end.
- [ ] User-chosen slots handle no data, widest value, unit and errors; settings are reachable on the watch.
- [ ] Words exist in English and Spanish (weekdays, months, states) and fit.
- [ ] Motion has a meaning, settles, and the face works without it.
- [ ] Mocked up on the real frame: the Descent's 24 h bezel ring around the pixels, at 1:1 physical size.

## 12. Lessons from design rounds

What design rounds teach that applies to any face: the reason or the usability fix, not the design. Add an entry per
lesson: date, the face (by name only), what was learned, why, and the evidence (the owner's words, a test).

*None yet.*

## Open questions

- **Bystander perception**: no study of what others notice on a watch face (number, colour, graph). Testable with
  people in a design round.
- **Health data in always-on**: Apple's docs both hide and show health data; nobody addresses medical data the wearer
  acts on.
- **Stale threshold**: products disagree (10.5–20 min); Sukrara's steps (5 / 20 min) are stricter than most.
- **Should the wrist alert?** Split ecosystem (1.6); the DVLA treats dependence on alarms as a reason to stop driving.
- **Trend vocabulary**: continuous angle (Sukrara) vs 5/7 classes vs inflection (Casio); no study of reading a trend
  arrow at a glance.
- **Rings vs bars**: concentric radial bars were ~6× slower to compare (2019), yet donuts beat bars (2019) and bars
  beat rings by ~80 ms on proportions (2023), where rings won on beauty; reading one live value or zone is untested.
- **Text vs graphics for one value**: text read slowest on proportions, yet it is what faces use most, and aviation
  pairs every readout with a scale because a number hides the margin.
- **Polarity and weight at night**: legibility research favours dark-on-light for small text in the dark; dark
  adaptation, always-on budgets, burn-in and battery all push to sparse light-on-dark. Lighter weights may read better
  (Dobres) but lose contrast first in sun and at an angle. Untested on a 1.2″ emissive screen.
- **Red/green vs colour blindness**: aviation, the Descent and Shearwater all use green/yellow/red, which ~8 % of men
  can't rely on and Red Shift erases; redundant coding (inversion, flashing, shape) is the documented answer.
- **Subtle change vs change blindness**: ambient theory asks for subtle changes, but a change between glances is
  invisible unless the face encodes it. With the wrist down the screen is out of sight, so peripheral-motion research
  mostly applies to the moment the wrist comes up.
- **Flash rules disagree**: 1.4–2.8 Hz (IEC high priority), 0.8–4 Hz cancellable (FAA), ≤ 3/s and an area limit
  (WCAG) that a full-face flash at wrist distance exceeds.
- **Garmin**: the always-on rule version on this firmware, the exact awake window on the Descent, whether Red Shift
  re-tints Connect IQ faces, whether a face's notification vibrates.
- **Gaps in this research**: Reddit was blocked to every tool used (web fetch, and Claude in Chrome refuses the site
  itself, even in a tab the owner opened), so people's voices come from Garmin store reviews, Play reviews, Diabetes UK
  and other forums, GitHub issues and qualitative studies; the App Store's reviews weren't readable; Hodinkee couldn't be fetched; a few numbers come
  from secondary summaries (marked in the evidence files).
