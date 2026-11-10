// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.sensorend

import app.mauri.sukrara.core.model.SensorEndConfig
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What the sensor-end notifier remembers (typed DataStore `sensor_end`), all epoch seconds. The sensor it planned for
 * ([start], [end], [warmupEnd]; 0 = none): a sensor whose start moves by more than [SensorEndPlan.SAME_SENSOR_S] is a
 * new one; an end that moves (an early end) keeps the rest. [lastReminder] = the last reminder slot shown (a slot's
 * *time*, so one an unlock brought forward isn't shown again at its time); the countdowns dismissed (swiped away);
 * the notices shown; [firstReading] = the first reading since the start (a restart must not bring the warm-up back);
 * [lastUnlock] = the latest unlock that brings a reminder forward; [config] = the settings the slots were planned
 * with (a change means the slots already past never fire); [lastEnded] = when *Sensor ended* last showed (its repeats
 * count from it); [parked] = the hub has no sensor right now (forgotten, or
 * not read yet): nothing is shown or planned, and the same sensor coming back keeps what was announced (#102).
 */
@Serializable
data class SensorEndMemory(
    val start: Long = 0,
    val end: Long = 0,
    val warmupEnd: Long = 0,
    val lastReminder: Long = 0,
    val countdownDismissed: Boolean = false,
    val endedShown: Boolean = false,
    val warmupShown: Boolean = false,
    val warmupDismissed: Boolean = false,
    val readyShown: Boolean = false,
    val firstReading: Long = 0,
    val lastUnlock: Long = 0,
    val config: Int = 0,
    val lastEnded: Long = 0,
    val parked: Boolean = false,
) {
    val known: Boolean get() = end > 0
}

/** Which words a reminder uses. */
enum class ReminderText {
    /** "Sensor ends tomorrow at 14:30" · "Have a new sensor ready." */
    ENDS,

    /** The end day, ending later today outside the night: "Take your spare sensor with you". */
    SPARE,

    /** The evening before a night-time end: "Sensor ends tonight at 3:10" · "Change it before bed". */
    TONIGHT,
}

/**
 * A reminder's moment: [at] (its configured time: the fallback alarm), [unlockFrom] (the first unlock from this time
 * until [at] shows it early; null = only at [at]) and [evening] (the evening-before-the-night reminder).
 */
data class Slot(
    val at: Long,
    val unlockFrom: Long? = null,
    val evening: Boolean = false,
)

/**
 * The sensor-end rules (pure, JVM-tested), all times epoch seconds:
 *
 * - A reminder's slot is (end day − daysBefore) at its time in the phone's zone, used only after the start and
 *   **before the end** (an end at 07:30 skips "on the end day at 09:00": the countdown covers that morning).
 * - A morning reminder (05:00–12:00) shows at the **first unlock** of that day from 05:00 if that is earlier; the alarm
 *   at its time stays as the fallback. Only an unlock after the last reminder counts.
 * - **A night-time end** (in the night window, start inclusive, end exclusive; it may wrap midnight) gets one more
 *   slot at bedtime − [EVENING_BEFORE] the evening before, or at the first unlock from [EVENING_UNLOCK] before it.
 * - Words: the evening slot says "tonight"; on the end day an end outside the night says to take the spare along.
 * - Slots that passed before the planner knew them (a new sensor, edited settings) never fire; a slot missed while
 *   the phone was off fires late if the sensor hasn't ended, and of several missed only the latest one.
 * - The end countdown runs from end − countdownHours to the end (0 h = none), unless dismissed for this sensor.
 * - The countdown takes the place of a reminder shown before its window (it says the same, #140); one that comes
 *   during the window stays.
 * - At the end one "Sensor ended", at most [ENDED_GRACE] after it (not days later after a reboot); then, with
 *   `endedRepeatHours`, again every so many hours from the last one until a new sensor, at most [ENDED_REPEAT_FOR]
 *   after the end, never in the night window (one due then waits for the wake-up time).
 * - Warm-up: a countdown from the start to the warm-up end while no reading came, unless dismissed. **It never goes
 *   below zero**: at the warm-up end it becomes "Almost ready" without a timer and gives up `grace` later. The first
 *   reading ends it; one "Sensor ready" follows if the countdown was shown, within [READY_GRACE] of that reading.
 * - Two alarms: the next event (reminder, window start, warm-up end and give-up, the end, *Sensor ended* again) is
 *   always set exact and waking; a progress tick (non-waking: it waits while the phone sleeps) only refreshes a bar
 *   before it. A sleeping phone never leaves the end to a tick (#140: the countdown's timer ran below zero).
 */
object SensorEndPlan {
    const val TICK = 15 * 60L
    const val WARMUP_TICK = 5 * 60L
    const val TICK_WINDOW = 60L
    const val ENDED_GRACE = 12 * 3600L
    const val ENDED_REPEAT_FOR = 3 * 86_400L
    const val READY_GRACE = 3600L
    const val WARMUP_GRACE = 15 * 60L
    const val SAME_SENSOR_S = 24 * 3600L
    const val MORNING_FROM = 5 * 60
    const val NOON = 12 * 60
    const val EVENING_BEFORE = 2 * 60
    const val EVENING_UNLOCK = 2 * 60

    data class Plan(
        /** A reminder slot to show now (its time), or null. */
        val remind: Long?,
        val countdown: Boolean,
        val ended: Boolean,
        /** The next event (exact, waking), or null = nothing left for this sensor. */
        val next: Long?,
        /** A progress refresh before [next] (non-waking: late while the phone sleeps), or null. */
        val tick: Long? = null,
        val warmup: Boolean = false,
        /** ... past its end, still without a reading: "Almost ready", no timer. */
        val warmupWaiting: Boolean = false,
        val ready: Boolean = false,
        val remindText: ReminderText = ReminderText.ENDS,
        /** The countdown takes the place of the reminder shown before its window. */
        val dropReminder: Boolean = false,
    )

    private fun local(
        t: Long,
        zone: ZoneId,
    ) = Instant.ofEpochSecond(t).atZone(zone).toLocalDateTime()

    private fun epoch(
        t: LocalDateTime,
        zone: ZoneId,
    ) = t.atZone(zone).toEpochSecond()

    private fun at(
        day: LocalDate,
        minute: Int,
    ) = day.atTime(LocalTime.ofSecondOfDay(minute * 60L))

    fun inNight(
        t: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): Boolean {
        val m = local(t, zone).toLocalTime().toSecondOfDay() / 60
        return if (cfg.nightStart < cfg.nightEnd) m in cfg.nightStart until cfg.nightEnd else m >= cfg.nightStart || m < cfg.nightEnd
    }

    /** The evening reminder before a night-time [end] (bedtime − 2 h of the night it falls in), or null. */
    fun eveningSlot(
        end: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): Slot? {
        if (!cfg.evening || !inNight(end, cfg, zone)) return null
        val e = local(end, zone)
        val m = e.toLocalTime().toSecondOfDay() / 60
        // the night started the day before when the end is in its after-midnight part
        val nightDay = if (cfg.nightStart > cfg.nightEnd && m < cfg.nightEnd) e.toLocalDate().minusDays(1) else e.toLocalDate()
        val evening = at(nightDay, cfg.nightStart).minusMinutes(EVENING_BEFORE.toLong())
        return Slot(
            epoch(evening, zone),
            if (cfg.unlock) epoch(evening.minusMinutes(EVENING_UNLOCK.toLong()), zone) else null,
            evening = true,
        )
    }

    /** [t], or the night window's end (wake-up) when [t] is in the night. */
    fun outOfNight(
        t: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): Long {
        if (!inNight(t, cfg, zone)) return t
        val l = local(t, zone)
        val wake = at(l.toLocalDate(), cfg.nightEnd)
        return epoch(if (wake.isAfter(l)) wake else wake.plusDays(1), zone)
    }

    /** When *Sensor ended* comes again after the one at [last] (0 = none yet: from the [end]), or null (off, past it). */
    fun endedAgainAt(
        end: Long,
        last: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): Long? {
        if (cfg.endedRepeatHours <= 0) return null
        return outOfNight(maxOf(last, end) + cfg.endedRepeatHours * 3600L, cfg, zone).takeIf { it < end + ENDED_REPEAT_FOR }
    }

    /** The reminder slots after [start] and before [end], earliest first (two at one time: the evening one). */
    fun slots(
        start: Long,
        end: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): List<Slot> {
        val endDay = local(end, zone).toLocalDate()
        val configured =
            cfg.reminders.map { r ->
                val day = endDay.minusDays(r.daysBefore.toLong())
                val morning = cfg.unlock && r.minuteOfDay > MORNING_FROM && r.minuteOfDay < NOON
                Slot(epoch(at(day, r.minuteOfDay), zone), if (morning) epoch(at(day, MORNING_FROM), zone) else null)
            }
        return (listOfNotNull(eveningSlot(end, cfg, zone)) + configured)
            .filter { it.at in (start + 1) until end }
            .distinctBy { it.at }
            .sortedBy { it.at }
    }

    private fun due(
        s: Slot,
        now: Long,
        mem: SensorEndMemory,
    ) = now >= s.at ||
        (
            s.unlockFrom != null &&
                mem.lastUnlock in s.unlockFrom until s.at &&
                mem.lastUnlock > mem.lastReminder &&
                mem.lastUnlock <= now
        )

    /** An unlock at [now] would bring a reminder forward (only those are recorded and re-plan). */
    fun unlockWanted(
        mem: SensorEndMemory,
        cfg: SensorEndConfig,
        now: Long,
        zone: ZoneId,
    ): Boolean =
        mem.known &&
            !mem.parked &&
            now < mem.end &&
            slots(mem.start, mem.end, cfg, zone).any { s ->
                s.unlockFrom != null && now in s.unlockFrom until s.at &&
                    s.at > mem.lastReminder
            }

    fun text(
        slot: Slot,
        end: Long,
        now: Long,
        cfg: SensorEndConfig,
        zone: ZoneId,
    ): ReminderText =
        when {
            slot.evening -> ReminderText.TONIGHT
            dayOffset(end, now, zone) == 0L && !inNight(end, cfg, zone) -> ReminderText.SPARE
            else -> ReminderText.ENDS
        }

    fun windowStart(
        end: Long,
        hours: Int,
    ) = end - hours * 3600L

    /**
     * What shows at [now] for the sensor in [mem] ([SensorEndMemory.start] / [SensorEndMemory.end] /
     * [SensorEndMemory.warmupEnd]); [firstReading] = the newest reading's time if it is since the start.
     */
    fun plan(
        cfg: SensorEndConfig,
        hours: Int,
        now: Long,
        zone: ZoneId,
        mem: SensorEndMemory,
        firstReading: Long? = null,
        grace: Long = WARMUP_GRACE,
    ): Plan {
        val start = mem.start
        val end = mem.end
        val warmupEnd = mem.warmupEnd
        val first = mem.firstReading.takeIf { it > 0 } ?: firstReading
        val slots = slots(start, end, cfg, zone)
        val shown = slots.lastOrNull { it.at > mem.lastReminder && due(it, now, mem) }?.takeIf { now < end }
        val remind = shown?.at
        val remindText = shown?.let { text(it, end, now, cfg, zone) } ?: ReminderText.ENDS
        // a slot an unlock brought forward needs no alarm at its time any more
        val last = maxOf(mem.lastReminder, remind ?: 0)
        val win = if (cfg.countdown && hours > 0) windowStart(end, hours) else null
        val countdown = win != null && now in win until end && !mem.countdownDismissed
        val firstEnded = now >= end && now < end + ENDED_GRACE && !mem.endedShown
        val againAt = if (now >= end) endedAgainAt(end, mem.lastEnded, cfg, zone) else null
        val ended = firstEnded || (againAt != null && now >= againAt && !inNight(now, cfg, zone))
        // a repeat due in the night waits for the wake-up
        val nextEnded =
            endedAgainAt(end, if (ended) now else mem.lastEnded, cfg, zone)
                ?.let { maxOf(it, outOfNight(now, cfg, zone)) }
                ?.takeIf { it > now && it < end + ENDED_REPEAT_FOR }
        val dropReminder = countdown && remind == null && win != null && mem.lastReminder < win
        val warmingUp = cfg.warmup && warmupEnd > start && now in start until warmupEnd + grace && first == null && now < end
        val warmup = warmingUp && !mem.warmupDismissed
        val ready = first != null && mem.warmupShown && !mem.warmupDismissed && !mem.readyShown && now < first + READY_GRACE && now < end
        val waiting = warmup && now >= warmupEnd
        val event =
            listOfNotNull(
                slots.firstOrNull { it.at > now && it.at > last }?.at,
                win?.takeIf { it > now },
                end.takeIf { it > now },
                warmupEnd.takeIf { warmingUp && it > now },
                (warmupEnd + grace).takeIf { warmingUp && it > now },
                nextEnded,
            ).minOrNull()
        // "Almost ready" has no timer and no bar to refresh: no tick
        val tick =
            when {
                warmup && !waiting -> now + WARMUP_TICK
                countdown -> now + TICK
                else -> null
            }?.takeIf { event == null || it + TICK_WINDOW < event }
        return Plan(remind, countdown, ended, event, tick, warmup, waiting, ready, remindText, dropReminder)
    }

    /** Calendar days from [now] to [end] in [zone]: 0 = today, 1 = tomorrow … */
    fun dayOffset(
        end: Long,
        now: Long,
        zone: ZoneId,
    ): Long {
        fun day(t: Long): LocalDate = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(day(now), day(end))
    }

    /**
     * The end countdown's bar: the time gone in the window, per mille (0 at its start, 1000 at the end). It fills toward
     * the end like a journey's ProgressStyle bar and the warm-up's (#140); the timer says the time left.
     */
    fun endPerMille(
        end: Long,
        hours: Int,
        now: Long,
    ): Int {
        val span = hours * 3600L
        if (span <= 0) return 1000
        return 1000 - (((end - now).coerceIn(0, span) * 1000) / span).toInt()
    }

    /** The warm-up's bar: the time done, per mille. */
    fun warmupPerMille(
        start: Long,
        warmupEnd: Long,
        now: Long,
    ): Int {
        val span = warmupEnd - start
        if (span <= 0) return 1000
        return (((now - start).coerceIn(0, span) * 1000) / span).toInt()
    }

    /**
     * The memory for the sensor ([start], [end], [warmupEnd]) at [now]: a new sensor (or none before) starts fresh with
     * the slots already past not firing; the same sensor keeps what was shown with its end updated; a changed
     * [configHash] makes the slots already past not fire and brings a dismissed countdown back.
     */
    fun remember(
        mem: SensorEndMemory,
        start: Long,
        end: Long,
        warmupEnd: Long,
        configHash: Int,
        now: Long,
    ): SensorEndMemory {
        // the start is derived (end − wear length, ready − warm-up): the same end also means the same sensor
        val same = mem.known && (kotlin.math.abs(mem.start - start) <= SAME_SENSOR_S || kotlin.math.abs(mem.end - end) <= SAME_SENSOR_S)
        val m =
            if (same) {
                // an early end already past stays: a source still reporting the planned end can't bring the countdown back
                val stays = mem.end < end && mem.end <= now
                mem.copy(
                    end = if (stays) mem.end else end,
                    warmupEnd = if (mem.firstReading > 0) mem.warmupEnd else warmupEnd,
                    parked = false,
                )
            } else {
                SensorEndMemory(start = start, end = end, warmupEnd = warmupEnd, lastReminder = now, config = configHash)
            }
        return if (m.config != configHash) {
            m.copy(lastReminder = maxOf(m.lastReminder, now), countdownDismissed = false, config = configHash)
        } else {
            m
        }
    }
}
