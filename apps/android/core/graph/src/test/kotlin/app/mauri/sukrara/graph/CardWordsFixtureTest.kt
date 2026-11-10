// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.content.res.Configuration
import android.content.res.Resources
import app.mauri.sukrara.core.graph.R
import app.mauri.sukrara.graph.Fixtures.str
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Every word the card draws, English and Spanish, against contracts/graph/words/words.json (#77): the Mac's CardWords
 * are checked against the same file (CardWordsFixtureTests), so both platforms draw the same words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardWordsFixtureTest {
    private val file = Fixtures.read("words/words.json")

    private fun resources(language: String): Resources {
        val base = RuntimeEnvironment.getApplication()
        val conf = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return base.createConfigurationContext(conf).resources
    }

    private fun id(name: String): Int {
        val field = R.string::class.java.fields.firstOrNull { it.name == name } ?: error("no string $name")
        return field.getInt(null)
    }

    private fun pluralId(name: String): Int {
        val field = R.plurals::class.java.fields.firstOrNull { it.name == name } ?: error("no plurals $name")
        return field.getInt(null)
    }

    @Test
    fun everyDrawnWordInBothLanguages() {
        val words = file["words"]!!.jsonObject
        assertTrue(words.size >= 30)
        for (language in listOf("en", "es")) {
            val r = resources(language)
            for ((name, byLanguage) in words) {
                assertEquals("$language $name", byLanguage.jsonObject[language].str(), r.getString(id(name)))
            }
        }
    }

    @Test
    fun timeLeftInBothLanguages() {
        val left = file["plurals"]!!.jsonObject["badge_time_left"]!!.jsonObject
        for (language in listOf("en", "es")) {
            val r = resources(language)
            val want = left[language]!!.jsonObject
            val id = pluralId("badge_time_left")
            assertEquals("$language one", want["one"].str(), r.getQuantityText(id, 1).toString())
            assertEquals("$language other", want["other"].str(), r.getQuantityText(id, 2).toString())
        }
    }
}
