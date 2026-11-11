// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.integrations.libreview.LibreViewUploader
import app.mauri.sukrara.integrations.localserver.NightscoutApiServer
import app.mauri.sukrara.integrations.nightscout.NightscoutUploader
import app.mauri.sukrara.integrations.tidepool.TidepoolUploader
import app.mauri.sukrara.sensor.pairing.PairingStore
import app.mauri.sukrara.share.follower.FollowerStore
import app.mauri.sukrara.share.owner.OwnerIdentityStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every secret a module keeps is one the app's SecureStore accepts: an unlisted name is refused at the first write
 * (a follower couldn't join: "Couldn't save the key on this phone", found with the local test relay, issue #155).
 */
class SecretNamesTest {
    @Test fun everyStoredSecretIsListed() {
        val used =
            setOf(
                OwnerIdentityStore.NAME,
                FollowerStore.NAME,
                NightscoutUploader.NAME,
                NightscoutApiServer.SECRET,
                NightscoutApiServer.LAN_SECRET,
                TidepoolUploader.NAME,
                TidepoolUploader.EMAIL,
                LibreViewUploader.NAME,
                LibreViewLogin.NAME,
                PairingStore.NAME,
                SecretName.JUGGLUCO, // JugglucoSecret writes it by this name
            )
        assertEquals(SecretName.ALL, used)
    }
}
