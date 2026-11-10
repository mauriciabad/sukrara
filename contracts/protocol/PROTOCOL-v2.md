# CGM-over-Nostr protocol — draft v2 (investigation 06)

Changes vs. [v1](PROTOCOL.md): an **identity key** separate from the publishing **device key**, a **stream id** for
tags, **epoch-versioned read keys** delivered privately per follower (no secret ever in a QR/link), **invite →
request → approve** sharing, **revocation by rotation**, and an **owner backup** so a new phone restores everything
from one password-manager entry. Design notes and sources: [docs/06](../../../docs/06-sharing-and-keys.md).
Modelled on the open NIP-DA proposal (nostr-protocol/nips#2411, scoped data grants).

## Keys

| Key | Held by | Purpose |
|---|---|---|
| **Identity `I`** | owner app (Keystore-encrypted) + **password manager** backup | the stream's identity followers trust; signs the roster, key deliveries, owner backup. Rarely used. |
| **Device `D`** | one phone (Keystore-encrypted), never backed up | signs live events + chunks. Replaced on phone change. |
| **Read key `G_n`** (epoch `n` ≥ 1) | owner + approved followers | NIP-44 v2 target of all data: `nip44(D.sec → G_n.pub)`. New epoch on every revocation / phone change. |
| **Stream id `sid`** | owner + approved followers | 32 random bytes, never rotates; only used to derive opaque tags |
| **Follower `F`** | each follower app | identifies a follower; receives key deliveries |

`gk_n = hex(sha256(G_n.pub))[:8]` names an epoch in tags (reveals *that* a rotation happened, nothing else).
All tags/ids below: `H(x) = hex(sha256(x))[:32]`.

## Events

All content is JSON, NIP-44 v2 encrypted. Every event has a NIP-40 `expiration`.

| Event | kind | signed by | tags | content (decrypted) |
|---|---|---|---|---|
| **live** | 21078 | `D` | `["gk", gk_n]`, exp now+1 h | `{"v":2,"t":unix_s,"sgv":mgdl,"r":rate|null,"dev":src,"ss"?,"se"?,"sw"?}` → `G_n`; or reading-less `{"v":2,"ss","se","sw"}` ([sensor life](#sensor-life)) |
| **hour chunk** | 30078 | `D` | `["d", H("cgm2:h:"+sid+":"+hourStart)]`, `["c", H("cgm2:glucose:"+sid)]`, `["gk", gk_n]`, exp hour+7 d | `{"v":2,"h":hourStart,"dev":src,"r":[[dt,sgv,rate100],…]}` → `G_n` |
| **settings** | 30078 | `D` | `["d", H("cgm2:settings:"+sid)]`, `["gk", gk_n]`, exp now+30 d | `{"v":2,"type":"settings","u","graph":{…},"alerts":{…}}` → `G_n` ([Settings](#settings--v2-addition-2026-10-21-round-f28)) |
| **roster** | 30078 | `I` | `["d", H("cgm2:roster:"+sid)]`, `["gk", gk_n]`, exp now+30 d (refreshed weekly) | `{"v":2,"epoch":n,"devices":[hex D…],"writers":[hex W…],"relays":[…],"profile":{…}}` → `G_n` (`profile`: the wearer's, see [Profiles](#profiles)) |
| **owner backup** | 30078 | `I` | `["d", H("cgm2:backup:"+I.pub_hex)]`, exp now+90 d (refreshed weekly) | `{"v":2,"sid","epochs":[{"n","g":nsec}],"followers":[{"f":hex,"name","avatar","since","revoked"?}],"invites":[{"t","x"}],"relays":[…],"profile":{…}}` → **`I` itself** |
| **invite** | rumor kind 30502 in gift wrap to `invite_key(secret)` | seal by `I` | wrap exp = invite expiry | `{"v":3,"type":"invite","x":expiry,"r":[relays],"profile":{…}}` ([PROTOCOL-identity](PROTOCOL-identity.md#short-invites-v3)) |
| **access request** | rumor kind 30500 in NIP-59 gift wrap (1059) to `I` | seal by `F` | — | `{"v":2,"type":"request","token":t,"profile":{"name":"Mom","avatar":{…}}}`; a [nearby](#nearby-pairing) request adds `"n":na` |
| **profile update** | rumor kind 30500 in gift wrap to `I` | seal by `F` | — | `{"v":2,"type":"profile","profile":{…}}` (applied only for an approved, non-revoked `F`) |
| **key delivery** | rumor kind 30501 in gift wrap to `F` | seal by `I` | — | `{"v":2,"type":"key","sid","epoch":n,"g":nsec(G_n),"devices":[…],"relays":[…]}` |
| **rekey request** | rumor kind 30500 in gift wrap to `I` | seal by `F` | — | `{"v":2,"type":"rekey","have":gk}` (follower saw an unknown `gk`; `have` = the `gk` of the newest epoch it holds, never the unknown one; owners only log it) |
| **history request** | rumor kind 30500 in gift wrap to `I` | seal by `F` | — | `{"v":2,"type":"history","since":hourStart}` (follower's backfill found hours missing) |
| **revoked notice** | rumor kind 30501 in gift wrap to `F` | seal by `I` | — | `{"v":2,"type":"revoked"}` (courtesy; security comes from rotation) |
| **invite used** | rumor kind 30501 in gift wrap to `F` | seal by `I` | exp +7 d | `{"v":2,"type":"invite-used"}`: another follower's request had already claimed this invite |
| **declined** | rumor kind 30501 in gift wrap to `F` | seal by `I` | exp +7 d | `{"v":2,"type":"declined"}`: the owner denied the request (no key follows) |
| **pairing nonce** | rumor kind 30501 in gift wrap to `F` | seal by `I` | exp +7 d | `{"v":2,"type":"pair","n":nb}`: opens the [nearby pairing](#nearby-pairing) commitment to the follower whose request claimed it |

Gift wraps (kind 1059) are signed by a random one-time key and `p`-tag the recipient; the **seal's signer** is the
authenticated sender. Their NIP-40 `expiration` is **absolute** (now + TTL): NIP-59 backdates the wrap's `created_at`
by up to 2 days, so an expiration computed from it makes anything shorter than 2 days (a 24 h invite, a 1 d history
request) arrive already expired ("invalid: event expired", found 2026-10-17). Followers accept a key delivery **only if the seal is signed by `I`**; the owner accepts a
request only with a valid, unexpired invite token (and the owner still confirms it in the UI).

TTLs (Android and Mac, both from now): access request, profile update, rekey request, invite used and declined **7 d**;
history request **1 d**; key delivery and revoked **30 d**; the invite wrap until the invite expires. A key delivery
whose `sid` differs from the one the follower already has is rejected: a stream's `sid` never changes.

### Relay rules (learned while testing, 2026-10-16)

- **NIP-42 AUTH**: some relays only serve kind 1059 `#p` subscriptions to the recipient. Answer
  `["AUTH", challenge]` with a kind 22242 event signed by the key the wraps are addressed to (owner → `I`,
  follower → `F`, invite lookup → the invite key) and re-issue the REQs.
- **Default relays** (Sukrara 0.11): nos.lol, relay.primal.net, nostr.mom, relay.snort.social. Each must take events
  from brand-new keys (every gift wrap is signed by one) and serve wraps back to their recipient
  ([relay_probe.py](identity/relay_probe.py), [relay_burst.py](identity/relay_burst.py),
  [results](results/relay-probe-2026-10-17.txt)). Not usable: relay.damus.io and relay.nostr.net (accept wraps,
  serve them to nobody: AUTH "needs serviceUrl"); offchain.pub and nostr.bitcoiner.social (refuse a new key after
  8–10 events, "not in our web of trust"); nostr.oxtr.dev (rate-limits a 25-chunk start); nostr-pub.wellorder.net
  ("spam" after ~47 events).
- **Addressable events need strictly increasing `created_at`** per (author, kind, `d`) — two versions in the same
  second are rejected ("replaced: have newer event"), which can leave a stale owner backup.
- Gift wraps' outer `created_at` is randomised up to 2 days into the past (NIP-59): subscribe with `since` ≥ 3 days.
- **What a relay sends is not trusted**: an `EVENT` for a subscription that isn't open, or that doesn't match that
  subscription's filter, is dropped before its signature is checked. A relay that sends more than 2000 events at once
  or more than 20 per second after that (a token bucket per connection) loses its socket and reconnects with a growing
  backoff. Event ids remembered as seen are capped at 5000 per follower (the least recently seen dropped first).
- **Relay lists** received in a roster, key delivery, owner backup, invite or nearby pairing are validated: at most 8
  (the first 8 valid ones, duplicates removed), each a `wss://` URL of at most 200 printable ASCII characters with no
  user info, query or fragment, an optional port 1–65535 and a public host name: at least two labels of `a-z 0-9 -`,
  not `localhost`, `.local`, `.lan`, `.home`, `.internal`, `.localdomain` or `.home.arpa`, no IPv6 literal and no
  private, loopback, link-local, shared (100.64/10), multicast or reserved IPv4 address. Invalid entries are skipped
  ([relays/relay-lists.json](relays/relay-lists.json)). A debug build's local test relay is configured on the device, never
  taken from a relay list.

## Treatments (insulin / carbs / notes) — v2 addition, 2026-10-16

Event format = [libreview SPEC §5](../libreview/SPEC.md); this section fixes who publishes what and how followers
and writers use it. Same bytes over relays and over BLE (`["EVENT", ev]`, [docs/07](../../../docs/07-bluetooth-followers.md)).

| Event | kind | signed by | tags | content (decrypted) |
|---|---|---|---|---|
| **treatment** | 30078 | `D` (owner's merged log) or a writer `W` (its own entries) | `["d", H("cgm2:tr:"+sid+":"+id)]`, `["c", H("cgm2:treatments:"+sid)]`, `["gk", gk_n]`, exp `created_at`+30 d | `{"v":1,"id","k","a","t","n","u","del","o"}` + `"pr","pen","pd"` on pen entries (SPEC §1 without `lv`) → `G_n`, NIP-44 from the signer |

- Pen entries (investigation 29) carry `pr` (primed units), `pen` (serial) and `pd` (the doses) as in SPEC §1; `a` is
  the injected units and the only amount any receiver draws, mutes with or exports. Every publisher and receiver keeps
  the three keys as received (merge, storage, edits, the owner's echo and republish); a writer's edit of its own entry
  never adds them. Pen entries are always the wearer's (`o` = `phone`): when a pen injection matches a writer's entry
  (`o` = `follower`), the owner tombstones that entry and publishes a new pen entry with its `n` (SPEC §1), so a
  writer never holds a pen entry it could edit. Public spec: [ogs-data D§6](../../spec/ogs-data.md#d6-treatments).
- `created_at` = publish time, **strictly increasing per (author, `d`)** (relays drop a same-second replacement).
  `u` (ms, inside the content) is the merge version; `created_at` is not.
- `o` is kept as created: `phone`, `mac` (legacy-link entries) or `follower` (writer entries; **every writer sends
  `follower`**, the Mac included). Deletes are the same event with `"del":true` (tombstone, never dropped from the log).
- **Writer scope** (investigation 24, P4): a writer may create entries and change or delete **only the entries it
  created**; the wearer may change any entry. Every receiver remembers the *creator* of each `id`: the key that signed
  the first version it accepted (any roster device counts as the wearer).

**Owner** (the wearer's phone) publishes its **whole merged log** signed by its device key `D` (the key in
`roster.devices`, the one that signs live/chunks): its own entries, the Mac's (legacy link), accepted writer
entries, deletes.
- On every change of the log: that entry, at once (CONTROL lane, paced ≥ 2 s per relay, replaced by `d` in the queue).
- **Republish** every entry with `t` ≥ now − 30 d (tombstones too), paced: on an **epoch rotation** (encrypted to the
  new `G`), when a **new follower is approved**, and at least **weekly** (keeps the 30-day expiry rolling).
- BLE identity (2026-10-21, spec/ogs-ble.md B§5/B§6): a follower that knows `sid` signs every `HELLO` with its
  follower key (`id` = BIP-340 signature of a kind-21080 event with content `H("cgm2:ble-id:"+sid+":"+nonce+":"+dev)`,
  `dev` = `phone` | `mac`, created_at 0, never sent); the wearer's phone finds which roster follower and device type
  signed it and names the link (Settings → Share with people → Nearby devices). Unnamed links aren't listed. No old
  `HELLO` without `id` is supported.
- BLE: every treatment event is pushed to subscribed centrals like any publish; the `HELLO` backlog also carries the
  treatment events of the last 30 days (newest 200), after the chunks and before live.
- Sukrara for Mac follows over v2 like any follower; while it is a writer, its entries go over v2 only (not the
  legacy Mac link), so "Can add entries" governs the Mac too. The legacy link (SPEC §2) refuses entries unless the
  wearer explicitly allows that unauthenticated path on the phone.
- The owner accepts a writer event only if the author ∈ roster `writers` ∧ active follower (docs/07) **and** the `id`
  is new or that writer created it (the owner keeps the first writer per `id`; an `id` in its log with no writer is the
  wearer's own), merges it (§1) into the log, and so re-publishes it signed by `D` (**the echo**; a BLE writer event is
  also forwarded as-is to the other BLE centrals).

**Followers** (Android + Mac) receive treatments:
- `REQ {"kinds":[30078], "#c":[H("cgm2:treatments:"+sid)], "authors": roster.devices ∪ roster.writers,
  "since": now − 30 d}`; re-issued when the roster's devices or writers change. Over BLE they arrive unasked.
- Accept iff: valid signature; author ∈ current `roster.devices ∪ roster.writers`; `gk` names a known epoch
  (unknown `gk` from a device ⇒ rekey request, as for chunks); NIP-44 decrypt with `(G_n.sec, author.pub)`;
  `d` == `H("cgm2:tr:"+sid+":"+id)`; `k` ∈ `rapid|long|carbs|note`; **writer scope**: a writer's version only for a
  new `id` or one that writer created. When the wearer's copy of a writer's entry (`o` = `follower`) came first, the
  writer's own copy is ignored (the echo already holds it), not counted as a rejection. A wearer's own entry (`o` ≠
  `follower`) signed by a device replaces a version a writer created first for that `id`, whatever its `u` (a writer
  that forged the id before the real entry reached this follower).
- Merge with SPEC §1 (newest `(u, o)` wins), persist in the follower's own DB, draw them, serve them from the
  follower's Nightscout API (`/api/v1/treatments`, SPEC §5 mapping).

**Writers** (followers in roster `writers`):
- Show *Add entry* only while the follower's own key `F` is in the newest roster's `writers` (live: a roster without
  it hides the form at once; otherwise "*<wearer> hasn't allowed you to add entries*").
- Per entry the writer sent: *sending* → *sent* (a relay `OK true` or the BLE write response) → **accepted** when an
  event for the same `id` signed by a **roster device key** arrives with `u ≥` the sent `u` (the echo). No echo after
  ~2 min ⇒ shown as *not accepted yet* (keeps listening; the owner may be offline). There is no explicit NACK:
  a rejected entry simply never echoes.

Verified on Android (two emulators, 2026-10-16, `spikes/showcase-android/evidence/n1-*`): wearer entries and deletes
reach the follower over relays and over BLE with the follower offline; writer entry accepted (echo) in < 5 s over
relays, ≈ 0.1 s over BLE, and after 3 min when the wearer's relays were off (*not accepted yet* → *accepted*).

## Bluetooth link

The rules every app follows on the Bluetooth link (they refine the OGS BLE draft's B§4.1 and B§6; the messages,
framing header and limits are in [docs/07](../../docs/07-bluetooth-followers.md)). Pinned by
[ble/link-rules.json](ble/link-rules.json) (Android `BleLinkRulesTest` + `NearbyLinkRulesTest`, Mac
`BleLinkRulesTests`) and [ble/default-mtu.json](ble/default-mtu.json) (a Mac follower ↔ Android wearer at ATT MTU 23,
plain and long write).

- **Frames**: every frame fits one ATT Write Request or notification: at most `min(ATT_MTU − 3, 512)` bytes, the
  6-byte header included. CoreBluetooth's ATT MTU is its `.withoutResponse` write length + 3, never the
  `.withResponse` length (always 512, which makes the stack send a long write).
- **Long writes**: the wearer still accepts a long (Prepare / Execute) write on RX from any central: parts in offset
  order, at most 512 bytes in all, each echoed in the Prepare Write Response, then judged as one frame on Execute Write
  (same limits as a plain write; out of order or over 512 bytes = refused and the central dropped). Each central has
  at most one such queue (≤ 512 bytes); it is dropped by an Execute Write that cancels, by the central disconnecting,
  and **30 s after its last part** without an Execute Write, like a reassembly buffer.
- **Which owner**: a follower writes nothing but `HELLO` until the phone's `INFO` proves it is its wearer's. Once the
  follower knows `sid`, `m` is checked first: only `m` over one of its **last 4** `HELLO` nonces makes the phone its
  wearer's (a recorded `INFO` answers an older nonce; a pairing `i` is public, so a copied `i` never proves anything),
  and an `i` naming another identity still makes it another wearer's. Before `sid` (request pending), `i` = the
  stream's `I` is its wearer's, another `i` is another wearer's, and no `i` is unknown: receive, but don't write.
- **Leaving a phone**: a phone proved to be another wearer's is left and avoided for **10 minutes**, and the scan goes
  on (that phone only, by its connection id: never the one connected since). While joining, a phone with nothing to
  pair with is left for **5 s** (it may open *Pair nearby* in a moment).

## Alerts — v2 addition, 2026-10-17 (investigation 15)

The wearer's app sends every glucose alert it fires or clears, so followers can alert at the same moment. Rules and
settings: [`spikes/alerts/SPEC.md`](../alerts/SPEC.md); report: [docs/15](../../../docs/15-alerts.md).

| Event | kind | signed by | tags | content (decrypted) |
|---|---|---|---|---|
| **alert** | 21079 (ephemeral) | `D` | `["gk", gk_n]`, exp now+1 h | `{"v":2,"type":"alert","id","k","state":"fire"\|"clear","at","t","sgv","r","th","n","tr"}` → `G_n` |

- `k` ∈ `very_low|low|high|very_high|extremely_high|falling_fast|rising_fast|no_data`; `id` is random per firing and a `clear`
  repeats it; `n` counts the episode's alerts; `tr` = treatments the headline acknowledges (0, 1, 2 = two or more:
  "Low glucose · eat more again"). A treatment-muted alert (after rapid insulin or carbs, falling back into range) is
  never sent. The wearer's alert notification actions (Mute / Relax, and Mute all) are per device and never sent: the events are
  what the rules decide without them, so followers keep alerting while the wearer has muted its phone (SPEC §2,
  2026-10-21).
- Sent at once on relays (LIVE lane, also when the upload interval batches readings) and over BLE (backlog key
  `alert`, next to `live`).
- Followers subscribe with `kinds: [21078, 21079]` in the live REQ, apply the live acceptance rules, dedupe by event
  id, drop `type ≠ "alert"` and anything with `at` older than 10 min.

## Sensor life — v2 addition, 2026-10-18 (round sensor-life)

The live event carries the owner's current sensor (unix s): `ss` = its start, `se` = its end (start + wear length;
the Libre 3 Plus says 15 days in its patch info; earlier when the sensor reported that it ended), `sw` = its warm-up's
end (start + the patch info's warm-up, 60 min). All three are left out when the owner knows no sensor. Juggluco as the
owner's source gives the start only (`sgv.json?sensor=Y`): end = start + 15 days, warm-up 60 min.

- Every reading's live event carries them. A new sensor has no readings for its warm-up, so the owner also publishes
  a **reading-less live event** `{"v":2,"ss","se","sw"}` (same kind, envelope and expiry, relays and Bluetooth)
  whenever its sensor info changes (the first sensor it knows after starting, a new sensor, one that ended early; a
  sensor removed is `{"v":2}`) and every chunk tick (5 min) while the sensor warms up: from `ss` until the first
  reading at or after `ss`, at most until 15 min past `sw`. Live events are ephemeral (relays don't keep them), so the tick is
  what a follower connecting during the warm-up gets. Android `PublishReadings` (`PublishReadingsTest`).
- Followers and the Mac take the sensor from the live event with the newest `created_at` (an older one from a backlog
  doesn't overwrite it); a live event without `se` clears what they knew. A reading-less event adds no reading.
  Pinned by [sync/sensor-life.json](sync/sensor-life.json) (ended early, removed, a new one warming up, its first
  reading, a late backlog copy of an old reading).
- They draw the value card's sensor badge, warm-up and countdown ring from it (docs/FEATURES.md, "Sensor life") and
  keep `no_data` quiet while the sensor has ended or warms up (spikes/alerts/SPEC.md §2).
- No serial on the wire (the start identifies the sensor). Chunks don't carry it; the BLE companion path relays the
  same live events.

## Settings — v2 addition, 2026-10-21 (round f28)

A follower (Android follower app, Mac) chooses where the settings it can mirror come from (*Settings from*: Default /
**Wearer** (default) / Custom; schema `follower.settingsFrom`). For *Wearer* the wearer's phone publishes its own
settings. Which ones: the rows of [`spikes/settings/settings.json`](../settings/settings.json) with `"mirror": true`,
under their `"wire"` key; everything else (sounds, like an alarm, the notification's Mute / Relax, Mute all, no data,
per-place windows and sensor time, the alerts main switch) is per device and never sent.

| Event | kind | signed by | tags | content (decrypted) |
|---|---|---|---|---|
| **settings** | 30078 (parameterized replaceable) | `D` | `["d", H("cgm2:settings:"+sid)]`, `["gk", gk_n]`, exp now+30 d | `{"v":2,"type":"settings","u":unix_s,"graph":{…},"alerts":{…}}` → `G_n`, NIP-44 from `D` |

- `u` = when the wearer last changed one of the mirrored settings (unix s; strictly increasing). `created_at` strictly
  increases per publish (replaceable events, see Relay rules).
- `graph`: Android's `GraphConfig` JSON keys and value forms — `veryLow`, `low`, `tight`, `high`, `veryHigh`,
  `extremelyHigh` (mg/dL ints), `defaultHours` (h), `ringCount` (0–3), `slots` (`"KIND:windowS:faded,…"`, 3 slots,
  e.g. `"TIR:86400:1,TIR:604800:1,GMI:1209600:1"`), `ringRange` (`IN_RANGE` / `TIGHT`), `ringOrder` (the six bands
  comma-separated, `TIGHT,TARGET,HIGH,VERY_HIGH,VERY_LOW,LOW`), `ringMarks`, `side`, `columnCrown` (bools), `lines`
  (`"100,120"`), `gridHours` (h, 0 = off), `lastPlace` (`OFF` / `NEAR_VALUE` / `END`), `lastElse` (one slot, `""` =
  nothing), plus `countdownHours` (the sensor countdown, h).
- `alerts`: the [alerts SPEC §3](../alerts/SPEC.md) shape with only `alerts.<kind>.{on, mgdl|rate, repeatMin}` for the 7
  kinds other than `no_data`, `muteAfterInsulin`, `muteFallingHigh`, `muteAfterCarbs` (`{on, min}`),
  `persistence` (`{lowMin, highMin, marginMgdl}`, #37) and `night` (`{on, from, to, alerts.<kind>.{on, mgdl|rate,
  repeatMin}}` for the same 7 kinds, #38).
- Values follow the schema's number formats (`numbers`); a receiver coerces anything out of spec to a valid value
  and takes a missing key as the default.
- **Published** by the wearer's phone on a change (debounced 3 s; only when the mirrored content changed), on a new
  epoch (rotation), at every start and weekly; also over BLE (backlog key `settings`, sent to a central that says
  HELLO) — so a follower in range without internet gets it too.
- **Followers**: `REQ {"kinds":[30078], "#d":[H("cgm2:settings:"+sid)], "authors": roster.devices}`. Accept iff the
  signature is valid, author ∈ `roster.devices`, `gk` is a known epoch, it decrypts, `d` matches, `v == 2` and
  `type == "settings"`; the **newest `u` wins** (an older or equal one is ignored). A follower that stops following
  forgets it (the next wearer's `u` may be older). Android `WearerSettings.received`, Mac `WearerSettings.received`.
- Privacy: followers already see the readings; the settings are encrypted to the group key like every other event.

## Invite link / QR

Since Sukrara 0.11 the invite is the **v3 short link** of [PROTOCOL-identity](PROTOCOL-identity.md#short-invites-v3):
`https://sukrara.mauri.app/join#<base64url(I.pub[0:8] ‖ secret[16])>`, 63 characters, QR version 5. The secret's
base64url is the request token `t`. Expiry, relays and the wearer's profile are in the invite gift wrap, so the
follower sees who is inviting before asking.

**Single use, no code (0.11.4).** The link is a bearer token: whoever holds it can ask. So:
- The **first** valid request the owner's phone receives **claims** the invite ("first" = first received;
  `created_at` can be backdated, so it isn't used). Later requests on it are not queued: each gets an
  `invite-used` notice, and the claimed request counts them (`others`), so the owner's dialog warns "another phone
  also tried this invite".
- The owner still taps **Allow** (no code): the follower's name and picture are self-asserted, and approving sends the
  last 24 h at once, which "Remove" can't take back. The owner's own label for the invite ("who is it for?", stored
  only on the owner's phone and in the owner backup) is shown next to the requester's name.
- **Deny** sends `declined`. Approving or denying marks the invite done; it stays until it expires (24 h) so late
  requesters still get `invite-used`.
- A leak before the intended follower uses the link lets someone ask in their place with any name and picture.
  Protection: the single claim, the `invite-used` notice to the real follower (who then tells the owner), and the
  owner's Allow. When others tried the invite (`others` > 0) the owner types the follower's **match code** =
  `int(sha256(F.pub_hex + token)) mod 10000`, zero-padded to 4 digits, shown on the follower's phone, before Allow.

## Nearby pairing

*Pair nearby* (BLE INFO `pair`, 10 minutes) reaches every phone in range, and anyone can advertise an offer with any
name and picture. So both phones check each other with a **numeric comparison** (as Bluetooth LE Secure Connections
pairing and Quick Share do): each side commits to a random nonce before it sees the other's, both derive the same
6-digit **pairing code**, and each person confirms on their own phone that the two screens show the same code. A
phone in the middle (posing as the wearer to the follower and as the follower to the wearer) has to fix its nonce on
each side before it learns the other side's, so the two codes it causes match with probability 10⁻⁶ per attempt.

- **Offer** (wearer): opening *Pair nearby* makes a single-use invite token `t` and a fresh 32-byte nonce `nb`
  (lowercase hex), kept only in memory and in the claimed request. While open, `INFO` carries `"i":I, "relays":[…],
  "pair":{"t":t, "x":expiry, "profile":{…}, "c":C(nb)}` with `C(nb)` = lowercase hex SHA-256 of
  `"cgm2:pair-c:" + nb` (64 hex). Every follower sees the same `c`; `nb` is revealed only to the request that claims the
  invite.
- **Listing** (follower): an offer is listed only with a valid `t`, a 64-hex `c`, and `now < x ≤ now + 600 + 120` (the
  10 minutes plus clock skew: an offer that claims to last longer is ignored, so a fake one can't stay listed).
- **Request** (follower): joining makes `F` and a fresh 32-byte nonce `na` *after* reading the offer, keeps `(t, c,
  na)` and sends the access request with `"n":na` (over Bluetooth and the relays, as any request).
- **Claim** (wearer): the first request with `t` and a 64-hex `n` claims the invite (single claim as for links; a
  nearby request without `n` claims nothing). The wearer computes the code and sends the **pairing nonce** `pair`
  `{"n":nb}` to that `F` at once (gift wrap, relays and Bluetooth). Later requests get `invite-used`; a repeated
  request from the claiming `F` changes nothing.
- **Code** = `int(SHA-256("cgm2:pair-code:" + I + ":" + F + ":" + t + ":" + na + ":" + nb)) mod 1000000`, zero-padded
  to 6 digits (`I`, `F` = x-only public keys, lowercase hex), shown as `482 913` and read digit by digit by TalkBack /
  VoiceOver.
- **Follower** on `pair` (sealed by `I`, while `requested` and joined nearby, no code yet): `C(nb)` must equal the
  offer's `c` (else `rejected:pair-commit`: not the phone that made the offer); then `accepted:pair` and the code is
  shown with *Same code on <wearer>'s phone?* A key delivery that arrives **before the person confirmed** is held, not
  used (`ignored:unconfirmed`): nothing from the stream is read until the person confirms. Confirming applies the held
  key (`accepted:key`) or lets the next one in; *Different code* ends the follow (the key `F` is discarded, nothing is
  sent). `declined` and `invite-used` apply as for links.
- **Wearer** shows the same code in the request (*Allow only if <name>'s phone shows this code*) and Allow sends the key
  as for a link; nearby requests are never allowed from the notification (the code must be compared).
- Vectors: [ble/nearby-pairing.json](ble/nearby-pairing.json) (commitment, code, offer expiry, the messages, and a
  follower sequence of real signed events), read by Android `NearbyPairingVectorsTest` and Mac
  `NearbyPairingVectorsTests`.
- A code derived only from the offer (`t`, `I`) wouldn't do: anyone in range sees the offer and can grind its own
  `(I', t')` to show the same few digits. The match code of [Invite link / QR](#invite-link--qr) stays for leaked
  links: there the token is secret and the request encrypted, so nobody can grind a key against it.

## Profiles

A name and a generated picture per person, so lists show faces instead of keys. **Never published**: they travel
only inside encrypted messages (invite wrap, access request, profile update, roster, owner backup). Self-asserted:
they help people recognise each other, they authenticate nothing (that's the pairing code's and match code's job).

```
profile = {"name": "Alex", "avatar": {"v": 1, "c": 0, "s": "cube", "a": 45, "n": 1497950029}}
```

- `name`: up to 40 characters.
- `avatar`: the numbers that draw the picture, never an image. **`v` is the renderer version.** Every app keeps the
  renderer of every version it ever shipped, so a picture made by an older build looks the same forever. An app that
  meets a newer `v` keeps and forwards the object unchanged and draws the fallback (grey `#78909C` square with the
  name's initial) until it is updated. Receivers ignore avatar objects over 512 bytes of JSON.
- **v1** (2026-10-17, Android `core/Avatar.kt`, Mac `Avatar.swift`; drawn in unit coordinates, y down, clipped to a
  circle by the UI):
  - `c` 0–7: linear gradient from colour A to B, `a` degrees (multiple of 15) from `0.5 − (cos a, sin a)/2` to
    `0.5 + (cos a, sin a)/2`. Pairs (ARGB): `1BB5C9→0B2E5E`, `C6F432→2E7D32`, `FF8A65→BF360C`, `BA68C8→4A148C`,
    `FFD54F→E65100`, `4FC3F7→01579B`, `F06292→880E4F`, `90A4AE→263238`.
  - `s` shape in white: `cube` (three faces, w = 0.22, alpha 235/170/110), `drop` (circle r = 0.2 at (0.5, 0.62) +
    triangle to (0.5, 0.2), alpha 220), `dots` (five circles from `sha256("sukrara/avatar/v1/dots:" + n)`: bytes
    4k…4k+3 → r = 0.08 + 0.12·b/255, x = 0.25 + 0.5·b/255, y = 0.25 + 0.5·b/255, alpha = 90 + ⌊110·b/255⌋;
    vectors in [identity/avatar-v1.json](identity/avatar-v1.json)), `rings` (4 concentric strokes, r = 0.09·i,
    width 0.03, alpha 60 + 40·i), `letter` (the name's first character, uppercased, bold system font at 0.5, alpha
    240, glyph box centred).
  - `n` seed 0 … 2³¹−1 (only `dots` uses it).
- **Wearer → followers**: the invite wrap and every roster carry `profile`; a change republishes the roster at once.
- **Follower → wearer**: the access request carries `profile`; a later change (Android *How you appear*, Mac
  Settings › Following › Edit…) is a `profile` rumor: kind 30500 `{"v":2,"type":"profile","profile":{name, avatar}}`
  sealed by `F`, wrapped to `I` (7 d), sent to the relays and over Bluetooth. A blank name keeps the previous one;
  the wearer applies it only from an approved follower.
- **Editing rules** (name trimmed and cut to 40, v1 numbers normalised: `c` mod 8, unknown shape → `cube`, `a` mod 360
  down to a multiple of 15, `n` & 0x7fffffff; "Turn the colours" = `a` + 45, `n` + 1) and the update body are pinned by
  [identity/profile-v1.json](identity/profile-v1.json), run by Android `ProfileFixtureTest` and Mac `-identityselftest`.

## Flows

**Share (follower joins)**
1. Owner taps *Share* → app makes a token, shows QR + share-sheet link.
2. Follower app (role "I follow someone") scans/opens it and fetches the invite wrap (reading only), then shows who is
   inviting (the wrap's `profile`) with *Follow* / *Cancel*; an opened link never asks by itself (another app can open
   a `sukrara:` link it made up). *Follow* generates `F`, sends an *access request* to `I` on the invite's relays,
   shows the match code, waits. Android `FollowerEngine.lookUp` / `join`, Mac `FollowerRuntime.lookUp` / `join(invite:)`.
3. Owner app sees the request (checked on every chunk publish ≈ 5 min, immediately while the app is open), shows
   "Mom's phone wants to see your glucose · code 4821 — Allow / Deny".
4. Allow → owner sends a *key delivery* for the current epoch, records `F` in the follower list and the owner backup,
   and republishes the last 24 h of chunks (paced, newest hour first) so the new follower gets a complete, fresh copy.
5. Follower decrypts the delivery, then: fetch roster → backfill chunks (`#c` + `authors = roster.devices`) → follow live.

**Missing history** (Sukrara, 2026-10-16): after a full backfill the follower checks the last 12 h. Hours without a
reading while the stream is live ⇒ backfill again after 90 s (the owner may still be pacing chunks out — a key
delivery jumps ahead of queued chunks), then a *history request* (≤ 1 per 30 min). The owner answers only for an
approved, non-revoked `F` and a rumor younger than 1 h, at most once per 10 min overall, by republishing its chunks for
hours ≥ `since` (capped at 24 h) under the current epoch on the relays (and BLE). Nothing is sent back privately: the
follower's open `#c` subscription picks the chunks up. Older followers/owners ignore the unknown `type`.

**Revoke** (owner removes a follower)
1. New `G_{n+1}`; *key delivery* to every remaining follower; *revoked notice* to the removed one.
2. Publish roster with `epoch n+1`; update owner backup; switch publishing to `G_{n+1}`.
3. Republish the last 24 h of chunks under `n+1` (paced) so freshly delivered keys see recent history.
4. Followers keep older epochs' keys → their local history stays readable. A revoked follower keeps what it already
   has (no forward secrecy) but gets nothing new.

**Phone change / reinstall** (seamless for followers)
1. New phone: *Restore* → Credential Manager returns the identity `nsec` from Proton Pass / Bitwarden / Google.
2. App fetches the *owner backup* → gets `sid`, all epochs, followers, relays.
3. Generates a new `D'`; rotates to `G_{n+1}` (the old phone knew `G_n`); key deliveries to all followers; roster
   with `devices:[D']`.
4. Followers see the new roster + key and continue; events from the old `D` are no longer accepted.

**Missed rotation**: a follower that sees an unknown `gk` sends a *rekey request*; the owner app answers with the
current key if `F` is still approved.

**Two apps as one identity** (e.g. the old phone still running after a restore): the owner app watches the rosters and
backups signed by `I` that come back from the relays. A newer roster naming other devices or under an epoch it doesn't
hold, or a newer backup listing epochs it doesn't hold (or missing its newest), is another app: it stops publishing
roster, backup, rotations and follower changes (readings and alerts go on) until the wearer retries
([sync/owner-conflict.json](sync/owner-conflict.json) `checks`). The retry first fetches the newest roster and backup
signed by `I` and decrypts the backup (it is encrypted to `I`): followers rank rosters and keys by epoch number, so
when the other app holds an epoch this app doesn't, the retry rotates to `max(own newest, every epoch in that backup)
+ 1`; when it holds one no readable backup names (its backup isn't on the relays, or no relay answered), to that
`+ 100` (a number it can't have reached). Otherwise roster and backup are republished. Either way their `created_at`
is past every event of the other app (`retries`; follower side: [sync/conflict-rotation.json](sync/conflict-rotation.json)).

**Lost identity key** (no backup): unrecoverable by design — followers must be re-invited. (Key-migration NIPs are
all unmerged as of 2026-10.)

## Acceptance rules (followers)

- Roster: author == `I`, under the `gk` of its own `epoch` (a roster whose `epoch` isn't its `gk`'s is rejected:
  `roster-epoch-mismatch`); the highest `(epoch, created_at)` wins, not the newest `created_at` alone. A key delivery
  for epoch n+1 outranks every roster of epoch ≤ n (its `devices` apply until roster n+1 arrives), so a late or
  refreshed roster from a replaced phone can't bring the old device back ([sync/rotation.json](sync/rotation.json)).
- Data (live/chunk): author ∈ current `roster.devices`, `gk` matches a known epoch, decrypts, `v == 2`. A chunk's `c`
  must be `H("cgm2:glucose:"+sid)` and its `d` must be `H("cgm2:h:"+sid+":"+h)` for the payload's own hour `h`. A
  live reading, a chunk's hour or any reading in it later than now + 300 s is rejected (`reading-in-future`).
- Unknown `gk`: a rekey request only when the event is signed by `I` or a roster device (≤ 1 per `gk` per 5 min), so a
  stranger can't make followers send them, and only when its `created_at` is newer than the newest key delivery's
  rumor (an older event is a backfill of hours from before the follower's latest key, not a missed rotation). Its
  `have` is the `gk` of the newest epoch the follower holds (the OGS vectors' `rekey-request`). The event isn't
  remembered as seen: it is read once the key arrives.
- Author not (yet) in the roster: rejected but not remembered as seen — the roster that adds it (a new phone, a new
  writer) may arrive later, and the event is judged again when a relay or Bluetooth delivers it again.
- Revoked notice vs key delivery: both live 30 d on relays and come back in any order after a restart, so they are
  ordered by their rumor `created_at`, not by arrival. A revoked notice older than the newest key delivery is ignored;
  a key delivery newer than the revoked notice re-approves the follower (the wearer invited the same `F` again), even
  when it carries a key the follower already holds. A follower must persist both times
  ([sync/revoke-reapprove.json](sync/revoke-reapprove.json)).
- Key delivery: sealed by `I`, and its `sid` equal to the one already known (if any).
- Settings: author ∈ `roster.devices`, known `gk`, decrypts, `d == H("cgm2:settings:"+sid)`, `type == "settings"`,
  newest `u` wins ([Settings](#settings--v2-addition-2026-10-21-round-f28)).
- Treatments: see [Treatments](#treatments-insulin--carbs--notes--v2-addition-2026-10-16) (writer scope).
- Never trust a reading because of *where* it came from inside the app — each stored reading records `via`
  (`relay:<host>`), and the follower DB is separate from the owner role's DB.
- Cross-checked: every follower sequence in [sync/](sync) and the [OGS vectors](ogs/ogs-draft1.json) (byte for byte,
  and their scenario) run through the independent Python reference follower (`poc/spec/reference`, OGS draft 1, frozen)
  with the rules above that it predates added in `tools/protocol/sync_crosscheck.py` (CI, `rules` job): the same
  verdict at every step, the same rekey `have` and the same final state as Android and the Mac.

## Backup to a password manager

Credential Manager **password** item (works with Proton Pass, Bitwarden, Google Password Manager, KeePassDX; no domain
needed; portable between managers by export/import):
`CreatePasswordRequest(id = "CGM Share · <npub I, short>", password = nsec(I))`. Restore = `GetPasswordOption`.
Optional for people who want it: NIP-49 `ncryptsec` (log_n 18, key_security 0x01) on a printable recovery sheet.
Passkeys (PRF) are **not** used: need a domain, Bitwarden Android doesn't return PRF, and passkeys can't yet move
from Proton Pass to Bitwarden (lock-in).
