// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.feature.sharing.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the source change dialog says is true of the change (docs/44 Appendix G finding 18): someone else's glucose
 * isn't "yours", a follow already allowed doesn't wait for a request, a pairing that doesn't exist doesn't stay, and the
 * Libre app's sensor taken over is said.
 */
class SourceChangeTextTest {
    private fun spec(
        person: Person,
        kind: SourceKind,
    ) = SourceSpec(person, kind)

    private fun text(
        to: SourceSpec,
        from: SourceSpec?,
        paired: Boolean = false,
        following: Boolean = false,
    ) = sourceChangeText(SourceChange(to, loss = null, replaces = from, paired = paired), following)

    @Test
    fun `someone else's glucose is theirs, not yours`() {
        val stream = spec(Person.Theirs, SourceKind.Sukrara)
        assertEquals(R.string.sh_setup_source_change_llu_theirs, text(spec(Person.Theirs, SourceKind.LibreLinkUp), stream))
        assertEquals(R.string.sh_setup_source_change_nightscout_theirs, text(spec(Person.Theirs, SourceKind.Nightscout), stream))
        assertEquals(R.string.sh_setup_source_change_llu, text(spec(Person.Yours, SourceKind.LibreLinkUp), null))
        assertEquals(R.string.sh_setup_source_change_nightscout, text(spec(Person.Yours, SourceKind.Nightscout), null))
    }

    @Test
    fun `a follow already allowed shows again, no request to accept`() {
        val to = spec(Person.Theirs, SourceKind.Sukrara)
        val from = spec(Person.Theirs, SourceKind.PhoneApp)
        assertEquals(R.string.sh_setup_source_change_sukrara_again, text(to, from, following = true))
        assertEquals(R.string.sh_setup_source_change_sukrara, text(to, from, following = false))
    }

    @Test
    fun `the pairing stays only when a sensor is paired`() {
        val llu = spec(Person.Yours, SourceKind.LibreLinkUp)
        val juggluco = spec(Person.Yours, SourceKind.PhoneApp)
        assertEquals(R.string.sh_setup_source_change_llu, text(llu, SourceSpec.YourSensor, paired = false))
        assertEquals(R.string.sh_setup_source_change_llu_from_sensor, text(llu, SourceSpec.YourSensor, paired = true))
        assertEquals(R.string.sh_setup_source_change_phone_app, text(juggluco, SourceSpec.YourSensor, paired = false))
        assertEquals(R.string.sh_setup_source_change_phone_app_from_sensor, text(juggluco, SourceSpec.YourSensor, paired = true))
    }

    @Test
    fun `taking the sensor from LibreLinkUp's Libre app says so`() {
        assertEquals(
            R.string.sh_setup_source_change_sensor_from_llu,
            text(SourceSpec.YourSensor, spec(Person.Yours, SourceKind.LibreLinkUp)),
        )
        assertEquals(R.string.sh_setup_source_change_sensor, text(SourceSpec.YourSensor, spec(Person.Yours, SourceKind.PhoneApp)))
    }
}
