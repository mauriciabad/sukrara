<!-- Sukrara — https://github.com/mauriciabad/sukrara -->
<!-- Copyright (C) 2026 Maurici Abad -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# THE trend

Where glucose is heading, one rule for every app (#204; the math and its numbers: [docs/34](../../docs/34-trend.md)).
One module per platform works it out (Android `:core:model` `TrendRate`, Mac `SukraraCore` `TrendRate`), at **one
place** per platform (Android: the readings repository's reads, `TrendReadings`; Mac: `AppModel`), for every reading at
its own time from the readings at or before it. Every arrow, spoken word, alert text, output and the fast alerts read
that; the sensor's own rate (`sensorRate`) is only stored, sent on the wire and written to LibreView / Tidepool / the
CSV (device records), and is the fallback.

## The rate

The setting *Trend arrow* (`glucose.trend`, mirrored from the wearer as `graph.trend`):

| Choice | What | ±1 mg/dL/min reached (owner's readings) | Band changes / h |
|---|---|---|---|
| `quick` | from the readings, τ = 2 min | 3 min after the hindsight slope | 10.2 |
| `balanced` (default) | from the readings, τ = 3 min | 4 min | 6.5 |
| `smooth` | from the readings, τ = 5 min | 6 min | 2.8 |
| `sensor` | the sensor's own rate | 7 min (p90 17), half the turns missed | 1.0 |

From the readings, at each reading: walk back while readings are less than 5τ older, stopping at a LO / HI or at a
step over 12.5 min between two readings; at least 2 readings spanning at least 3 min; a least-squares slope with
weights `exp(−age / τ)` (time in s from the newest reading, sums oldest first, `Σw·(dt − t̄)·(v − v̄) × 60 /
Σw·(dt − t̄)²`). **Fallback**: no rate from the readings → the reading's own sensor rate → no trend (the ring). A LO /
HI has no trend. 5-min sources use the same numbers.

## The arrow

- **Text band** (text arrows, spoken words, Nightscout direction): Nightscout's ±1 / 2 / 3 (a boundary belongs to the
  steeper band) with **hysteresis 0.2**: a band is left only once the rate is 0.2 past its edge (→ to ↗ at 1.2, back
  below 0.8). The band before is remembered while the next reading follows within 12.5 min with a trend.
- **Doubled** (band ±3, ⇈ / ⇊, "rising very fast"): entered only when the rate was at least 3 that way at **every
  reading of the last 2 min** (back to the first reading at or before 2 min earlier: 3 readings of a 1-min sensor, 2
  of a 5-min one), left with the 0.2. On the owner's readings (Balanced) this takes a third of the doubled arrows away (11.4 →
  7.5 a day, half of those with no fast move behind them) and misses no more of the real ones (1 of 20) for 2 min
  more delay; a third minute buys little (6.7 a day for another minute at p90). `investigations/204-trend/holds.py`.
- **Drawn angle**: continuous, `deg = −clamp(rate / 3, −1, 1) × 90°` (y-down: negative = up), doubled with the band,
  animated with a ~300 ms spring between readings (none with reduced motion). Places that can't rotate draw the band.
- **Fast alerts** (`held`): the rate past the threshold at every reading of the last 5 min, the one nearest zero
  (contracts/alerts/SPEC.md §2 *Fast alerts read the trend*); From the sensor: the sensor's rate as it is.

## Files (written by `python3 tools/trend/cases.py`)

| File | Cases | Run by |
|---|---|---|
| `rate.json` | readings + sensor rates + the setting → at every reading: rate, from, band, held | Android `TrendRateTest`, Mac `TrendRateTests` |
| `bands.json` | the arrow per band (text, doubled, words en / es, Nightscout), plain bands, angles, band sequences | Android `TrendRateTest` + `TrendFixtureTest`, Mac `TrendRateTests` + `TrendFixtureTests` |
| `clear.json` | the #193 quiets' clear-move rule (SPEC §2), unchanged | Android `TrendRateTest`, Mac `TrendRateTests` |

Rates compare within 1e-9 (`exp` may differ in the last bit). The cross-surface tests (Android `TrendEverywhereTest`,
Mac `TrendEverywhereTests`) feed readings through the real annotate point and check every place shows the same arrow.
