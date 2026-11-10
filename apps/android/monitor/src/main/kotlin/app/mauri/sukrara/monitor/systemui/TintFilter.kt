// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Our number icons' range colour as a colour filter of their own (#118): System UI's Compose `StatusBarIcon` hands
 * every icon a `BlendModeColorFilter`; the module draws ours with a new one in the range colour and leaves System UI's
 * untouched, so no other icon can ever take Sukrara's colour, also if System UI starts sharing one filter between
 * icons. Plain reflection over the filter's class, so the JVM tests run it on a class of the same shape.
 *
 * The constructor is found by shape (`(color: Long, blendMode: Int, …)` as Kotlin compiles Compose's value classes);
 * any further parameter takes the platform filter when it can, else null (Kotlin's constructor marker).
 */
class TintFilter private constructor(
    private val constructor: Constructor<*>,
    private val blendMode: Field?,
    private val nativeType: Class<*>,
) {
    /** The constructor takes the platform filter (else Compose builds its own from the colour and the mode). */
    val takesNative: Boolean get() = constructor.parameterTypes.drop(2).any { it.isAssignableFrom(nativeType) }

    /** A new filter in [argb] with [original]'s blend mode and [native] (the platform filter) where it is taken. */
    fun make(
        original: Any,
        argb: Int,
        native: Any,
    ): Any {
        val mode = blendMode?.getInt(original) ?: SRC_IN
        val args =
            constructor.parameterTypes.mapIndexed { i, type ->
                when {
                    i == 0 -> packed(argb)
                    i == 1 -> mode
                    type.isAssignableFrom(nativeType) -> native
                    else -> null
                }
            }
        return constructor.newInstance(*args.toTypedArray())
    }

    companion object {
        /** Compose's `BlendMode.SrcIn`, when the original's mode can't be read. */
        const val SRC_IN = 5

        /** Compose's packed sRGB colour of [argb]: ARGB in the high 32 bits. */
        fun packed(argb: Int): Long = (argb.toLong() and 0xFFFFFFFFL) shl 32

        /**
         * How to build [filterClass] (`androidx.compose.ui.graphics.BlendModeColorFilter`) with [nativeType]
         * (`android.graphics.ColorFilter`), or null when it has no constructor of that shape. Prefers one that takes
         * the platform filter, then the fewest parameters.
         */
        fun of(
            filterClass: Class<*>,
            nativeType: Class<*>,
        ): TintFilter? {
            val long = Long::class.javaPrimitiveType
            val int = Int::class.javaPrimitiveType
            val shaped =
                filterClass.declaredConstructors.filter { c ->
                    val p = c.parameterTypes
                    p.size >= 2 && p[0] == long && p[1] == int && p.drop(2).none { it.isPrimitive }
                }
            val chosen =
                shaped.minWithOrNull(
                    compareBy<Constructor<*>> { c -> if (c.parameterTypes.drop(2).any { it.isAssignableFrom(nativeType) }) 0 else 1 }
                        .thenBy { it.parameterCount },
                ) ?: return null
            chosen.isAccessible = true
            val mode =
                filterClass.declaredFields
                    .filter { it.type == int && !Modifier.isStatic(it.modifiers) }
                    .let { ints -> ints.firstOrNull { it.name == "blendMode" } ?: ints.singleOrNull() }
                    ?.apply { isAccessible = true }
            return TintFilter(chosen, mode, nativeType)
        }
    }
}
