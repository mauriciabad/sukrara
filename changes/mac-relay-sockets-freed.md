---
apps: [mac]
kind: fix
---
- **Following no longer fills the memory** ([#263](https://github.com/mauriciabad/sukrara/issues/263)): a relay that
  answered and then closed every connection was reconnected every 2 s, and each connection stayed in memory (4.7 GB
  after a day and a half). Closed connections are now freed, and such a relay is retried less and less often.
