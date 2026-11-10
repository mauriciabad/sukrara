// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.errors.UserErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException

/** Every error a person sees is a sentence: never a class name, an HTTP code or an address (#144). */
@RunWith(RobolectricTestRunner::class)
class UserErrorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val failures: List<Pair<Throwable, Trouble>> =
        listOf(
            UnknownHostException("Unable to resolve host \"ns.example.org\": No address associated with hostname") to
                Trouble.ServerNotFound,
            ConnectException("Failed to connect to ns.example.org/192.168.1.20:1337") to Trouble.CantConnect,
            ConnectException("failed to connect to /10.0.0.2 (port 443) after 10000ms: connect failed: ENETUNREACH") to
                Trouble.NoInternet,
            SocketTimeoutException("timeout") to Trouble.TimedOut,
            SSLHandshakeException("Chain validation failed for ns.example.org") to Trouble.SecureConnection,
            // a self-signed or private-CA site (#121): Android's chain, the JVM's, and wrapped by OkHttp
            handshake(CertificateException(CertPathValidatorException("Trust anchor for certification path not found."))) to
                Trouble.UntrustedCertificate,
            handshake(
                CertificateException("PKIX path building failed", CertPathBuilderException("unable to find valid certification path")),
            ) to
                Trouble.UntrustedCertificate,
            IOException("canceled", handshake(CertPathValidatorException("Trust anchor for certification path not found."))) to
                Trouble.UntrustedCertificate,
            // out of date (or the phone's clock) isn't "untrusted": check the date and time
            handshake(CertificateException(CertPathValidatorException("timestamp check failed", CertificateExpiredException("expired")))) to
                Trouble.SecureConnection,
            handshake(
                CertPathValidatorException("validity check failed", null, null, -1, CertPathValidatorException.BasicReason.EXPIRED),
            ) to Trouble.SecureConnection,
            IOException("unexpected end of stream on http://192.168.1.20:1337/...") to Trouble.Other,
            // OkHttp / coroutines wrap the cause
            IOException("canceled", SocketTimeoutException("timeout")) to Trouble.TimedOut,
            IllegalStateException("boom") to Trouble.Other,
        )

    private fun handshake(cause: Throwable) = SSLHandshakeException(cause.toString()).apply { initCause(cause) }

    private fun words(
        t: Trouble,
        lang: String,
    ): String {
        val cfg = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return context.createConfigurationContext(cfg).getString(t.text)
    }

    @Test fun eachFailureHasItsKind() {
        for ((e, kind) in failures) assertEquals(e.toString(), kind, UserErrors.of(e))
    }

    @Test fun httpAnswersByKind() {
        assertEquals(Trouble.SignIn, UserErrors.ofHttp(401))
        assertEquals(Trouble.SignIn, UserErrors.ofHttp(403))
        assertEquals(Trouble.TimedOut, UserErrors.ofHttp(408))
        assertEquals(Trouble.ServerError, UserErrors.ofHttp(429))
        assertEquals(Trouble.ServerError, UserErrors.ofHttp(500))
        assertEquals(Trouble.ServerError, UserErrors.ofHttp(503))
        assertEquals(Trouble.BadAddress, UserErrors.ofHttp(301))
        assertEquals(Trouble.Refused, UserErrors.ofHttp(400))
    }

    @Test fun noClassNameCodeOrAddressReachesTheText() {
        val outputs =
            failures.map { (e, _) -> UserErrors.of(e) } + UserErrors.ofHttp(500) + Trouble.entries
        for (t in outputs) {
            for (lang in listOf("en", "es")) {
                val s = words(t, lang)
                assertTrue("$t/$lang is empty", s.isNotBlank())
                for (bad in listOf("Exception", "HTTP ", "500", "ns.example.org", "192.168", "ENETUNREACH", "SSL", "timeout")) {
                    assertFalse("$t/$lang: \"$s\" contains $bad", s.contains(bad))
                }
            }
        }
    }

    @Test fun spanishIsTranslated() {
        assertTrue(words(Trouble.Other, "es") != words(Trouble.Other, "en"))
    }
}
