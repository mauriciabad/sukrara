// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import app.mauri.sukrara.core.data.settings.UpdateSettings
import app.mauri.sukrara.core.model.AlertHold
import app.mauri.sukrara.core.model.AppUpdates
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateRelease
import app.mauri.sukrara.core.model.UpdateState
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.io.File

/** What the updater keeps across a restart (typed store `update`). */
@Serializable
data class UpdateRecord(
    /** The newest version the daily check told the person about: each version is announced once. */
    val notified: String = "",
    /** An install handed to Android and not seen finished: what it was (the status and the new version read it). */
    val pending: PendingInstall? = null,
)

@Serializable
data class PendingInstall(
    val version: String,
    val notesUrl: String,
    val prerelease: Boolean,
    val automatic: Boolean,
) {
    fun model() = UpdateRelease(version, notesUrl, prerelease, installable = true)
}

/** The updater's inputs (`Store` of the full flavor builds them from the app graph). */
class UpdatePorts(
    /** The running version's name ("0.13.0") and code. */
    val installed: String,
    val installedCode: Long,
    /** A 0.x version: release candidates are offered (every 0.x release is one). */
    val prerelease: Boolean,
    val endpoint: UpdateEndpoint,
    val http: OkHttpClient,
    val io: CoroutineDispatcher,
    /** The process scope. */
    val scope: CoroutineScope,
    /** This phone's own settings (`update.*`; credential-encrypted: read only after [unlocked]). */
    val settings: SettingsRepository,
    val record: StateStore<UpdateRecord>,
    /** Returns once the phone was unlocked since it started. */
    val unlocked: suspend () -> Unit,
    /** What an install must wait for now (the alert runtime's [AlertHold]). */
    val hold: suspend () -> AlertHold,
    val notifications: UpdateNotificationPorts,
    /** The diagnostic log: what failed, never data. */
    val log: (String) -> Unit = {},
    /** Run the daily check once at start too: only a debug build's local test server (the `UPDATES` command). */
    val checkAtStart: Boolean = false,
)

/**
 * The GitHub version's updater (#188; docs/FEATURES.md › Updates (Android, GitHub version)). A daily check
 * ([UpdateCheckWorker], while *Check for updates automatically* is on) and About's check find a newer release; the
 * notification and About offer *Update*, which downloads the APK ([UpdateInstallWorker]), checks its SHA-256 and its
 * signer ([ApkCheck]), waits while an alert is up or a very low is on ([InstallGate]) and hands it to Android
 * ([Installer]). With *Download and install updates automatically* on, the daily check does that by itself. The new
 * version starts monitoring again on `MY_PACKAGE_REPLACED` (`:monitor` BootReceiver) and clears what is left
 * ([onReplaced]). One instance per process ([current]): the app builds it at start.
 */
class UpdateModule(
    context: Context,
    private val ports: UpdatePorts,
) : AppUpdates {
    private val ctx = context.applicationContext

    /** What Android reads from a downloaded APK ([ApkCheck.facts]; tests hand in an archive's facts). */
    internal var facts: (File) -> ApkFacts = { ApkCheck.facts(ctx.packageManager, it) }

    private val downloader = Downloader(ports.http, ports.endpoint, ports.io)
    private val installer = Installer(ctx)
    val notifier = UpdateNotifier(ctx, ports.notifications, ports.installed)

    private val _state = MutableStateFlow<UpdateState?>(null)
    override val state: StateFlow<UpdateState?> = _state.asStateFlow()

    /** The newest release a check found (About's *Update* needs no second check). */
    @Volatile private var latest: Release? = null

    /** Android's install prompt for the session waiting on the person ([UpdateState.Confirm]). */
    @Volatile private var confirmIntent: Intent? = null

    init {
        current = this
    }

    /** At app start: once the phone is unlocked, the daily check follows its switch. */
    fun start() {
        ports.scope.launch {
            ports.unlocked()
            if (ports.checkAtStart) runCatching { UpdateWork.checkNow(ctx) }
            ports.settings
                .flow(UpdateSettings.AutomaticChecks)
                .distinctUntilChanged()
                .collect { on ->
                    runCatching { UpdateWork.schedule(ctx, on) }.onFailure { ports.log("schedule: ${it.javaClass.simpleName}") }
                }
        }
    }

    /**
     * About's check: the page shows the answer, the notification isn't posted again (a release is announced once).
     * A failed update of the same release stays failed (*Try again* and why); nothing newer means the notification goes.
     */
    override fun check() {
        if (busy()) return
        val failed = state.value as? UpdateState.Failed
        if (failed == null) _state.value = UpdateState.Checking
        ports.scope.launch {
            val next = stateOf(fetch())
            _state.value =
                if (failed != null && (next as? UpdateState.Available)?.release?.version == failed.release.version) failed else next
            if (next == UpdateState.UpToDate) notifier.cancel()
        }
    }

    override fun update() {
        val release = (state.value as? UpdateState.Available)?.release ?: (state.value as? UpdateState.Failed)?.release
        if (release != null && !release.installable) return openPage(release.notesUrl)
        release?.let { show(UpdateState.Downloading(it, null)) }
        UpdateWork.install(ctx, automatic = false)
    }

    override fun cancel() {
        UpdateWork.cancelInstall(ctx)
        confirmIntent = null
        val installer = ctx.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        ports.scope.launch { ports.record.update { it.copy(pending = null) } }
        _state.value = latest?.let { UpdateState.Available(it.model()) }
        notifier.cancel()
    }

    override fun confirm() {
        val intent = confirmIntent ?: return update()
        runCatching { ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { ports.log("confirm: ${it.javaClass.simpleName}") }
    }

    /**
     * The daily check ([UpdateCheckWorker]): a newer release is announced once per version, or, with automatic
     * updates on, installed. False = the check failed (WorkManager tries again).
     */
    suspend fun dailyCheck(): Boolean {
        if (!ports.settings.flow(UpdateSettings.AutomaticChecks).first()) return true
        val result = fetch()
        if (!busy()) _state.value = stateOf(result)
        val release = result.getOrElse { return false } ?: return true
        if (release.apk != null && ports.settings.flow(UpdateSettings.AutomaticInstall).first()) {
            if (!busy()) UpdateWork.install(ctx, automatic = true)
            return true
        }
        if (ports.record.data
                .first()
                .notified != release.version
        ) {
            if (!busy()) notifier.show(UpdateState.Available(release.model()))
            ports.record.update { it.copy(notified = release.version) }
        }
        return true
    }

    enum class Outcome { Done, Retry }

    /**
     * One run of [UpdateInstallWorker]: the newest release is downloaded (or the file already checked is reused),
     * checked, and installed once no alert holds it ([Outcome.Retry]: WorkManager asks again in a while). Every
     * failure is said (state and notification) and leaves the running version as it is.
     */
    suspend fun install(automatic: Boolean): Outcome {
        val release =
            fetch().getOrElse {
                val known = latest ?: return if (automatic) Outcome.Retry else Outcome.Done.also { show(UpdateState.Offline) }
                return fail(known.model(), UpdateFailure.Download)
            } ?: return Outcome.Done.also { show(UpdateState.UpToDate) }
        val model = release.model()
        val apk = release.apk ?: return Outcome.Done.also { show(UpdateState.Available(model)) }
        val file = File(dir(), "Sukrara-${release.version}.apk")
        if (!(file.exists() && file.length() == apk.size && ApkCheck.sha256(file) == apk.sha256)) {
            show(UpdateState.Downloading(model, 0f))
            var shown = 0
            try {
                downloader.download(apk, file) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != shown) {
                        shown = percent
                        show(UpdateState.Downloading(model, p))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ports.log("download: ${e.javaClass.simpleName}")
                file.delete()
                return fail(model, UpdateFailure.Download)
            }
        }
        val verdict =
            ApkCheck.verify(
                facts(file),
                ApkExpected(apk.sha256, ctx.packageName, ports.installedCode, ports.endpoint.cert),
            )
        if (verdict != ApkVerdict.Ok) {
            ports.log("check: $verdict")
            file.delete()
            return fail(model, if (verdict == ApkVerdict.WrongHash) UpdateFailure.Hash else UpdateFailure.Signature)
        }
        val hold = ports.hold()
        if (!InstallGate.mayInstall(hold)) {
            show(UpdateState.Waiting(model, hold))
            return Outcome.Retry
        }
        ports.record.update { it.copy(pending = PendingInstall(release.version, release.url, release.prerelease, automatic)) }
        show(UpdateState.Installing(model))
        return try {
            installer.install(file, release.version, InstallGate.silent(automatic, installer.mayInstallPackages()))
            Outcome.Done
        } catch (e: Exception) {
            ports.log("install: ${e.javaClass.simpleName}")
            ports.record.update { it.copy(pending = null) }
            fail(model, UpdateFailure.Install)
        }
    }

    /** The install session's status ([UpdateReceiver]): Android asks, it worked, or it failed. */
    suspend fun onStatus(
        status: Int,
        prompt: Intent?,
    ) {
        val pending =
            ports.record.data
                .first()
                .pending
        val release = pending?.model() ?: busyRelease() ?: return
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // only Android's own install prompt is ever started from here
                val confirm = prompt?.takeIf { it.action == CONFIRM_INSTALL }
                confirmIntent = confirm
                _state.value = UpdateState.Confirm(release)
                notifier.show(UpdateState.Confirm(release), automatic = pending?.automatic == true, confirm = confirm)
                if (confirm != null && inFront()) confirm()
            }

            PackageInstaller.STATUS_SUCCESS -> {
                ports.record.update { it.copy(pending = null) }
                show(UpdateState.UpToDate)
            }

            else -> {
                ports.log("install status $status")
                confirmIntent = null
                ports.record.update { it.copy(pending = null) }
                fail(release, UpdateFailure.Install)
            }
        }
    }

    /**
     * The new version's first broadcast (`MY_PACKAGE_REPLACED`; `:monitor` starts monitoring from the same one): the
     * download goes, the notification too; after an automatic update it says which version is running now.
     */
    suspend fun onReplaced() {
        dir().deleteRecursively()
        notifier.cancel()
        val pending =
            ports.record.data
                .first()
                .pending ?: return
        ports.record.update { it.copy(pending = null) }
        if (pending.automatic && pending.version == ports.installed) notifier.updated(pending.model())
    }

    private suspend fun fetch(): Result<Release?> =
        downloader.releases().map { Releases.newer(it, ports.installed, ports.prerelease).also { r -> latest = r } }

    private fun stateOf(result: Result<Release?>): UpdateState =
        result.fold({ it?.let { r -> UpdateState.Available(r.model()) } ?: UpdateState.UpToDate }, { UpdateState.Offline })

    private fun show(state: UpdateState) {
        _state.value = state
        notifier.show(state)
    }

    private fun fail(
        release: UpdateRelease,
        reason: UpdateFailure,
    ): Outcome {
        show(UpdateState.Failed(release, reason))
        return Outcome.Done
    }

    private fun busy() =
        when (state.value) {
            is UpdateState.Downloading, is UpdateState.Waiting, is UpdateState.Confirm, is UpdateState.Installing -> true
            else -> false
        }

    private fun busyRelease(): UpdateRelease? =
        when (val s = state.value) {
            is UpdateState.Downloading -> s.release
            is UpdateState.Waiting -> s.release
            is UpdateState.Confirm -> s.release
            is UpdateState.Installing -> s.release
            else -> null
        }

    private fun openPage(url: String) {
        if (!ports.endpoint.isReleasePage(url) && url != ports.endpoint.releasesPage) return
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** A screen of Sukrara is in front (Android lets it start the install prompt). */
    private fun inFront(): Boolean =
        ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance ==
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    /** The downloaded APKs: app-private, never backed up; gone after the update. */
    private fun dir() = File(ctx.noBackupFilesDir, "update")

    companion object {
        /** Android's install prompt (`PackageInstaller.ACTION_CONFIRM_INSTALL`, a system API: not in the public SDK). */
        private const val CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"

        /**
         * This process's updater, for its worker and receiver (the app builds it in `Application.onCreate`). It keeps
         * only the application context, which lives as long as the process.
         */
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var current: UpdateModule? = null
            private set
    }
}
