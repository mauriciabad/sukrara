// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.inv68

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/**
 * Issue #68: what SQLCipher costs and how it fails, against Sukrara's own Room setup. Every result goes to logcat
 * (tag `Inv68`) and `files/results.json`; `run.sh` collects them. Emulator only: timings are relative, not a phone's.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DbEncryptionBench {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ---- 0. loading the native library (once per process) ----

    @Test
    fun t0_loadLibrary() {
        val ms = timeMs { Bench.factoryOf(Variant.Plain) }
        record("loadLibraryMs", ms)
    }

    // ---- 1. the workload Sukrara has: 90 days of 1-minute readings, a reading a minute, the graph's reads ----

    @Test
    fun t1_workload() {
        val variants =
            listOf(
                Variant.Plain,
                Variant.Cipher("cipher-raw", Bench.rawKey(KEY)),
                Variant.Cipher("cipher-kdf", PASSPHRASE),
                Variant.CipherDriver("cipher-driver", Bench.rawKey(KEY)),
            )
        val runs = variants.associate { it.name to mutableListOf<Map<String, Double>>() }
        repeat(REPS) { rep ->
            for (v in variants) runs.getValue(v.name).add(workload(v, "w-${v.name}.db").also { log("rep $rep ${v.name} $it") })
        }
        val out = JSONObject()
        for ((name, list) in runs) {
            val med = JSONObject()
            for (k in list.first().keys) med.put(k, median(list.map { it.getValue(k) }))
            out.put(name, med)
        }
        record("workload", out)
    }

    private fun workload(
        v: Variant,
        file: String,
    ): Map<String, Double> {
        ctx.deleteDatabase(file)
        val m = linkedMapOf<String, Double>()
        var db = Bench.open(ctx, file, v)
        m["openNewMs"] = timeMs { db.readings().count() }
        m["backfill90dMs"] = timeMs { for (d in 0 until DAYS) db.readings().upsert(day(d)) }
        val single =
            (0 until SINGLE).map { i ->
                val row = ReadingEntity(END + (i + 1) * MINUTE, SOURCE, 100 + i % 50, 0.1)
                timeUs { db.readings().upsert(row) }
            }
        m["insertOneMedianUs"] = median(single)
        m["insertOneP95Us"] = single.sorted()[(single.size * 0.95).toInt()]
        m["read24hMedianMs"] = median((0 until 30).map { timeMs { check(db.readings().since(END - DAY_MS).size >= 1440) } })
        m["read14dMedianMs"] = median((0 until 10).map { timeMs { check(db.readings().since(END - 14 * DAY_MS).size >= 14 * 1440) } })
        db.close()
        m["fileBytes"] = dbBytes(file).toDouble()
        db = Bench.open(ctx, file, v)
        m["reopenMs"] = timeMs { check(db.readings().count() == DAYS * PER_DAY + SINGLE) }
        m["prune30dMs"] = timeMs { db.readings().deleteBefore(END - 60 * DAY_MS) }
        db.close()
        ctx.deleteDatabase(file)
        return m
    }

    // ---- 2. what is in the file, and the engines' settings ----

    @Test
    fun t2_fileAndPragmas() {
        val out = JSONObject()
        for (v in listOf(Variant.Plain, Variant.Cipher("cipher-raw", Bench.rawKey(KEY)))) {
            val file = "p-${v.name}.db"
            ctx.deleteDatabase(file)
            val db = Bench.open(ctx, file, v)
            db.readings().upsert((0 until 60).map { ReadingEntity(END + it * MINUTE, MARKER, 123, null) })
            val o = JSONObject()
            val sql = db.openHelper.writableDatabase
            for (p in PRAGMAS) {
                runCatching { sql.query(p).use { c -> if (c.moveToFirst()) c.getString(0) else null } }
                    .onSuccess { o.put(p, it ?: JSONObject.NULL) }
                    .onFailure { o.put(p, "error: ${it.javaClass.simpleName}") }
            }
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            db.close()
            val bytes = ctx.getDatabasePath(file).readBytes()
            o.put("header", String(bytes.copyOfRange(0, 15), Charsets.ISO_8859_1).filter { it in ' '..'~' })
            o.put("markerInFile", String(bytes, Charsets.ISO_8859_1).contains(MARKER))
            out.put(v.name, o)
            ctx.deleteDatabase(file)
        }
        record("file", out)
    }

    // ---- 3. moving an existing plaintext database to SQLCipher (sqlcipher_export), then Room opens it ----

    @Test
    fun t3_migratePlaintext() {
        val name = "migrate.db"
        ctx.deleteDatabase(name)
        val plainDb = Bench.open(ctx, name, Variant.Plain)
        for (d in 0 until DAYS) plainDb.readings().upsert(day(d))
        plainDb.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        plainDb.close()
        val plain = ctx.getDatabasePath(name)
        val enc = File(plain.path + ".enc")
        enc.delete()
        val plainBytes = plain.length()
        val ms =
            timeMs {
                // As Zetetic's ImportUnencryptedDatabaseTest: CREATE_IF_NECESSARY (ATTACH opens the new file with the
                // main file's flags: without it, "unable to open database") and ATTACH through execSQL (it turns WAL
                // off, so the statements after it run on the connection that has the attachment)
                val flags = CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY
                val db = CipherDatabase.openDatabase(plain.path, "", null, flags, null, null)
                val version = db.version
                db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf<Any>(enc.path, String(Bench.rawKey(KEY))))
                db.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                db.execSQL("PRAGMA encrypted.user_version = $version")
                db.execSQL("DETACH DATABASE encrypted")
                db.close()
                check(android.database.sqlite.SQLiteDatabase.deleteDatabase(plain))
                check(enc.renameTo(plain))
            }
        val roomDb = Bench.open(ctx, name, Variant.Cipher("cipher-raw", Bench.rawKey(KEY)))
        val count = roomDb.readings().count()
        val lastDay = roomDb.readings().since(END - DAY_MS).size
        roomDb.close()
        record(
            "migrate",
            JSONObject()
                .put("rows", count)
                .put("lastDayRows", lastDay)
                .put("plainBytes", plainBytes)
                .put("encryptedBytes", ctx.getDatabasePath(name).length())
                .put("exportAndSwapMs", ms),
        )
        ctx.deleteDatabase(name)
    }

    // ---- 4. failure modes: wrong key, a plaintext file, a damaged file; who gets told, what is deleted ----

    @Test
    fun t4_failures() {
        val out = JSONObject()
        val right = Variant.Cipher("cipher-raw", Bench.rawKey(KEY))
        val wrong = Variant.Cipher("cipher-wrong", Bench.rawKey(OTHER_KEY))

        out.put("wrongKey", openAfter("fw-wrong.db", seed = right, open = wrong, reopenWith = right))
        out.put(
            "wrongKeyDriver",
            openAfter(
                "fw-wrongd.db",
                seed = right,
                open = Variant.CipherDriver("driver-wrong", Bench.rawKey(OTHER_KEY)),
                reopenWith = right,
            ),
        )
        out.put("plaintextOpenedWithKey", openAfter("fw-plain-key.db", seed = Variant.Plain, open = right, reopenWith = Variant.Plain))
        out.put("garbagePlain", openGarbage("fw-garbage-p.db", Variant.Plain))
        out.put("garbageCipher", openGarbage("fw-garbage-c.db", right))
        out.put("bitFlipPlain", bitFlips("fw-flip-p.db", Variant.Plain))
        out.put("bitFlipCipher", bitFlips("fw-flip-c.db", right))
        record("failures", out)
    }

    /** Seeds [file] with one day through [seed], opens it through [open], then again through [reopenWith]. */
    private fun openAfter(
        file: String,
        seed: Variant,
        open: Variant,
        reopenWith: Variant,
    ): JSONObject {
        ctx.deleteDatabase(file)
        Bench.open(ctx, file, seed).apply {
            readings().upsert(day(0))
            close()
        }
        val before = ctx.getDatabasePath(file).length()
        val o = attempt(file, open)
        o.put("fileBytesBefore", before).put("fileBytesAfter", ctx.getDatabasePath(file).length())
        o.put("rowsWithRightKeyAfter", runCatching { Bench.open(ctx, file, reopenWith).use { it.readings().count() } }.getOrElse { -1 })
        ctx.deleteDatabase(file)
        return o
    }

    /** A file of random bytes where the database should be (a torn copy, disk damage). */
    private fun openGarbage(
        file: String,
        v: Variant,
    ): JSONObject {
        ctx.deleteDatabase(file)
        val path = ctx.getDatabasePath(file)
        path.parentFile!!.mkdirs()
        path.writeBytes(ByteArray(64 * 1024).also { SecureRandom().nextBytes(it) })
        val o = attempt(file, v)
        o.put("fileBytesAfter", path.length())
        ctx.deleteDatabase(file)
        return o
    }

    /** Opens through [v] with a recording factory: what was thrown, whether onCorruption ran, what the file is now. */
    private fun attempt(
        file: String,
        v: Variant,
    ): JSONObject {
        val o = JSONObject()
        val rec = Bench.factoryOf(v)?.let { RecordingFactory(it) }
        val db = Bench.open(ctx, file, v, rec)
        runCatching { db.readings().count() }
            .onSuccess { o.put("result", "opened, $it rows") }
            .onFailure { o.put("result", describe(it)) }
        runCatching { db.close() }
        o.put("onCorruptionCalls", rec?.corruptionCalls ?: "n/a (driver)")
        o.put("fileExistsAfter", ctx.getDatabasePath(file).exists())
        return o
    }

    /**
     * One bit flipped in the table's pages, one place at a time (the middle of a page: headers / free space; 12 bytes
     * before its end: a row's values): is the damage noticed, or read back as data? Then what is left in the file.
     */
    private fun bitFlips(
        file: String,
        v: Variant,
    ): JSONArray {
        val arr = JSONArray()
        val places = listOf(3, 5, 8, 13, 21).map { it to PAGE / 2 } + listOf(4, 9, 14, 19, 22).map { it to PAGE - 12 }
        for ((page, at) in places) {
            ctx.deleteDatabase(file)
            Bench.open(ctx, file, v).apply {
                readings().upsert(day(0))
                openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                close()
            }
            val path = ctx.getDatabasePath(file)
            val offset = (page - 1L) * PAGE + at
            RandomAccessFile(path, "rw").use { raf ->
                raf.seek(offset)
                val b = raf.read()
                raf.seek(offset)
                raf.write(b xor 0x10)
            }
            val o = JSONObject().put("page", page).put("offsetInPage", at)
            val rec = Bench.factoryOf(v)?.let { RecordingFactory(it) }
            val db = Bench.open(ctx, file, v, rec)
            runCatching {
                val rows = db.readings().since(0)
                val expected = day(0).associateBy { it.t }
                val wrong = rows.count { r -> expected[r.t]?.let { it != r } ?: true }
                "read ${rows.size} rows, $wrong differ from what was written"
            }.onSuccess { o.put("result", it) }.onFailure { o.put("result", describe(it)) }
            runCatching {
                db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { if (it.moveToFirst()) it.getString(0) else null }
            }.onSuccess { o.put("integrityCheck", it) }.onFailure { o.put("integrityCheck", describe(it)) }
            runCatching { db.close() }
            o.put("onCorruptionCalls", rec?.corruptionCalls)
            o.put("rowsLeftAfter", runCatching { Bench.open(ctx, file, v).use { it.readings().count() } }.getOrElse { describe(it) })
            arr.put(o)
        }
        ctx.deleteDatabase(file)
        return arr
    }

    // ---- 5. the key: unwrapping a Keystore-sealed DB key, and why field-level encryption with Keystore keys is slow ----

    @Test
    fun t5_keystore() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.deleteEntry(ALIAS)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        val wrapKey = gen.generateKey()
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val enc = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey) }
        val sealed = enc.iv + enc.doFinal(dbKey)

        fun unwrap(): ByteArray {
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(ALIAS, null) as SecretKey
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
            return c.doFinal(sealed, 12, sealed.size - 12)
        }
        val first = timeMs { check(unwrap().contentEquals(dbKey)) }
        val unwrapMs = median((0 until 20).map { timeMs { unwrap() } })

        val plain = ByteArray(8)
        val keystoreOpUs =
            median(
                (0 until 200).map {
                    timeUs { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey) }.doFinal(plain) }
                },
            )
        val soft = SecretKeySpec(dbKey, "AES")
        val iv = ByteArray(12)
        val softOpUs =
            median(
                (0 until 10_000).map { i ->
                    iv[0] = i.toByte()
                    iv[1] = (i shr 8).toByte()
                    timeUs { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, soft, GCMParameterSpec(128, iv)) }.doFinal(plain) }
                },
            )
        ks.deleteEntry(ALIAS)
        record(
            "keystore",
            JSONObject()
                .put("unwrapFirstMs", first)
                .put("unwrapMedianMs", unwrapMs)
                .put("keystoreAesGcmPerValueUs", keystoreOpUs)
                .put("softwareAesGcmPerValueUs", softOpUs)
                .put("keystore90dBackfillEstimateS", keystoreOpUs * DAYS * PER_DAY / 1e6),
        )
    }

    // ---- helpers ----

    private fun day(d: Int): List<ReadingEntity> {
        val start = END - (DAYS - d) * DAY_MS
        return (0 until PER_DAY).map { i ->
            val n = d * PER_DAY + i
            ReadingEntity(start + i * MINUTE, SOURCE, 70 + (n * 7) % 180, ((n % 11) - 5) / 10.0)
        }
    }

    private fun dbBytes(file: String): Long {
        val f = ctx.getDatabasePath(file)
        return listOf(f, File(f.path + "-wal"), File(f.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
    }

    private fun describe(t: Throwable): String {
        val chain = generateSequence(t) { it.cause }.toList()
        return chain.joinToString(" <- ") { "${it.javaClass.name}: ${it.message?.take(160)}" }
    }

    private fun record(
        key: String,
        value: Any,
    ) {
        val f = File(ctx.filesDir, "results.json")
        val all = if (f.exists()) JSONObject(f.readText()) else JSONObject()
        all.put(key, value)
        f.writeText(all.toString(2))
        log("$key = $value")
    }

    private fun log(s: String) = Log.i("Inv68", s)

    private inline fun timeMs(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e6
    }

    private inline fun timeUs(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e3
    }

    private fun median(xs: List<Double>): Double = xs.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    private fun <T : androidx.room.RoomDatabase, R> T.use(block: (T) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val DAYS = 90
        const val PER_DAY = 1440
        const val REPS = 5
        const val SINGLE = 300
        const val MINUTE = 60_000L
        const val DAY_MS = 86_400_000L
        const val PAGE = 4096
        const val END = 1_790_000_000_000L
        const val SOURCE = "libre3"
        const val MARKER = "MARKER-SUKRARA-INV68"
        const val ALIAS = "inv68.dbkey"
        val KEY = ByteArray(32) { (it * 37 + 11).toByte() }
        val OTHER_KEY = ByteArray(32) { (it * 53 + 7).toByte() }
        val PASSPHRASE = "a passphrase, so SQLCipher runs PBKDF2 at every open".toByteArray()
        val PRAGMAS =
            listOf(
                "SELECT sqlite_version()",
                "PRAGMA journal_mode",
                "PRAGMA synchronous",
                "PRAGMA cache_size",
                "PRAGMA wal_autocheckpoint",
                "PRAGMA page_size",
                "PRAGMA secure_delete",
                "PRAGMA cipher_version",
                "PRAGMA cipher_provider",
                "PRAGMA cipher_provider_version",
                "PRAGMA kdf_iter",
                "PRAGMA cipher_kdf_algorithm",
                "PRAGMA cipher_hmac_algorithm",
                "PRAGMA cipher_page_size",
                "PRAGMA cipher_memory_security",
                "PRAGMA temp_store",
            )
    }
}
