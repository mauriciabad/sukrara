// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.files.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant

/** What kind of store a damaged file belongs to: decides what the damage means for the person. */
enum class StoreKind {
    /** A settings store (DataStore preferences). */
    Settings,

    /** The readings / treatments database. */
    Database,

    /** A Keystore-encrypted secret. */
    Secret,

    /** A state or data file (`TypedStore`, [FileStore]). */
    File,
}

/** A stored file that couldn't be read. The damaged bytes are kept at [quarantinedTo] for the report. */
data class CorruptFile(
    val kind: StoreKind,
    /** Which store of [kind]: a file name, a settings store, a secret name. */
    val store: String,
    /** The kept copy of the damaged bytes; null when the damaged file was already gone. */
    val quarantinedTo: File?,
    val at: Instant,
    /** The decoder's error type ([reasonOf]), never its message: messages can quote the stored content. */
    val reason: String,
)

/**
 * The report-safe reason for [error]: the class name of its root cause, never a message (kotlinx serialization and
 * DataStore messages can quote the damaged input, i.e. readings or keys).
 */
fun reasonOf(error: Throwable): String {
    var root = error
    while (true) root = root.cause?.takeIf { it !== root } ?: break
    return root.javaClass.name.substringAfterLast('.')
}

/** A directory [CorruptionLog.rescan] searches for kept copies, and the kind of store kept there. */
data class QuarantineRoot(
    val dir: File,
    val kind: StoreKind,
)

/**
 * Every corrupt file not yet acknowledged. `AppGraph.startupState` reads it and the home screen shows a visible
 * problem with "Copy report" while it is not empty: data loss is never silent. Reports survive the process: the kept
 * copies (`*.corrupt-<epochMs>`) are found again by [rescan] at every start until the person acknowledges them.
 */
class CorruptionLog {
    private val found = MutableStateFlow<List<CorruptFile>>(emptyList())
    val files: StateFlow<List<CorruptFile>> = found.asStateFlow()

    fun report(file: CorruptFile) {
        found.update { current ->
            if (file.quarantinedTo != null && current.any { it.quarantinedTo == file.quarantinedTo }) current else current + file
        }
    }

    /**
     * Reports every kept copy under [roots] (a previous process found them; nobody acknowledged them yet). A copy under
     * several roots belongs to the deepest one (the secrets' dir sits inside the files dir).
     */
    fun rescan(roots: List<QuarantineRoot>) {
        roots
            .filter { it.dir.isDirectory }
            .flatMap { root -> root.dir.walkTopDown().filter { it.isFile && QUARANTINE_MARK in it.name } }
            .distinctBy { it.canonicalPath }
            .forEach { copy ->
                val kind =
                    roots
                        .filter { copy.canonicalPath.startsWith(it.dir.canonicalPath + File.separator) }
                        .maxBy { it.dir.canonicalPath.length }
                        .kind
                val name = copy.name.substringBefore(QUARANTINE_MARK)
                val store =
                    when (kind) {
                        StoreKind.Settings -> name.removeSuffix(".preferences_pb")
                        StoreKind.Secret -> name.removeSuffix(".bin")
                        else -> name
                    }
                val at =
                    copy.name
                        .substringAfter(QUARANTINE_MARK)
                        .toLongOrNull()
                        ?.let(Instant::ofEpochMilli) ?: Instant.EPOCH
                report(CorruptFile(kind, store, copy, at, "found at start"))
            }
    }

    /** The person has seen and copied the report: the copy is renamed `*.seen-<epochMs>` (kept, not re-reported). */
    fun acknowledge(file: CorruptFile) {
        file.quarantinedTo?.let { copy -> copy.renameTo(File(copy.parentFile, copy.name.replace(QUARANTINE_MARK, SEEN_MARK))) }
        found.update { it - file }
    }

    private companion object {
        const val QUARANTINE_MARK = FileStore.QUARANTINE_MARK
        const val SEEN_MARK = ".seen-"
    }
}
