# Change files

Every change a person would notice adds one file here, `changes/<slug>.md`, in the same commit (docs/48 › Change
files; check `changes` notes an app's change without one):

```markdown
---
apps: [android, mac]
kind: fix
---
- **Extremely high stays quiet after insulin**: rapid insulin you log now quiets the extremely high alert too, …
```

- `apps`: the ids of [contracts/apps/apps.json](../contracts/apps/apps.json) whose users notice it (`android`, `mac`,
  `aura`, `garmin-app`, `garmin-field`, `face-sdk`).
- `kind`: `fix` (the release bumps the patch: 0.17.1 → 0.17.2) or `feature` (the minor: 0.17.1 → 0.18.0).
- The text: the bullet as it reads in each app's CHANGELOG, links from the repository's root. Text that differs per
  app goes in separate files.

`tools/release/release.py` lists what is waiting; `release.py android mac` releases those apps: each named file's
bullet goes into the app's CHANGELOG entry, the highest `kind` sets its version, and the app leaves the file's `apps`
(the file goes once it names none). An internal change (refactor, tests, tooling) has no file: say so with a
`Changes-skip: <reason>` trailer, and it ships with the app's next real change.
