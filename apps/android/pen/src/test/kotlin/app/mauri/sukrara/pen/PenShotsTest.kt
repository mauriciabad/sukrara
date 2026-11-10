// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.PenCandidate
import app.mauri.sukrara.core.model.PenCheck
import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenHeldDose
import app.mauri.sukrara.core.model.PenInfo
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.PenReview
import app.mauri.sukrara.core.model.PenSessionInfo
import app.mauri.sukrara.core.model.PenSessionStatus
import app.mauri.sukrara.core.model.PenTaps
import app.mauri.sukrara.core.model.PenTypedBy
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.pen.PenEntrySheet
import app.mauri.sukrara.designsystem.pen.PenQuestionCard
import app.mauri.sukrara.designsystem.pen.PenSameSheet
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.HomeCallbacks
import app.mauri.sukrara.feature.home.HomeContent
import app.mauri.sukrara.feature.home.HomeUiState
import app.mauri.sukrara.feature.home.NoDataReason
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.feature.home.homeLook
import app.mauri.sukrara.feature.settings.pens.DosesReadPage
import app.mauri.sukrara.feature.settings.pens.PenPage
import app.mauri.sukrara.feature.settings.pens.PenViewModel
import app.mauri.sukrara.feature.settings.pens.PensContent
import app.mauri.sukrara.feature.settings.pens.PensUi
import app.mauri.sukrara.feature.settings.pens.PensViewModel
import app.mauri.sukrara.graph.Clock
import app.mauri.sukrara.pen.engine.Ledger
import app.mauri.sukrara.pen.engine.Pen
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Goldens of the pen screens (synthetic pens only): Settings › Insulin pens (empty, pens, taps blocked, NFC off,
 * notification blocked), a pen's page (tested, the untested pen's check, not usable, a new pen's last injection),
 * *Doses read* and its *Review*, the entry sheet and the *Same injection?* sheet, home's question card and *Pen taps
 * are blocked* banner, and the read card's *Untested pen* / *Pen not usable*; light + dark × default + largest font.
 * The Goldens workflow (CI, Linux) records src/test/snapshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PenShotsTest {
    @get:Rule val compose = createComposeRule()

    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()

    // a fixed now (the pages take it from their view model): the goldens never move with the day they are recorded on
    private val now = Instant.parse("2026-10-27T14:00:00Z").epochSecond
    private val ago = { min: Long -> now - min * 60 }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private val rapid =
        PenInfo(
            serial = "AB1234567",
            name = "NovoPen Echo Plus",
            model = "NovoPen Echo Plus",
            kind = "rapid",
            since = ago(9000),
            first = ago(9000),
            lastScan = ago(14),
            older = 412,
            returningSince = null,
            untested = false,
            check = PenCheck.Ok,
            opened = true,
            held = null,
            review = PenReview(at = ago(30), since = ago(5000), entries = 3, merged = 1, keys = setOf("k1", "k2")),
        )
    private val untested =
        rapid.copy(
            serial = "NP6000123",
            name = "NovoPen 6",
            model = "NovoPen 6",
            kind = "long",
            untested = true,
            check = PenCheck.None,
            opened = false,
            held = PenHeldDose(16.0, ago(120), ago(1)),
            review = null,
        )
    private val notUsable = untested.copy(serial = "NP6000999", check = PenCheck.Failed, held = null, name = "NovoPen 6 ·999")

    private fun dose(
        key: String,
        min: Long,
        units: Double,
        primed: Boolean = false,
    ) = PenDoseInfo(key, ago(min), units, primed, incomplete = false)

    private fun session(
        min: Long,
        units: Double,
        status: PenSessionStatus,
        primed: Double = 0.0,
        question: String? = null,
        same: Boolean = false,
    ) = PenSessionInfo(
        serial = rapid.serial,
        t = ago(min),
        units = units,
        primed = primed,
        status = status,
        entry = if (status == PenSessionStatus.Logged || status == PenSessionStatus.Merged) "e$min" else null,
        question = question,
        sameQuestion = same,
        candidates = if (same) 2 else 0,
        mergedFrom = if (status == PenSessionStatus.Merged) 8.0 else null,
        incomplete = false,
        doses = listOfNotNull(dose("k$min-p", min + 1, primed, primed = true).takeIf { primed > 0 }, dose("k$min", min, units)),
    )

    private val sessions =
        listOf(
            session(14, 8.5, PenSessionStatus.Logged, primed = 1.5),
            session(40, 1.5, PenSessionStatus.Waiting, question = "q1"),
            session(200, 6.0, PenSessionStatus.Merged).copy(doses = listOf(dose("k1", 200, 6.0))),
            session(400, 0.0, PenSessionStatus.Priming).copy(primed = 1.0, doses = listOf(dose("k400", 400, 1.0, primed = true))),
            session(500, 4.0, PenSessionStatus.NotLogged).copy(doses = listOf(dose("k2", 500, 4.0))),
            session(9100, 7.0, PenSessionStatus.Before),
        )

    private val same =
        PenQuestionInfo(
            id = "q2",
            serial = rapid.serial,
            same = true,
            keys = listOf("k60"),
            doses = listOf(dose("k60", 60, 8.5)),
            units = 8.5,
            t = ago(60),
            candidates =
                listOf(
                    PenCandidate("t1", 8.0, ago(55), PenTypedBy.Phone),
                    PenCandidate("t2", 9.0, ago(75), PenTypedBy.Follower),
                ),
        )
    private val doses =
        PenQuestionInfo(
            id = "q1",
            serial = rapid.serial,
            same = false,
            keys = listOf("k40-a", "k40"),
            doses = listOf(dose("k40-a", 41, 1.0, primed = true), dose("k40", 40, 1.5)),
            units = 1.5,
            t = ago(40),
        )

    private val entry =
        PenEntryInfo(
            id = "e14",
            serial = rapid.serial,
            kind = "rapid",
            units = 8.5,
            primed = 1.5,
            t = ago(14),
            note = "Lunch",
            doses = listOf(dose("0", 16, 1.0, primed = true), dose("1", 15, 0.5, primed = true), dose("2", 14, 8.5)),
            replaced = 8.0 to ago(20),
        )

    private fun port(
        pens: List<PenInfo> = listOf(rapid),
        taps: PenTaps = PenTaps(blocked = false, nfcOff = false, notifyBlocked = false),
    ) = FakePensPort(pens, sessions, listOf(doses, same), taps, mapOf(entry.id to entry))

    private val rules = PensModule.Detection.map { it.default }

    private var current by mutableStateOf<Pair<String, @Composable () -> Unit>?>(null)
    private var dark by mutableStateOf(false)
    private var scale by mutableFloatStateOf(1f)
    private var started = false

    /** [content] in light and dark, at the default and the largest font (each from scratch: its own remembered state). */
    private fun scene(
        name: String,
        screen: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (!started) {
            started = true
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    SukraraTheme(dark = dark, dynamic = false) {
                        current?.let { (n, c) -> key(n, dark, scale) { c() } }
                    }
                }
            }
        }
        for (d in listOf(false, true)) {
            for (f in listOf(1f, 2f)) {
                dark = d
                scale = f
                current = name to content
                compose.waitForIdle()
                val file = "src/test/snapshots/pen-$name-${if (d) "dark" else "light"}${if (f > 1f) "-largest" else ""}.png"
                if (screen) captureScreenRoboImage(file) else compose.onRoot().captureRoboImage(file)
            }
        }
        current = null
        compose.waitForIdle()
    }

    private fun pens(
        ui: PensUi,
        port: FakePensPort,
    ) = scene("settings-${ui.tag}") {
        PensContent(ui, remember { PensViewModel(InMemorySettingsRepository(), port) }, onBack = {})
    }

    private val PensUi.tag get() =
        when {
            taps.blocked -> "taps-blocked"
            taps.nfcOff -> "nfc-off"
            taps.notifyBlocked -> "notify-blocked"
            pens.isEmpty() -> "empty"
            else -> "pens"
        }

    @Test fun settingsInsulinPens() {
        pens(PensUi("ask", notify = false, rules = rules), port(emptyList()))
        pens(
            PensUi("on", notify = false, rules = rules, pens = listOf(rapid, untested, notUsable)),
            port(listOf(rapid, untested, notUsable)),
        )
        val blocked = PenTaps(blocked = true, nfcOff = false, notifyBlocked = false)
        pens(PensUi("on", notify = false, rules = rules, pens = listOf(rapid), taps = blocked), port(taps = blocked))
        val off = PenTaps(blocked = false, nfcOff = true, notifyBlocked = false)
        pens(PensUi("on", notify = false, rules = rules, pens = listOf(rapid), taps = off), port(taps = off))
        val muted = PenTaps(blocked = false, nfcOff = false, notifyBlocked = true)
        pens(PensUi("on", notify = true, rules = rules, pens = listOf(rapid), taps = muted), port(taps = muted))
    }

    private fun page(
        name: String,
        pen: PenInfo,
        sessions: List<PenSessionInfo> = this.sessions,
    ) = scene("page-$name") {
        val port = FakePensPort(listOf(pen), sessions.map { it.copy(serial = pen.serial) })
        PenPage(remember { PenViewModel(port, pen.serial, InMemorySettingsRepository()) { now } }, onBack = {}, openDoses = {})
    }

    @Test fun aPensPage() {
        page("tested", rapid)
        page("check", untested)
        page("not-usable", notUsable)
        // a new pen: its newest injection before Sukrara, under the offer window, can be logged
        page("new", rapid.copy(review = null, lastScan = ago(1)), listOf(session(50, 8.5, PenSessionStatus.Before)))
    }

    @Test fun dosesRead() {
        scene(
            "doses-read",
        ) { DosesReadPage(remember { PenViewModel(port(), rapid.serial, InMemorySettingsRepository()) { now } }, onBack = {}) }
        scene("doses-review") {
            DosesReadPage(remember { PenViewModel(port(), rapid.serial, InMemorySettingsRepository()) { now } }, onBack = {}, review = true)
        }
    }

    @Test fun sheets() {
        scene("sheet-entry", screen = true) { PenEntrySheet(entry, { Clock.time(it) }, onSave = {}, onDismiss = {}, onDelete = {}) }
        scene("sheet-same", screen = true) { PenSameSheet(same, { Clock.time(it) }, onAnswer = {}, onDismiss = {}) }
    }

    private fun home(
        questions: List<PenQuestionInfo> = emptyList(),
        taps: BlockedTaps = BlockedTaps(),
    ): HomeUiState {
        val at = Instant.ofEpochSecond(now)
        val readings = (36 downTo 0).map { i -> Reading(at.minusSeconds(i * 300L), 120 + (i % 7) * 3, 0.2, SourceId("synthetic")) }
        val g =
            GlucoseState.Initial.copy(
                role = Role.Wearer,
                latest = readings.last(),
                recent = readings,
                sensor = SensorState.Active(at.plusSeconds(6 * 86400L)),
            )
        val look = homeLook(g, at, GlucoseUnit.MGDL)
        return HomeUiState(
            glucose = g,
            now = at,
            look = look,
            reason = NoDataReason.of(g, look),
            banner = if (taps.any) BannerKind.TapsBlocked else null,
            firstProblem = null,
            nextSetupStep = null,
            dataProblems = emptyList(),
            treatments = emptyList(),
            undo = null,
            blockedTaps = taps,
        )
    }

    @Test fun homeQuestionAndBlockedTaps() {
        scene("home-question") { HomeContent(home(), HomeCallbacks(), penQuestions = listOf(doses, same)) }
        scene("home-question-same") {
            Box(Modifier.padding(16.dp)) { PenQuestionCard(listOf(same), { Clock.time(it) }, onAnswer = { _, _ -> }) }
        }
        scene("home-taps-blocked") { HomeContent(home(taps = BlockedTaps(pen = true)), HomeCallbacks()) }
    }

    @Test fun readCardUntestedAndNotUsable() {
        val text = PenCardText(ApplicationProvider.getApplicationContext())
        val l = Ledger(mapOf(untested.serial to Pen(untested.serial, model = PenProfiles.NOVOPEN_6.id, since = 0, first = 0)))
        val check = PenReadState(PenReadState.Stage.Untested, untested.serial, ledger = l)
        val failed = check.copy(notUsable = true)
        for ((name, st) in listOf("untested" to check, "not-usable" to failed)) {
            scene("card-$name") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    PenReadCard(text.card(st), reading = false, canUndo = false, onAction = {}, onClose = {}, onTouch = {}, text = text)
                }
            }
        }
    }
}
