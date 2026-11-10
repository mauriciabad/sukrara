# 68 — Encrypting the database (SQLCipher)

The proof of concept behind [docs/31-db-encryption.md](../../docs/31-db-encryption.md) (issue #68). It is a
standalone Gradle build, not part of `apps/android`: nothing here ships in Sukrara.

It puts Sukrara's `readings` table (`core/data` `ReadingEntity`, same Room version) behind four ways of opening the
file and measures each on an emulator:

| Variant | How Room opens the file |
|---|---|
| `plain` | Room's default `FrameworkSQLiteOpenHelperFactory`: the platform's SQLite, what Sukrara ships |
| `cipher-raw` | SQLCipher's `SupportOpenHelperFactory` with a raw 256-bit key (`x'…'`, no key derivation) |
| `cipher-kdf` | the same with a passphrase: SQLCipher runs PBKDF2-HMAC-SHA512 (256,000 rounds) at every open |
| `cipher-driver` | SQLCipher's `SQLCipherDriver` through Room's `SQLiteDriver` API (`setDriver`, what Room 3 requires) |

`DbEncryptionBench` (`src/androidTest`) runs, in order:

1. `t0` loading `libsqlcipher.so`;
2. `t1` Sukrara's workload, 5 interleaved runs per variant, medians: open a new file, 90 days of 1-minute readings
   (129,600 rows, one transaction per day), 300 single inserts (a reading a minute), the last 24 h and the last 14 days
   read back, the file size, reopening, retention's delete;
3. `t2` the engines' settings (`PRAGMA`s) and whether a marker string can be found in the file;
4. `t3` converting an existing 90-day plaintext file with `sqlcipher_export`, then opening it with Room;
5. `t4` failures: a wrong key (both integrations), a plaintext file opened with a key, a file of random bytes, one bit
   flipped in ten places; whether Room's `onCorruption` runs (Sukrara's `QuarantiningOpenHelperFactory` hooks it),
   what is thrown, what is left of the file;
6. `t5` the Keystore: unsealing a 32-byte database key with an AES-GCM Keystore key (as `KeystoreSecretBox` does),
   and one AES-GCM operation per value with a Keystore key vs a software key (the cost of field-level encryption).

## Run

Emulator only (`run.sh` refuses any serial that isn't `emulator-*`). With your own AVD started:

```sh
investigations/68-db-encryption/run.sh emulator-5668 investigations/68-db-encryption/results/<name>.json
```

It builds with `apps/android`'s Gradle wrapper (JDK 21, `ANDROID_HOME`), installs both APKs, runs the instrumentation
and copies `files/results.json` out of the app. Logcat tag `Inv68` shows each run as it finishes.

## Results

[`results/emulator-android37.json`](results/emulator-android37.json): Android 17 emulator (`google_apis` arm64,
4 cores, own AVD, 2026-11-10). `t3` was re-run alone after its fix (four runs, the last one kept). The numbers are
relative, not a phone's. Read them with the report.
