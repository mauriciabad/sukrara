// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.errors

import androidx.annotation.StringRes
import app.mauri.sukrara.core.designsystem.R
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLException

/**
 * Why something failed, in the person's words: the small set every error or status line on screen is worded from.
 * Each [text] is a sentence with its next step where there is one; never an exception name, an HTTP code or an
 * address (those go to the log only).
 */
enum class Trouble(
    @StringRes val text: Int,
) {
    /** The phone has no network at all. */
    NoInternet(R.string.ds_err_no_internet),

    /** The name didn't resolve: a typo in the address, or no internet. */
    ServerNotFound(R.string.ds_err_not_found),

    /** The server is there but nothing answered on that address. */
    CantConnect(R.string.ds_err_cant_connect),

    TimedOut(R.string.ds_err_timed_out),

    /** TLS failed: a wrong address, an expired certificate or the phone's clock. */
    SecureConnection(R.string.ds_err_secure),

    /**
     * The certificate doesn't chain to a public authority (self-signed, a private CA): only system CAs are trusted, never
     * user-installed ones (#121), so the text says why and what a self-hoster can do instead.
     */
    UntrustedCertificate(R.string.ds_err_untrusted_cert),

    /** The service refused the sign-in, token or secret. */
    SignIn(R.string.ds_err_sign_in),

    /** The service failed or is busy (5xx, 408, 429): it is tried again. */
    ServerError(R.string.ds_err_server),

    /** The service answered but didn't accept what was sent. */
    Refused(R.string.ds_err_refused),

    /** The address can't be used (not a web address, or not https where it must be). */
    BadAddress(R.string.ds_err_address),

    Other(R.string.ds_err_other),
}

/** The one mapper from exceptions and HTTP answers to a [Trouble] (CLAUDE.md: plain words, technical detail in logs). */
object UserErrors {
    /** The kind of [e], looking through its causes (OkHttp and coroutines wrap them). */
    fun of(e: Throwable): Trouble {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 8) {
            kind(t)?.let { return it }
            t = t.cause.takeIf { it !== t }
            depth++
        }
        return Trouble.Other
    }

    /**
     * A TLS failure caused by a certificate the phone doesn't trust (Android: `CertPathValidatorException` "Trust anchor
     * for certification path not found"; the JVM: a `CertificateException` over a `CertPathBuilderException`), unless
     * it is only out of date: an expired or not yet valid certificate, or the phone's clock, keeps [Trouble.SecureConnection].
     */
    private fun untrusted(e: SSLException): Boolean {
        val causes = generateSequence(e.cause) { c -> c.cause.takeIf { it !== c } }.take(8).toList()
        val dated =
            causes.any {
                it is CertificateExpiredException ||
                    it is CertificateNotYetValidException ||
                    (it as? CertPathValidatorException)?.reason.let { r ->
                        r == CertPathValidatorException.BasicReason.EXPIRED || r == CertPathValidatorException.BasicReason.NOT_YET_VALID
                    }
            }
        val cert = causes.any { it is CertificateException || it is CertPathValidatorException || it is CertPathBuilderException }
        return cert && !dated
    }

    /** The kind of a non-2xx HTTP status [code]. */
    fun ofHttp(code: Int): Trouble =
        when {
            code == 401 || code == 403 -> Trouble.SignIn
            code == 408 -> Trouble.TimedOut
            code == 429 || code >= 500 -> Trouble.ServerError
            code in 300..399 -> Trouble.BadAddress
            code in 400..499 -> Trouble.Refused
            else -> Trouble.Other
        }

    private fun kind(t: Throwable): Trouble? {
        val m = t.message.orEmpty()
        val offline = "ENETUNREACH" in m || "Network is unreachable" in m
        return when (t) {
            is UnknownHostException -> Trouble.ServerNotFound

            is SSLException -> if (untrusted(t)) Trouble.UntrustedCertificate else Trouble.SecureConnection

            // SocketTimeoutException is one; a call's own timeout is a plain InterruptedIOException
            is InterruptedIOException -> Trouble.TimedOut

            is ConnectException, is NoRouteToHostException -> if (offline) Trouble.NoInternet else Trouble.CantConnect

            is SocketException -> if (offline) Trouble.NoInternet else null

            else -> null
        }
    }
}
