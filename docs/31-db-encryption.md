# 31 — Encrypting the database: SQLCipher or not

*2026-11-10 · investigation report · issue #68 · standalone PoC `investigations/68-db-encryption` (Room 2.8.5 with
Sukrara's `readings` table, SQLCipher for Android 4.20.0) · Android, SQLCipher, OWASP and Room docs; the source of
xDrip+, AndroidAPS, Juggluco, GlucoDataHandler, Loop, Trio and Signal · measured on an Android 17 emulator (own AVD,
`google_apis` arm64), never on the phone*

## Goal

`sukrara.db` (90 days of readings, treatments, the sharing ledger) is a plain SQLite file in the app's private,
credential-encrypted storage. Find out whether encrypting it inside the app (SQLCipher with a key sealed by the
Keystore through `SecureStore`, or something else) protects the person from anything real, what it costs, how it
fails, and what other health apps do.

## Short answer

- **Recommendation: don't adopt.** Revisit only on one of the triggers in section 7.
- **It doesn't protect against the attacks that are left.** Android's file-based encryption already protects the file
  on a phone that is off or restarted, and Android now restarts a phone left locked for 3 days. The sandbox and
  Sukrara's backup rules keep it from other apps, backups and device transfer. What remains is someone who can read the
  file on a running, unlocked-once phone: root, an exploit, a forensic full-file-system extraction. SQLCipher's key
  would have to be usable while the phone is locked, because every reading and every alert happens then. A key the app
  can use then can also be used by that attacker, by running code as the app (Cellebrite did this to Signal's
  SQLCipher key; a rooted phone and Frida did it to the Libre 3 app's encrypted Realm).
- **What it costs**: 2.0 MB of native code in an arm64 APK of 20.7 MB (about 1 MB more to download) and a key to
  unseal before the first read. On the emulator, reading 14 days and retention's delete took about 2.5× as long (24 →
  59 ms, 28 → 75 ms), the first query on the 90-day file +40 ms, and a single reading +0.1 ms. Every existing file also
  needs a conversion. The run time alone would not be a reason to say no.
- **It adds a way to lose the history.** With a wrong, lost or unreadable key, SQLCipher refuses the whole file
  (`SQLiteNotADatabaseException`). Room's `onCorruption` is never called, so Sukrara's quarantine never runs and nothing
  recreates the file: every write of a reading throws until new code decides what to do. A Keystore that is busy after
  an OS update would stop the history from opening at all. One honest gain: SQLCipher's per-page HMAC caught all 10
  flipped bits, where the plain file read back a wrong reading twice without an error.
- **The alternatives don't change this.** Jetpack Security is deprecated and never covered databases. Tink or
  field-level encryption have the same key problem, and leave the times (the pattern of a day) readable. With a
  Keystore key per value they are also slow: 0.5–3.9 ms each, 1–8 minutes for a 90-day backfill.
- **Nobody else does it.** No DIY CGM or loop app encrypts its glucose database (xDrip+, AndroidAPS, Juggluco,
  GlucoDataHandler). Loop and Trio on iOS rely on the OS file protection, at the class that keeps data readable while
  the phone is locked, for the same reason Sukrara must. Health Connect keeps its records in plaintext behind
  file-based encryption. Of the vendor apps, only the Libre 3 app is known to encrypt (Realm, with a wrapped key).
- **SQLCipher itself is ready** if the answer ever changes: maintained (4.20.0, October 2026), BSD-licensed (fits
  GPL-3.0), 16 KB-aligned, and it works with Room through both `SupportOpenHelperFactory` and the `SQLCipherDriver` that
  Room 3 needs. Converting a 90-day file took about 0.17 s on the emulator, and Room opened the result unchanged.

## 1. Threat model: what already protects the file

What `sukrara.db` holds: readings (time, source, mg/dL, rate), treatments (insulin, carbs, notes, pen data, their
authors), the follower ledger (gift-wrap ids, rejected events with author keys and relays) and the outbox (signed
events, the followers' already encrypted). Health data, a special category under the GDPR, whose art. 32 asks for
encryption "as appropriate" to the risk ([GDPR art. 32](https://gdpr-info.eu/art-32-gdpr/)).

What protects it today:

- **File-based encryption (FBE)**: required on every device that launched with Android 10 or later
  ([AOSP](https://source.android.com/docs/security/features/encryption/file-based)); Sukrara's minimum is Android 16.
  `databases/` is in credential-encrypted (CE) storage, keyed to the lock-screen secret: unreadable until the first
  unlock after a boot (docs/30 measured it: `ENOENT`, `null` listings).
- **Inactivity reboot**: since Google Play services 25.14 (April 2025) a phone locked for 3 days restarts, back to
  Before First Unlock ([TechCrunch](https://techcrunch.com/2025/04/15/for-security-android-phones-will-now-auto-reboot-after-three-days),
  [Android Authority](https://www.androidauthority.com/android-inactivity-reboot-rollout-3544779/)).
- **The sandbox**: only Sukrara's UID can open `/data/data/app.mauri.sukrara`.
- **No copies leave the phone**: `allowBackup="false"` and `data_extraction_rules.xml` exclude every domain from cloud
  backup and device transfer; `adb backup` exports no app data for apps that target Android 12+ and aren't debuggable
  ([Android 12 behaviour changes](https://developer.android.com/about/versions/12/behavior-changes-12)).
- **Deleted rows are zeroed**: the platform's SQLite runs with `secure_delete` on (measured: `PRAGMA secure_delete` =
  1), so retention and deletions don't leave old readings in free pages.

| Threat | Today | With SQLCipher, key sealed by a Keystore key without user authentication |
|---|---|---|
| Phone lost or seized while off, restarted, or locked > 3 days (BFU) | protected (FBE) | same |
| Another app on the phone | protected (sandbox) | same |
| Cloud backup, device transfer, `adb backup` | protected (excluded) | same |
| Phone locked after the first unlock (AFU), attacker with an exploit or a forensic full-file-system extraction | readable | **readable**: the same access runs code as Sukrara and asks the Keystore to unseal the key; Cellebrite described this for Signal's SQLCipher key ([SecurityAffairs](https://securityaffairs.com/112571/hacking/cellebrites-claims-signal-decrypt.html), [Signal's answer](https://signal.org/blog/cellebrite-and-clickbait/)) |
| Root (some people run Sukrara with LSPosed for the status bar and lock screen) | readable by any root app | **readable**: hook the app or call the Keystore as its UID; how the Libre 3 app's Realm key was unwrapped with Frida on a rooted Android ([frdmtoplay, 2023](https://www.frdmtoplay.com/freeing-glucose-data-from-the-freestyle-libre-3/)) |
| A debug build (`run-as`) | readable | readable (same UID) |
| Someone holding the unlocked phone | open Sukrara | same |
| A glance at the locked phone | the value and graph are on the lock screen, the notification and the widgets by design | same |

Only a key the attacker can't use would change the AFU and root rows: a Keystore key with `setUnlockedDeviceRequired`
or user authentication. Such a key can't be used for decryption while the device is locked
([KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUnlockedDeviceRequired(boolean));
keystore2 super-encrypts it, docs/30 §1). A CGM's work all happens while the phone is locked: readings arrive every
minute in a pocket, and the alert engine reads the recent history to ring on time. So Sukrara would have to keep
everything the engine and the lock-screen surfaces read outside the encrypted file anyway (see section 5, *write while
locked, read when unlocked*).

The database is also not the only copy. Readings and doses are in the typed state (`alert_state`, `pens`,
`libreview-unsent`), in Health Connect when it is on (the platform keeps its records in plaintext behind FBE:
[Pérez-Fuente et al., 2026](https://www.frontiersin.org/journals/digital-health/articles/10.3389/fdgth.2026.1818536/full)),
on the lock screen and in Nightscout / Tidepool / LibreView when uploads are on. Encrypting `sukrara.db` alone would
leave all of those as they are.

OWASP's mobile standard draws the same line: storing data unencrypted in the app's internal storage met the base level
(L1); extra encryption with Keystore-managed keys was asked only for L2 (defense in depth, high-risk apps)
([MASTG-TEST-0001](https://github.com/OWASP/mastg/blob/master/tests/android/MASVS-STORAGE/MASTG-TEST-0001.md), now
replaced by [MASWE-0001](https://mas.owasp.org/MASVS/controls/MASVS-STORAGE-1/)). For L2, that extra layer works only
if the key is unusable while the attacker has the phone, which a CGM can't accept.

## 2. SQLCipher for Android today

| | |
|---|---|
| Artifact | `net.zetetic:sqlcipher-android` 4.20.0 (2026-10-27, [release](https://www.zetetic.net/blog/2026/10/27/sqlcipher-4.20.0-release/)). The old `android-database-sqlcipher` is deprecated since 4.5.5 and has no 16 KB build ([migration guide](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/)) |
| Engine (measured) | `cipher_version` 4.20.0 community, SQLite 3.54.1 (the emulator's platform SQLite: 3.50.6) |
| Crypto (measured) | community edition on LibTomCrypt 1.18.2 (since 4.14.0, [4.18.0 notes](https://www.zetetic.net/blog/2026/08/18/sqlcipher-4.18.0-release/)); AES-256 per 4 KB page with an HMAC-SHA512 per page ([design](https://www.zetetic.net/sqlcipher/design/)); a passphrase goes through PBKDF2-HMAC-SHA512, 256,000 rounds, at every open; a raw key `x'<64 hex>'` skips it; `cipher_memory_security` off |
| File (measured) | no `SQLite format 3` header, a marker string written to the table is not in the file (it is in the plain file) |
| Licence | BSD 3-clause by Zetetic ([LICENSE](https://github.com/sqlcipher/sqlcipher-android/blob/master/LICENSE)): GPL-3.0 compatible; a binary must reproduce the notice (Settings › About's licences) |
| Size | AAR 4.05 MB; `arm64-v8a/libsqlcipher.so` 2,110,992 bytes, stored uncompressed in the APK (1.03 MB gzip-9, about what a download adds) + 135 KB of classes, all kept by its consumer R8 rules. Sukrara ships arm64-v8a only; 0.11.52's APK is 20.7 MB: about +10 % installed, +5 % download |
| 16 KB pages | every `LOAD` segment of all four ABIs is aligned to `0x4000` (`llvm-readelf -l`), and `zipalign -c -P 16` passes on the PoC's APK: meets Play's 16 KB rule ([page sizes](https://developer.android.com/guide/practices/page-sizes)) |
| Room | Room 2.x: `openHelperFactory(SupportOpenHelperFactory(key))`. Room's driver API (required by Room 3, stable since July 2026, [release notes](https://developer.android.com/jetpack/androidx/releases/room3)): `setDriver(SQLCipherDriver(key, hook, errorHandler))`, Room 3 support since 4.18.0. Both ran Sukrara's table in the PoC |
| Start | `System.loadLibrary("sqlcipher")` before the first open: 9 ms on the emulator when the host was quiet (80–310 ms when it was busy) |

## 3. What it costs (emulator)

This is Sukrara's own workload on its own `readings` table (`DbEncryptionBench.t1`): five interleaved runs per
variant, medians. The variants are in the PoC's README: `cipher-raw` is the key #68 proposed, `cipher-kdf` a
passphrase, `cipher-driver` Room's driver API. The run in `results/emulator-android37.json` had a quiet host. A first
run on a busy host (load average ~50 on 10 cores) was 10–40× slower on the bulk rows and too noisy to compare.

| | plain (today) | cipher-raw | cipher-kdf | cipher-driver |
|---|---|---|---|---|
| Open a new file, create the table | 7 ms | 7 ms | 591 ms | 7 ms |
| Write 90 days (129,600 readings, a transaction per day) | 703 ms | 651 ms | 626 ms | 1,325 ms |
| One reading in its own transaction, median of 300 | 0.34 ms | 0.43 ms | 0.43 ms | 0.41 ms |
| One reading, 95th percentile | 1.04 ms | 0.90 ms | 0.84 ms | 0.94 ms |
| Read the last 24 h (1,440 rows), median of 30 | 2.0 ms | 1.5 ms | 1.5 ms | 1.5 ms |
| Read the last 14 days (20,160 rows), median of 10 | 24 ms | 59 ms (×2.5) | 61 ms | 56 ms |
| Reopen the 90-day file, first query | 3 ms | 40 ms | 583 ms | 48 ms |
| Retention: delete 30 days | 28 ms | 75 ms (×2.7) | 74 ms | 76 ms |
| File size after 90 days | 7.45 MB | 7.67 MB (+3 %) | 7.67 MB | 7.67 MB |

What this says:

- **The bulk work costs 2.5×.** Reading 14 days (the report, the statistics) and deleting a month cost about 2.5× the
  time, because every page read is decrypted and its HMAC checked. The 24 h graph read and a single reading are within
  noise. Bulk writes come out even because the two engines checkpoint differently: the platform every 100 WAL pages,
  SQLCipher every 1,000 (measured `wal_autocheckpoint`).
- **A passphrase is never the right key.** PBKDF2's 256,000 rounds add ~0.6 s to every open. A random key sealed by
  the Keystore, passed raw, has no KDF.
- **The driver integration** (`SQLCipherDriver`, what Room 3 requires) costs the same, except a slower bulk write here.
- **Unsealing the key** with an AES-GCM Keystore key (`t5`, as `KeystoreSecretBox` does): 6–33 ms the first time in a
  process, then 1.4–3.7 ms, across three runs.

On a phone every number is smaller. For Sukrara's sizes the run time is milliseconds either way.

## 4. How it fails

`DbEncryptionBench.t4`, every case through Room with a factory that records `onCorruption` the way
`QuarantiningOpenHelperFactory` hooks it:

| Case | Platform SQLite (today) | SQLCipher |
|---|---|---|
| Wrong key (both integrations) | — | `SQLiteNotADatabaseException: file is not a database (code 26)` at the first query; `onCorruption` not called; file untouched; the right key opens all rows afterwards |
| Plaintext file opened with a key (a conversion that never ran) | — | the same exception; file untouched |
| A file of random bytes | `onCorruption` called; the default handler deletes the file and Room starts an empty one (Sukrara copies it aside first) | the same exception as a wrong key; nothing reported, nothing recreated |
| One bit flipped, 10 places, one at a time | 4 not noticed (2 in free space, 2 in the index: reads fine, `integrity_check` finds them); **2 read back a different reading with no error**; 4 `SQLiteDatabaseCorruptException` → `onCorruption` → the file is deleted and recreated empty | **10 of 10** `SQLiteDatabaseCorruptException` (the page's HMAC); `onCorruption` not called; file kept; a `COUNT(*)` afterwards worked in 5 (it didn't need the damaged page) |

Why `onCorruption` never runs: SQLCipher's `SupportHelper` builds its open helper with no error handler and doesn't
forward Room's callback, and its `DefaultDatabaseErrorHandler` deletes nothing in a build with a codec ("it may be that
the user has specified an incorrect password"; [source](https://github.com/sqlcipher/sqlcipher-android/blob/master/sqlcipher/src/main/java/net/zetetic/database/DefaultDatabaseErrorHandler.java)).
Not deleting is right for a database whose key might come back. But it means adopting SQLCipher needs a new path in
`DataModule`: tell a key that can't be unsealed (transient: retry, keep the readings somewhere) from a file that will
never open (quarantine, report, start a new file with a new key). Until that path has run, every reading's write
throws.

The bit flips show the one thing SQLCipher does better: the plain file has no checksums. A damaged row can come back
as a different reading, and SQLCipher's HMAC turns that into an error. Flash storage has its own error correction,
and FBE decrypts a damaged block into 16 garbled bytes rather than one changed bit, so a lone flipped bit in the file
is rare. If silent damage ever needs guarding, an integrity check doesn't need encryption. A per-row checksum, or
SQLite's checksum VFS (`cksumvfs`) in a self-built SQLite, would do it without a key that can go missing.

The key is one more step before the first read (section 3). The Keystore is also where Sukrara has already met
transient errors after OS updates (`KeystoreSecretBox`'s failure policy). Signal crashes on start when its database secret can't be unsealed
([#8313](https://github.com/signalapp/Signal-Android/issues/8313), [#7400](https://github.com/signalapp/Signal-Android/issues/7400)),
and its fork Session had to stop a crash loop on that path ([session-android #2219](https://github.com/session-foundation/session-android/pull/2219)).
For a messenger that is an outage; for Sukrara it is a phone that can't store a reading.

Converting an existing file (`t3`) means opening the plaintext file with SQLCipher and an empty key, `ATTACH` a new
file with the key, `SELECT sqlcipher_export('encrypted')`, copy `user_version`, `DETACH`, then swap the files. For 90
days (129,600 rows, 7.43 MB → 7.16 MB) that took 160–177 ms in four runs. Room then opened the converted file with no
migration: `room_master_table` and the schema are copied, and all 129,600 rows were there. Two details from Zetetic's
own test ([ImportUnencryptedDatabaseTest](https://github.com/sqlcipher/sqlcipher-android/blob/master/sqlcipher/src/androidTest/java/net/zetetic/database/sqlcipher/ImportUnencryptedDatabaseTest.java))
matter, and the first attempts without them failed ("unable to open database", "unknown database encrypted", no file
at all). The plaintext file must be opened with `CREATE_IF_NECESSARY`, because `ATTACH` creates the new file with the
main file's flags. And `ATTACH` should go through `execSQL`, which turns WAL off so the next statements run on the
connection that has the attachment. Sukrara's *no backwards compatibility* rule would then have this conversion code
removed later, or the history dropped.

## 5. Alternatives

| Option | What it is | Verdict |
|---|---|---|
| Jetpack Security (`EncryptedFile`, `EncryptedSharedPreferences`) | AES-GCM files / prefs with a Keystore master key | deprecated in 1.1.0, no more releases ([Android cryptography](https://developer.android.com/privacy-and-security/cryptography), "Deprecated functionality"); never covered SQLite |
| Tink AEAD with a Keystore-wrapped keyset | blob or field encryption in the app | the same key problem as SQLCipher; Sukrara's `SecretBox` already does this with the Keystore directly, for the secrets |
| Field-level encryption (mg/dL, notes) | encrypt some columns, keep the rest | times stay plaintext (when the person ate, slept, went low), no range queries on values, per-value cost: a Keystore AES-GCM operation took 0.47–3.9 ms over three runs (1–8 minutes for a 90-day backfill), a software one 0.017–0.025 ms with a data key that sits in memory like SQLCipher's |
| SQLite3 Multiple Ciphers | MIT-licensed SQLite with several ciphers, prebuilt Android AAR ([releases](https://github.com/utelle/SQLite3MultipleCiphers/releases)) | no Room factory or driver of its own; same threat model as SQLCipher |
| SQLite Encryption Extension (SEE) | the SQLite authors' paid extension | its source can't be published: not for a GPL app |
| Encrypted Realm | the Libre 3 app's choice | Atlas Device SDKs reached end of life on 2025-09-30 ([MongoDB](https://www.mongodb.com/docs/device-sdks-deprecation/)) |
| Write while locked, read when unlocked | new readings encrypted to a public key while locked; history behind a `setUnlockedDeviceRequired` private key, like HealthKit's journal that merges at unlock ([Apple](https://support.apple.com/guide/security/protecting-access-to-users-health-data-sec88be9900f/web)) | the only design that keeps the history from an AFU attacker. But the alert engine, the lock-screen card, widgets, followers and uploads read the history while locked, so only an archive older than all of them could be locked away. A large design for a narrow gain |

## 6. What other health apps do

| App | Local data at rest | Source |
|---|---|---|
| xDrip+ | plain SQLite (`DexDrip.db`, ActiveAndroid); *Export database* zips the raw file | [AndroidManifest](https://github.com/NightscoutFoundation/xDrip/blob/20b8e5bd36ce03e216aa5f52359e029fd17b4450/app/src/main/AndroidManifest.xml#L155-L159), [DatabaseUtil](https://github.com/NightscoutFoundation/xDrip/blob/20b8e5bd36ce03e216aa5f52359e029fd17b4450/app/src/main/java/com/eveningoutpost/dexdrip/utils/DatabaseUtil.java#L69-L105) |
| AndroidAPS | plain Room (`BundledSQLiteDriver`); since 2026-10 its settings file is AES-GCM with a Keystore key (no user authentication); the master password covers only exported settings | [AppDatabaseBuilder](https://github.com/nightscout/AndroidAPS/blob/f77abd9b82b308e8fc330da0d7a31370fb4a0f49/database/impl/src/androidMain/kotlin/app/aaps/database/di/AppDatabaseBuilder.kt#L20-L38), [KeystoreStoreCipher](https://github.com/nightscout/AndroidAPS/blob/f77abd9b82b308e8fc330da0d7a31370fb4a0f49/app/src/main/kotlin/app/aaps/settingsstore/KeystoreStoreCipher.kt#L125-L131) |
| Juggluco | plain memory-mapped files (`data.dat`, `polls.dat`); encryption only on its network mirror | [inout.hpp](https://github.com/j-kaltes/Juggluco/blob/afe6539f1e48aa0b6aeb1a34741752323858a5ad/Common/src/main/cpp/inout.hpp#L326-L360) |
| GlucoDataHandler | plain Room | [dbAccess.kt](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/database/dbAccess.kt#L119-L130) |
| FreeStyle Libre 3 app | encrypted Realm, key wrapped by a white-box library; unwrapped with Frida on a rooted Android | [frdmtoplay](https://www.frdmtoplay.com/freeing-glucose-data-from-the-freestyle-libre-3/) |
| mySugr | "data stored … on your mobile device is encrypted", method not stated | [mySugr](https://www.mysugr.com/support/en/about-mysugr-glucose-insights) |
| LibreLink, Dexcom G7, MiniMed, Omnipod 5 | nothing public about storage at rest (Play listings: "encrypted in transit") | [Abbott FAQ](https://www.freestyle.abbott/en-ca/support/is-my-data-safe.html) |
| Loop, Trio (iOS) | Core Data with Data Protection class C (`completeUntilFirstUserAuthentication`): readable while locked after the first unlock, so background CGM and loop work | [LoopKit PersistenceController](https://github.com/LoopKit/LoopKit/blob/79f4291b226222b8b08a7e75af618ca496c63831/LoopKit/Persistence/PersistenceController.swift#L207-L224), [Trio CoreDataStack](https://github.com/nightscout/Trio/blob/0b29cd53415e7f0e668677749cad8daf81c68c2f/Model/CoreDataStack.swift#L37-L61) |
| Health Connect | records in plaintext in `healthcore.db`, behind FBE | [Pérez-Fuente et al., 2026](https://www.frontiersin.org/journals/digital-health/articles/10.3389/fdgth.2026.1818536/full) |
| Signal (comparison) | SQLCipher, random 32-byte secret sealed by a Keystore AES-GCM key without user authentication: the design #68 proposed | [DatabaseSecretProvider](https://github.com/signalapp/Signal-Android/blob/d507a800a61cd5819a3670fe71e84e103efa7bda/lib/storage/src/main/java/org/signal/storage/database/DatabaseSecretProvider.kt#L65-L73) |

A review of 79 NHS-accredited health apps found that none encrypted the personal data they stored locally
([Huckvale et al., BMC Medicine 2015](https://doi.org/10.1186/s12916-015-0444-y)). A decade later the DIY diabetes
apps still don't. Read as a whole, the field relies on the OS for storage at rest. It encrypts in the app where data
leaves the phone (Juggluco's mirror, AndroidAPS's exports, Sukrara's Nostr gift wraps), and for secrets.

## 7. Recommendation

**Don't adopt SQLCipher, and don't add field-level encryption.** The design #68 proposed (a random key sealed by
`SecureStore`) is the one Signal uses, and it does what it can. But on Android 16+ it guards only against someone who
can read Sukrara's files without being able to run code as Sukrara. Next to FBE, the inactivity reboot and the
sandbox, that case is narrow. Against it stand a 2 MB library, slower reads and a conversion. Above all, there is a new
way for 90 days of history and every new reading to become unwritable. Sukrara's storage is built so that damage is
never silent and a busy Keystore deletes nothing (docs/ARCHITECTURE.md "Corruption is never silent"). A database key
that can't be unsealed would block every reading's write instead.

Revisit when one of these becomes true:

- Sukrara has to meet a requirement that names encryption at rest: OWASP MASVS L2, a regulator, a store policy or a
  medical-device classification.
- A feature puts history where FBE doesn't cover it, such as external or shared storage. #160's readings in
  device-protected storage don't count: a key the app can use before the first unlock is one an attacker can use then
  too. Keeping the device-protected data minimal (docs/30) is the protection there.
- The owner wants the *write while locked, read when unlocked* archive (section 5). It is a design of its own, not
  SQLCipher, and needs a product round first.

## In Sukrara

| Added | Where |
|---|---|
| The decision, the threat model and where it is recorded | this report; docs/ARCHITECTURE.md "Android storage" (replaces "not encrypted yet (issue: SQLCipher)") |
| The PoC: the bench, its run script, the emulator results | `investigations/68-db-encryption/` (standalone Gradle build, not in `apps/android`; nothing ships) |

| Left out | Why |
|---|---|
| SQLCipher, through either Room integration | no protection against the attacks FBE leaves open (section 1); a new way to lose or block the history (section 4); 2 MB and slower reads (sections 2, 3) |
| A Keystore-sealed database key in `SecureStore` (`SecretName.DATABASE`) | only needed with SQLCipher |
| Converting existing files (`sqlcipher_export`) | measured to work, not needed |
| Field-level or Tink encryption of values and notes | same key problem; the times stay readable; 0.5–3.9 ms per value with a Keystore key |
| A checksum against silent damage (per row, or `cksumvfs`) | not part of #68; 2 wrong readings in 10 single-bit flips is a lab result, real flash rarely does that (section 4) |
| A history locked while the phone is locked | conflicts with alerts, widgets, followers and uploads reading it while locked; its own design if ever wanted |
| `PRAGMA secure_delete` | already on in the platform's SQLite |

## Run it

```sh
# your own emulator, never the phone (run.sh refuses non-emulator serials)
investigations/68-db-encryption/run.sh emulator-5668 investigations/68-db-encryption/results/emulator-android37.json
```
