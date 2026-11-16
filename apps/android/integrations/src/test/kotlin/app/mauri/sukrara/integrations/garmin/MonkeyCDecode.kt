// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import java.nio.ByteBuffer

/**
 * Reads the Connect IQ message format back ([MonkeyC] writes it), as the watch does: the string block, then the data
 * block's values, each list or map filled in the order it was met. Tests only.
 */
object MonkeyCDecode {
    fun decode(bytes: ByteArray): Any? {
        val buf = ByteBuffer.wrap(bytes)
        var strings = ByteArray(0)
        var magic = buf.int
        if (magic == 0xABCDABCD.toInt()) {
            strings = ByteArray(buf.int).also { buf.get(it) }
            magic = buf.int
        }
        check(magic == 0xDA7ADA7A.toInt()) { "no data block" }
        val data = ByteBuffer.wrap(ByteArray(buf.int).also { buf.get(it) })
        check(!buf.hasRemaining()) { "bytes after the data block" }

        fun string(offset: Int): String {
            val s = ByteBuffer.wrap(strings, offset, strings.size - offset)
            val n = s.short.toInt()
            val b = ByteArray(n - 1).also { s.get(it) }
            check(s.get() == 0.toByte()) { "string not terminated" }
            return String(b, Charsets.UTF_8)
        }

        // a container's place: filled later, in order
        val pending = ArrayDeque<Pair<Any, Int>>()

        fun one(): Any? =
            when (val type = data.get().toInt()) {
                0 -> {
                    null
                }

                1 -> {
                    data.int
                }

                2 -> {
                    data.float
                }

                3 -> {
                    string(data.int)
                }

                5 -> {
                    ArrayList<Any?>().also { pending.addLast(it to data.int) }
                }

                9 -> {
                    data.get() != 0.toByte()
                }

                11 -> {
                    LinkedHashMap<Any?, Any?>().also { pending.addLast(it to data.int) }
                }

                14 -> {
                    data.long
                }

                15 -> {
                    data.double
                }

                else -> {
                    error("type $type")
                }
            }

        val root = one()
        while (pending.isNotEmpty()) {
            val (c, n) = pending.removeFirst()
            @Suppress("UNCHECKED_CAST")
            when (c) {
                is ArrayList<*> -> repeat(n) { (c as ArrayList<Any?>).add(one()) }
                is LinkedHashMap<*, *> -> repeat(n) { (c as LinkedHashMap<Any?, Any?>)[one()] = one() }
            }
        }
        check(!data.hasRemaining()) { "values left over" }
        return root
    }
}
