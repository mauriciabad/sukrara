// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.settings.SettingsSpec

/**
 * Whose glucose this phone shows (schema `source.person`, docs/44 "Person × Source"): the person's own, or someone
 * else's (a child, a partner, a friend). It decides the words (*Your glucose* / *Ana's glucose*) and what a change
 * deletes: a person change deletes this phone's readings and treatments (confirmed first), a source change keeps them.
 */
enum class Person(
    val id: String,
) {
    Yours("yours"),

    Theirs("theirs"),
    ;

    companion object {
        fun of(id: String?): Person? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What a source says of the sensor's life (contracts/sources/sources.json `life`, docs/44 decision 10): [Full] = its
 * start and length; [Start] = the start, not the length (the source's wear setting gives it); [MaybeStart] = a start
 * only when one is logged (a Nightscout site's *Sensor Start*); [None] = nothing. A manual start (your glucose) is
 * offered where the start may be missing ([manualStart]).
 */
enum class SourceLife {
    Full,
    Start,
    MaybeStart,
    None,
    ;

    val id: String get() = name.replaceFirstChar(Char::lowercase)

    /** The person may set when the sensor started (the Sensor page): the source may not say it. */
    val manualStart: Boolean get() = this == MaybeStart || this == None
}

/** How the source picker groups a source: by what the person feels (contracts/sources/sources.json `group`). */
enum class SourceGroup { Offline, Network, Online, Sukrara }

/**
 * Where the glucose comes from (schema `source.kind`), with what it declares in contracts/sources/sources.json: the
 * persons it can bring ([persons]), whether this phone gets the data itself ([firstHand]) or another Sukrara relays it,
 * whether it works offline ([offline]), the picker's [group], the usual delay from the sensor ([delaySeconds]), whether
 * it fills gaps ([backfill]), the reading ids it stores ([readings]) and what it says of the sensor's life ([life], with
 * the setting that holds its wear length when it doesn't say it: [wear]). Rules and screens read these, never the id
 * (`SourcesContractTest`). The Android sources only: the Mac's Juggluco on the network is the Mac's.
 */
enum class SourceKind(
    val id: String,
    val persons: Set<Person>,
    val firstHand: Boolean,
    val offline: Boolean,
    val group: SourceGroup,
    val delaySeconds: Int,
    val backfill: Boolean,
    val readings: Set<SourceId>,
    /** What it says of the sensor's life (sources.json `life`): the start, the length, both or neither. */
    val life: SourceLife,
    /**
     * The stored key (in the source file, `SettingsSpec.Keys`) of this source's wear length, for a source that doesn't
     * say it (sources.json `wear`); null = the source says it.
     */
    val wear: String?,
) {
    /** Sukrara reads the sensor itself (a Libre 3 by NFC + Bluetooth, the other families by Bluetooth). */
    Sensor(
        "sensor",
        setOf(Person.Yours),
        firstHand = true,
        offline = true,
        group = SourceGroup.Offline,
        delaySeconds = 60,
        backfill = true,
        readings = setOf(SourceId.Libre3, SourceId.DexcomG7, SourceId.DexcomG6, SourceId.SmartGuide, SourceId.AidexX),
        life = SourceLife.Full,
        wear = null,
    ),

    /** An app on this phone that reads the sensor and serves its readings: Juggluco's local web server. */
    PhoneApp(
        "phoneApp",
        setOf(Person.Yours, Person.Theirs),
        firstHand = true,
        offline = true,
        group = SourceGroup.Offline,
        delaySeconds = 60,
        backfill = true,
        readings = setOf(SourceId.Juggluco),
        life = SourceLife.Start,
        wear = SettingsSpec.Keys.JUGGLUCO_WEAR,
    ),

    /**
     * A Nightscout site (docs/44 step 1): the person's own, or someone else's (a parent reading a child's site). Online,
     * a few minutes late (as fresh as what uploads to it), backfills, and treatments go both ways with the site.
     */
    Nightscout(
        "nightscout",
        setOf(Person.Yours, Person.Theirs),
        firstHand = true,
        offline = false,
        group = SourceGroup.Online,
        delaySeconds = 120,
        backfill = true,
        readings = setOf(SourceId.Nightscout),
        life = SourceLife.MaybeStart,
        wear = SettingsSpec.Keys.NIGHTSCOUT_WEAR,
    ),

    /**
     * LibreLinkUp (docs/44 step 2): Abbott's follower service, which the Libre app uploads to each minute. The person's
     * own (an account that follows itself) or someone else's (a parent's account following a child). Online, a minute or
     * two late, backfills about 12 h (the graph's points only fill gaps: [SourceId.LibreLinkUpGraph]); brings the sensor's
     * start, no treatments. Its data never goes back to LibreView (the Libre app uploads there).
     */
    LibreLinkUp(
        "libreLinkUp",
        setOf(Person.Yours, Person.Theirs),
        firstHand = true,
        offline = false,
        group = SourceGroup.Online,
        delaySeconds = 90,
        backfill = true,
        readings = setOf(SourceId.LibreLinkUp, SourceId.LibreLinkUpGraph),
        life = SourceLife.Start,
        wear = SettingsSpec.Keys.LIBRE_LINK_UP_WEAR,
    ),

    /** Sukrara on another phone or Mac shares it (over the internet, or Bluetooth nearby): a Sukrara stream. */
    Sukrara(
        "sukrara",
        setOf(Person.Yours, Person.Theirs),
        firstHand = false,
        offline = false,
        group = SourceGroup.Sukrara,
        delaySeconds = 60,
        backfill = true,
        readings = setOf(SourceId.Relay, SourceId.Nearby),
        life = SourceLife.Full,
        wear = null,
    ),
    ;

    companion object {
        fun of(id: String?): SourceKind? = entries.firstOrNull { it.id == id }

        /** The sources the picker offers for [person], in the picker's order (grouped). */
        fun forPerson(person: Person): List<SourceKind> = entries.filter { person in it.persons }
    }
}

/**
 * THE choice that replaced the wearer / follower role (docs/44): whose glucose × where it comes from. Every behaviour
 * that was the role's is keyed here, as contracts/sources/sources.json `keying` says (`SourcesContractTest`):
 *
 * - [owner]: your glucose, read first-hand. This phone owns it: it shares it with Sukrara followers, uploads it, reads
 *   insulin pens, keeps sensor reminders, offers Import history, alerts as the wearer ([alertRole] WEARER).
 * - [stream]: a Sukrara stream (your other phone, or someone else's): the follower engine, *Settings from*,
 *   escalation, writer scope, the Following rows.
 * - [person]: the words (*Your glucose* / the person's name) and what a change deletes.
 *
 * Only a valid pair exists ([of]): someone else's glucose never comes from a sensor this phone reads.
 */
data class SourceSpec(
    val person: Person,
    val kind: SourceKind,
) {
    init {
        require(person in kind.persons) { "${kind.id} doesn't bring ${person.id} glucose" }
    }

    /** Your glucose, read first-hand by this phone: it shares, uploads and alerts as the wearer. */
    val owner: Boolean get() = person == Person.Yours && kind.firstHand

    /** A Sukrara stream: another Sukrara relays the glucose (the follower engine runs). */
    val stream: Boolean get() = kind == SourceKind.Sukrara

    /** Someone else's glucose: the card names them, nothing of it is shared or uploaded. */
    val theirs: Boolean get() = person == Person.Theirs

    /**
     * Your glucose that the official Libre app uploads to LibreView (LibreLinkUp as the source): LibreView takes one
     * uploading device per account, so Sukrara sends it nothing (docs/44 "Data" rule 6) until it reads the sensor.
     */
    val officialAppUploads: Boolean get() = person == Person.Yours && kind == SourceKind.LibreLinkUp

    /** An online source (a Nightscout site, LibreLinkUp): needs internet, readings a few minutes late, no Bluetooth of its own. */
    val online: Boolean get() = kind.group == SourceGroup.Online

    /** This phone talks to the sensor itself (Bluetooth, NFC). */
    val readsSensor: Boolean get() = kind == SourceKind.Sensor

    /**
     * The alert engine's role, unchanged from the roles it replaces: WEARER for your glucose read first-hand; a Sukrara
     * stream with *Settings from: Wearer* mirrors the wearer's alerts ([mirrorsWearer]); every other case runs this
     * phone's own rules.
     */
    fun alertRole(mirrorsWearer: Boolean): SourceAlertRole =
        when {
            owner -> SourceAlertRole.Wearer
            stream && mirrorsWearer -> SourceAlertRole.FollowerMirror
            else -> SourceAlertRole.FollowerOwn
        }

    companion object {
        /** The pair, or null when no app offers it (someone else's glucose from this phone's sensor). */
        fun of(
            person: Person?,
            kind: SourceKind?,
        ): SourceSpec? = if (person != null && kind != null && person in kind.persons) SourceSpec(person, kind) else null

        /** Your glucose from the sensor Sukrara reads: the usual start. */
        val YourSensor = SourceSpec(Person.Yours, SourceKind.Sensor)

        /** Someone else's glucose from their Sukrara: what following was. */
        val TheirSukrara = SourceSpec(Person.Theirs, SourceKind.Sukrara)
    }
}

/** The alert engine's role a [SourceSpec] runs as (`:core:alerts` AlertRole, which this module can't see). */
enum class SourceAlertRole { Wearer, FollowerMirror, FollowerOwn }
