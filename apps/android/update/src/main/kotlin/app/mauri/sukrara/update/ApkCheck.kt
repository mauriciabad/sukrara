// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/**
 * What a downloaded APK is: its [sha256] (lower-case hex), and what Android reads from the archive (null when it can't
 * parse it): [packageName], [versionCode] and the SHA-256 of each certificate it is signed with now ([signers],
 * upper-case hex).
 */
data class ApkFacts(
    val sha256: String,
    val packageName: String?,
    val versionCode: Long,
    val signers: Set<String>,
)

/** What the APK must be: [sha256] as GitHub lists it, this app's [packageName], newer than [installedCode], [cert]. */
data class ApkExpected(
    val sha256: String,
    val packageName: String,
    val installedCode: Long,
    val cert: String,
)

enum class ApkVerdict {
    Ok,

    /** Not the file the release lists. */
    WrongHash,

    /** Not signed by Sukrara's release key alone (another key, several signers, or none Android could read). */
    WrongSigner,

    /** Not Sukrara, or not newer than the version running. */
    NotAnUpdate,
}

/**
 * The checks before an install (#188). The hash proves the download is the release's file; the certificate proves it
 * is Sukrara's (Android checks it too, but an install that fails there would only say so after the prompt); the
 * package and version prove it is an update of this app.
 */
object ApkCheck {
    fun verify(
        facts: ApkFacts,
        expected: ApkExpected,
    ): ApkVerdict =
        when {
            !facts.sha256.equals(expected.sha256, ignoreCase = true) -> ApkVerdict.WrongHash
            facts.signers != setOf(expected.cert.uppercase()) -> ApkVerdict.WrongSigner
            facts.packageName != expected.packageName || facts.versionCode <= expected.installedCode -> ApkVerdict.NotAnUpdate
            else -> ApkVerdict.Ok
        }

    /** The SHA-256 of [file], lower-case hex. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().hex()
    }

    /** [file]'s facts as Android reads the archive (PackageManager's archive info, its current signers). */
    fun facts(
        pm: PackageManager,
        file: File,
    ): ApkFacts {
        val info =
            runCatching {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            }.getOrNull()
        // the current signers only (a rotated key's past certificates don't count); several signers never equal one cert
        val signers =
            info
                ?.signingInfo
                ?.apkContentsSigners
                .orEmpty()
                .map { certSha256(it.toByteArray()) }
                .toSet()
        return ApkFacts(sha256(file), info?.packageName, info?.longVersionCode ?: 0, signers)
    }

    /** A certificate's SHA-256 as apksigner prints it (upper-case hex): the form of android-cert.sha256. */
    fun certSha256(der: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(der)
            .hex()
            .uppercase()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
