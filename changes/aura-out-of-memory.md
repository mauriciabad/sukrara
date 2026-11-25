---
apps: [aura]
kind: fix
---
- **Sukrara Aura stays on the watch**: it could run out of memory, and the watch then put its own face back (a few
  times a day on a Descent Mk3 43, more with weather, the second time zone or the sensor rail shown). It now leaves
  about a tenth of a watch face's memory free, and a test keeps it so ([#217](https://github.com/mauriciabad/sukrara/issues/217)).
