// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import org.junit.Assert.assertEquals
import org.junit.Test

/** *Share your data with your clinic*: either LibreView or Tidepool set up hides it (#147). */
class ClinicSetTest {
    @Test fun eitherServiceIsTheClinicUpload() {
        assertEquals(true, clinicSet(wearer = true, libreView = true, tidepool = false))
        assertEquals(true, clinicSet(wearer = true, libreView = false, tidepool = true))
        assertEquals(true, clinicSet(wearer = true, libreView = null, tidepool = true))
        assertEquals(false, clinicSet(wearer = true, libreView = false, tidepool = false))
    }

    @Test fun unknownUntilBothStoresWereRead() {
        assertEquals(null, clinicSet(wearer = true, libreView = false, tidepool = null))
        assertEquals(null, clinicSet(wearer = true, libreView = null, tidepool = false))
    }

    @Test fun neverOnAFollowersPhone() {
        assertEquals(null, clinicSet(wearer = false, libreView = false, tidepool = false))
    }
}
