// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * The Connect IQ message format (what Garmin Connect hands a watch app as `Communications.PhoneAppMessage.data`):
 * an optional string block (`0xABCDABCD`, its length, each distinct string once as a 2-byte length counting the
 * terminating 0, the UTF-8 bytes, 0) and a data block (`0xDA7ADA7A`, its length, the values), big-endian. A list or
 * map writes its type and size where it stands; its items follow later, container by container in the order they were
 * met (breadth first), a map's as key, value, key, value. Written for Sukrara (contracts/watchface/payload checks the
 * bytes), so the app needs no Garmin library.
 */
object MonkeyC {
    private const val STRINGS = 0xABCDABCD.toInt()
    private const val DATA = 0xDA7ADA7A.toInt()

    private const val NULL: Int = 0
    private const val INT: Int = 1
    private const val FLOAT: Int = 2
    private const val STRING: Int = 3
    private const val ARRAY: Int = 5
    private const val BOOLEAN: Int = 9
    private const val HASH: Int = 11
    private const val LONG: Int = 14
    private const val DOUBLE: Int = 15

    /** [value]: null, Int, Long, Float, Double, Boolean, String, a List or a Map of those (keys too). */
    fun encode(value: Any?): ByteArray {
        val strings = LinkedHashMap<String, Int>()
        var stringBytes = 0
        val data = ByteArrayOutputStream()
        val out = DataOutputStream(data)
        val containers = ArrayDeque<Any>()

        fun one(v: Any?) {
            when (v) {
                null -> {
                    out.writeByte(NULL)
                }

                is Int -> {
                    out.writeByte(INT)
                    out.writeInt(v)
                }

                is Long -> {
                    out.writeByte(LONG)
                    out.writeLong(v)
                }

                is Float -> {
                    out.writeByte(FLOAT)
                    out.writeFloat(v)
                }

                is Double -> {
                    out.writeByte(DOUBLE)
                    out.writeDouble(v)
                }

                is Boolean -> {
                    out.writeByte(BOOLEAN)
                    out.writeByte(if (v) 1 else 0)
                }

                is String -> {
                    val offset =
                        strings.getOrPut(v) {
                            stringBytes.also { stringBytes += 2 + v.toByteArray(Charsets.UTF_8).size + 1 }
                        }
                    out.writeByte(STRING)
                    out.writeInt(offset)
                }

                is List<*> -> {
                    out.writeByte(ARRAY)
                    out.writeInt(v.size)
                    containers.addLast(v)
                }

                is Map<*, *> -> {
                    out.writeByte(HASH)
                    out.writeInt(v.size)
                    containers.addLast(v)
                }

                else -> {
                    throw IllegalArgumentException("not a Monkey C value: ${v::class.simpleName}")
                }
            }
        }

        one(value)
        while (containers.isNotEmpty()) {
            when (val c = containers.removeFirst()) {
                is List<*> -> {
                    c.forEach(::one)
                }

                is Map<*, *> -> {
                    c.forEach { (k, v) ->
                        one(k)
                        one(v)
                    }
                }
            }
        }
        out.flush()
        val body = data.toByteArray()
        val buf = ByteBuffer.allocate((if (strings.isEmpty()) 0 else 8 + stringBytes) + 8 + body.size)
        if (strings.isNotEmpty()) {
            buf.putInt(STRINGS).putInt(stringBytes)
            for (s in strings.keys) {
                val b = s.toByteArray(Charsets.UTF_8)
                buf
                    .putShort((b.size + 1).toShort())
                    .put(b)
                    .put(0)
            }
        }
        buf.putInt(DATA).putInt(body.size).put(body)
        return buf.array()
    }
}
