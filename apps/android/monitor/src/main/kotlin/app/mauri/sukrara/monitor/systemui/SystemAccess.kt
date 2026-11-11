// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** What one `su -c` run gave: [started] false = `su` couldn't be started (not there for Sukrara); [exit] null = no answer in time. */
class SuResult(
    val exit: Int?,
    val output: String,
    val started: Boolean = true,
)

/** Runs a command with `su`, waiting up to `waitS` (the root prompt may be showing). Tests pass a fake. */
fun interface SuShell {
    fun run(
        cmd: String,
        waitS: Long,
    ): SuResult
}

/**
 * Root for Sukrara and the Sukrara LSPosed module in System UI, as the status-bar page and the home card need them.
 * Root: `su -c id -u`, only when the person asks for a root feature or one they turned on runs ([checkRoot]); Magisk
 * shows its prompt then, KernelSU and APatch never prompt and hide `su` from apps they haven't allowed. Signs of root
 * that need no `su` ([SystemAccessState.rootSeen]: LSPosed, the module's hello, a visible `su` file) only pick the hint.
 * The module: LSPosed's own report (libxposed service: Sukrara enabled, System UI in its scope) plus the module's hello
 * from inside System UI, which carries the code it runs ([HookCode.VERSION]). LSPosed's report is advisory too.
 */
class SystemAccess(
    private val io: CoroutineDispatcher,
    /** Where [refresh] looks for a `su` file (off the main thread). */
    private val scope: CoroutineScope,
    private val shell: SuShell = SuShell(::su),
    /** Whether a file exists (the `su` paths): tests pass a fake. */
    private val exists: (String) -> Boolean = { File(it).exists() },
    /** One line per root answer for logcat (what the owner's phone said, without the person's data). */
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : SystemAccessPort {
    private val _state = MutableStateFlow(SystemAccessState())
    override val state: StateFlow<SystemAccessState> = _state

    @Volatile private var service: XposedService? = null

    /** The module's hello since this app process started: the code System UI runs (null = no hello yet). */
    @Volatile private var helloCode: String? = null

    /** A `su` file is visible to Sukrara (looked for off the main thread: [checkRoot], [lookForSu]). */
    @Volatile private var suFile = false

    /** One root request at a time: a second one while it runs waits for nothing and changes nothing. */
    private val requesting = AtomicBoolean(false)

    private var listening = false

    /** Starts listening for LSPosed (it binds within a moment of the app starting); none after 3 s = not there. */
    fun start() {
        if (listening) return
        listening = true
        runCatching {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(s: XposedService) {
                        service = s
                        refreshModule()
                    }

                    override fun onServiceDied(s: XposedService) {
                        service = null
                        refreshModule()
                    }
                },
            )
        }.onFailure { Log.w(TAG, "LSPosed listener: ${it.javaClass.simpleName}") }
        Handler(Looper.getMainLooper()).postDelayed({ refreshModule(checked = true) }, 3_000)
        scope.launch { lookForSu() }
    }

    /** System UI's module said hello with the code it runs. */
    fun onHello(code: String?) {
        helloCode = code ?: ""
        refreshModule()
    }

    override suspend fun checkRoot() {
        if (!requesting.compareAndSet(false, true)) return
        try {
            _state.update { it.copy(asking = true) }
            // as long as the restart's: the person may be reading Magisk's prompt
            val (r, file) = withContext(io) { shell.run("id -u", SU_WAIT_S) to suSeen(exists) }
            suFile = file
            val root = rootOf(r)
            // `su` not started: its error (error=2 = not there for Sukrara); else its exit code, never its output
            log(
                "root: $root (su ${if (r.started) {
                    "exit ${r.exit}"
                } else {
                    r.output.take(
                        80,
                    )
                }}; seen file=$file lsposed=${service != null} hello=${helloCode != null})",
            )
            _state.update { it.copy(root = root, rootSeen = seen(), asking = false) }
        } finally {
            // cancelled while asking (the page closed): the last answer stays
            _state.update { if (it.asking) it.copy(asking = false) else it }
            requesting.set(false)
        }
    }

    /** Looks for a `su` file again (back on a page): a sign of root that needs no request. */
    suspend fun lookForSu() {
        suFile = withContext(io) { suSeen(exists) }
        _state.update { it.copy(rootSeen = seen()) }
    }

    private fun seen() = suFile || service != null || helloCode != null

    override suspend fun restartSystemUi(): String? {
        // a kill, not `am crash` (crashes count towards rescue mode); System UI is persistent, Android starts it at once
        val r = withContext(io) { shell.run("kill \$(pidof ${SystemUi.PACKAGE})", SU_WAIT_S) }
        val problem = restartError(r.exit, r.output)
        if (r.exit == 0) _state.update { it.copy(root = RootAccess.Granted) }
        if (!r.started) _state.update { it.copy(root = RootAccess.Missing) }
        if (problem != null) return problem
        helloCode = null
        // it takes a few seconds to start and load the module again: follow it up for 20 s
        repeat(20) {
            delay(1_000)
            refreshModule()
            if (_state.value.module == ModuleState.Active) return null
        }
        return null
    }

    override suspend fun requestScope(): String? {
        val s = service ?: return "LSPosed isn't connected"
        // LSPosed may never answer (prompt ignored, service gone): the page's buttons mustn't stay busy
        return withTimeoutOrNull(SCOPE_WAIT_MS) { scopeRequest(s) } ?: "no answer from LSPosed"
    }

    private suspend fun scopeRequest(s: XposedService): String? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                s.requestScope(
                    listOf(SystemUi.PACKAGE),
                    object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String>) {
                            refreshModule()
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onScopeRequestFailed(message: String) {
                            if (cont.isActive) cont.resume(message)
                        }
                    },
                )
            }.onFailure { if (cont.isActive) cont.resume(it.javaClass.simpleName) }
        }

    override fun refresh() {
        refreshModule()
        scope.launch { lookForSu() }
    }

    private fun refreshModule(checked: Boolean = false) {
        val s = service
        val lsposed =
            s?.let {
                runCatching {
                    Lsposed(
                        framework = "${it.frameworkName} ${it.frameworkVersion}",
                        inScope = SystemUi.PACKAGE in it.scope,
                    )
                }.getOrNull()
            }
        _state.update { old ->
            val module = decide(lsposed, helloCode, HookCode.VERSION, waiting = !checked && old.module == ModuleState.Checking)
            old.copy(module = module, framework = lsposed?.framework, rootSeen = seen())
        }
    }

    /** What LSPosed reports: its name and version, and whether System UI is in the module's scope. */
    data class Lsposed(
        val framework: String,
        val inScope: Boolean,
    )

    companion object {
        private const val TAG = "Sukrara.systemui"
        private const val SU_WAIT_S = 30L
        private const val SCOPE_WAIT_MS = 60_000L
        internal const val TIMED_OUT = "no answer from su"

        /**
         * Where root managers put `su` for the apps that may see it: Magisk's (unless Sukrara is on its DenyList), the
         * emulator's debug `su`, KernelSU's and APatch's only for apps they allow. Looking needs no request.
         */
        val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/su/bin/su")

        fun suSeen(exists: (String) -> Boolean): Boolean = SU_PATHS.any { runCatching { exists(it) }.getOrDefault(false) }

        /**
         * What a `su -c id -u` run means: root when it answered 0 as its last line; [RootAccess.Missing] when `su`
         * couldn't even start (KernelSU / APatch before Sukrara is allowed, or no root); no answer in time; else refused
         * (Magisk's Deny prints *Permission denied*, the emulator's `su` *not allowed*).
         */
        fun rootOf(r: SuResult): RootAccess =
            when {
                !r.started -> {
                    RootAccess.Missing
                }

                r.exit == null -> {
                    RootAccess.NoAnswer
                }

                r.exit == 0 && r.output
                    .lines()
                    .lastOrNull { it.isNotBlank() }
                    ?.trim() == "0" -> {
                    RootAccess.Granted
                }

                else -> {
                    RootAccess.Refused
                }
            }

        /**
         * The module's state: the hello is the truth (System UI runs the module, with this code or another); without
         * one, LSPosed tells why: not there ([ModuleState.Missing]), System UI not in scope, or in scope but not loaded
         * yet ([ModuleState.Restart]). [waiting] = still within LSPosed's first seconds.
         */
        fun decide(
            lsposed: Lsposed?,
            hello: String?,
            mine: String,
            waiting: Boolean = false,
        ): ModuleState =
            when {
                hello != null && hello == mine -> ModuleState.Active
                hello != null -> ModuleState.Update
                lsposed == null -> if (waiting) ModuleState.Checking else ModuleState.Missing
                !lsposed.inScope -> ModuleState.NoScope
                else -> ModuleState.Restart
            }

        /** What a `su -c kill …` run means: null = System UI was killed; else a short reason (exit null = no answer). */
        fun restartError(
            exit: Int?,
            output: String,
        ): String? =
            when (exit) {
                null -> {
                    TIMED_OUT
                }

                0 -> {
                    null
                }

                else -> {
                    output
                        .lines()
                        .map { it.trim() }
                        .lastOrNull { it.isNotEmpty() }
                        ?.take(120) ?: "su exit $exit"
                }
            }
    }
}

/** Runs [cmd] with the real `su`, waiting up to [waitS]; not started = `su` can't be run by Sukrara (not there, not allowed). */
private fun su(
    cmd: String,
    waitS: Long,
): SuResult {
    val p =
        try {
            ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return SuResult(-1, e.message ?: "su not found", started = false)
        }
    return runCatching {
        if (!p.waitFor(waitS, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            SuResult(null, "")
        } else {
            SuResult(p.exitValue(), p.inputStream.bufferedReader().readText())
        }
    }.getOrElse {
        p.destroyForcibly()
        SuResult(-1, it.message ?: it.javaClass.simpleName)
    }
}
