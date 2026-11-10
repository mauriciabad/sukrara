// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every module's English strings have their Spanish twins (CLAUDE.md "Strings"): the same `string`, `plurals` and
 * `string-array` names in `values/strings.xml` and `values-es/strings.xml`, the same format arguments, the ellipsis
 * character instead of three dots, and Spanish *queda / quedan* agreeing with the time left.
 */
class StringsParityTest {
    /** One translatable resource: its tag and name, and the texts of its items (one for a `string`). */
    private data class Res(
        val tag: String,
        val name: String,
        val items: Map<String, String>,
    )

    private val modules: List<File> =
        File("..")
            .canonicalFile
            .walk()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.name == "strings.xml" && it.parentFile.name == "values" && it.path.endsWith("src/main/res/values/strings.xml") }
            .map { it.parentFile.parentFile }
            .sortedBy { it.path }
            .toList()

    private fun read(file: File): Map<String, Res> {
        if (!file.exists()) return emptyMap()
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        val children = root.childNodes
        return (0 until children.length)
            .mapNotNull { children.item(it) as? Element }
            .filter { it.tagName in TAGS && it.getAttribute("translatable") != "false" }
            .associate { e ->
                val items =
                    if (e.tagName == "string") {
                        mapOf("" to e.textContent)
                    } else {
                        val list = e.getElementsByTagName("item")
                        (0 until list.length).associate { i ->
                            val item = list.item(i) as Element
                            (item.getAttribute("quantity").ifEmpty { i.toString() }) to item.textContent
                        }
                    }
                "${e.tagName}:${e.getAttribute("name")}" to Res(e.tagName, e.getAttribute("name"), items)
            }
    }

    private fun Res.args(): Set<String> =
        items.values
            .flatMap { FORMAT.findAll(it).map { m -> m.value }.filter { a -> a != "%%" } }
            .toSet()

    private fun eachModule(check: (module: String, en: Map<String, Res>, es: Map<String, Res>) -> Unit) {
        assertTrue("no module strings found from ${File("..").canonicalPath}", modules.size > 5)
        for (res in modules) {
            val module = res.parentFile.parentFile.parentFile.name
            check(module, read(File(res, "values/strings.xml")), read(File(res, "values-es/strings.xml")))
        }
    }

    @Test
    fun `every module has the same keys in English and Spanish`() =
        eachModule { module, en, es ->
            assertEquals("$module: missing in values-es", emptySet<String>(), en.keys - es.keys)
            assertEquals("$module: only in values-es (stale)", emptySet<String>(), es.keys - en.keys)
        }

    @Test
    fun `every translation keeps the format arguments`() =
        eachModule { module, en, es ->
            for ((key, e) in en) {
                val s = es[key] ?: continue
                assertEquals("$module $key: format arguments", e.args(), s.args())
            }
        }

    @Test
    fun `texts use the ellipsis character, not three dots`() =
        eachModule { module, en, es ->
            for ((lang, words) in listOf("en" to en, "es" to es)) {
                for (r in words.values) {
                    r.items.values.forEach { assertTrue("$module ${r.name} ($lang): \"...\" instead of \"…\"", "..." !in it) }
                }
            }
        }

    /**
     * *queda* (one) / *quedan* (other) agree with the time left: inside `plurals`, or in a `_one` / `_other` pair that
     * the code picks by the duration's quantity (`MuteText`); never a plain string the code can't choose.
     */
    @Test
    fun `spanish time left agrees with its quantity`() =
        eachModule { module, _, es ->
            for (r in es.values) {
                for ((quantity, text) in r.items) {
                    val word = QUEDA.find(text)?.groupValues?.get(1) ?: continue
                    val form =
                        when {
                            r.tag == "plurals" -> quantity
                            r.name.endsWith("_one") -> "one"
                            r.name.endsWith("_other") -> "other"
                            else -> null
                        }
                    assertTrue("$module ${r.name}: \"$word %…\" in a plain string; use plurals or a _one / _other pair", form != null)
                    val want = if (form == "one") "queda" else "quedan"
                    assertEquals("$module ${r.name} ($form)", want, word)
                }
            }
        }

    @Test
    fun `the patterns find what the rules check`() {
        assertEquals(setOf("%1\$s", "%2\$d", "%.1f"), Res("string", "a", mapOf("" to "%1\$s and %2\$d, %.1f, 100%%")).args())
        assertEquals("quedan", QUEDA.find("Sensor: quedan %1\$s")?.groupValues?.get(1))
        assertEquals("queda", QUEDA.find("Alertas silenciadas · queda %1\$s")?.groupValues?.get(1))
        assertEquals(null, QUEDA.find("No queda nada registrado de esta lectura."))
    }

    private companion object {
        val TAGS = setOf("string", "plurals", "string-array")
        val FORMAT = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z%]""")
        val QUEDA = Regex("""\b(quedan?)\s+%""")
    }
}
