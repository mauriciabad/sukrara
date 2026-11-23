// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.designsystem.sharing.PathMark
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.backup.BackupScreen
import app.mauri.sukrara.feature.sharing.backup.BackupUi
import app.mauri.sukrara.feature.sharing.backup.FreshUi
import app.mauri.sukrara.feature.sharing.backup.ReplaceActions
import app.mauri.sukrara.feature.sharing.backup.ReplaceConfirmDialog
import app.mauri.sukrara.feature.sharing.backup.ReplaceIdentityScreen
import app.mauri.sukrara.feature.sharing.backup.ReplaceStep
import app.mauri.sukrara.feature.sharing.backup.ReplaceUi
import app.mauri.sukrara.feature.sharing.conflict.IdentityConflictScreen
import app.mauri.sukrara.feature.sharing.follower.FollowingContent
import app.mauri.sukrara.feature.sharing.followers.FollowerSheet
import app.mauri.sukrara.feature.sharing.followers.FollowersActions
import app.mauri.sukrara.feature.sharing.followers.FollowersPage
import app.mauri.sukrara.feature.sharing.followers.NearbyPage
import app.mauri.sukrara.feature.sharing.profile.ProfileSave
import app.mauri.sukrara.feature.sharing.profile.ProfileScreen
import app.mauri.sukrara.feature.sharing.setup.AlertThresholds
import app.mauri.sukrara.feature.sharing.setup.JoinUi
import app.mauri.sukrara.feature.sharing.setup.LluChoice
import app.mauri.sukrara.feature.sharing.setup.LluConnectOutcome
import app.mauri.sukrara.feature.sharing.setup.LluConnection
import app.mauri.sukrara.feature.sharing.setup.LluUi
import app.mauri.sukrara.feature.sharing.setup.RestoreGate
import app.mauri.sukrara.feature.sharing.setup.RestoreUi
import app.mauri.sukrara.feature.sharing.setup.RootUi
import app.mauri.sukrara.feature.sharing.setup.ScanInvite
import app.mauri.sukrara.feature.sharing.setup.ScanState
import app.mauri.sukrara.feature.sharing.setup.SetupActions
import app.mauri.sukrara.feature.sharing.setup.SetupContent
import app.mauri.sukrara.feature.sharing.setup.SetupFacts
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SetupUi
import app.mauri.sukrara.feature.sharing.setup.SiteConnectResult
import app.mauri.sukrara.feature.sharing.setup.SiteConnection
import app.mauri.sukrara.feature.sharing.setup.SiteUi
import app.mauri.sukrara.feature.sharing.setup.SourceChange
import app.mauri.sukrara.feature.sharing.setup.ThresholdField
import app.mauri.sukrara.feature.sharing.setup.UpdateChoice
import app.mauri.sukrara.feature.sharing.share.ApproveDialog
import app.mauri.sukrara.feature.sharing.share.ApproveRefusedDialog
import app.mauri.sukrara.feature.sharing.share.InviteProgress
import app.mauri.sukrara.feature.sharing.share.InviteUi
import app.mauri.sukrara.feature.sharing.share.ShareScreen
import app.mauri.sukrara.integrations.garmin.GarminApp
import app.mauri.sukrara.integrations.garmin.iconRes
import app.mauri.sukrara.integrations.garmin.inlineNameRes
import app.mauri.sukrara.integrations.garmin.nameRes
import app.mauri.sukrara.integrations.garmin.whatRes
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.ble.BleMessages
import app.mauri.sukrara.protocol.event.Avatar
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.follow.FollowUpdate
import app.mauri.sukrara.protocol.owner.IdentityCheck
import app.mauri.sukrara.share.ble.NearbyServerCore
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.JoinResult
import app.mauri.sukrara.share.follower.NearbyOffer
import app.mauri.sukrara.share.follower.NearbySearch
import app.mauri.sukrara.share.follower.PairingCheck
import app.mauri.sukrara.share.owner.ApproveResult
import app.mauri.sukrara.share.owner.FollowerRecord
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.PendingRequest
import app.mauri.sukrara.share.owner.TellingFollowers
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import com.github.takahirom.roborazzi.uiMode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Roborazzi goldens of every setup step and the sharing screens (synthetic data only): one phone size, light + dark ×
 * default + largest font. `recordRoborazziDebug` writes src/test/snapshots (the orchestrator copies them to the UX review).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SharingScreenshotTest {
    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    private val facts = SetupFacts(spec = SourceSpec.YourSensor, ownSensorLive = false, hasIdentity = false)

    private fun ui(
        step: SetupStep,
        index: Int,
        facts: SetupFacts = this.facts,
        missing: Set<AlertRequirement> = emptySet(),
        alerts: AlertThresholds = AlertThresholds(AlertSettings()),
        share: OwnerShareState = OwnerShareState.NoIdentity,
        restore: RestoreUi = RestoreUi.Idle,
        record: SetupRecord = SetupRecord(),
    ) = SetupUi(
        step = step,
        index = index,
        count = 7,
        facts = facts,
        record = record,
        readiness = AlertReadiness(missing),
        alerts = alerts,
        gate = RestoreGate.evaluate(facts.ownSensorLive),
        share = share,
        restore = restore,
        busy = false,
        single = false,
        canBack = index > 3,
    )

    private val live = facts.copy(ownSensorLive = true)

    /** Sukrara's watch apps on the Garmin step, Sukrara Aura on the watch. */
    private val watchApps =
        GarminApp.entries.map {
            SetupWatchApp(it.key, it.nameRes, it.whatRes, it.iconRes, url = it::storeUrl, onWatch = it == GarminApp.Aura)
        }

    /** Your glucose from LibreLinkUp (docs/44 step 2). */
    private val llu = facts.copy(spec = SourceSpec(Person.Yours, SourceKind.LibreLinkUp))
    private val lluSignedIn =
        LluConnection(
            email = "alex@example.com",
            signedIn = true,
            patient = "p1",
            name = "Alex Example",
            people = listOf(LluChoice("p1", "Alex Example")),
            health = OnlineHealth.Connecting,
        )

    /** Your glucose from a Nightscout site (docs/44 step 1). */
    private val nightscout = facts.copy(spec = SourceSpec(Person.Yours, SourceKind.Nightscout))

    /** A connected site in [health], its newest reading [agoS] seconds old (null = none). */
    private fun site(
        health: OnlineHealth,
        agoS: Long?,
    ) = SiteConnection(
        "https://ns.example.org",
        "ns.example.org",
        saved = true,
        health = health,
        newest = agoS?.let { Instant.now().minusSeconds(it) },
    )

    private val conflict =
        OwnerShareState.AnotherAppSharingAsYou(
            IdentityCheck.Reason.entries.first(),
            followersReceiving = true,
            relays = emptyMap(),
        )
    private val followers =
        listOf(
            FollowerRecord("k1", "Anna", since = 1_761_000_000, avatarJson = Avatar.v1(1, "dots", 45, 7).toString()),
            FollowerRecord("k2", "MacBook", since = 1_759_000_000),
            FollowerRecord("k4", "Old phone", since = 1_750_000_000, revoked = 1_755_000_000),
        )
    private val request =
        PendingRequest(
            "k3",
            "Dad",
            "t",
            "4821",
            at = 1_761_500_000,
            avatarJson = Avatar.v1(4, "cube", 90, 1).toString(),
            label = "Dad's phone",
            inviteSentAt = 1_761_499_000,
        )
    private val me = Profile("Núria", Avatar.v1(0, "drop", 45, 3))
    private val sharing =
        OwnerShareState.Sharing(
            1,
            followers,
            listOf(request),
            emptyMap(),
            backupPublishedAt = 1_761_400_000,
            recoveryExportedAt = 0,
            profile = me,
            writers = setOf("k1"),
        )

    private val readyInvite =
        InviteUi.Ready(Invite("https://sukrara.mauri.app/join#c3ludGhldGljLWludml0ZS1mb3ItdGVzdHM", "Anna", 1_761_600_000, "t"))

    /** The join step after a link join, in [status]. */
    private fun linkUi(status: FollowStatus) =
        ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(
            follow =
                FollowState.Following(
                    status = status,
                    wearer = me,
                    code = "4821",
                    canWrite = false,
                    lastHeardAt = null,
                    relays = emptyMap(),
                ),
        )

    /** The join step while a *Pair nearby* join waits on [check]. */
    private fun pairingUi(check: PairingCheck) =
        ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(
            follow =
                FollowState.Following(
                    status = FollowStatus.Requested,
                    wearer = me,
                    code = "1234",
                    canWrite = false,
                    lastHeardAt = null,
                    relays = emptyMap(),
                    pairing = check,
                ),
        )

    internal fun scenes(): List<Pair<String, @Composable () -> Unit>> {
        // the app passes Settings' threshold rows; this module only sees their slot
        val a =
            SetupActions(thresholdRows = { settings, _ ->
                for (field in ThresholdField.entries) Text("${field.name}: ${settings.rule(field.kind).threshold}")
            })
        return listOf(
            "setup-1-welcome" to { SetupContent(ui(SetupStep.Welcome, 1, facts.copy(spec = null)), a) },
            "setup-2-person" to { SetupContent(ui(SetupStep.Person, 2, facts.copy(spec = null)), a) },
            "setup-2-person-yours" to { SetupContent(ui(SetupStep.Person, 2, facts.copy(spec = null)).copy(person = Person.Yours), a) },
            "setup-3-source-yours" to { SetupContent(ui(SetupStep.Source, 3, facts.copy(spec = null)).copy(person = Person.Yours), a) },
            "setup-3-source-theirs" to { SetupContent(ui(SetupStep.Source, 3, facts.copy(spec = null)).copy(person = Person.Theirs), a) },
            "setup-3-source-change" to {
                val juggluco = SourceSpec(Person.Yours, SourceKind.PhoneApp)
                SetupContent(ui(SetupStep.Source, 3, facts).copy(sourceChange = SourceChange(juggluco, null, SourceSpec.YourSensor)), a)
            },
            "setup-3-source-delete-history" to {
                val loss = HistoryLoss(4 * 1440, 12, Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-11-10T07:00:00Z"))
                SetupContent(
                    ui(SetupStep.Source, 3, facts.copy(spec = SourceSpec.TheirSukrara))
                        .copy(person = Person.Yours, sourceChange = SourceChange(SourceSpec.YourSensor, loss, SourceSpec.TheirSukrara)),
                    a,
                )
            },
            "setup-5-juggluco" to
                { SetupContent(ui(SetupStep.Sensor, 5, facts.copy(spec = SourceSpec(Person.Yours, SourceKind.PhoneApp))), a) },
            "setup-3-permissions" to {
                SetupContent(
                    ui(SetupStep.Permissions, 3, missing = setOf(AlertRequirement.FullScreenIntent, AlertRequirement.BatteryExemption)),
                    a.copy(sendTestAlert = {}),
                )
            },
            // docs/40: a follower's phone asks Nearby devices with the rest (Bluetooth, the internet's backup)
            "setup-3-permissions-follower" to {
                SetupContent(ui(SetupStep.Permissions, 3, facts.copy(spec = SourceSpec.TheirSukrara, following = true)), a)
            },
            "setup-3b-updates" to { SetupContent(ui(SetupStep.Updates, 4, facts.copy(updates = UpdateChoice.Open)), a) },
            "setup-3b-updates-on" to { SetupContent(ui(SetupStep.Updates, 4, facts.copy(updates = UpdateChoice.On)), a) },
            // the Garmin step; a watch app that reads another version of this phone's payload (THE VERSION RULE)
            "setup-garmin" to { SetupContent(ui(SetupStep.Garmin, 6, facts.copy(garmin = true)).copy(garminApps = watchApps), a) },
            "setup-garmin-update" to {
                val update = SetupGarminUpdate(GarminApp.Aura.key, GarminApp.Aura.inlineNameRes, "Descent Mk3i 43mm", newer = false)
                SetupContent(ui(SetupStep.Garmin, 6, facts.copy(garmin = true)).copy(garminApps = watchApps, garminUpdate = update), a)
            },
            // docs/44 step 1: a Nightscout site's Connect step in each state
            "setup-5-nightscout" to { SetupContent(ui(SetupStep.Sensor, 5, nightscout), a) },
            "setup-5-nightscout-theirs" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout.copy(spec = SourceSpec(Person.Theirs, SourceKind.Nightscout))), a)
            },
            "setup-5-nightscout-connecting" to
                { SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(connecting = true)), a) },
            "setup-5-nightscout-https" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(result = SiteConnectResult.NeedsHttps)), a)
            },
            "setup-5-nightscout-refused" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(result = SiteConnectResult.Refused)), a)
            },
            "setup-5-nightscout-unreachable" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(result = SiteConnectResult.Unreachable)), a)
            },
            "setup-5-nightscout-waiting" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(site(OnlineHealth.Connecting, null))), a)
            },
            "setup-5-nightscout-received" to {
                SetupContent(
                    ui(SetupStep.Sensor, 5, nightscout.copy(ownSensorLive = true)).copy(site = SiteUi(site(OnlineHealth.Live, 90))),
                    a,
                )
            },
            "setup-5-nightscout-silent" to {
                SetupContent(ui(SetupStep.Sensor, 5, nightscout).copy(site = SiteUi(site(OnlineHealth.NoNewReadings, 7200))), a)
            },
            // docs/44 step 2: LibreLinkUp's Connect step in each state
            "setup-5-llu-libreview" to {
                SetupContent(ui(SetupStep.Sensor, 5, llu).copy(llu = LluUi(LluConnection(libreViewEmail = "alex@example.com"))), a)
            },
            "setup-5-llu-form" to { SetupContent(ui(SetupStep.Sensor, 5, llu), a) },
            "setup-5-llu-theirs" to {
                SetupContent(ui(SetupStep.Sensor, 5, llu.copy(spec = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp))), a)
            },
            "setup-5-llu-refused" to {
                SetupContent(ui(SetupStep.Sensor, 5, llu).copy(llu = LluUi(result = LluConnectOutcome.BadCredentials)), a)
            },
            "setup-5-llu-rate-limited" to {
                SetupContent(ui(SetupStep.Sensor, 5, llu).copy(llu = LluUi(result = LluConnectOutcome.RateLimited)), a)
            },
            "setup-5-llu-choose" to {
                SetupContent(
                    ui(SetupStep.Sensor, 5, llu.copy(spec = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp))).copy(
                        llu =
                            LluUi(
                                lluSignedIn.copy(
                                    patient = "",
                                    people = listOf(LluChoice("p2", "Ana Example"), LluChoice("p3", "Leo")),
                                ),
                            ),
                    ),
                    a,
                )
            },
            "setup-5-llu-no-one" to {
                SetupContent(ui(SetupStep.Sensor, 5, llu).copy(llu = LluUi(lluSignedIn.copy(patient = "", people = emptyList()))), a)
            },
            "setup-5-llu-waiting" to { SetupContent(ui(SetupStep.Sensor, 5, llu).copy(llu = LluUi(lluSignedIn)), a) },
            "setup-5-llu-received" to {
                SetupContent(
                    ui(SetupStep.Sensor, 5, llu.copy(ownSensorLive = true)).copy(
                        llu = LluUi(lluSignedIn.copy(health = OnlineHealth.Live, newest = Instant.now().minusSeconds(70))),
                    ),
                    a,
                )
            },
            "setup-7-history-llu" to { SetupContent(ui(SetupStep.History, 7, llu.copy(ownSensorLive = true)), a) },
            "setup-7-history-nightscout" to { SetupContent(ui(SetupStep.History, 7, nightscout.copy(ownSensorLive = true)), a) },
            "setup-4-sensor-waiting" to { SetupContent(ui(SetupStep.Sensor, 4), a) },
            "setup-4-sensor-live" to { SetupContent(ui(SetupStep.Sensor, 4, live), a) },
            "setup-5-alerts" to { SetupContent(ui(SetupStep.Alerts, 5, live), a) },
            "setup-5-alerts-out-of-order" to {
                SetupContent(ui(SetupStep.Alerts, 5, live, alerts = AlertThresholds(AlertSettings()).with(ThresholdField.High, 260.0)), a)
            },
            "setup-6-identity-gate-closed" to { SetupContent(ui(SetupStep.Identity, 6), a) },
            "setup-6-identity-gate-open" to {
                SetupContent(ui(SetupStep.Identity, 6, live), a)
            },
            "setup-6-restore-wrong-password" to {
                SetupContent(
                    ui(SetupStep.Identity, 6, live, restore = RestoreUi.Failed(RestoreIdentity.Result.WrongPassword)),
                    a,
                )
            },
            "setup-6-restore-busy" to {
                SetupContent(ui(SetupStep.Identity, 6, live, restore = RestoreUi.Busy), a)
            },
            "setup-6-identity-conflict" to { SetupContent(ui(SetupStep.Identity, 6, live, share = conflict), a) },
            "setup-7-history" to { SetupContent(ui(SetupStep.History, 7, live), a) },
            // #181: the essentials end on Ready, which lists what is left; the optional insulin pens step
            "setup-5b-ready" to {
                SetupContent(
                    ui(
                        SetupStep.Ready,
                        0,
                        live,
                    ).copy(
                        count = 0,
                        extras =
                            listOf(
                                SetupStep.Updates,
                                SetupStep.Root,
                                SetupStep.Notification,
                                SetupStep.Identity,
                                SetupStep.Pens,
                                SetupStep.History,
                            ),
                    ),
                    a,
                )
            },
            // docs/36 §11: is this phone rooted; the way to allow root; the choice with root picked; without root
            "setup-6c-root" to { SetupContent(ui(SetupStep.Root, 2, live.copy(root = false)).copy(count = 6), a) },
            "setup-6c-root-allow" to {
                SetupContent(
                    ui(SetupStep.Root, 2, live.copy(root = false)).copy(
                        count = 6,
                        root = RootUi(SystemAccessState(root = RootAccess.Missing, rootSeen = true), rooted = true),
                    ),
                    a,
                )
            },
            "setup-6d-notification-root" to {
                SetupContent(
                    ui(SetupStep.Notification, 3, live.copy(root = true)).copy(
                        count = 6,
                        root =
                            RootUi(
                                SystemAccessState(root = RootAccess.Granted, module = ModuleState.Active, framework = "LSPosed"),
                                rooted = true,
                                style = NotificationStyle.Text,
                            ),
                    ),
                    a,
                )
            },
            "setup-6d-notification" to { SetupContent(ui(SetupStep.Notification, 1, live).copy(count = 4), a) },
            "setup-6b-pens" to { SetupContent(ui(SetupStep.Pens, 3, live.copy(pens = false)).copy(count = 4), a) },
            "conflict-screen" to { IdentityConflictScreen(conflict, {}, {}) },
            "share-new" to { ShareScreen(InviteUi.None, { _, _ -> }, {}, {}) },
            "share-ready" to {
                ShareScreen(
                    InviteUi.Ready(Invite("https://sukrara.mauri.app/join#c3ludGhldGljLWludml0ZS1mb3ItdGVzdHM", "Anna", 1_761_600_000)),
                    { _, _ -> },
                    {},
                    {},
                )
            },
            // #174: the share screen moves on with its invite
            "share-asked" to { ShareScreen(readyInvite, { _, _ -> }, {}, {}, progress = InviteProgress.Asked(request)) },
            "share-following" to {
                ShareScreen(readyInvite, { _, _ -> }, {}, {}, progress = InviteProgress.Following(followers[0]))
            },
            "share-denied" to { ShareScreen(readyInvite, { _, _ -> }, {}, {}, progress = InviteProgress.Denied("Dad")) },
            "share-expired" to { ShareScreen(readyInvite, { _, _ -> }, {}, {}, progress = InviteProgress.Expired) },
            "followers" to { FollowersPage(sharing, FollowersActions()) },
            // docs/48: Anna's Sukrara is newer (this phone updates), the MacBook's older (it updates)
            "followers-update" to { FollowersPage(sharing.copy(otherVersions = mapOf("k1" to true, "k2" to false)), FollowersActions()) },
            // someone with a newer Sukrara opened an invite (or found Pair nearby) while it is open
            "followers-update-joining" to { FollowersPage(sharing.copy(newerInvitee = true), FollowersActions()) },
            "nearby-pair-update" to {
                NearbyPage(
                    sharing.copy(newerInvitee = true),
                    NearbyUi(status = NearbyStatus.Searching, pairingUntil = 1_761_600_000 + 581),
                    FollowersActions(),
                    now = { 1_761_600_000 },
                )
            },
            // who is paired nearby: Anna's phone connected now, the MacBook paired and out of range
            "followers-nearby" to {
                FollowersPage(
                    sharing.copy(nearbyPaired = setOf("k1", "k2")),
                    FollowersActions(),
                    nearby =
                        NearbyUi(
                            status = NearbyStatus.Connected(1),
                            links = listOf(NearbyServerCore.Named("k1", "phone", 1_761_600_000_000)),
                        ),
                )
            },
            "nearby-pair" to {
                NearbyPage(
                    sharing,
                    NearbyUi(status = NearbyStatus.Searching, pairingUntil = 1_761_600_000 + 581),
                    FollowersActions(),
                    now = { 1_761_600_000 },
                )
            },
            "nearby-devices" to {
                NearbyPage(
                    sharing.copy(nearbyPaired = setOf("k2")),
                    NearbyUi(
                        status = NearbyStatus.Connected(1),
                        links = listOf(NearbyServerCore.Named("k1", "phone", 1_761_600_000_000)),
                        internet = false,
                    ),
                    FollowersActions(),
                    now = { 1_761_600_000 },
                )
            },
            // the owner's case (docs/40): someone follows, Nearby devices doesn't cover advertising
            "nearby-permission" to { NearbyPage(sharing, NearbyUi(status = NearbyStatus.NeedsPermission), FollowersActions()) },
            "nearby-off" to { NearbyPage(sharing, NearbyUi(on = false), FollowersActions()) },
            "nearby-bluetooth-off" to { NearbyPage(sharing, NearbyUi(status = NearbyStatus.BluetoothOff), FollowersActions()) },
            "followers-bluetooth-off" to
                { FollowersPage(sharing, FollowersActions(), nearby = NearbyUi(status = NearbyStatus.BluetoothOff)) },
            "setup-join-find-nearby" to {
                val offer =
                    NearbyOffer(
                        "phone",
                        BleMessages.Pairing(PublicKeyHex("ab".repeat(32)), emptyList(), "t".repeat(22), 1_761_600_600, me, "c".repeat(64)),
                    )
                SetupContent(
                    ui(
                        SetupStep.Join,
                        2,
                        facts.copy(spec = SourceSpec.TheirSukrara),
                    ).copy(nearby = NearbySearch(NearbyStatus.Searching, listOf(offer))),
                    a,
                )
            },
            // docs/48: offers of another version: a newer one (this phone updates) and an older one (the wearer does)
            "setup-join-find-nearby-update" to {
                fun other(name: String) =
                    BleMessages.Pairing(PublicKeyHex("ab".repeat(32)), emptyList(), "", 1_761_600_600, Profile(name, me.avatar), "")
                SetupContent(
                    ui(
                        SetupStep.Join,
                        2,
                        facts.copy(spec = SourceSpec.TheirSukrara),
                    ).copy(
                        nearby =
                            NearbySearch(
                                NearbyStatus.Searching,
                                listOf(
                                    NearbyOffer("a", other("Marta"), FollowUpdate.ThisApp),
                                    NearbyOffer("b", other("Dad"), FollowUpdate.Wearer),
                                ),
                            ),
                    ),
                    a,
                )
            },
            "setup-join-find-nearby-off" to {
                SetupContent(
                    ui(
                        SetupStep.Join,
                        2,
                        facts.copy(spec = SourceSpec.TheirSukrara),
                    ).copy(nearby = NearbySearch(NearbyStatus.BluetoothOff, emptyList())),
                    a,
                )
            },
            // a link join waiting: the match code as one quiet line (the wearer asks for it only for a leaked link)
            "setup-join-waiting" to { SetupContent(linkUi(FollowStatus.Requested), a) },
            "setup-join-allowed" to { SetupContent(linkUi(FollowStatus.Active), a) },
            "following-waiting" to {
                FollowingContent(linkUi(FollowStatus.Requested).follow, failed = false, onStop = {}, onJoinAgain = {})
            },
            "setup-join-pair-asking" to { SetupContent(pairingUi(PairingCheck(null, confirmed = false)), a) },
            "setup-join-pair-compare" to { SetupContent(pairingUi(PairingCheck("021472", confirmed = false)), a) },
            "setup-join-pair-waiting" to { SetupContent(pairingUi(PairingCheck("021472", confirmed = true)), a) },
            "setup-join-pair-differed" to {
                SetupContent(
                    ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(join = JoinUi.Failed(JoinResult.CodesDiffered)),
                    a,
                )
            },
            // docs/48: an invite of a newer version (this phone updates) and of an older one (the inviter does)
            "setup-join-update" to {
                SetupContent(
                    ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(join = JoinUi.Failed(JoinResult.UpdateToJoin)),
                    a,
                )
            },
            "setup-join-inviter-update" to {
                SetupContent(
                    ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(join = JoinUi.Failed(JoinResult.InviterUpdates)),
                    a,
                )
            },
            "setup-join-confirm" to {
                val invite =
                    app.mauri.sukrara.protocol.invite.Invite(
                        PublicKeyHex("ab".repeat(32)),
                        "t".repeat(22),
                        1_761_600_000,
                        emptyList(),
                        me,
                    )
                SetupContent(ui(SetupStep.Join, 2, facts.copy(spec = SourceSpec.TheirSukrara)).copy(join = JoinUi.Confirm(invite)), a)
            },
            "setup-join-scan-why" to { ScanScene(ScanState.Rationale) },
            "setup-join-scan-off" to { ScanScene(ScanState.Denied) },
            "setup-join-scan-no-camera" to { ScanScene(ScanState.NoCamera) },
            "approve-nearby" to {
                ApproveDialog(
                    request.copy(nearby = true, code = "021472", label = ""),
                    working = false,
                    onAllow = { _, _ -> },
                    onDeny = {},
                    onLater = {},
                    time = ::utcTime,
                )
            },
            "followers-empty" to {
                FollowersPage(OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0), FollowersActions())
            },
            "follower-sheet" to {
                FollowerSheet(followers[0], nearby = null, writer = true, refused = false, onWriter = {}, onRemove = {}, onClose = {})
            },
            "follower-sheet-nearby" to {
                FollowerSheet(
                    followers[0],
                    nearby = PathMark.Nearby,
                    writer = true,
                    refused = false,
                    onWriter = {},
                    onRemove = {},
                    onClose = {},
                )
            },
            "follower-sheet-paired" to {
                FollowerSheet(
                    followers[1],
                    nearby = PathMark.Paired,
                    writer = false,
                    refused = false,
                    onWriter = {},
                    onRemove = {},
                    onClose = {},
                )
            },
            "approve" to
                { ApproveDialog(request, working = false, onAllow = { _, _ -> }, onDeny = {}, onLater = {}, time = ::utcTime) },
            // #173: a long label stays whole (two lines), the hint wraps under it
            "approve-long-label" to {
                ApproveDialog(
                    request.copy(name = "Maurici", label = "Maurici's MacBook Pro at the office"),
                    working = false,
                    onAllow = { _, _ -> },
                    onDeny = {},
                    onLater = {},
                    time = ::utcTime,
                )
            },
            // the invite was made with Let them add treatments: the switch starts on
            "approve-writer" to {
                ApproveDialog(request.copy(writer = true), working = false, onAllow = {
                    _,
                    _,
                    ->
                }, onDeny = {}, onLater = {}, time = ::utcTime)
            },
            "approve-no-label" to {
                ApproveDialog(request.copy(label = ""), working = false, onAllow = { _, _ -> }, onDeny = {}, onLater = {}, time = ::utcTime)
            },
            "approve-leaked" to {
                ApproveDialog(
                    request.copy(others = 2),
                    working = false,
                    onAllow = { _, _ -> },
                    onDeny = {},
                    onLater = {},
                    time = ::utcTime,
                )
            },
            "approve-refused" to { ApproveRefusedDialog(request, ApproveResult.Gone, {}) },
            "profile" to { ProfileScreen(me, ProfileSave.Idle, {}, {}) },
            "profile-failed" to { ProfileScreen(Profile("", null), ProfileSave.Failed, {}, {}) },
            "backup-missing" to { BackupScreen(sharing, BackupUi.Idle, {}, { null }, {}) },
            "replace-choose" to { ReplaceIdentityScreen(sharing, ReplaceUi(), ReplaceActions()) },
            "replace-restore" to { ReplaceIdentityScreen(sharing, ReplaceUi(step = ReplaceStep.Restore), ReplaceActions()) },
            "replace-confirm" to {
                ReplaceConfirmDialog(
                    "Start fresh?",
                    "Start fresh",
                    listOf("Dad", "Marta"),
                    backedUp = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            },
            "replace-telling" to {
                ReplaceIdentityScreen(sharing, ReplaceUi(fresh = FreshUi.Busy, telling = TellingFollowers(3, 1)), ReplaceActions())
            },
            "replace-cancelled" to { ReplaceIdentityScreen(sharing, ReplaceUi(cancelled = true), ReplaceActions()) },
            "replace-done-fresh" to { ReplaceIdentityScreen(null, ReplaceUi(fresh = FreshUi.Done), ReplaceActions()) },
            "backup-done" to {
                BackupScreen(
                    sharing.copy(passwordManagerSavedAt = 1_761_450_000, recoveryExportedAt = 1_761_460_000),
                    BackupUi.Saved,
                    {},
                    { null },
                    {},
                )
            },
            "backup-no-manager" to { BackupScreen(sharing, BackupUi.NoPasswordManager, {}, { null }, {}) },
            "backup-failed-again" to {
                BackupScreen(sharing.copy(passwordManagerSavedAt = 1_761_450_000), BackupUi.Failed, {}, { null }, {})
            },
        )
    }

    @Test
    fun screens() {
        for (dark in listOf(false, true)) {
            for (largest in listOf(false, true)) {
                val options =
                    RoborazziComposeOptions {
                        size(widthDp = 411, heightDp = 891)
                        uiMode(if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                        fontScale(if (largest) 2f else 1f)
                    }
                for ((name, content) in scenes()) {
                    val suffix = (if (dark) "-dark" else "-light") + if (largest) "-largest" else ""
                    captureRoboImage("src/test/snapshots/$name$suffix.png", roborazziComposeOptions = options) {
                        SukraraTheme(dark = dark, dynamic = false) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Every update notice of these pages (docs/48 "When versions differ"): light + dark × default + largest font, and
 * Spanish. A review gallery into build/outputs/roborazzi (written only with roborazzi.test.record), never compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SharingUpdateGallery {
    @Test fun english() = gallery("", dark = listOf(false, true))

    @Test
    @Config(qualifiers = "es")
    fun spanish() = gallery("es-", dark = listOf(false))

    private fun gallery(
        prefix: String,
        dark: List<Boolean>,
    ) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val update =
            setOf(
                "followers-update",
                "setup-join-update",
                "setup-join-inviter-update",
                "setup-garmin-update",
                "nearby-pair-update",
                "followers-update-joining",
            )
        // Find phones nearby's offers (setup-join-find-nearby-update) never go idle (the search spinner): the
        // screenshot test's own pictures cover them
        val scenes = SharingScreenshotTest().scenes().filter { it.first in update }
        for (d in dark) {
            for (largest in listOf(false, true)) {
                val options =
                    RoborazziComposeOptions {
                        size(widthDp = 411, heightDp = 891)
                        uiMode(if (d) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                        fontScale(if (largest) 2f else 1f)
                    }
                for ((name, content) in scenes) {
                    val suffix = (if (d) "-dark" else "-light") + if (largest) "-largest" else ""
                    captureRoboImage("build/outputs/roborazzi/$prefix$name$suffix.png", roborazziComposeOptions = options) {
                        SukraraTheme(dark = d, dynamic = false) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
                        }
                    }
                }
            }
        }
    }
}

/** The join step's QR scanner when the camera can't be used (each with its way out); the live preview needs a camera. */
@Composable
private fun ScanScene(state: ScanState) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanInvite(enabled = true, onInvite = {}, initial = state)
    }
}

/** Fixed-zone times for the goldens. */
private fun utcTime(epochS: Long): String =
    java.time.Instant
        .ofEpochSecond(epochS)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalTime()
        .toString()
