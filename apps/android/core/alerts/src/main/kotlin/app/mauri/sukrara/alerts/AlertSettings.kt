// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One kind's rule. [threshold]: mg/dL for levels, mg/dL/min (positive) for rates, minutes for no data.
 * [repeatMin] 0 = once per episode. [alarm]: ring like an alarm (alarm volume / Time Sensitive), local to the device.
 */
data class KindRule(val on: Boolean, val threshold: Double, val repeatMin: Int, val alarm: Boolean = false)

/** A treatment / falling-high quiet rule (SPEC §2): on, and its window in minutes. */
data class QuietRule(val on: Boolean, val min: Int)

/** SPEC §3 `sounds`: the pack every kind uses, per-kind overrides, a random variant each time. */
data class SoundSettings(
    val pack: String = "quest",
    val perKind: Map<AlertKind, String> = emptyMap(),
    val shuffle: Boolean = true,
) {
    fun soundOf(kind: AlertKind): String = perKind[kind] ?: pack
}

/**
 * SPEC §3 `notificationActions` (per device): the first length *Mute…* offers, the interval *Remind less* sets, and
 * the margin ([backMgdl] inside the threshold for [backMin] minutes) that ends Mute and Remind less.
 */
data class NotificationActions(
    val muteMin: Int = 30,
    val relaxMin: Int = 30,
    val backMgdl: Int = 10,
    val backMin: Int = 10,
)

/**
 * SPEC §1 "Waiting before the first alert" and "Back by the margin" (#37): a low side's / high side's first alert of an
 * episode waits until the side has been out of range for [lowMin] / [highMin] minutes in a row; a level episode is back
 * in range only once [marginMgdl] inside the threshold. All 0 = today's behaviour.
 */
data class Persistence(val lowMin: Int = 0, val highMin: Int = 0, val marginMgdl: Int = 0) {
    /**
     * Minutes [kind]'s first alert waits for [r]: very low, extremely high, a LO, a HI and the non-level kinds never
     * wait (the safety net is never delayed).
     */
    fun waitMin(kind: AlertKind, r: AlertReading?): Int =
        when {
            r == null || r.isLo || r.isHi -> 0
            kind == AlertKind.VERY_LOW || kind == AlertKind.EXTREMELY_HIGH -> 0
            kind.side == Side.LOW -> lowMin
            kind.side == Side.HIGH -> highMin
            else -> 0
        }
}

/**
 * SPEC §1 "Night alerts" (#38): a second set of every kind's rule in force each day from [fromMin] to [toMin] (minutes
 * after local midnight; the window may cross midnight; equal = never). Very low is always on at night.
 */
data class NightProfile(
    val on: Boolean = false,
    val fromMin: Int = DEFAULT_FROM,
    val toMin: Int = DEFAULT_TO,
    val rules: Map<AlertKind, KindRule> = AlertSettings.DEFAULT_RULES,
) {
    /** [kind]'s night rule; very low can't be off at night (the safety net). */
    fun rule(kind: AlertKind): KindRule =
        (rules[kind] ?: AlertSettings.DEFAULT_RULES.getValue(kind)).let { if (kind == AlertKind.VERY_LOW) it.copy(on = true) else it }

    fun withRule(kind: AlertKind, rule: KindRule): NightProfile = copy(rules = rules + (kind to rule))

    /** The night rules are in force at [epoch] (local time in [zone]). */
    fun active(epoch: Long, zone: ZoneId): Boolean {
        if (!on || fromMin == toMin) return false
        val t = Instant.ofEpochSecond(epoch).atZone(zone).toLocalTime()
        val m = t.hour * 60 + t.minute
        return if (fromMin < toMin) m in fromMin until toMin else m >= fromMin || m < toMin
    }

    /** The next moment after [epoch] the rules switch (night starts or ends), or null when off. */
    fun nextSwitch(epoch: Long, zone: ZoneId): Long? {
        if (!on || fromMin == toMin) return null
        val now = Instant.ofEpochSecond(epoch).atZone(zone)
        return listOf(fromMin, toMin)
            .flatMap { m -> (0L..1L).map { d -> at(now, d, m) } }
            .filter { it > epoch }
            .minOrNull()
    }

    private fun at(now: ZonedDateTime, days: Long, minute: Int): Long =
        ZonedDateTime.of(now.toLocalDate().plusDays(days), LocalTime.of(minute / 60, minute % 60), now.zone).toEpochSecond()

    companion object {
        /** 22:00–07:00 (LoopFollow's night starts at 22:00; Nightscout's day starts at 7). */
        const val DEFAULT_FROM = 22 * 60
        const val DEFAULT_TO = 7 * 60
    }
}

/**
 * SPEC §4 "No answer to a very low" (#39), each device's own: the wearer escalates a very low nobody answered on its
 * phone for [min] minutes to its followers ([on]); a follower shows those escalations ([follow]).
 */
data class Escalation(val on: Boolean = true, val min: Int = 5, val follow: Boolean = true)

/** Followers: mirror the wearer's `alert` events, or run these rules on the followed data (SPEC §3 `source`). */
enum class AlertSource(val wire: String) { WEARER("wearer"), OWN("own") }

/** The alert settings of SPEC §3 (the same JSON on Android, Mac and the followers' mirror; [AlertSettingsCodec]). */
data class AlertSettings(
    val on: Boolean = true,
    val rules: Map<AlertKind, KindRule> = DEFAULT_RULES,
    val afterInsulin: QuietRule = QuietRule(on = true, min = 60),
    val fallingHigh: QuietRule = QuietRule(on = true, min = 20),
    val afterCarbs: QuietRule = QuietRule(on = true, min = 15),
    val sounds: SoundSettings = SoundSettings(),
    val actions: NotificationActions = NotificationActions(),
    val persistence: Persistence = Persistence(),
    val night: NightProfile = NightProfile(),
    val escalation: Escalation = Escalation(),
    val source: AlertSource = AlertSource.WEARER,
    /**
     * A fact of the device running the rules, never stored: carbs may quiet very low only on the wearer's own phone
     * (#2, SPEC §2); its run for the followers, every follower and the Mac keep alerting on very low.
     */
    val veryLowQuietByCarbs: Boolean = true,
) {
    fun rule(kind: AlertKind): KindRule = rules[kind] ?: DEFAULT_RULES.getValue(kind)

    /** The night rules are in force at [epoch] (SPEC §1 "Night alerts"). */
    fun nightAt(epoch: Long, zone: ZoneId): Boolean = night.active(epoch, zone)

    /** These settings with the rules in force at [epoch]: the night set inside its window, else as they are. */
    fun at(epoch: Long, zone: ZoneId): AlertSettings =
        if (!night.active(epoch, zone)) this else copy(rules = AlertKind.entries.associateWith(night::rule))

    fun withRule(kind: AlertKind, rule: KindRule): AlertSettings = copy(rules = rules + (kind to rule))

    /**
     * The treatment whose window quiets [kind] now, or null: its rule is off, or [kind] is never quieted by a
     * treatment (extremely high, no data).
     */
    fun mutedBy(kind: AlertKind): TreatmentKind? =
        when {
            kind == AlertKind.EXTREMELY_HIGH -> null
            kind == AlertKind.VERY_LOW && !veryLowQuietByCarbs -> null
            kind.answeredBy == TreatmentKind.CARBS -> TreatmentKind.CARBS.takeIf { afterCarbs.on }
            kind.answeredBy == TreatmentKind.RAPID -> TreatmentKind.RAPID.takeIf { afterInsulin.on }
            else -> null
        }

    /** How long a [treatment] quiets the alerts it answers, in seconds (its window, whether the rule is on or not). */
    fun quietSeconds(treatment: TreatmentKind): Long =
        (if (treatment == TreatmentKind.RAPID) afterInsulin.min else afterCarbs.min) * 60L

    /** The longest repeat of the enabled level alerts: a *Remind less* interval must be slower (SPEC §3 `relaxMin`). */
    fun relaxFloor(): Int =
        AlertKind.LEVELS_BY_SEVERITY
            .map(::rule)
            .filter { it.on && it.repeatMin > 0 }
            .maxOfOrNull { it.repeatMin } ?: 0

    /** The *Remind less* intervals the settings offer: only ones slower than every enabled level alert's repeat. */
    fun relaxChoices(): List<Int> =
        AlertLimits.ACTION_RELAX.presetMinutes
            .filter { it > relaxFloor() }
            .ifEmpty { listOf(AlertLimits.ACTION_RELAX.max.toInt()) }

    /** The *Mute…* lengths a notification offers: [NotificationActions.muteMin] first, then the presets. */
    fun muteChoices(): List<Int> = (listOf(actions.muteMin) + AlertLimits.ACTION_MUTE.presetMinutes).distinct()

    /** *Remind less* would slow [kind] down (it repeats faster than the relaxed interval). */
    fun canRelax(kind: AlertKind): Boolean = rule(kind).repeatMin in 1 until actions.relaxMin

    companion object {
        /** The owner's own settings (2026-10-18), SPEC §3. */
        val DEFAULT_RULES: Map<AlertKind, KindRule> =
            mapOf(
                AlertKind.VERY_LOW to KindRule(on = true, threshold = 55.0, repeatMin = 5, alarm = true),
                AlertKind.LOW to KindRule(on = true, threshold = 69.0, repeatMin = 5),
                AlertKind.HIGH to KindRule(on = true, threshold = 180.0, repeatMin = 15),
                AlertKind.VERY_HIGH to KindRule(on = true, threshold = 250.0, repeatMin = 10),
                AlertKind.EXTREMELY_HIGH to KindRule(on = true, threshold = 300.0, repeatMin = 5, alarm = true),
                AlertKind.FALLING_FAST to KindRule(on = false, threshold = 2.0, repeatMin = 30),
                AlertKind.RISING_FAST to KindRule(on = false, threshold = 2.0, repeatMin = 30),
                AlertKind.NO_DATA to KindRule(on = true, threshold = 20.0, repeatMin = 60),
            )
    }
}
