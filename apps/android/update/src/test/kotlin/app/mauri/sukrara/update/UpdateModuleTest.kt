// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.mauri.sukrara.core.data.settings.UpdateSettings
import app.mauri.sukrara.core.model.AlertHold
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateState
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The updater (#188) against a local release server: the daily check and its notification, automatic installs, the
 * checks before an install (a file that isn't the release's, another key: refused, the running version untouched),
 * the wait while an alert is up, Android's answers (asks, failed) and the new version's first start. The workers and
 * the receiver run as Android runs them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateModuleTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = InMemorySettingsRepository()
    private val record = InMemoryStateStore(UpdateRecord())

    // the receiver posts from its own thread (Dispatchers.Default) while the test reads
    private val posted: MutableList<Notification> = CopyOnWriteArrayList()
    private var cancelled = 0
    private var hold = AlertHold()

    private val apk = ByteArray(50_000) { (it * 7 % 256).toByte() }
    private val sha = ApkCheck.sha256(File.createTempFile("apk", "").apply { writeBytes(apk) })
    private var listedSha = sha

    /** What Android reads from the downloaded file (Robolectric parses no APK): Sukrara's, signed with [signer]. */
    private var signer = UpdateEndpoint.RELEASE_CERT

    private lateinit var module: UpdateModule

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/releases" -> {
                            val base = server.url("/")
                            MockResponse().setBody(
                                """
                                [{"tag_name": "v9.0.0", "html_url": "${base}releases/tag/v9.0.0", "prerelease": true,
                                  "assets": [{"name": "Sukrara-9.0.0.apk", "browser_download_url": "${base}download/Sukrara-9.0.0.apk",
                                              "digest": "sha256:$listedSha", "size": ${apk.size}}]},
                                 {"tag_name": "v0.13.0", "html_url": "${base}releases/tag/v0.13.0", "prerelease": true, "assets": []}]
                                """.trimIndent(),
                            )
                        }

                        "/download/Sukrara-9.0.0.apk" -> {
                            MockResponse().setBody(Buffer().write(apk))
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
            }
        server.start()
        module = build()
    }

    private fun build(installed: String = "0.13.0") =
        UpdateModule(
            app,
            UpdatePorts(
                installed = installed,
                installedCode = 1300,
                prerelease = true,
                endpoint = UpdateEndpoint.testServer(server.url("/").toString(), UpdateEndpoint.RELEASE_CERT),
                http = OkHttpClient(),
                io = Dispatchers.IO,
                scope = scope,
                settings = settings,
                record = record,
                unlocked = {},
                hold = { hold },
                notifications =
                    UpdateNotificationPorts(
                        channel = "app.updates",
                        channelGroup = "info",
                        group = "app.mauri.sukrara.INFO",
                        post = { posted += it },
                        cancel = { cancelled++ },
                        openAbout = { Intent("app.mauri.sukrara.action.ABOUT_PAGE").setPackage(app.packageName) },
                    ),
            ),
        ).also { m -> m.facts = { file -> ApkFacts(ApkCheck.sha256(file), app.packageName, 9000, setOf(signer)) } }

    @After fun tearDown() {
        server.shutdown()
        scope.cancel()
        File(app.noBackupFilesDir, "update").deleteRecursively()
    }

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private val Notification.actionTitles get() = actions.orEmpty().map { it.title.toString() }

    private fun installWork(): WorkInfo? =
        WorkManager
            .getInstance(app)
            .getWorkInfosForUniqueWork(UpdateWork.INSTALL)
            .get()
            .firstOrNull()

    @Test fun `the daily check announces a new version once, quietly and outside the alerts`() =
        runBlocking {
            assertTrue(module.dailyCheck())
            assertTrue(module.dailyCheck())
            val n = posted.single()
            assertEquals("Sukrara 9.0.0 is available", n.title)
            assertEquals(listOf("Update", "Release notes"), n.actionTitles)
            assertEquals("app.updates", n.channelId)
            assertEquals("app.mauri.sukrara.INFO", n.group)
            val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel("app.updates")
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertEquals("9.0.0", (module.state.value as UpdateState.Available).release.version)
            assertEquals("9.0.0", record.data.first().notified)
            assertNull(installWork())
        }

    @Test fun `with the daily check off nothing is fetched`() =
        runBlocking {
            settings.set(UpdateSettings.AutomaticChecks, false)
            assertTrue(module.dailyCheck())
            assertEquals(0, server.requestCount)
            assertTrue(posted.isEmpty())
        }

    @Test fun `with automatic installs on the daily check starts an install on an unmetered network`() =
        runBlocking {
            settings.set(UpdateSettings.AutomaticInstall, true)
            assertTrue(module.dailyCheck())
            assertTrue(posted.isEmpty())
            val work = checkNotNull(installWork())
            assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
        }

    @Test fun `About's check says up to date or offline`() =
        runBlocking {
            module = build(installed = "9.0.0")
            module.check()
            val state = module.state.first { it != UpdateState.Checking && it != null }
            assertEquals(UpdateState.UpToDate, state)
            server.shutdown()
            module.check()
            assertEquals(UpdateState.Offline, module.state.first { it != UpdateState.Checking && it != UpdateState.UpToDate })
        }

    @Test fun `About's check posts nothing and keeps a failure of the same release`() =
        runBlocking {
            module.check()
            assertTrue(module.state.first { it is UpdateState.Available } is UpdateState.Available)
            assertTrue(posted.isEmpty())
            signer = "0705C6FA800C15FBD345A08B4AEA76CFF4BD0AC445A14617E16037F26856BE47"
            module.install(automatic = false)
            val failed = module.state.value as UpdateState.Failed
            val requests = server.requestCount
            module.check()
            waitUntil { server.requestCount > requests }
            Thread.sleep(200)
            assertEquals(failed, module.state.value)
        }

    @Test fun `an APK signed with another key is refused and deleted, the running version untouched`() =
        runBlocking {
            signer = "0705C6FA800C15FBD345A08B4AEA76CFF4BD0AC445A14617E16037F26856BE47"
            assertEquals(UpdateModule.Outcome.Done, module.install(automatic = false))
            assertEquals(UpdateState.Failed(module.state.value.release(), UpdateFailure.Signature), module.state.value)
            val n = posted.last()
            assertEquals("The update to 9.0.0 didn't install", n.title)
            assertTrue(n.text, n.text.contains("Sukrara 0.13.0 keeps running; nothing changed"))
            assertEquals(listOf("Try again", "Release notes"), n.actionTitles)
            assertFalse(File(app.noBackupFilesDir, "update/Sukrara-9.0.0.apk").exists())
            assertTrue(
                app.packageManager.packageInstaller.mySessions
                    .isEmpty(),
            )
            assertNull(record.data.first().pending)
        }

    @Test fun `a file that isn't the release's is refused`() =
        runBlocking {
            listedSha = "0".repeat(64)
            module.install(automatic = false)
            assertEquals(UpdateFailure.Hash, (module.state.value as UpdateState.Failed).reason)
            assertTrue(
                app.packageManager.packageInstaller.mySessions
                    .isEmpty(),
            )
        }

    @Test fun `while an alert is up the checked update waits, then installs once it is quiet`() =
        runBlocking {
            hold = AlertHold(showing = setOf("very_low"), veryLow = true)
            assertEquals(UpdateModule.Outcome.Retry, module.install(automatic = true))
            assertTrue(module.state.value is UpdateState.Waiting)
            assertEquals("It installs once no alert is showing.", posted.last().text)
            assertTrue(
                app.packageManager.packageInstaller.mySessions
                    .isEmpty(),
            )
            val downloads = server.requestCount

            hold = AlertHold()
            assertEquals(UpdateModule.Outcome.Done, module.install(automatic = true))
            // the file checked before is used again: only the releases were asked
            assertEquals(downloads + 1, server.requestCount)
            assertTrue(module.state.value is UpdateState.Installing)
            val session =
                app.packageManager.packageInstaller.mySessions
                    .single()
            assertEquals(app.packageName, session.appPackageName)
            assertEquals(
                PendingInstall("9.0.0", server.url("/").toString() + "releases/tag/v9.0.0", true, automatic = true),
                record.data.first().pending,
            )
        }

    @Test fun `when Android asks, the update says so and offers its prompt`() =
        runBlocking {
            module.install(automatic = true)
            val prompt = Intent("android.content.pm.action.CONFIRM_INSTALL")
            receive(PackageInstaller.STATUS_PENDING_USER_ACTION, prompt)
            assertTrue(module.state.value is UpdateState.Confirm)
            val n = posted.last()
            assertEquals("Sukrara 9.0.0 is ready to install", n.title)
            assertEquals("Android asks you to confirm this update. Tap Install.", n.text)
            assertEquals(listOf("Install", "Cancel"), n.actionTitles)
            // About's Install shows the prompt again
            module.confirm()
            assertEquals("android.content.pm.action.CONFIRM_INSTALL", shadowOf(app).nextStartedActivity?.action)
        }

    @Test fun `only Android's own install prompt is ever started`() =
        runBlocking {
            module.install(automatic = false)
            receive(PackageInstaller.STATUS_PENDING_USER_ACTION, Intent(Intent.ACTION_VIEW))
            module.confirm()
            assertNull(shadowOf(app).nextStartedActivity)
        }

    @Test fun `a failed or cancelled install says so and leaves the running version`() =
        runBlocking {
            module.install(automatic = false)
            receive(PackageInstaller.STATUS_FAILURE_ABORTED, null)
            assertEquals(UpdateFailure.Install, (module.state.value as UpdateState.Failed).reason)
            assertEquals("Android didn't install it. Sukrara 0.13.0 keeps running; nothing changed.", posted.last().text)
            assertNull(record.data.first().pending)
        }

    @Test fun `the new version cleans up and, after an automatic update, says it updated`() =
        runBlocking {
            module.install(automatic = true)
            assertTrue(File(app.noBackupFilesDir, "update/Sukrara-9.0.0.apk").exists())
            module = build(installed = "9.0.0")
            UpdateReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
            waitUntil { record.data.first().pending == null }
            assertFalse(File(app.noBackupFilesDir, "update").exists())
            assertEquals("Sukrara updated to 9.0.0", posted.last().title)
            assertEquals(listOf("Release notes"), posted.last().actionTitles)
        }

    @Test fun `Cancel stops the run and offers the release again`() =
        runBlocking {
            module.dailyCheck()
            module.update()
            assertTrue(module.state.value is UpdateState.Downloading)
            assertEquals(WorkInfo.State.ENQUEUED, installWork()?.state)
            UpdateReceiver().onReceive(app, Intent(UpdateReceiver.ACTION_CANCEL))
            assertEquals(WorkInfo.State.CANCELLED, installWork()?.state)
            assertTrue(module.state.value is UpdateState.Available)
        }

    @Test fun `the workers run the daily check and a held install asks again later`() =
        runBlocking {
            val check = TestListenableWorkerBuilder<UpdateCheckWorker>(app).build()
            assertEquals(ListenableWorker.Result.success(), check.doWork())
            assertEquals("Sukrara 9.0.0 is available", posted.last().title)

            hold = AlertHold(showing = setOf("low"))
            val install = TestListenableWorkerBuilder<UpdateInstallWorker>(app).build()
            assertEquals(ListenableWorker.Result.retry(), install.doWork())
        }

    @Test fun `the daily check is scheduled while its switch is on`() =
        runBlocking {
            UpdateWork.schedule(app, on = true)
            val check =
                WorkManager
                    .getInstance(app)
                    .getWorkInfosForUniqueWork(UpdateWork.CHECK)
                    .get()
                    .single()
            assertEquals(NetworkType.CONNECTED, check.constraints.requiredNetworkType)
            assertEquals(WorkInfo.State.ENQUEUED, check.state)
            UpdateWork.schedule(app, on = false)
            assertEquals(
                WorkInfo.State.CANCELLED,
                WorkManager
                    .getInstance(app)
                    .getWorkInfosForUniqueWork(UpdateWork.CHECK)
                    .get()
                    .single()
                    .state,
            )
        }

    private fun receive(
        status: Int,
        prompt: Intent?,
    ) = runBlocking {
        val before = module.state.value
        UpdateReceiver().onReceive(
            app,
            Intent(UpdateReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_STATUS, status)
                .apply { prompt?.let { putExtra(Intent.EXTRA_INTENT, it) } },
        )
        waitUntil { module.state.value != before }
    }

    private suspend fun waitUntil(done: suspend () -> Boolean) {
        val until = System.nanoTime() + 5_000_000_000L
        while (!done() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue("timed out", done())
    }

    private fun UpdateState?.release() =
        when (this) {
            is UpdateState.Failed -> release
            is UpdateState.Available -> release
            else -> error("$this")
        }
}
