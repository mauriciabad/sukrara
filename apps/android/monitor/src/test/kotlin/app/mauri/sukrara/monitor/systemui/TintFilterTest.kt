// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The range colour goes on a filter of our own (#118): System UI's filter object is never changed. Run on classes of
 * the shapes Kotlin compiles Compose's `BlendModeColorFilter` to (value-class parameters: `long`, `int`).
 */
class TintFilterTest {
    /** android.graphics.ColorFilter's stand-in. */
    open class Native

    class Marker

    /** Compose's: the internal constructor takes the platform filter, the public one builds it. */
    @Suppress("unused")
    class Full private constructor(
        val color: Long,
        val blendMode: Int,
        val native: Native?,
    ) {
        constructor(color: Long, blendMode: Int, native: Native, marker: Marker?) : this(color, blendMode, native)
        constructor(color: Long, blendMode: Int, marker: Marker?) : this(color, blendMode, null as Native?)
    }

    /** What R8 may leave: only the public constructor. */
    class Public(
        val color: Long,
        val blendMode: Int,
        @Suppress("unused") marker: Marker?,
    )

    class Other(
        val color: Int,
    )

    private val red = 0xFFE53935.toInt()

    @Test fun `a new filter in our colour, System UI's left as it was`() {
        val tint = checkNotNull(TintFilter.of(Full::class.java, Native::class.java))
        assertTrue(tint.takesNative)
        val system = Full(TintFilter.packed(0xFFFFFFFF.toInt()), 3, Native(), null)
        val native = Native()
        val ours = tint.make(system, red, native) as Full
        assertNotSame(system, ours)
        assertEquals(TintFilter.packed(red), ours.color)
        assertEquals(3, ours.blendMode) // System UI's mode
        assertSame(native, ours.native)
        // the shared one is untouched
        assertEquals(TintFilter.packed(0xFFFFFFFF.toInt()), system.color)
        assertEquals(3, system.blendMode)
    }

    @Test fun `without the platform filter's constructor Compose builds its own`() {
        val tint = checkNotNull(TintFilter.of(Public::class.java, Native::class.java))
        val ours = tint.make(Public(0, 5, null), red, Native()) as Public
        assertEquals(TintFilter.packed(red), ours.color)
        assertEquals(5, ours.blendMode)
    }

    @Test fun `another shape is no filter`() = assertNull(TintFilter.of(Other::class.java, Native::class.java))

    @Test fun `colours pack as Compose's sRGB`() {
        assertEquals(0xFFE53935L shl 32, TintFilter.packed(red))
        assertEquals(0L, TintFilter.packed(0))
    }
}
