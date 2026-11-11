// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import app.mauri.sukrara.core.model.AlertHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The checks before an install and the rule for when it may happen (#188). */
class ApkCheckTest {
    private val cert = UpdateEndpoint.RELEASE_CERT
    private val hash = "cecb94ab15ba22a86503602bad0a6175eb9013b6a0803e8773fc5163edcb3c6d"
    private val expected = ApkExpected(hash, "app.mauri.sukrara", installedCode = 1300, cert = cert)
    private val good = ApkFacts(hash, "app.mauri.sukrara", versionCode = 1400, signers = setOf(cert))

    @Test
    fun `the release's file signed by Sukrara's key and newer is installed`() {
        assertEquals(ApkVerdict.Ok, ApkCheck.verify(good, expected))
        // GitHub lists the digest in lower case; either case is the same file
        assertEquals(ApkVerdict.Ok, ApkCheck.verify(good.copy(sha256 = hash.uppercase()), expected))
    }

    @Test
    fun `another file is refused before anything else`() {
        assertEquals(ApkVerdict.WrongHash, ApkCheck.verify(good.copy(sha256 = "0".repeat(64)), expected))
        assertEquals(ApkVerdict.WrongHash, ApkCheck.verify(good.copy(sha256 = "0".repeat(64), signers = emptySet()), expected))
    }

    @Test
    fun `another key, several keys or none are refused`() {
        val debug = "0705C6FA800C15FBD345A08B4AEA76CFF4BD0AC445A14617E16037F26856BE47"
        assertEquals(ApkVerdict.WrongSigner, ApkCheck.verify(good.copy(signers = setOf(debug)), expected))
        assertEquals(ApkVerdict.WrongSigner, ApkCheck.verify(good.copy(signers = setOf(cert, debug)), expected))
        assertEquals(ApkVerdict.WrongSigner, ApkCheck.verify(good.copy(signers = emptySet()), expected))
    }

    @Test
    fun `another app or no newer version is not an update`() {
        assertEquals(ApkVerdict.NotAnUpdate, ApkCheck.verify(good.copy(packageName = "app.evil"), expected))
        assertEquals(ApkVerdict.NotAnUpdate, ApkCheck.verify(good.copy(packageName = null), expected))
        assertEquals(ApkVerdict.NotAnUpdate, ApkCheck.verify(good.copy(versionCode = 1300), expected))
        assertEquals(ApkVerdict.NotAnUpdate, ApkCheck.verify(good.copy(versionCode = 1200), expected))
    }

    @Test
    fun `hashes are lower-case hex, certificates upper-case as apksigner prints them`() {
        val file = File.createTempFile("apk", ".bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApkCheck.sha256(file))
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", ApkCheck.certSha256("abc".toByteArray()))
        file.delete()
    }

    @Test
    fun `an update never installs while an alert is up or a very low is on`() {
        assertTrue(InstallGate.mayInstall(AlertHold()))
        assertFalse(InstallGate.mayInstall(AlertHold(showing = setOf("high"))))
        assertFalse(InstallGate.mayInstall(AlertHold(showing = setOf("no_data"))))
        // a very low that was answered (muted, carbs logged) still holds it
        assertFalse(InstallGate.mayInstall(AlertHold(veryLow = true)))
    }

    @Test
    fun `Android is asked not to prompt only for an automatic update with installs allowed`() {
        assertTrue(InstallGate.silent(automatic = true, mayInstallPackages = true))
        assertFalse(InstallGate.silent(automatic = true, mayInstallPackages = false))
        assertFalse(InstallGate.silent(automatic = false, mayInstallPackages = true))
    }

    @Test
    fun `the built-in release certificate is tools-release's`() {
        val file = File("../../../tools/release/android-cert.sha256")
        assertEquals(file.readText().trim(), UpdateEndpoint.RELEASE_CERT)
        assertEquals(UpdateEndpoint.RELEASE_CERT, UpdateEndpoint.GitHub.cert)
        assertTrue(UpdateEndpoint.GitHub.httpsOnly)
    }

    @Test
    fun `a debug build's test server is plain http under its base, trusting its own key`() {
        val local = UpdateEndpoint.testServer("http://10.0.2.2:8765/", "abcd")
        assertEquals("http://10.0.2.2:8765/releases", local.api)
        assertEquals("ABCD", local.cert)
        assertFalse(local.httpsOnly)
        assertTrue(local.isDownload("http://10.0.2.2:8765/download/Sukrara-9.0.0.apk"))
        assertFalse(local.isDownload("https://github.com/mauriciabad/sukrara/releases/download/v9.0.0/Sukrara-9.0.0.apk"))
    }
}
