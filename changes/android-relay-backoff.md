---
apps: [android]
kind: fix
---
- **Fewer reconnects to a relay that keeps closing**
  ([#263](https://github.com/mauriciabad/sukrara/issues/263)): a relay that answers and then closes every connection
  is retried less and less often, not every 2 s (less traffic and battery).
