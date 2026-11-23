---
apps: [mac]
kind: fix
---
- **An entry added while Nightscout or LibreLinkUp syncs is kept**: a treatment added on the Mac at the moment a round
  saved what it had read could be dropped; every change now reads and saves the source's log in one step.
