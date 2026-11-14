# Investigation 34: the trend from the readings

Standalone prototype for [docs/34](../../docs/34-trend.md) (issue #204). Python 3.9 standard library only; nothing here
is part of Sukrara. **No health data is in this folder**: the evaluation reads a readings file you point it at and
prints aggregate numbers only (nothing per reading). Never commit a readings file, its output per reading, or plots of
it.

```
methods.py        the candidates, each causal and streaming (one call per reading): two-point delta, AndroidAPS
                  delta / shortAvgDelta, least squares, recency-weighted least squares, local quadratic
                  (Savitzky–Golay end point), Theil–Sen, Holt, constant-velocity Kalman, the sensor's own rate;
                  `reference()` = the recommendation as the pure function the apps would run
evaluate.py       the tables in docs/34, on a readings file: error and delay against a hindsight slope, delays at
                  turning points, band changes per hour (with hysteresis), angle jitter; --every 5 (a 5-min
                  sensor), --noise, --gaps, --hysteresis, --sweep, --episode
vectors.py        the proposed shared test vectors (synthetic readings → expected rate, bands with hysteresis, angles)
test_methods.py   the estimators on hand-made readings
```

```sh
F="$HOME/Library/Application Support/app.mauri.sukrara/readings.csv"   # t_ms, mgdl, rate: the Mac writes it
python3 -I investigations/204-trend/test_methods.py
python3 -I investigations/204-trend/evaluate.py "$F"                   # the main table, ~5 s
python3 -I investigations/204-trend/evaluate.py "$F" --sweep           # every parameter set, ~15 s
python3 -I investigations/204-trend/evaluate.py "$F" --every 5         # as a 5-min sensor would deliver them
python3 -I investigations/204-trend/evaluate.py "$F" --noise 2         # extra reading noise (sd 2 mg/dL)
python3 -I investigations/204-trend/evaluate.py "$F" --gaps 40         # 40 random 3-20 min holes
python3 -I investigations/204-trend/evaluate.py "$F" --hysteresis      # text-band changes per hour, h = 0 … 0.3
python3 -I investigations/204-trend/evaluate.py "$F" --truth 10        # a smoother hindsight "truth" (default ±7 min)
python3 -I investigations/204-trend/evaluate.py "$F" --episode "2026-11-13 20:08" "2026-11-13 20:45" \
                                                         --episode "2026-11-14 14:30" "2026-11-14 15:10"
python3 -I investigations/204-trend/vectors.py > /tmp/trend-vectors.json
```

`--only "RECOMMENDED,Abbott"` keeps the candidates whose names contain those words. The "truth" is a centred
(non-causal) local quadratic of the sensor's own readings: it says how well each method recovers the slope of what the
person sees, a few minutes late at best; it is not blood glucose. Another person's sensor, another sensor model or a
different week can move the numbers; the ranking held for every truth width and noise level tried (docs/34 §4).
