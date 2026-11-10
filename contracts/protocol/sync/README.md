<!-- Sukrara — https://github.com/mauriciabad/sukrara -->
<!-- Copyright (C) 2026 Maurici Abad -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Protocol sync fixtures

Owner → follower event sequences for Nostr protocol v2 ([PROTOCOL-v2.md](../PROTOCOL-v2.md)), with the verdict each
event must get and the follower state the sequence must leave. Every app replays them: Android
`apps/android/core/protocol` `SyncFixturesTest` / `IdentityConflictTest`, Mac `SyncFixtureTests`. Keys are throwaway
test keys (the OGS draft-1 vector keys plus `D2` = sha256("sukrara/sync/D2")); all glucose data is synthetic.

| file | what it pins |
|---|---|
| `join-readings.json` | key delivery → roster with the wearer's profile → hour chunk → live (reading + sensor), a 1-minute jump from 80 to 300 accepted unchanged (followers make no plausibility check) → reading-less sensor update → outsider rejected → a roster without a profile (the wearer's is kept) |
| `sensor-life.json` | the wearer's sensor without readings: ended early, removed (`{"v":2}`), a new one warming up (reading-less live events), its first reading, a late backlog copy of an old reading that can't bring the old sensor back, a live event from a clock more than 5 min ahead rejected whole (its `ss` / `se` / `sw` don't change the sensor) |
| `treatments-writer.json` | writer scope: wearer entry, writer's own entry and edit, writer hijack rejected, wearer tombstone, older `(u,o)` ignored |
| `settings-mirror.json` | mirrored settings: newest `u` wins, only from a roster device; the payload holds every mirrored setting (graph + alerts) |
| `rotation.json` | phone change / revoke: epoch 2 with a new device, old device and old epoch rejected, late epoch-1 roster ignored. Also the identity-restore case (same wire sequence: new device key, new epoch delivered to every follower, roster with only the new device); Mac `ProtocolCompatTests` replays it through the runtime |
| `revoke-reapprove.json` | the wearer removes the follower, then re-approves the same follower key; across app restarts the relays replay the old revoked notice and both key deliveries: the older notice is ignored, a newer one removes it again. Also: no rekey for an old backfill under an unknown epoch, a rekey with `have` = newest held gk (throttled), a writer's entry rejected before the roster lists it and accepted after |
| `owner-conflict.json` | A3, owner side: the wearer's own echoes, its own rotation and late old events are `fine`; a newer roster or backup from another app publishing as the same identity is a `conflict`; the epoch the wearer's retry rotates to (above the other app's) |
| `conflict-rotation.json` | the follower side of that retry: the old phone rotates to epoch 3 naming itself (the new phone's readings rejected), the new phone's retry rotates to epoch 4 (accepted again), the old phone's epoch-3 events rejected / ignored |
| `replace-identity.json` | the wearer replaces its identity (PROTOCOL-v2 "Replace the identity"). `steps`: a replace cancelled (the revoked notice, then the same key one second newer: following again), then a real one (the notice: removed; the old phone's late reading ignored); after a restart the relays replay it all and the follower stays removed. `replace` (the wearer's side, Android `ReplaceIdentityTest`): who is told and when the identity is replaced for each way the relays answer |

## Format (`v` 1)

The fixtures carry the real signed, encrypted events (not decrypted payloads), so every acceptance rule — signature,
roster membership, epoch, decryption, tags — runs on every platform exactly as it does on relays and Bluetooth.

Follower sequences: `keys` (`{name: {sec, pub}}`), `sid`, `follower` (key name), `identity` (I's pub), `now` (unix s,
the clock for every step), `events` (`{step: nostr event}`), `steps` (`[{event, expect, rekey?}]`, `expect` =
`accepted|rejected|ignored:<what>`, the reference follower's codes; `rekey` = the `have` gk of the rekey request that
step must send, none when absent; a step `{"restart": true}` relaunches the follower: persisted state kept, seen event
ids and rekey throttles forgotten), `expected`:

- `status` (`new|requested|active|declined|inviteused|revoked`), `epochs` (sorted), `devices`, `writers`;
- `wearer`: the wearer's profile `{name, avatar}` from the newest accepted roster that carries one (a roster without a
  profile keeps the last one), `null` when none did;
- `readings`: `[t, sgv, rate|null]` sorted by `t`, first reading per `t` kept;
- `sensor`: `{ss, se, sw}` from the newest live event (`null` when none or cleared);
- `treatments`: `[{id, k, a, u, o, del}]` sorted by `id`, merged by `(u, o)` (a wearer entry overrides a forged writer copy);
- `settings`: `{u, graph, alerts}` of the newest accepted settings (`settings-mirror.json` carries every `"mirror": true`
  setting of contracts/settings, checked by Android `SyncFixturesTest`), or `null`; `alerts`: count accepted.

`owner-conflict.json`: `marks` (what the wearer's phone persists: its identity, its device key, the epochs it holds,
`lastRosterAt` / `lastBackupAt` = `created_at` of the last roster / backup it published), `events`, `checks`
(`[{event, expect}]`, `expect` = `fine` | `conflict:<RosterFromOtherDevice|RosterUnknownEpoch|BackupFromElsewhere>`),
`retries` (`[{found, unknownEpoch, rotateTo}]`: the events the relays return to the retry's fetch, whether a roster under
an unknown epoch was seen, and the epoch the retry rotates to, `null` = republish roster + backup only).

`replace-identity.json` `replace`: `followers` (`[{f, removed?}]`, key names: the wearer's followers before the
replace, a removed one gets nothing), `timeoutS` (how long the wearer waits for the relays), `runs`
(`[{name, internet, acks, cancelAt, replacedAt, resent, keyAgain}]`): `internet` = internet sharing on; `acks` =
`{follower: s}`, when after the notices went out a relay accepted that follower's (`OK true`); `cancelAt` = when the
wearer cancels (s, `null` = never); `replacedAt` = when the new identity's first event goes out (s after the notices,
`null` = kept); `resent` = followers whose notice goes out again with the new identity (no relay took it); `keyAgain`
= followers that get the key again, newer than their notice (cancelled).

## Changing them

Generated by the Android tests with `-Psukrara.regen=true` (events are re-encrypted with fresh NIP-44 nonces; the
`steps` and `expected` must not change unless the spec changed — then the Mac lands in the same change). Every follower
sequence is cross-checked against the independent Python reference (`poc/spec/reference`, its own secp256k1 / NIP-44
code) by `python3 tools/protocol/sync_crosscheck.py`, in CI: the same verdict at every step, the same rekey `have` and
the same final state. The reference is frozen with the PoC (OGS draft 1), so the PROTOCOL-v2 rules it predates are
added in the tool, one method each: a newer epoch's key outranks every older-epoch roster (`rotation`), rekey `have` =
the newest held gk and only for events newer than the latest key, revoked notice vs key delivery ordered by the
rumor's `created_at` and a restart (`revoke-reapprove`), an author not yet in the roster judged again, the settings
rule (`settings-mirror`), readings in the future (a live event rejected whole), a roster without a profile keeping the
last one (`join-readings`).
