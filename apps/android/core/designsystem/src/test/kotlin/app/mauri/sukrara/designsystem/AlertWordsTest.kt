// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.alerts.AlertWords
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #181: the mute and night lines' words are found by the ids written in code ([AlertWords.Ids]), never only by their
 * name: resource shrinking removed the strings found by name from 0.13–0.14.1's release builds, and the lookup crashed
 * the screen that showed a mute or the night alerts (opening *Night alerts* from the search), monitoring included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertWordsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun names(dir: String): Set<String> {
        val nodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/$dir/strings.xml"))
                .getElementsByTagName("string")
        return (0 until nodes.length)
            .map {
                nodes
                    .item(it)
                    .attributes
                    .getNamedItem("name")
                    .nodeValue
            }.toSet()
    }

    @Test
    fun everyMuteSideAndNightStringHasItsId() {
        val byName = names("values").filter { it.startsWith("mute_") || it.startsWith("side_") || it == "night_until" }.toSet()
        assertEquals(byName, AlertWords.Ids.keys)
    }

    @Test
    fun eachNameReadsItsString() {
        val words = AlertWords.of(context)
        for ((name, id) in AlertWords.Ids) assertEquals(name, context.getString(id), words.get(name))
    }

    /** A name in no table and not in the resources reads as itself: a wrong line, never a crash. */
    @Test
    fun anUnknownNameIsNotACrash() {
        assertEquals("no_such_string", AlertWords.of(context).get("no_such_string"))
    }
}
