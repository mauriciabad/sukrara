// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.testing.Contracts
import java.io.File

/**
 * The wearer under test in a scenario: the bare [AlertMachine] ([MachineDevice], `:core:alerts`) or the Android alert
 * runtime around it (`:monitor`). [handle] returns the effects the device applied at its clock's [t] (epoch s) and the
 * monotonic [elapsed] (s: the scenario's own time, so a `clock back` is measurable); [restart] is a process restart:
 * the device resumes from what it saved.
 */
interface ScenarioDevice {
    fun handle(input: AlertInput, t: Long, elapsed: Long): List<AlertEffect>

    fun restart(settings: AlertSettings, doses: Doses, sensor: SensorWindow?, t: Long, elapsed: Long): List<AlertEffect>
}

/** The machine alone; a restart goes through the saved state's JSON like the runtime's store. */
class MachineDevice(settings: AlertSettings, t0: Long) : ScenarioDevice {
    private var machine = AlertMachine(AlertRole.WEARER, settings).also { it.start(null, null, null, t0, t0) }

    override fun handle(input: AlertInput, t: Long, elapsed: Long) = machine.handle(input, t, elapsed)

    override fun restart(settings: AlertSettings, doses: Doses, sensor: SensorWindow?, t: Long, elapsed: Long): List<AlertEffect> {
        val saved = AlertStateCodec.decode(AlertStateCodec.encode(machine.snapshot()))
        val last = machine.engine.last
        machine = AlertMachine(AlertRole.WEARER, settings, doses)
        return machine.start(saved, last, sensor, t, elapsed)
    }
}

/**
 * The shared alert timelines (contracts/alerts/scenarios/\*.txt; format and driver in contracts/alerts/SPEC.md §7) run
 * through a wearer [ScenarioDevice], and for `mirror` scenarios a follower machine mirroring it fed with the wearer's
 * [AlertEffect.Mirror] events. Strict: an unknown line, event or setting fails the scenario.
 */
object AlertScenarios {
    data class Line(val minute: Int, val at: Int, val events: List<String>, val expect: List<String>)

    data class Scenario(
        val file: String,
        val title: String,
        val settings: List<String>,
        val lines: List<Line>,
        val end: Int,
        val mirror: Boolean,
    )

    /** A whole minute. */
    private const val T0 = 1_760_000_040L

    fun dir(): File = Contracts.root.resolve("alerts/scenarios")

    fun load(): List<Scenario> {
        val files = dir().listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        check(files.isNotEmpty()) { "no scenario files in ${dir()}" }
        return files.flatMap { f -> parse(f.name, f.readText()).also { check(it.isNotEmpty()) { "${f.name}: no scenarios" } } }
    }

    fun parse(file: String, text: String): List<Scenario> {
        val out = mutableListOf<Scenario>()
        var title: String? = null
        var settings = emptyList<String>()
        var lines = mutableListOf<Line>()
        var end: Int? = null
        var mirror = false

        fun flush() {
            val t = title ?: return
            out += Scenario(file, t, settings, lines, end ?: lines.maxOf { it.minute }, mirror)
            title = null
            settings = emptyList()
            lines = mutableListOf()
            end = null
            mirror = false
        }
        for (raw in text.lines()) {
            val l = raw.substringBefore('#').trim()
            if (l.isEmpty()) continue
            when {
                l.startsWith("scenario ") -> {
                    flush()
                    title = l.removePrefix("scenario ").trim()
                }

                l.startsWith("settings ") -> {
                    settings = l.removePrefix("settings ").trim().split(Regex("\\s+"))
                }

                l.startsWith("end ") -> {
                    end = l.removePrefix("end ").trim().toInt()
                }

                l == "mirror" -> {
                    mirror = true
                }

                else -> {
                    requireNotNull(title) { "$file: a line before any scenario: $raw" }
                    val (left, right) = if ("=>" in l) l.substringBefore("=>") to l.substringAfter("=>") else l to ""
                    val time = left.trim().substringBefore(' ')
                    require(time.matches(Regex("""\d+(:\d{2})?"""))) { "$file: not a time: $raw" }
                    val at = time.split(':').let { it[0].toInt() * 60 + (it.getOrNull(1)?.toInt() ?: 0) }
                    val events =
                        left
                            .trim()
                            .removePrefix(time)
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    val expect = right.split(';').map { it.trim() }.filter { it.isNotEmpty() && it != "nothing" }
                    require(lines.none { it.minute == at / 60 }) { "$file: two lines for minute ${at / 60}" }
                    lines += Line(at / 60, at, events, expect)
                }
            }
        }
        flush()
        return out
    }

    /** One `key=value` of a `settings` line or `set` event on top of [s]. */
    fun set(s: AlertSettings, kv: String): AlertSettings {
        val (k, v) = kv.split('=', limit = 2).also { require(it.size == 2) { "not key=value: $kv" } }.let { it[0] to it[1] }

        fun quiet(q: QuietRule) = if (v == "off") q.copy(on = false) else QuietRule(on = true, min = v.toInt())
        return when (k) {
            "alerts" -> {
                s.copy(on = v == "on")
            }

            "carbs.mute" -> {
                s.copy(afterCarbs = quiet(s.afterCarbs))
            }

            "insulin.mute" -> {
                s.copy(afterInsulin = quiet(s.afterInsulin))
            }

            "falling.mute" -> {
                s.copy(fallingHigh = quiet(s.fallingHigh))
            }

            "mute.min" -> {
                s.copy(actions = s.actions.copy(muteMin = v.toInt()))
            }

            "relax.min" -> {
                s.copy(actions = s.actions.copy(relaxMin = v.toInt()))
            }

            "back.mgdl" -> {
                s.copy(actions = s.actions.copy(backMgdl = v.toInt()))
            }

            "back.min" -> {
                s.copy(actions = s.actions.copy(backMin = v.toInt()))
            }

            "persist.low" -> {
                s.copy(persistence = s.persistence.copy(lowMin = v.toInt()))
            }

            "persist.high" -> {
                s.copy(persistence = s.persistence.copy(highMin = v.toInt()))
            }

            "persist.margin" -> {
                s.copy(persistence = s.persistence.copy(marginMgdl = v.toInt()))
            }

            else -> {
                val kind = requireNotNull(AlertKind.fromWire(k.substringBefore('.'))) { "unknown setting $kv" }
                val r = s.rule(kind)
                s.withRule(
                    kind,
                    when (k.substringAfter('.', "")) {
                        "" -> {
                            when (v) {
                                "on" -> r.copy(on = true)
                                "off" -> r.copy(on = false)
                                else -> r.copy(threshold = v.toDouble())
                            }
                        }

                        "repeat" -> {
                            r.copy(repeatMin = v.toInt())
                        }

                        else -> {
                            error("unknown setting $kv")
                        }
                    },
                )
            }
        }
    }

    /** A scenario's length ("30", "2h", "1 h 30", "1:30") in minutes. */
    fun minutes(text: String): Int {
        val t = text.trim().lowercase()
        Regex("""(\d+):(\d{2})""").matchEntire(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        val m = requireNotNull(Regex("""(?:(\d+)\s*h)?\s*(?:(\d+)\s*(?:min|m)?)?""").matchEntire(t)) { "not a duration: $text" }
        require(t.isNotEmpty()) { "empty duration" }
        return (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }

    /**
     * Runs [sc] (driver: SPEC §7) on the wearer [device] made from the scenario's settings and start time; one line per
     * minute that differs, empty when it all matches.
     */
    fun run(sc: Scenario, device: (AlertSettings, Long) -> ScenarioDevice = ::MachineDevice): List<String> {
        var s = sc.settings.fold(AlertSettings()) { acc, kv -> set(acc, kv) }
        val got = HashMap<Int, MutableList<String>>()
        // the scenario's time (the monotonic clock); the wearer's own clock is `wall()`, moved by `clock back`
        var t = T0
        var offset = 0L

        fun wall() = t + offset

        fun note(what: String) {
            got.getOrPut(((t - T0) / 60).toInt()) { mutableListOf() } += what
        }

        val wearer = device(s, T0)

        // the follower's own no_data isn't run (SPEC §7)
        fun followerSettings() = s.withRule(AlertKind.NO_DATA, s.rule(AlertKind.NO_DATA).copy(on = false))
        val follower = AlertMachine(AlertRole.FOLLOWER_MIRROR, followerSettings()).also { it.start(null, null, null, T0, T0) }
        val doses = mutableListOf<Pair<TreatmentKind, Long>>()
        val deleted = mutableSetOf<Int>()
        var sensor: SensorWindow? = null

        // when each device asked to be woken, on the scenario's time
        var timer: Long? = null
        var followerTimer: Long? = null

        fun followerSaw(effects: List<AlertEffect>) =
            effects.forEach { e ->
                when (e) {
                    is AlertEffect.Fire -> note("follower ${Headlines.english(e.firing.kind, e.firing.treated)}")
                    is AlertEffect.Clear -> note("follower clear ${e.kind.wire}")
                    is AlertEffect.Back -> note("follower back ${e.side.wire}")
                    is AlertEffect.Mirror -> error("a follower never publishes")
                    is AlertEffect.Schedule -> followerTimer = e.at
                }
            }

        // the follower is another device: its clock is the scenario's
        fun follow(input: AlertInput) {
            if (sc.mirror) followerSaw(follower.handle(input, t, t))
        }

        fun wearer(input: AlertInput) = wearer.handle(input, wall(), t)

        fun wearerDid(effects: List<AlertEffect>) =
            effects.forEach { e ->
                when (e) {
                    is AlertEffect.Fire -> {
                        note(
                            if (e.silent) "resume ${e.firing.kind.wire}" else Headlines.english(e.firing.kind, e.firing.treated),
                        )
                    }

                    is AlertEffect.Clear -> {
                        note("clear ${e.kind.wire}")
                    }

                    is AlertEffect.Back -> {
                        note("back ${e.side.wire}")
                    }

                    is AlertEffect.Mirror -> {
                        follow(AlertInput.Remote(e.event))
                    }

                    is AlertEffect.Schedule -> {
                        timer = e.at?.let { it - offset }
                    }
                }
            }

        fun doses() =
            Doses(
                carbs = doses.filterIndexed { i, d -> i !in deleted && d.first == TreatmentKind.CARBS }.map { it.second },
                rapid = doses.filterIndexed { i, d -> i !in deleted && d.first == TreatmentKind.RAPID }.map { it.second },
            )

        var glucose = 0
        var rate: Double? = null
        var age = 0
        var sensorOn = true
        var every = 1
        var nextReading: Long? = null
        val byTime = sc.lines.associateBy { T0 + it.at }
        val stop = T0 + (sc.end + 1) * 60L

        fun allEnd(w: List<String>, from: Int, now: Long = wall()): Long =
            if (w.getOrNull(from) == "never") Rules.NEVER else now + minutes(w.drop(from).joinToString(" ")) * 60L

        fun kind(word: String) = requireNotNull(AlertKind.fromWire(word)) { "unknown kind $word" }

        fun length(w: List<String>, from: Int) =
            w
                .drop(from)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
                ?.let(::minutes) ?: s.actions.muteMin

        while (t < stop) {
            byTime[t]?.events?.forEach { ev ->
                val w = ev.split(Regex("\\s+"))
                when (w[0]) {
                    "glucose" -> {
                        glucose = w[1].toInt()
                        rate = w.indexOf("rate").takeIf { it > 0 }?.let { w[it + 1].toDouble() }
                        age = w.indexOf("age").takeIf { it > 0 }?.let { w[it + 1].toInt() } ?: 0
                        if (nextReading == null) nextReading = t
                    }

                    "readings" -> {
                        every = w[2].toInt()
                    }

                    "sensor" -> {
                        when (w[1]) {
                            "ended" -> {
                                sensorOn = false
                                sensor = SensorWindow(wall() - 15 * 86_400L, wall(), wall() - 15 * 86_400L + 3600)
                                wearerDid(wearer(AlertInput.Sensor(sensor)))
                            }

                            "started" -> {
                                sensorOn = false
                                sensor = SensorWindow.started(wall(), 15 * 1440)
                                wearerDid(wearer(AlertInput.Sensor(sensor)))
                            }

                            "on", "off" -> {
                                sensorOn = w[1] == "on"
                                if (sensorOn) nextReading = t
                            }

                            else -> {
                                error("unknown event: $ev")
                            }
                        }
                    }

                    "source" -> {
                        require(w.getOrNull(1) == "started") { "unknown event: $ev" }
                        wearerDid(wearer(AlertInput.Source(wall())))
                    }

                    "clock" -> {
                        require(w.getOrNull(1) == "back") { "unknown event: $ev" }
                        offset -= minutes(w.drop(2).joinToString(" ")) * 60L
                        wearerDid(wearer(AlertInput.Tick)) // the system's clock-change notice (TIME_SET)
                    }

                    "carbs", "insulin" -> {
                        doses += (if (w[0] == "carbs") TreatmentKind.CARBS else TreatmentKind.RAPID) to wall()
                        wearerDid(wearer(AlertInput.Treatments(doses())))
                    }

                    "delete" -> {
                        val k = if (w[1] == "carbs") TreatmentKind.CARBS else TreatmentKind.RAPID
                        deleted += doses.indices.last { it !in deleted && doses[it].first == k }
                        wearerDid(wearer(AlertInput.Treatments(doses())))
                    }

                    "mute" -> {
                        wearerDid(
                            wearer(
                                if (w[1] ==
                                    "all"
                                ) {
                                    AlertInput.MuteAll(allEnd(w, 2))
                                } else {
                                    AlertInput.Mute(kind(w[1]), length(w, 2))
                                },
                            ),
                        )
                    }

                    "relax" -> {
                        wearerDid(wearer(AlertInput.RemindLess(kind(w[1]))))
                    }

                    "unmute" -> {
                        wearerDid(wearer(if (w[1] == "all") AlertInput.UnmuteAll else AlertInput.Unmute(kind(w[1]))))
                    }

                    "unrelax" -> {
                        wearerDid(wearer(AlertInput.RemindAsUsual(kind(w[1]))))
                    }

                    "follower" -> {
                        require(sc.mirror) { "follower event without mirror: $ev" }
                        follow(
                            when (w[1]) {
                                "mute" -> {
                                    if (w[2] ==
                                        "all"
                                    ) {
                                        AlertInput.MuteAll(allEnd(w, 3, t))
                                    } else {
                                        AlertInput.Mute(kind(w[2]), length(w, 3))
                                    }
                                }

                                "relax" -> {
                                    AlertInput.RemindLess(kind(w[2]))
                                }

                                "unmute" -> {
                                    if (w[2] == "all") AlertInput.UnmuteAll else AlertInput.Unmute(kind(w[2]))
                                }

                                "unrelax" -> {
                                    AlertInput.RemindAsUsual(kind(w[2]))
                                }

                                else -> {
                                    error("unknown event: $ev")
                                }
                            },
                        )
                    }

                    "set" -> {
                        s = w.drop(1).fold(s) { acc, kv -> set(acc, kv) }
                        follow(AlertInput.Settings(followerSettings()))
                        wearerDid(wearer(AlertInput.Settings(s)))
                    }

                    // the process restarts: the device resumes from its saved state and last reading
                    "restart" -> {
                        wearerDid(wearer.restart(s, doses(), sensor, wall(), t))
                    }

                    else -> {
                        error("unknown event: $ev")
                    }
                }
            }
            val due = nextReading
            if (sensorOn && due != null && due <= t) {
                // stamped by the wearer's clock; the follower gets it with the wearer's own time
                val r = AlertReading(wall() - age * 60L, glucose, rate)
                wearerDid(wearer(AlertInput.Reading(r)))
                follow(AlertInput.Reading(r.copy(t = t - age * 60L)))
                nextReading = t + every * 60L
            } else if (timer.let { it != null && it <= t }) {
                wearerDid(wearer(AlertInput.Tick))
                follow(AlertInput.Tick)
            } else if (sc.mirror && followerTimer.let { it != null && it <= t }) {
                follow(AlertInput.Tick)
            }
            t =
                listOfNotNull(
                    byTime.keys.filter { it > t }.minOrNull(),
                    nextReading?.takeIf { sensorOn },
                    timer,
                    followerTimer?.takeIf { sc.mirror },
                ).filter { it > t }.minOrNull()
                    ?: break
        }

        // the wearer's and the follower's actions are compared apart (their order within a minute is free)
        fun split(l: List<String>) = l.partition { !it.startsWith("follower ") }.let { (w, f) -> w + f }
        val expected = sc.lines.associate { it.minute to it.expect }
        return (0..sc.end).mapNotNull { m ->
            val want = split(expected[m].orEmpty())
            val have = split(got[m].orEmpty())
            if (want ==
                have
            ) {
                null
            } else {
                "minute $m: expected ${want.ifEmpty { listOf("nothing") }}, got ${have.ifEmpty { listOf("nothing") }}"
            }
        }
    }
}

/** SPEC §1's English headlines (the apps word them from their own strings; the scenarios use these). */
object Headlines {
    private fun label(k: AlertKind) =
        when (k) {
            AlertKind.VERY_LOW -> "Very low"
            AlertKind.LOW -> "Low"
            AlertKind.HIGH -> "High"
            AlertKind.VERY_HIGH -> "Very high"
            AlertKind.EXTREMELY_HIGH -> "Extremely high"
            AlertKind.FALLING_FAST -> "Falling fast"
            AlertKind.RISING_FAST -> "Rising fast"
            AlertKind.NO_DATA -> "No data"
        }

    fun english(k: AlertKind, treated: Int): String {
        val base =
            when {
                k.isLevel -> "${label(k)} glucose"
                k == AlertKind.NO_DATA -> "No glucose data"
                else -> label(k)
            }
        val more =
            when {
                treated <= 0 || !k.isLevel -> null
                k.side == Side.LOW -> if (treated == 1) "eat more" else "eat more again"
                else -> if (treated == 1) "still high after insulin" else "still high after more insulin"
            }
        return if (more == null) base else "$base · $more"
    }
}
