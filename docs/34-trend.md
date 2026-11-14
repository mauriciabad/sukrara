# 34 — The trend from the readings: which math

*2026-11-14 · investigation report · issue #204 · manufacturers' guides and patents (Dexcom, Abbott, Medtronic,
Sanvita), literature (Facchinetti / Sparacino / Cobelli 2010, Aleppo 2017, Kudva 2018, Eichenlaub 2024, Kovatchev 2005,
Dunn 2004, 21 CFR 862.1355), the sources of xDrip+, xDrip4iOS, Nightscout, oref0, AndroidAPS, Loop, Trio, Juggluco and
GlucoDataHandler, user discussions · nine candidates prototyped and evaluated on the owner's own 1-min readings, locally
and read-only ([`investigations/204-trend`](../investigations/204-trend)); this report holds aggregate numbers only*

## Goal

The arrow beside the value answers "where is it going?", the question behind most decisions about food and insulin.
Today it comes from the sensor's own rate (Abbott's, for the Libre 3), which lags: on 2026-11-14 the readings climbed
about 1 mg/dL a minute while the sensor said 0.79 and the arrow showed →; on the #193 evening it still said "falling"
minutes into the rebound. The owner decided the trend is worked out from the readings by default (the sensor's rate
stays a setting and the fallback), and asked: *simply subtracting the glucose from x minutes ago seems like a bad
method; what math should it be?* This investigation compares the methods used by manufacturers, research and DIY apps
on the owner's real readings and recommends one, with its parameters, the text bands' hysteresis, the drawn arrow's
angle and the shared test vectors.

## Short answer

- **Recommendation: a least-squares slope weighted toward the newest readings**: weights `exp(−age / τ)` over the
  last 5τ, **τ = 3 min** (15 min of readings), at least two readings spanning at least 3 min, starting over after a
  step longer than 12.5 min or a LO / HI. It is a pure function of the readings (no state: a restart, a backfilled
  reading or a follower with the same readings gets the same arrow), reads a straight line exactly, needs no resampling
  and uses **the same numbers for 1-min and 5-min sensors**. About 15 multiply-adds per reading.
- **On the owner's 4.6 days of 1-min readings it reaches ±1 mg/dL/min 4 min after the hindsight slope (p90 5 min);
  Abbott's rate takes 7 min (p90 17 min) and misses half the crossings**: its rate is only about a third of how fast
  the readings move (gain 0.35). The two-point delta (Nightscout, xDrip+) is 1–2 min quicker but changes band twice as
  often and falls apart with noise; Loop's 15-min regression is 3 min slower; the Savitzky–Golay end point is the
  noisiest; Holt, a Kalman filter and AndroidAPS' `shortAvgDelta` score the same as the recommendation (they are
  nearly the same filter) but are stateful or need a 5-min grid.
- **Presets, if wanted: Quick τ 2 / Balanced τ 3 (default) / Smooth τ 5**, with "From the sensor" as the fourth choice
  of the same setting. **Text bands with hysteresis 0.2 mg/dL/min** (−20 % band changes, +0.7 min).
- **The drawn arrow: linear, 0° at 0, vertical at ±3 mg/dL/min (the PoC), no extra smoothing, a short spring between
  readings.** Doubled from 3, with the same hysteresis.
- **Two consequences for the owner** (§7): the readings move ≥ 2 mg/dL/min far more often than Abbott's rate says
  (~25 times a day instead of ~2): ↑ / ↓ and ⇈ / ⇊ will be seen much more, and the rate alerts can't simply switch
  to this trend. The #193 quiets keep their own rule: nothing here beats it for what it does.

## 1. How others compute it

**Manufacturers** (the algorithms are not published):

| Who | What is known | Source |
|---|---|---|
| Dexcom | bands ±1 / 2 / 3 mg/dL/min (G7: "< 30 / 30–60 / 60–90 / > 90 mg/dL in 30 minutes"); "trend arrows are determined by recent rtCGM measurements (generally the most recent 10 minutes of glucose values)"; the G7 transmitter sends the rate itself (signed byte ÷ 10 mg/dL/min) | guides [G7](https://dexcompdf.s3.us-west-2.amazonaws.com/en-us/G7-CGM-Users-Guide.pdf), [G6](https://dexcompdf.s3.us-west-2.amazonaws.com/en-us/Using-Your-G6-new.pdf) (*wording from search excerpts, not checked against the PDFs*); [Aleppo 2017](https://dx.doi.org/10.1210/js.2017-00388); [G7SensorKit G7GlucoseMessage.swift L33-52](https://github.com/LoopKit/G7SensorKit/blob/HEAD/G7SensorKit/Messages/G7GlucoseMessage.swift#L33-L52) |
| Dexcom patent | "maximal sustained rate of change … about 4 to 6 mg/dl/min", "maximum sustained acceleration … about 0.1 to 0.2 mg/min/min", used to reject outliers, not for the arrow | [US8954128B2](https://patents.google.com/patent/US8954128B2/en) |
| Abbott Libre | five arrows at ±1 / 2; "The trend arrows are calculated from glucose readings with an emphasis on the most recent 15 minutes"; the Libre 3 sends `rateOfChange` (int16 ÷ 100) | [Kudva 2018](https://academic.oup.com/jes/article/2/12/1320/5181247); [Juggluco libre3/bluetooth.cpp L134-151](https://github.com/j-kaltes/Juggluco/blob/HEAD/Common/src/main/cpp/libre3/bluetooth.cpp#L134-L151); how it smooths: *not found* |
| Medtronic patent | "SG smoothed rate of change … using the 5 most-recent Isig values", weights [0.2, 0.1, 0, −0.1, −0.2] (a 5-point least-squares slope) | [US11253176](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/11253176) (*excerpt*) |
| Sanvita patent | rate "based on five consecutive data points", bands 1 / 2 / 3 | [US11197627B2](https://patents.google.com/patent/US11197627B2/en) |

**Research and regulation:**

- Kalman / Bayesian smoothing: Facchinetti, Sparacino, Cobelli, *An online self-tunable method to denoise CGM sensor
  data*, IEEE TBME 2010;57(3):634 (and TBME 2011;58(9):2664). Their review: "the more the filtering, the larger the
  delay"; "the delay introduced by KF is about 35% less than that obtained by MA"; hypo alerts by "first-order linear
  extrapolation of glucose obtained from the last two/three samples"
  ([Sparacino 2010, PMC3231130](https://pmc.ncbi.nlm.nih.gov/articles/PMC3231130/)). Their filter is tuned per person
  and per sensor noise; it smooths the value, the rate falls out of the state.
- Displayed arrows can't be rebuilt from the readings: a least-squares slope of 4 readings in 15–20 min matched the
  Dexcom G5's displayed arrow in 90.7 % of cases, two readings matched the Libre 1's in 77.9 %; "the displayed trend
  arrows cannot be reliably reconstructed based on the stored CGM readings"
  ([Eichenlaub, Pleus, Freckmann 2024, PMC11307235](https://pmc.ncbi.nlm.nih.gov/articles/PMC11307235/)).
- The FDA's iCGM rule judges the rate only by its worst errors: ≤ 1 % of readings may say > +1 when the true rate is
  < −2 mg/dL/min, and the reverse ([21 CFR 862.1355 (b)(1)(i)(J)/(K)](https://www.law.cornell.edu/cfr/text/21/862.1355)).
- Physiology: rates over 2 mg/dL/min about 10 % of the time around meals (Dunn 2004, Diabetes Care 27:2161); sustained
  rates of ±2 / 3 / 4 mg/dL/min as 90 / 95 / 99 % bounds (Kovatchev 2005, DTT 7:849, *from an excerpt*).
- Savitzky–Golay: no CGM paper on SG *rate* estimation found; xDrip+ and xDrip4iOS use SG to smooth the drawn values.

**DIY apps** (code read at each repo's HEAD, 2026-11-14):

| App | Trend | Source |
|---|---|---|
| xDrip+ | two-point slope of the newest two readings (1 min apart with Libre 1-min data); bands ±1 / 2 / 3.5; SG smoothing for the graph only; Libre 2 values: a linearly decaying weighted average over 25 min | [BgReading.java L305-311, L1734-1749](https://github.com/NightscoutFoundation/xDrip/blob/HEAD/app/src/main/java/com/eveningoutpost/dexdrip/models/BgReading.java#L305-L311), [Dex_Constants.java L86-155](https://github.com/NightscoutFoundation/xDrip/blob/HEAD/app/src/main/java/com/eveningoutpost/dexdrip/importedlibraries/dexcom/Dex_Constants.java#L86-L155), [LibreReceiver.java L138-149](https://github.com/NightscoutFoundation/xDrip/blob/HEAD/app/src/main/java/com/eveningoutpost/dexdrip/LibreReceiver.java#L138-L149) |
| xDrip4iOS | two-point slope against the nearest reading 4–21 min older ("very close readings … become an extreme per-minute slope"); optional SG value smoothing (two passes for 1-min data) | [BgReading+CoreDataClass.swift L280-311](https://github.com/JohanDegraeve/xdripswift/blob/HEAD/xDrip/Core%20Data/classes/BgReading+CoreDataClass.swift#L280-L311), [ConstantsBgSmoothing.swift](https://github.com/JohanDegraeve/xdripswift/blob/HEAD/xDrip/Constants/ConstantsBgSmoothing.swift) |
| Nightscout | `delta`: mean of the newest 5-min bucket − mean of the one before; the arrow is the uploader's `direction` (no bands of its own) | [bgnow.js L142-172](https://github.com/nightscout/cgm-remote-monitor/blob/HEAD/lib/plugins/bgnow.js#L142-L172), [direction.js L48](https://github.com/nightscout/cgm-remote-monitor/blob/HEAD/lib/plugins/direction.js#L48) |
| oref0, Trio | `delta` = mean of `(now − then) / minutes × 5` for readings 2.5–7.5 min old; `short_avgdelta` 2.5–17.5; `long_avgdelta` 17.5–42.5. Fed 1-min readings unbucketed it degenerates: its running `now_date` keeps every next reading < 2.5 min away, so all are averaged into "now" and the deltas are 0 (found by the prototype) | [glucose-get-last.js L25-84](https://github.com/openaps/oref0/blob/HEAD/lib/glucose-get-last.js#L25-L84), [Trio DetermineBasal+Helpers.swift L52-85](https://github.com/nightscout/Trio/blob/HEAD/Trio/Sources/APS/OpenAPSSwift/DetermineBasal/DetermineBasal+Helpers.swift#L52-L85) |
| AndroidAPS | the same windows (`DeltaCalculator`) over readings first interpolated onto a 5-min grid ending at the newest one; AutoISF fits a parabola over up to 47.5 min; optional smoothing plugins incl. an adaptive unscented Kalman filter with state [G, Ġ] | [DeltaCalculator.kt L46-113](https://github.com/nightscout/AndroidAPS/blob/HEAD/plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/openAPS/DeltaCalculator.kt#L46-L113), [AutosensDataStoreObject.kt L331-433](https://github.com/nightscout/AndroidAPS/blob/HEAD/plugins/main/src/commonMain/kotlin/app/aaps/plugins/main/iob/iobCobCalculator/data/AutosensDataStoreObject.kt#L331), [UnscentedKalmanFilterPlugin.kt L47-60](https://github.com/nightscout/AndroidAPS/blob/HEAD/plugins/smoothing/src/commonMain/kotlin/app/aaps/plugins/smoothing/UnscentedKalmanFilterPlugin.kt#L47-L60) |
| Loop | glucose momentum: ordinary least squares over 15 min, > 2 readings, continuous, one source, no calibrations, capped at 4 mg/dL/min; the arrow is the CGM's own | [GlucoseMath.swift L65-118](https://github.com/LoopKit/LoopKit/blob/HEAD/LoopKit/GlucoseKit/GlucoseMath.swift#L65-L118) |
| Juggluco | the sensor's rate; the drawn arrow is continuous, `atan(0.75 × rate)`, near-zero snapped flat; sensors without a rate: a weighted regression of the newest 3 points | [curve.cpp L1701-1740](https://github.com/j-kaltes/Juggluco/blob/HEAD/Common/src/main/cpp/curve/curve.cpp#L1701), [interpretgs3.cpp L376-393](https://github.com/j-kaltes/Juggluco/blob/HEAD/Common/src/main/cpp/sibionics3/interpretgs3.cpp#L376) |
| GlucoDataHandler | the source's rate; optionally its own: per-reading slopes over 25 min weighted 2 / 1.5 / 1 / 0.5 by age; rotated arrow `clamp(rate, ±2) / 2 × 90°`, ±14° dead zone, 5° steps | [ReceiveData.kt L423-443](https://github.com/pachi81/GlucoDataHandler/blob/HEAD/common/src/main/java/de/michelinside/glucodatahandler/common/ReceiveData.kt#L423-L443), [GlucoDataUtils.kt L176-182](https://github.com/pachi81/GlucoDataHandler/blob/HEAD/common/src/main/java/de/michelinside/glucodatahandler/common/utils/GlucoDataUtils.kt#L176-L182) |

Nobody publishes a comparison of these on real data. The DIY apps that compute a trend use a two-point delta or an
average of deltas; only research uses Kalman filtering.

## 2. What people say

- "the direction of the trend arrow does not match the actual trend"; a maintainer: it is "calculated using the
  current and previous reading"; "I'll see my bg is stable until I investigate the graph" (one equal reading during a
  drop) ([xDrip+ discussion #3861](https://github.com/NightscoutFoundation/xDrip/discussions/3861), 2025-26).
- Libre 3 in xDrip+: "a minute time delta would lead to a non significant bg delta", and smoothing on top "leads to a
  big delay" ([#2598](https://github.com/NightscoutFoundation/xDrip/discussions/2598), 2023). Libre 3 1-min values
  "pretty raw and jumpy at times" ([#4102](https://github.com/NightscoutFoundation/xDrip/discussions/4102), *from a
  search excerpt*).
- The owner (#193, #204): the sensor's arrow said falling minutes into the rebound and flat during a clear rise.
- Reddit threads could not be fetched (blocked) and searches returned none: *not verified*.

Both failure modes show up: too jumpy (two-point slopes) and too late (the sensor's own smoothing).

## 3. The candidates

All causal and run on every reading, as the apps would (`methods.py`): **two-point delta** (newest vs the reading
1 min / 5 / 10 / 15 min before: xDrip+ and Nightscout); **AndroidAPS `delta` and `shortAvgDelta`** (5-min grid, as
above); **least squares** over 4–15 min (Sukrara's #193 `Trend` is 5 min, Loop 15 min); **recency-weighted least
squares** `exp(−age / τ)`, τ 2–5 min; **local quadratic** evaluated at the newest reading (the Savitzky–Golay end-point
derivative), 7–30 min, plain and weighted; **Theil–Sen** (median of pairwise slopes; robust to one outlier) over 5 /
10 min; **Holt** double exponential smoothing for uneven steps (α 0.2–0.7, β 0.1–0.3 per minute); a
**constant-velocity Kalman filter** (white-noise acceleration q 0.001–0.05 (mg/dL/min²)²/min, reading noise 1 mg/dL²,
restart after 10 min); and **Abbott's rate** as recorded. Each window method restarts after a gap and needs a minimum
of readings; `evaluate.py --sweep` runs all 50 parameter sets.

## 4. Evaluation on the owner's readings

**Data**: 6,501 Libre 3 readings, one a minute, 2026-11-10 to 2026-11-14 (4.6 days), with Abbott's rate for each;
4 holes over 5 min; no LO / HI; read locally from the Mac's `readings.csv`, nothing derived from them is committed
except the aggregates below. Reading-to-reading noise is about 0.6 mg/dL, but steps of 5 mg/dL or more from one
minute to the next happen about 70 times a day (7 or more: about 25).

**"Truth"**: a hindsight slope the apps can't have: at each reading, the slope of a parabola fitted with tricube
weights to the readings ±7 min around it (a centred Savitzky–Golay derivative). It is the slope of what the person sees,
not of blood glucose. Turning points: 203 times it crosses 0, 145 times ±1 and 64 times ±2 (each held ≥ 5 min).

**Metrics**: *delay (best fit)*: the shift that best lines the estimate up with the truth, and the *gain* there (< 1:
the arrow understates the move); *delay to cross 0 / ±1 / ±2*: minutes after the truth until the estimate does,
median (p90); *missed*: crossings it didn't make within 20 min (of 412); *band changes per hour* of the Nightscout
bands (±1 / 2 / 3), raw and with hysteresis 0.2; *angle change*: the median change of the continuous arrow (§6) from
one reading to the next. The truth itself changes band 9.3 times an hour (7.9 with hysteresis), so a perfect, delayless
method would too.

**1-min readings** (`evaluate.py READINGS`):

| Method | Delay | Gain | Cross 0 | Cross ±1 (p90) | Cross ±2 | Missed | Band changes/h | … hysteresis 0.2 | Angle change |
|---|---|---|---|---|---|---|---|---|---|
| Abbott's rate (today) | 3.5 min | **0.35** | 5 | **7 (17)** | 15 | **214** | 1.5 | 1.0 | 1.2° |
| delta, newest two (xDrip+) | 0.5 | 1.30 | −2 | −1 (1) | 0 | 0 | 33.0 | 17.6 | 29.5° |
| delta 5 min (Nightscout; AAPS `delta`) | 2.5 | 1.16 | 2 | 3 (4) | 3 | 0 | 14.0 | 11.7 | 6.0° |
| AAPS `shortAvgDelta` | 3.5 | 0.82 | 4 | 4 (6) | 4 | 5 | 7.6 | 5.9 | 4.1° |
| least squares 5 min (#193 quiets) | 2.0 | 1.22 | 1 | 2 (3) | 2 | 0 | 16.9 | 15.0 | 8.9° |
| least squares 15 min (Loop) | 7.5 | 0.78 | 7 | 7 (9) | 7 | 9 | 5.4 | 4.2 | 2.8° |
| quadratic 15 min (S-G end point) | 3.5 | 1.56 | 3 | 3 (4) | 3 | 0 | 17.5 | 15.5 | 7.5° |
| Theil–Sen 10 min | 4.5 | 1.02 | 4 | 5 (5) | 5 | 0 | 9.0 | 7.5 | 3.8° |
| Holt α 0.5 β 0.2 | 3.5 | 0.91 | 4 | 4 (5) | 4 | 1 | 8.3 | 6.6 | 4.2° |
| Kalman q 0.01 | 4.0 | 0.95 | 4 | 4 (5) | 4 | 0 | 8.5 | 7.0 | 4.2° |
| **weighted LS τ 2** (Quick) | 2.5 | 1.08 | 3 | 3 (4) | 3 | 0 | 12.7 | 10.2 | 6.1° |
| **weighted LS τ 3** (Balanced) | 3.5 | 0.90 | 4 | **4 (5)** | 4 | **0** | 8.1 | **6.5** | 4.1° |
| **weighted LS τ 5** (Smooth) | 5.5 | 0.65 | 6 | 6 (10) | 6 | 80 | 3.8 | 2.8 | 2.2° |

What it says:

- **Abbott's rate is not just late, it is flat.** It moves at about a third of the readings' slope, so it reaches ±1
  late or never (214 of 412 crossings missed within 20 min) and almost never says ↑ / ↓. That is the owner's
  experience in both episodes. Its low band-change count is the same flatness, not quality.
- **Every honest method trades delay for band changes along one line.** At about 6–7 band changes an hour (close to
  the truth's own 7.9) the recency-weighted slope, Holt, the Kalman filter and AAPS' `shortAvgDelta` all reach ±1 in
  4 min; plain least squares needs a 12-min window for the same calm and is then 1 min slower (sweep: 5 min at
  6.1 / h). Weighting the newest readings more is what buys the minute.
- **The two-point delta of the newest readings is unusable at 1 min** (33 band changes an hour, the arrow turning 30°
  a minute), and the 5-min delta and the 5-min least squares change band twice as often as τ 3 for 1–2 min less
  delay.
- **The Savitzky–Golay end point** has the least lag for a smooth curve in theory and the worst noise in practice:
  more band changes than the 5-min slope at the same delay, and it overshoots (gain 1.56).
- **Theil–Sen** scores like plain least squares of the same window: a robust slope buys nothing here, because the
  Libre 3's jumps are steps that stay, not single readings off the line.

**Robustness** (all in `evaluate.py`):

| Test | τ 3 (Balanced) | Others |
|---|---|---|
| Truth ±5 / ±10 min instead of ±7 | ±1 in 4 (6) / 4 (5) min | same ranking: delta 5 min 3 / 2 min with 2× the band changes, Loop 7 / 7, Abbott 6 (15) / 8 (17), gain 0.29 / 0.45 |
| Extra reading noise sd 2 mg/dL (rounded) | ±1 in 4 (5); 7.3 band changes/h with hysteresis | delta 5 min 17.9 / h, least squares 5 min 19.4, newest-two delta 44.7, AAPS short 7.9, Holt / Kalman 7.5 |
| 40 random 3–20 min holes | a rate on 99 % of judged readings; after a hole: at once if ≤ 12.5 min (bridged), else after 3 min of readings (max 6) | Loop-style 15-min continuity: median 8, max 37 min without an arrow |
| 5-min sensor (every 5th reading) | ±1 in 5 (8) min; 4.2 band changes/h; same parameters | delta 5 min 5 (7) at 5.2 / h; AAPS short 6 (9), 34 missed; Loop 7 (9); Holt 6 (8); Kalman q 0.01 7 (10) |
| Hysteresis 0 / 0.1 / 0.2 / 0.3 | 8.1 / 7.3 / 6.5 / 5.9 band changes/h; entering ±1 on average 4.2 / 4.5 / 4.8 / 5.1 min after the truth | — |

**The two episodes** (`--episode`; in words only):

- *The #193 evening, 20:08–20:45* (after holes at 20:02 and 20:19): the readings' hindsight slope turned upward around
  20:28, passed +1 at 20:29 and +2 at 20:33, peaking near 3.4 mg/dL/min. Abbott's rate turned positive only at 20:38,
  reached +1 at 20:44 (15 min late) and never went above 1.0: the arrow never showed more than ↗. τ 3 showed a rise as
  soon as 3 min of readings followed the 20:19 hole, passed +1 at 20:33 and +2 at 20:37, peaking near the truth's 3.4.
- *The rise of 2026-11-14 afternoon* (14:30–15:10): the hindsight slope was positive from about 14:35 and above +1 from
  14:45, peaking near 1.8. Abbott's rate turned positive at 14:46 and peaked at 0.8: → for the whole rise. τ 3 was
  positive at 14:39, passed +1 at 14:48 and peaked at 1.8.

## 5. Recommendation

**The method.** At every reading, from the readings this device has:

1. Walk back from the newest reading while readings are less than 15 min (5τ) older than it, stopping at a LO / HI or
   at a step longer than 12.5 min between two readings (2.5 readings of a 5-min sensor; a 1-min sensor's shorter holes
   are bridged: the slope across them is still a slope).
2. Need at least two readings spanning at least 3 min, else no trend from the readings.
3. Weight each reading `w = exp(−(t_newest − t) / τ)`, τ = 180 s; time in seconds from the newest reading; sums
   taken oldest first; `rate = Σw·(dt − t̄)·(v − v̄) × 60 / Σw·(dt − t̄)²` mg/dL/min (t̄, v̄ the weighted means).

Why this one over its equals: it is a **pure function of the readings** (Holt and Kalman carry state: a restart, a
backfilled or late reading, a reordered sync or a follower joining later would each get a different arrow; their
"after a gap" is a reset with its own warm-up), it is **defined in time, not in readings** (AAPS' deltas need a 5-min
grid and interpolation; the same τ is right for 1-min and 5-min sources, §4), it is **exact on a straight line** and
lags a steady acceleration by about 4.5 min instead of plain 15-min least squares' 7, and it is the same arithmetic as
the #193 `Trend` slope with weights added, so both platforms already sum it the same way.

**Presets.** The line from quick to smooth is continuous (§4) and the right point is personal (someone who doses from
the arrow wants it quick; someone who glances wants it calm). One setting, four choices: **Quick** (τ 2 min: ±1 in 3
min, 10.2 band changes/h), **Balanced** (τ 3, the default: 4 min, 6.5 / h), **Smooth** (τ 5: 6 min, 2.8 / h, starts
missing short moves), **From the sensor** (the sensor's own rate, the owner's existing decision). These replace the
plan's least-squares windows "Quick 4 / Balanced 5 / Smooth 10 min" (sweep, hysteresis 0.2): Quick is as calm as an
8-min window (10.2 / h) and reaches ±1 a minute sooner; Balanced is calmer than the 10-min window (6.5 vs 8.0 / h) and
turns a minute sooner (crossing 0: 4 vs 5 min); a 4- or 5-min window changes band 15–16 times an hour.

**Text bands** (text arrows, spoken words, Nightscout direction, the xDrip+ broadcast's own rule aside): Nightscout's
±1 / 2 / 3 with **hysteresis 0.2 mg/dL/min**: a band is left only once the rate is 0.2 past its edge (→ to ↗ at 1.2,
back to → below 0.8). The band remembered is per device and starts fresh with no previous band.

**Fallbacks**: the readings' trend → the newest reading's own rate from the sensor (when it has one) → no trend (the
"no trend" ring). LO / HI as the newest reading: no trend from the readings; the readings before a LO / HI never count.
The freshness rules of the card stay as they are (a stale value already hides or marks its arrow).

**Cost**: at most 15 readings (1-min) or 3 (5-min): 15 `exp` and about 60 multiply-adds per reading, 13 µs in
CPython, well under a microsecond compiled; memory: the last 15 min of readings (the alert `Trend` keeps 5 min today).

**Shared test vectors**: `vectors.py` writes the proposal, synthetic readings only: flat, straight rises and falls at
1 and 5 min, uneven timing, alternating noise, an acceleration (shows the 4.5-min lag), a turn, too few readings,
exactly 3 min, a 5-min sensor's second reading, an 8-min hole bridged, a 13-min gap, a newest LO, readings after a HI,
an old rise outside the window, duplicates and disorder, a compression-like dip; then a sequence of rates with the
bands they must show under hysteresis, and the drawn angles. Rates compare within 1e-9 (`exp` may differ in the last
bit between platforms).

## 6. The drawn arrow

- **Mapping: linear, `deg = clamp(rate / 3, −1, 1) × 90°`** (the PoC; y-down: up is negative), doubled from |rate| ≥ 3
  with the same hysteresis as the bands. With the bands at ±1 / 2 / 3, the drawn arrow is always within 30° of the
  text arrow beside it: → covers −30…30°, ↗ 30…60° (its symbol is 45°), ↑ 60…90°. Juggluco's `atan(0.75 × rate)`
  never reaches vertical (66° at 3, so "rising fast" at 2 is drawn 56°, 34° away from ↑); GlucoDataHandler's
  ±2 → vertical with a ±14° dead zone and 5° steps is a quantised version of the same idea.
- **No smoothing of the angle.** τ 3 already moves the arrow a median 4° per 1-min reading (p90 15°, p99 32°; the
  hindsight slope itself 4.4°): any further filter is extra delay on the one thing the change is for. Animate each
  change with a short spring without overshoot (Material motion / SwiftUI spring, ~300 ms); reduced motion: jump.
  A dead zone around flat is not needed (a 3° tilt at 0.1 mg/dL/min is the truth, and small).
- Places that can't rotate (system icons, widgets) keep pre-rendered steps; the PoC's 5° steps are fine-grained enough.

## 7. Consequences

- **The arrow will say ↑ / ↓ and ⇈ / ⇊ far more often.** The owner's readings move at ≥ 2 mg/dL/min about 17 times a
  day even by the ±10-min hindsight slope (median 6 min each); Abbott's rate says so about twice a day, τ 3 about 25
  times (14 if held 5 readings), τ 5 about 11. ≥ 3 (doubled arrow): Abbott ~0.6 a day, τ 3 ~12. That is the readings
  being honest, but it is a visible change; Smooth exists for those who prefer calm.
- **The rate alerts can't simply switch to this trend.** `falling_fast` / `rising_fast` (off by default, 2.0
  mg/dL/min) would go from about 2 episodes a day to about 25 on these readings. Before they read the one trend they
  need their own rule (for example the rate held for 10 min: ~3.5 a day, close to the hindsight slope's 3.5) and new
  timeline scenarios, with the owner's OK (CLAUDE.md: never change a scenario's expectations without it).
- **The #193 quiets keep their own rule.** Their 5-min least squares is the fastest smooth-enough slope (±1 in 2 min)
  and their extra guards (no step against the direction over 1 mg/dL, still moving, at least 0.5 mg/dL/min) answer a
  different question: "is it clearly still moving?" A weighted slope would notice a stall 1–2 min later, the wrong
  direction for a rule that holds an alert. Nothing here is clearly better for them. Their noise guard is conservative
  with the Libre 3's 5–10 mg/dL steps (the step voids the trend and the alert fires as without the quiet): the safe
  side.
- **Compression lows**: the arrow follows the readings' fall and rebound like the value does, within 3–4 min; docs/33
  stands (nothing at the alarm says "pressure").
- **Followers** compute from the readings they have: a 5-min Nightscout follower gets the 5-min numbers of §4 with the
  same parameters; the wire keeps the sensor's rate; LibreView / Tidepool / CSV keep the sensor's rate (owner's
  decision).

## Open questions for the owner

1. Presets: Quick / Balanced / Smooth / From the sensor as one setting, or only Balanced + From the sensor?
2. The doubled arrow ~12 times a day on your readings (§7): acceptable as honest, or double only from a higher rate
   (e.g. 3.5, xDrip+'s threshold) or with the rate held for a few readings?
3. The rate alerts (off for you today): hold them on the sensor's rate until their own rule and scenarios exist, or
   design that rule now?
4. Hysteresis 0.2 for the text bands (the drawn arrow stays continuous): fine?

## In Sukrara

This investigation changes no app code, no setting, no alert rule and no contract. The build (#204, after #195) is the
plan below.

| Brought in by the build | Why |
|---|---|
| One trend per platform (Android `:core` / Mac `SukraraCore`): the weighted least-squares slope of §5, τ from the setting, with the fallback readings → sensor rate → none; every arrow, word, output and (once their rule exists) the rate alerts read it; a repo check forbids reading the raw rate elsewhere | the owner's "one source of truth"; §4: 4 min to ±1 instead of 7 (17) and no missed turns |
| The setting *Trend*: Quick / Balanced (default) / Smooth / From the sensor, in `contracts/settings/settings.json`, mirrored to followers; its place decided with the settings organisation work | §5 presets; the owner's decision on the sensor choice and mirroring |
| Text bands with hysteresis 0.2 (text arrows, spoken words, Nightscout direction) | §4: −20 % band changes for +0.7 min |
| The drawn arrow continuous: linear, vertical at ±3, doubled from 3 with hysteresis, a short spring between readings; `contracts/graph/trend/trend.json` gets the continuous `deg` and the hysteresis cases (its snapped degrees and the `TrendAngle` "snaps" doc go) | §6; the owner's "not snapped" decision |
| `contracts/trend/` test vectors written by a `tools/` script grown from `vectors.py`, run by both apps' tests | the same number on both platforms, pinned like the alert scenarios |
| docs/FEATURES.md *Trend* section with its states and exits (each preset, from the sensor, no trend, fallback) | complete flows |

| Left out | Why |
|---|---|
| Kalman / Holt filters | the same numbers as the weighted slope (§4) with state: restarts, backfill and followers would disagree |
| Savitzky–Golay end-point and other local-quadratic slopes | the noisiest at every delay; overshoot |
| Two-point deltas (xDrip+, Nightscout) and AAPS' deltas | 2× the band changes, or a 5-min grid; oref0 unbucketed returns 0 on 1-min data |
| Theil–Sen | no gain: the Libre 3's jumps are steps, not outliers |
| A per-person tuned filter (Facchinetti) | needs per-sensor noise estimation; the gain over a fixed τ is unproven here and the presets cover the preference |
| Smoothing or a dead zone for the drawn angle | extra delay for a 4° median change per reading |
| Changing the #193 quiets | §7: their rule is faster where it matters and errs to alerting |
| Switching the rate alerts now | §7: ~10× more alerts on the same threshold; needs its own rule, scenarios and the owner's OK |
| Using this trend for LibreView / Tidepool / CSV or on the wire | the owner's decision: those keep the sensor's rate |
| Real readings, per-reading outputs or plots in the repo | health data stays on the owner's machine; the prototype reads a path and prints aggregates |
