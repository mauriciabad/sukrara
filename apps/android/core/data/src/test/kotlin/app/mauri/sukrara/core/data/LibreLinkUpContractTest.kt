// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.data.libreview.LibreViewStatus
import app.mauri.sukrara.core.data.libreview.LluCallOutcome
import app.mauri.sukrara.core.data.libreview.LluLogin
import app.mauri.sukrara.core.data.libreview.LluPick
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** THE LibreLinkUp client's rules ([LibreLinkUp]) against contracts/sources/librelinkup (login, calls, people, graph). */
class LibreLinkUpContractTest {
    private fun file(name: String) = Json.parseToJsonElement(Contracts.file("sources/librelinkup/$name").readText()).jsonObject

    private fun JsonObject.cases() = getValue("cases").jsonArray.map { it.jsonObject }

    private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

    private fun JsonObject.long(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it != JsonNull }?.longOrNull

    @Test
    fun `every login answer means what the contract says`() {
        for (c in file("login.json").cases()) {
            val name = c.str("name")!!
            val e = c.getValue("expect").jsonObject
            val got =
                LibreLinkUp.parseLogin(
                    c.getValue("code").jsonPrimitive.intOrNull!!,
                    c.getValue("body").toString(),
                    c.long("retryAfter"),
                )
            val want: LluLogin =
                when (e.str("answer")) {
                    "account" -> {
                        LluLogin.Account(
                            e.str("accountId")!!,
                            e.str("token")!!,
                            java.time.Instant.ofEpochSecond(e.long("expires")!!),
                        )
                    }

                    "redirect" -> {
                        LluLogin.Redirect(e.str("region")!!)
                    }

                    "badCredentials" -> {
                        LluLogin.BadCredentials
                    }

                    "terms" -> {
                        LluLogin.Terms(e.str("step"))
                    }

                    "rateLimited" -> {
                        LluLogin.RateLimited(e.long("retryAfter_s"))
                    }

                    "version" -> {
                        LluLogin.Version(e.str("minimumVersion")!!)
                    }

                    "retired" -> {
                        LluLogin.Retired
                    }

                    "maintenance" -> {
                        LluLogin.Maintenance
                    }

                    else -> {
                        LluLogin.Failed("")
                    }
                }
            if (want is LluLogin.Failed) {
                assertEquals(name, true, got is LluLogin.Failed)
            } else if (want is LluLogin.Account) {
                val a = got as LluLogin.Account
                assertEquals(name, listOf(want.accountId, want.token, want.expires), listOf(a.accountId, a.token, a.expires))
            } else {
                assertEquals(name, want, got)
            }
        }
    }

    @Test
    fun `every authenticated answer has the contract's outcome`() {
        for (c in file("calls.json").cases()) {
            val e = c.getValue("expect").jsonObject
            val got =
                LibreLinkUp.parseCall(
                    c.getValue("code").jsonPrimitive.intOrNull!!,
                    c.getValue("body").toString(),
                    c.long("retryAfter"),
                )
            val name = c.str("name")!!
            assertEquals(name, e.str("outcome"), got.outcome.name.replaceFirstChar(Char::lowercase))
            if (got.outcome == LluCallOutcome.RateLimited) assertEquals(name, e.long("retryAfter_s"), got.retryAfterS)
            if (got.outcome == LluCallOutcome.Version) assertEquals(name, e.str("minimumVersion"), got.minimumVersion)
        }
    }

    @Test
    fun `whose glucose is picked as the contract says`() {
        val f = file("connections.json")
        val account = f.str("account")!!
        for (c in f.cases()) {
            val e = c.getValue("expect").jsonObject
            val people =
                LibreLinkUp.people(
                    JsonObject(
                        mapOf(
                            "status" to kotlinx.serialization.json.JsonPrimitive(0),
                            "data" to c.getValue("data"),
                        ),
                    ),
                )
            val got = LibreLinkUp.pick(people, account, c.str("person") == "yours", c.str("saved"))
            val name = c.str("name")!!
            assertEquals(
                name,
                e.getValue("choices").jsonArray.map { it.jsonObject.str("id") to it.jsonObject.str("name") },
                people.map {
                    it.id to
                        it.name
                },
            )
            assertEquals(name, e.str("state"), got.state.name.replaceFirstChar(Char::lowercase))
            assertEquals(name, e.str("pick"), got.pick)
            if (got.state == LluPick.State.Picked) assertEquals(name, true, got.pick != null)
        }
    }

    @Test
    fun `every graph answer brings the contract's readings and sensor`() {
        for (c in file("graph.json").cases()) {
            val e = c.getValue("expect").jsonObject
            val g = LibreLinkUp.graph(c.getValue("body").jsonObject)
            val name = c.str("name")!!
            val want =
                e.getValue("readings").jsonArray.map {
                    val r = it.jsonArray
                    listOf(
                        r[0].jsonPrimitive.longOrNull,
                        r[1].jsonPrimitive.intOrNull,
                        r[2].jsonPrimitive.content,
                        r[3].jsonPrimitive.doubleOrNull,
                    )
                }
            assertEquals(name, want, g.readings.map { listOf(it.t.epochSecond, it.mgdl, it.source.id, it.sensorRate) })
            val sensor = e["sensor"] as? JsonObject
            assertEquals(name, sensor?.str("serial"), g.sensor?.serial)
            assertEquals(name, sensor?.long("start"), g.sensor?.start?.epochSecond)
            assertEquals(name, e.str("name"), g.name)
        }
    }

    @Test
    fun `the headers name the contract's product and version`() {
        val h = file("poll.json").getValue("headers").jsonObject
        assertEquals(h.str("product"), LibreLinkUp.PRODUCT)
        assertEquals(h.str("version"), LibreLinkUp.VERSION)
    }

    @Test
    fun `LibreView's maintenance windows are read from the status page as the contract says`() {
        val f = file("status.json")
        assertEquals(f.str("url"), LibreViewStatus.URL)
        assertEquals(f.long("every_s"), LibreViewStatus.EVERY_S)
        assertEquals(f.long("maintenanceEvery_s"), LibreViewStatus.MAINTENANCE_EVERY_S)
        assertEquals(f.long("warnHome_s"), LibreViewStatus.WARN_HOME_S)
        assertEquals(f.long("notify_s"), LibreViewStatus.NOTIFY_S)
        assertEquals(
            f.getValue("regions").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } },
            LibreViewStatus.REGIONS,
        )
        assertEquals(
            f
                .getValue("groups")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet(),
            LibreViewStatus.GROUPS,
        )
        for (c in f.cases()) {
            val name = c.str("name")!!
            val e = c.getValue("expect").jsonObject
            val now = java.time.Instant.ofEpochSecond(c.long("now")!!)
            val ws = LibreViewStatus.windows(c.getValue("body").jsonObject, c.str("region"))
            assertEquals(
                name,
                e.getValue("windows").jsonArray.map {
                    val w = it.jsonObject
                    Triple(
                        w.long("start"),
                        w.long("end"),
                        w
                            .getValue("live")
                            .jsonPrimitive.content
                            .toBoolean(),
                    )
                },
                ws.map { Triple(it.start.epochSecond, it.end.epochSecond, it.live) },
            )
            assertEquals(name, e.long("until"), LibreViewStatus.until(ws, now)?.epochSecond)
            val up = e["upcoming"] as? JsonObject
            val got = LibreViewStatus.upcoming(ws, now)
            assertEquals(name, up?.long("start") to up?.long("end"), got?.start?.epochSecond to got?.end?.epochSecond)
        }
    }

    @Test
    fun `the version sent is the one asked for unless this build's is newer`() {
        for (c in file("poll.json").getValue("versions").jsonArray.map { it.jsonObject }) {
            val asked = c.str("asked")
            val send = asked?.takeIf { LibreLinkUp.newer(it, LibreLinkUp.VERSION) } ?: LibreLinkUp.VERSION
            assertEquals(c.str("name"), c.str("send"), send)
        }
    }
}
