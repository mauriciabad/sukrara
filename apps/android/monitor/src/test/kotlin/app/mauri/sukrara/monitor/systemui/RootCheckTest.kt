// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * #178: root for Sukrara is what `su -c id -u` answers, asked only when [SystemAccess.checkRoot] is called (the
 * person's action or a root feature already on); the signs that need no `su` (a visible `su` file, LSPosed, the
 * module's hello) only say the phone has root. KernelSU / APatch hide `su` from an app they haven't allowed: that is
 * [RootAccess.Missing] with the signs saying root is there, the owner's phone before Sukrara is allowed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootCheckTest {
    private fun started(
        exit: Int?,
        output: String,
    ) = SuResult(exit, output)

    @Test fun `su answering 0 is root, whatever it printed before`() {
        assertEquals(RootAccess.Granted, SystemAccess.rootOf(started(0, "0\n")))
        assertEquals(RootAccess.Granted, SystemAccess.rootOf(started(0, "WARNING: linker: something\n0\n\n")))
    }

    @Test fun `su that can't start is missing, not refused`() {
        val noSu = SuResult(-1, "Cannot run program \"su\": error=2, No such file or directory", started = false)
        assertEquals(RootAccess.Missing, SystemAccess.rootOf(noSu))
        // the emulator's debug su (root:shell 4750): there, but not for apps
        val notExecutable = SuResult(-1, "Cannot run program \"su\": error=13, Permission denied", started = false)
        assertEquals(RootAccess.Missing, SystemAccess.rootOf(notExecutable))
    }

    @Test fun `a refusal, a wrong uid and no answer are told apart`() {
        assertEquals(RootAccess.Refused, SystemAccess.rootOf(started(1, "Permission denied\n")))
        assertEquals(RootAccess.Refused, SystemAccess.rootOf(started(1, "su: uid 10405 not allowed to su\n")))
        assertEquals(RootAccess.Refused, SystemAccess.rootOf(started(0, "10405\n")))
        assertEquals(RootAccess.Refused, SystemAccess.rootOf(started(0, "")))
        assertEquals(RootAccess.NoAnswer, SystemAccess.rootOf(started(null, "")))
    }

    @Test fun `a su file in any of the places root managers use is a sign of root`() {
        assertFalse(SystemAccess.suSeen { false })
        for (path in SystemAccess.SU_PATHS) assertTrue(path, SystemAccess.suSeen { it == path })
        assertTrue(SystemAccess.suSeen { it == "/system/bin/su" })
        // a path the app may not even look at is no sign, never a crash
        assertFalse(SystemAccess.suSeen { throw SecurityException("denied") })
    }

    private class FakeSu(
        @Volatile var answer: SuResult,
    ) : SuShell {
        val commands = mutableListOf<String>()
        var gate: CountDownLatch? = null

        override fun run(
            cmd: String,
            waitS: Long,
        ): SuResult {
            commands += cmd
            gate?.await(5, TimeUnit.SECONDS)
            return answer
        }
    }

    /** What the access logged (logcat on the phone). */
    private val logs = mutableListOf<String>()

    private fun TestScope.access(
        su: FakeSu,
        files: Set<String> = emptySet(),
    ) = SystemAccess(UnconfinedTestDispatcher(testScheduler), backgroundScope, su, exists = { it in files }, log = { logs += it })

    @Test fun `nothing asks su until checkRoot`() =
        runTest {
            val su = FakeSu(started(0, "0\n"))
            val a = access(su)
            a.lookForSu()
            assertEquals(SystemAccessState(), a.state.value)
            assertTrue(su.commands.isEmpty())
            a.checkRoot()
            assertEquals(listOf("id -u"), su.commands)
            assertEquals(RootAccess.Granted, a.state.value.root)
            assertTrue(a.state.value.granted)
        }

    @Test fun `KernelSU before Sukrara is allowed - missing su, root seen`() =
        runTest {
            // the owner's phone: KernelSU shows su only to apps it allows; LSPosed or the module say root is there
            val su = FakeSu(SuResult(-1, "error=2", started = false))
            val a = access(su)
            a.checkRoot()
            assertEquals(RootAccess.Missing, a.state.value.root)
            assertFalse(a.state.value.rootSeen)
            // logcat says why, for the next "it doesn't detect root"
            assertEquals("root: Missing (su error=2; seen file=false lsposed=false hello=false)", logs.last())
            a.onHello("abc")
            assertTrue("the module's hello is root on the phone", a.state.value.rootSeen)
            // allowed in KernelSU, back on the page: asked again, now root
            su.answer = started(0, "0\n")
            a.checkRoot()
            assertEquals(RootAccess.Granted, a.state.value.root)
        }

    @Test fun `a visible su file is root seen, without asking`() =
        runTest {
            val su = FakeSu(started(1, "Permission denied\n"))
            val a = access(su, files = setOf("/system/xbin/su"))
            a.lookForSu()
            assertTrue(a.state.value.rootSeen)
            assertEquals(RootAccess.Unknown, a.state.value.root)
            assertTrue(su.commands.isEmpty())
            a.checkRoot()
            assertEquals(RootAccess.Refused, a.state.value.root)
        }

    @Test fun `while su runs the state says asking and keeps the last answer`() =
        runTest {
            val su = FakeSu(started(0, "0\n"))
            // a real thread: su blocks it while the root manager's prompt may be showing
            val a = SystemAccess(Dispatchers.IO, backgroundScope, su, exists = { false }, log = {})
            a.checkRoot()
            assertEquals(RootAccess.Granted, a.state.value.root)
            // root revoked since: the next request
            su.answer = SuResult(-1, "", started = false)
            val gate = CountDownLatch(1).also { su.gate = it }
            val running = async(Dispatchers.Default) { a.checkRoot() }
            a.state.first { it.asking }
            assertEquals("the running status bar doesn't blink while asking", RootAccess.Granted, a.state.value.root)
            // one request at a time: a second one while it runs asks nothing
            a.checkRoot()
            gate.countDown()
            running.await()
            assertFalse(a.state.value.asking)
            assertEquals(RootAccess.Missing, a.state.value.root)
            assertEquals(listOf("id -u", "id -u"), su.commands)
        }

    @Test fun `a restart that worked proves root, one that couldn't start su says it's missing`() =
        runTest {
            val su = FakeSu(started(0, ""))
            val a = access(su)
            // the restart follows LSPosed for up to 20 s afterwards: only its first answer matters here
            backgroundScope.launch { a.restartSystemUi() }
            runCurrent()
            assertEquals(RootAccess.Granted, a.state.value.root)
            val b = access(FakeSu(SuResult(-1, "error=2", started = false)))
            assertEquals("error=2", b.restartSystemUi())
            assertEquals(RootAccess.Missing, b.state.value.root)
        }
}
